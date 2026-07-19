package nl.giejay.android.tv.immich.shared.util

import arrow.core.Either
import arrow.core.Option
import arrow.core.left
import arrow.core.right
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import nl.giejay.android.tv.immich.api.ApiClient
import nl.giejay.android.tv.immich.api.ApiClientConfig
import nl.giejay.android.tv.immich.api.model.Asset
import nl.giejay.android.tv.immich.shared.prefs.API_KEY
import nl.giejay.android.tv.immich.shared.prefs.DEBUG_MODE
import nl.giejay.android.tv.immich.shared.prefs.DISABLE_SSL_VERIFICATION
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import nl.giejay.mediaslider.model.MetaDataProvider
import nl.giejay.mediaslider.model.MetaDataType
import timber.log.Timber

class AlbumMetaDataProvider(private val assetId: String) : MetaDataProvider {
    override suspend fun getValue(): String? {
        val map: Either<String, String> = ApiClient.getClient(
            ApiClientConfig(
                PreferenceManager.hostName,
                PreferenceManager.get(API_KEY),
                PreferenceManager.get(DISABLE_SSL_VERIFICATION),
                PreferenceManager.get(DEBUG_MODE)
            )
        ).listAlbums(Option.invoke(assetId)).map { albums ->
            albums.distinct().joinToString(", ") { it.albumName }
        }
        return map.getOrNull()
    }
}

/**
 * Lazy [GET /assets/{id}] metadata for slider details. Field projection is delegated to
 * [AssetMetaDataMapping] so new metadata types are defined in one place.
 */
class AssetDetailMetaDataProvider(
    private val assetId: String,
    private val field: MetaDataType
) : MetaDataProvider {

    override suspend fun getValue(): String? {
        return AssetDetailCache.get(assetId).fold(
            { error ->
                Timber.w("Failed to load asset $assetId for field $field: $error")
                null
            },
            { asset -> AssetMetaDataMapping.valueOf(asset, field) }
        )
    }
}

/**
 * Completed-result LRU plus in-flight [Deferred] coalescing so concurrent detail requests for
 * the same asset share one network call (Caffeine-style without a new dependency).
 */
object AssetDetailCache {
    /** Cap detail payloads so long timeline/memory sessions cannot grow without bound. */
    private const val MAX_ENTRIES = 100

    // Access-order LinkedHashMap (not android.util.LruCache) so JVM unit tests can run without Robolectric.
    private val cache = object : LinkedHashMap<String, Asset>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Asset>?): Boolean =
            size > MAX_ENTRIES
    }
    private val mutex = Mutex()
    private val inFlight = mutableMapOf<String, CompletableDeferred<Either<String, Asset>>>()

    private val defaultFetch: suspend (String) -> Either<String, Asset> = { assetId ->
        val client = ApiClient.getClient(
            ApiClientConfig(
                PreferenceManager.hostName,
                PreferenceManager.get(API_KEY),
                PreferenceManager.get(DISABLE_SSL_VERIFICATION),
                PreferenceManager.get(DEBUG_MODE)
            )
        )
        client.getAsset(assetId)
    }

    /** Test seam — production uses [ApiClient.getAsset]. */
    @Volatile
    var fetchAsset: suspend (String) -> Either<String, Asset> = defaultFetch

    /**
     * Returns the asset or an error message if the API call fails.
     */
    suspend fun get(assetId: String): Either<String, Asset> {
        mutex.withLock { cache[assetId] }?.let { return it.right() }

        val (deferred, isOwner) = mutex.withLock {
            cache[assetId]?.let { cached ->
                return@withLock CompletableDeferred<Either<String, Asset>>(cached.right()) to false
            }
            inFlight[assetId]?.let { return@withLock it to false }
            val created = CompletableDeferred<Either<String, Asset>>()
            inFlight[assetId] = created
            created to true
        }

        if (isOwner) {
            try {
                val result = fetchAsset(assetId)
                result.onRight { asset ->
                    mutex.withLock { cache[assetId] = asset }
                }
                deferred.complete(result)
            } catch (e: Exception) {
                deferred.complete("Unexpected error: ${e.message}".left())
            } finally {
                mutex.withLock { inFlight.remove(assetId) }
            }
        }

        return deferred.await()
    }

    /** Test helper. */
    fun clear() {
        cache.clear()
        inFlight.clear()
        fetchAsset = defaultFetch
    }
}
