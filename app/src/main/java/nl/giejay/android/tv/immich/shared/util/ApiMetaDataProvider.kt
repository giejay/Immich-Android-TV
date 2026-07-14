package nl.giejay.android.tv.immich.shared.util

import android.os.Parcel
import android.os.Parcelable
import arrow.core.Either
import arrow.core.Option
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
import java.util.concurrent.ConcurrentHashMap

class AlbumMetaDataProvider(private val assetId: String) : MetaDataProvider {
    constructor(parcel: Parcel) : this(parcel.readString()!!)

    override suspend fun getValue(): String? {
        val map: Either<String, String> = ApiClient.getClient(
            ApiClientConfig(
                PreferenceManager.get(HOST_NAME),
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
 * Lazy [GET /assets/{id}] metadata for slider details. Used by the timeline mosaic (which only
 * has thin bucket payloads) and by memories (which omit EXIF/people entirely). DATE is usually
 * supplied statically from the list response; when missing, it is resolved here on open.
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
        return when (field) {
            MetaDataType.DATE -> {
                val date = asset.exifInfo?.dateTimeOriginal
                    ?: asset.fileCreatedAt
                    ?: asset.fileModifiedAt
                date?.let { formatAssetDate(it) }
            }
            MetaDataType.CITY -> asset.exifInfo?.city
            MetaDataType.COUNTRY -> asset.exifInfo?.country
            MetaDataType.DESCRIPTION -> asset.exifInfo?.description
            MetaDataType.FILENAME -> asset.originalFileName
            MetaDataType.FILEPATH -> asset.originalPath
            MetaDataType.PEOPLE -> asset.people
                ?.mapNotNull { it.name }
                ?.filter { it.isNotBlank() }
                ?.joinToString(", ")
            MetaDataType.CAMERA -> listOfNotNull(asset.exifInfo?.make, asset.exifInfo?.model)
                .joinToString(" ")
                .ifBlank { null }
            else -> null
        }
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

object AssetDetailCache {
    private val cache = ConcurrentHashMap<String, Asset>()

    /**
     * Returns the asset or throws if the API call fails. Callers should not mark a metadata
     * field as "fetched" on failure so opening details can retry.
     */
    suspend fun get(assetId: String): Asset {
        cache[assetId]?.let { return it }
        val client = ApiClient.getClient(
            ApiClientConfig(
                PreferenceManager.get(HOST_NAME),
                PreferenceManager.get(API_KEY),
                PreferenceManager.get(DISABLE_SSL_VERIFICATION),
                PreferenceManager.get(DEBUG_MODE)
            )
        )
        return client.getAsset(assetId).fold(
            { error -> throw IllegalStateException("Failed to load asset $assetId: $error") },
            { asset ->
                cache[assetId] = asset
                asset
            }
        )
    }
}
