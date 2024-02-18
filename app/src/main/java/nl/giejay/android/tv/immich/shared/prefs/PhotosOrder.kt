package nl.giejay.android.tv.immich.shared.prefs

import nl.giejay.android.tv.immich.api.model.Asset
import nl.giejay.android.tv.immich.shared.util.Utils.compareToNullSafe

enum class PhotosOrder(val sort: Comparator<Asset>, val title: String) {
    NEWEST_OLDEST(
        { a1, a2 ->
            (a2.exifInfo?.dateTimeOriginal ?: a2.fileModifiedAt)?.compareToNullSafe(
                a1.exifInfo?.dateTimeOriginal ?: a1.fileModifiedAt
            ) ?: 1
        },
        "Newest - Oldest"
    ),
    OLDEST_NEWEST(
        { a1, a2 ->
            (a1.exifInfo?.dateTimeOriginal ?: a1.fileModifiedAt)?.compareToNullSafe(
                a2.exifInfo?.dateTimeOriginal ?: a2.fileModifiedAt
            ) ?: 1
        },
        "Oldest - Newest"
    ),
    ALPHABETICALLY({ a1, a2 -> a1.deviceAssetId?.compareToNullSafe(a2.deviceAssetId) ?: 1 }, "Alphabetically")
}