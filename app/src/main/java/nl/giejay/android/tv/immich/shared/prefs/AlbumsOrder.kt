package nl.giejay.android.tv.immich.shared.prefs

import nl.giejay.android.tv.immich.ImmichApplication
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.api.model.Album

enum class AlbumsOrder(val sort: Comparator<Album>) : EnumWithTitle {
    ALPHABETICALLY_A_Z(compareBy { it.albumName }) {
        override fun getTitle(): String {
            return ImmichApplication.appContext!!.getString(R.string.order_alphabetically_az)
        }
    },
    ALPHABETICALLY_Z_A(ALPHABETICALLY_A_Z.sort.reversed() ) {
        override fun getTitle(): String {
            return ImmichApplication.appContext!!.getString(R.string.order_alphabetically_za)
        }
    },
    LAST_UPDATED(compareByDescending { it.endDate } ) {
        override fun getTitle(): String {
            return ImmichApplication.appContext!!.getString(R.string.order_last_updated)
        }
    },
    LEAST_UPDATED(LAST_UPDATED.sort.reversed() ) {
        override fun getTitle(): String {
            return ImmichApplication.appContext!!.getString(R.string.order_least_updated)
        }
    },
    ASSET_COUNT(compareBy<Album> { it.assetCount }.reversed() ) {
        override fun getTitle(): String {
            return ImmichApplication.appContext!!.getString(R.string.albums_order_asset_count)
        }
    };

    companion object {
        fun valueOfSafe(name: String, default: AlbumsOrder): AlbumsOrder {
            return entries.find { it.toString() == name } ?: default
        }
    }
}
