package nl.giejay.android.tv.immich.shared.util

import android.os.Parcel
import android.os.Parcelable
import arrow.core.Either
import arrow.core.Option
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import nl.giejay.android.tv.immich.api.ApiClient
import nl.giejay.android.tv.immich.api.ApiClientConfig
import nl.giejay.android.tv.immich.api.model.Asset
import nl.giejay.android.tv.immich.shared.prefs.API_KEY
import nl.giejay.android.tv.immich.shared.prefs.DEBUG_MODE
import nl.giejay.android.tv.immich.shared.prefs.DISABLE_SSL_VERIFICATION
import nl.giejay.android.tv.immich.shared.prefs.HOST_NAME
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import nl.giejay.mediaslider.model.MetaDataProvider
import nl.giejay.mediaslider.model.MetaDataType
import java.util.LinkedHashMap

class AlbumMetaDataProvider(private val assetId: String) : MetaDataProvider {
    constructor(parcel: Parcel) : this(parcel.readString()!!)

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

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(assetId)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<AlbumMetaDataProvider> {
        override fun createFromParcel(parcel: Parcel): AlbumMetaDataProvider = AlbumMetaDataProvider(parcel)
        override fun newArray(size: Int): Array<AlbumMetaDataProvider?> = arrayOfNulls(size)
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

    constructor(parcel: Parcel) : this(
        parcel.readString()!!,
        MetaDataType.valueOf(parcel.readString()!!)
    )

    override suspend fun getValue(): String? {
        val asset = AssetDetailCache.get(assetId)
        return AssetMetaDataMapping.valueOf(asset, field)
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(assetId)
        parcel.writeString(field.name)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<AssetDetailMetaDataProvider> {
        override fun createFromParcel(parcel: Parcel): AssetDetailMetaDataProvider =
            AssetDetailMetaDataProvider(parcel)

        override fun newArray(size: Int): Array<AssetDetailMetaDataProvider?> = arrayOfNulls(size)
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
    private val inFlight = mutableMapOf<String, CompletableDeferred<Asset>>()

    private val defaultFetch: suspend (String) -> Asset = { assetId ->
        val client = ApiClient.getClient(
            ApiClientConfig(
                PreferenceManager.hostName,
                PreferenceManager.get(API_KEY),
                PreferenceManager.get(DISABLE_SSL_VERIFICATION),
                PreferenceManager.get(DEBUG_MODE)
            )
        )
        client.getAsset(assetId).fold(
            { error -> throw IllegalStateException("Failed to load asset $assetId: $error") },
            { it }
        )
    }

    /** Test seam — production uses [ApiClient.getAsset]. */
    @Volatile
    var fetchAsset: suspend (String) -> Asset = defaultFetch

    /**
     * Returns the asset or throws if the API call fails. Callers should not mark a metadata
     * field as "fetched" on failure so opening details can retry.
     */
    suspend fun get(assetId: String): Asset {
        mutex.withLock { cache[assetId] }?.let { return it }

        val (deferred, isOwner) = mutex.withLock {
            cache[assetId]?.let { cached ->
                return@withLock CompletableDeferred(cached) to false
            }
            inFlight[assetId]?.let { return@withLock it to false }
            val created = CompletableDeferred<Asset>()
            inFlight[assetId] = created
            created to true
        }

        if (isOwner) {
            try {
                val asset = fetchAsset(assetId)
                mutex.withLock { cache[assetId] = asset }
                deferred.complete(asset)
            } catch (e: Exception) {
                deferred.completeExceptionally(e)
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
