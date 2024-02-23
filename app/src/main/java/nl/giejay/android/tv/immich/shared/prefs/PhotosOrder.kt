package nl.giejay.android.tv.immich.shared.prefs

import nl.giejay.android.tv.immich.api.model.Asset
import nl.giejay.android.tv.immich.shared.util.Utils.compareToNullSafe

enum class PhotosOrder(val sort: Comparator<Asset>) {
    NEWEST_OLDEST(
        { a1, a2 ->
            (a2.exifInfo?.dateTimeOriginal ?: a2.fileModifiedAt)?.compareToNullSafe(
                a1.exifInfo?.dateTimeOriginal ?: a1.fileModifiedAt
            ) ?: 1
        }
    ),
    OLDEST_NEWEST(
        { a1, a2 ->
            (a1.exifInfo?.dateTimeOriginal ?: a1.fileModifiedAt)?.compareToNullSafe(
                a2.exifInfo?.dateTimeOriginal ?: a2.fileModifiedAt
            ) ?: 1
        }
    ),
    ALPHABETICALLY_A_Z({ a1, a2 -> a1.deviceAssetId?.compareToNullSafe(a2.deviceAssetId) ?: 1 }),
    ALPHABETICALLY_Z_A({ a2, a1 -> a1.deviceAssetId?.compareToNullSafe(a2.deviceAssetId) ?: 1 })
}