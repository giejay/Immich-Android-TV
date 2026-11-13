package nl.giejay.android.tv.immich.shared.prefs

import nl.giejay.android.tv.immich.ImmichApplication
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.api.model.Asset
import nl.giejay.android.tv.immich.shared.util.Utils.compareToNullSafe

enum class PhotosOrder(val sort: Comparator<Asset>): EnumWithTitle {
    NEWEST_OLDEST(
        { a1, a2 ->
            (a2.exifInfo?.dateTimeOriginal ?: a2.fileModifiedAt)?.compareToNullSafe(
                a1.exifInfo?.dateTimeOriginal ?: a1.fileModifiedAt
            ) ?: 1
        }
    ) {
        override fun getTitle(): String {
            return ImmichApplication.appContext!!.getString(R.string.order_newest_oldest)
        }
    },
    OLDEST_NEWEST(
        { a1, a2 ->
            (a1.exifInfo?.dateTimeOriginal ?: a1.fileModifiedAt)?.compareToNullSafe(
                a2.exifInfo?.dateTimeOriginal ?: a2.fileModifiedAt
            ) ?: 1
        }

    ) {
        override fun getTitle(): String {
            return ImmichApplication.appContext!!.getString(R.string.order_oldest_newest)
        }
    },
    // Keeps the order in which assets arrive (sortedWith is stable). Only meaningful for sources that
    // deliver assets in random order themselves, e.g. album details via RandomAlbumAssetLoader.
    RANDOM({ _, _ -> 0 }) {
        override fun getTitle(): String {
            return ImmichApplication.appContext!!.getString(R.string.random)
        }
    };

    companion object {
        fun valueOfSafe(name: String, default: PhotosOrder): PhotosOrder{
            return entries.find { it.toString() == name } ?: default
        }
    }
}
