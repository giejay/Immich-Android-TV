package nl.giejay.android.tv.immich.album

import arrow.core.Either
import arrow.core.getOrElse
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import nl.giejay.android.tv.immich.api.model.Asset
import timber.log.Timber

/**
 * Serves the assets of one album in random order, without duplicates.
 *
 * /search/random samples with replacement across requests, so a single request never yields the whole album
 * and later requests increasingly repeat what was already seen. This loader therefore works in two phases:
 *
 *  1. Random phase: keep asking the random endpoint for [randomBatchSize] assets and drop ids that were
 *     already seen. This phase ends when every asset of the album was seen, or when a batch contains too few new
 *     assets ([MIN_NEW_RATIO]) to be worth another request, or when the random endpoint returns nothing
 *     (e.g. the album is only shared with the user, which /search/random does not return) or fails.
 *  2. Sweep phase: fetch the complete album with the regular (deterministic) endpoint, drop everything seen
 *     in phase 1 and append the remainder shuffled.
 *
 * Everything is fetched lazily: [next] only makes requests while it cannot satisfy the requested count
 * from what is already buffered, so nothing beyond what the user scrolls to is loaded.
 *
 * The API calls are injected so the logic can be unit-tested without a server.
 */
class RandomAlbumAssetLoader(
    private val albumAssetCount: suspend () -> Either<String, Int>,
    private val randomFetch: suspend (size: Int) -> Either<String, List<Asset>>,
    private val sweepFetch: suspend () -> Either<String, List<Asset>>,
    private val randomBatchSize: Int = DEFAULT_RANDOM_BATCH_SIZE
) {
    private val mutex = Mutex()
    private val seenIds = HashSet<String>()
    private val buffer = ArrayDeque<Asset>()
    private var totalCount: Int? = null
    private var totalCountLoaded = false
    private var randomPhaseDone = false
    private var sweepDone = false

    /** True once every asset has been handed out; there is nothing left to load. */
    @Volatile
    var exhausted: Boolean = false
        private set

    /**
     * Returns up to [minCount] unseen assets (fewer only when the album is exhausted). A failed sweep
     * returns the error and keeps its state, so calling again retries it.
     */
    suspend fun next(minCount: Int): Either<String, List<Asset>> = mutex.withLock {
        while (buffer.size < minCount && !sweepDone) {
            if (!randomPhaseDone) {
                fetchRandomBatch()
            } else {
                sweep().onLeft { return@withLock Either.Left(it) }
            }
        }
        val result = ArrayList<Asset>(minCount)
        while (result.size < minCount && buffer.isNotEmpty()) {
            result.add(buffer.removeFirst())
        }
        exhausted = sweepDone && buffer.isEmpty()
        Either.Right(result)
    }

    private suspend fun fetchRandomBatch() {
        if (!totalCountLoaded) {
            // only an optimisation (skips the sweep for fully-seen albums), so a failure is not fatal
            totalCount = albumAssetCount().getOrNull()
            totalCountLoaded = true
        }
        val batch = randomFetch(randomBatchSize).getOrElse { error ->
            Timber.w("Random fetch for album failed, falling back to full album fetch: $error")
            randomPhaseDone = true
            return
        }
        val newAssets = batch.filter { seenIds.add(it.id) }
        buffer.addAll(newAssets)

        val count = totalCount
        when {
            count != null && seenIds.size >= count -> sweepDone = true // seen everything, nothing to sweep
            batch.isEmpty() || newAssets.size < batch.size * MIN_NEW_RATIO -> randomPhaseDone = true
        }
    }

    private suspend fun sweep(): Either<String, Unit> = sweepFetch().map { all ->
        buffer.addAll(all.filter { seenIds.add(it.id) }.shuffled())
        sweepDone = true
    }

    companion object {
        const val DEFAULT_RANDOM_BATCH_SIZE = 150

        // Once fewer than this share of a random batch is new, further random requests mostly return
        // duplicates (coupon collector tail), so the deterministic sweep is cheaper.
        const val MIN_NEW_RATIO = 0.10
    }
}
