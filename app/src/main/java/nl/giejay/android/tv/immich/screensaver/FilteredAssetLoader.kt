package nl.giejay.android.tv.immich.screensaver

import arrow.core.Either
import kotlinx.coroutines.delay
import nl.giejay.android.tv.immich.api.model.Asset
import nl.giejay.android.tv.immich.api.model.AssetResponse

internal class EmptyFilteredBatchBackoff(
    private val retryDelaysMillis: List<Long> = DEFAULT_RETRY_DELAYS_MILLIS
) {
    init {
        require(retryDelaysMillis.isNotEmpty())
        require(retryDelaysMillis.all { it >= 0 })
    }

    private var attempt = 0

    fun delayBeforeNextRequestMillis(): Long = retryDelaysMillis[attempt.coerceAtMost(retryDelaysMillis.lastIndex)]

    fun recordEmptyBatch() {
        if (attempt < retryDelaysMillis.lastIndex) {
            attempt += 1
        }
    }

    fun reset() {
        attempt = 0
    }

    companion object {
        private val DEFAULT_RETRY_DELAYS_MILLIS = listOf(
            0L,
            1_000L,
            2_000L,
            4_000L,
            8_000L,
            16_000L,
            32_000L,
            60_000L,
            120_000L,
            240_000L,
            480_000L,
            960_000L,
            1_920_000L,
            3_600_000L
        )
    }
}

/**
 * Retries repeatable asset sources when every returned asset is excluded locally.
 * Errors and genuinely exhausted sources are returned without retrying.
 */
internal class FilteredAssetLoader(
    private val filterAssets: (List<Asset>) -> List<Asset>,
    private val backoff: EmptyFilteredBatchBackoff = EmptyFilteredBatchBackoff(),
    private val wait: suspend (Long) -> Unit = { delay(it) }
) {
    fun reset() {
        backoff.reset()
    }

    suspend fun load(fetch: suspend () -> Either<String, AssetResponse>): Either<String, AssetResponse> {
        while (true) {
            val retryDelayMillis = backoff.delayBeforeNextRequestMillis()
            if (retryDelayMillis > 0) {
                wait(retryDelayMillis)
            }

            when (val result = fetch()) {
                is Either.Left -> {
                    backoff.reset()
                    return result
                }
                is Either.Right -> {
                    val response = result.value
                    val filteredAssets = filterAssets(response.assets)
                    if (filteredAssets.isNotEmpty() || !response.canLoadMore) {
                        backoff.reset()
                        return Either.Right(response.copy(assets = filteredAssets))
                    }
                    backoff.recordEmptyBatch()
                }
            }
        }
    }
}
