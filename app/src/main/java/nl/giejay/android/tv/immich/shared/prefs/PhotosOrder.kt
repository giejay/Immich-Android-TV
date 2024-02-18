package nl.giejay.android.tv.immich.shared.prefs

import nl.giejay.android.tv.immich.api.model.Asset

enum class PhotosOrder(val sort: Comparator<Asset>, val title: String) {
    NEWEST_OLDEST({ a1, a2 -> (a2.exifInfo?.dateTimeOriginal ?: a2.fileModifiedAt).compareTo(a1.exifInfo?.dateTimeOriginal ?: a1.fileModifiedAt) }, "Newest - Oldest"),
    OLDEST_NEWEST({ a1, a2 -> (a1.exifInfo?.dateTimeOriginal ?: a1.fileModifiedAt).compareTo(a2.exifInfo?.dateTimeOriginal ?: a2.fileModifiedAt) }, "Oldest - Newest"),
    ALPHABETICALLY({ a1, a2 -> a1.deviceAssetId.compareTo(a2.deviceAssetId) }, "Alphabetically")
}