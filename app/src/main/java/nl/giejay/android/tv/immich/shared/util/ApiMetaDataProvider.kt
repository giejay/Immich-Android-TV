package nl.giejay.android.tv.immich.shared.util

import android.os.Parcel
import android.os.Parcelable
import arrow.core.Either
import arrow.core.Option
import nl.giejay.android.tv.immich.api.ApiClient
import nl.giejay.android.tv.immich.api.ApiClientConfig
import nl.giejay.android.tv.immich.shared.prefs.API_KEY
import nl.giejay.android.tv.immich.shared.prefs.DEBUG_MODE
import nl.giejay.android.tv.immich.shared.prefs.DISABLE_SSL_VERIFICATION
import nl.giejay.android.tv.immich.shared.prefs.HOST_NAME
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import nl.giejay.mediaslider.model.MetaDataProvider

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