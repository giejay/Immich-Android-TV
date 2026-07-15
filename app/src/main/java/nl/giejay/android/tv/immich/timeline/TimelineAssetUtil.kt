package nl.giejay.android.tv.immich.timeline

import nl.giejay.android.tv.immich.api.model.Asset
import nl.giejay.android.tv.immich.api.model.AssetExifInfo
import nl.giejay.android.tv.immich.api.model.TimelineAsset
import nl.giejay.android.tv.immich.api.util.ApiUtil
import nl.giejay.android.tv.immich.card.Card
import nl.giejay.android.tv.immich.shared.util.toSliderItem
import nl.giejay.mediaslider.model.SliderItem
import nl.giejay.mediaslider.model.SliderItemViewHolder
import java.util.Date
import kotlin.math.roundToInt

fun TimelineAsset.toCard(): Card = Card(
    title = "",
    description = null,
    id = id,
    thumbnailUrl = ApiUtil.getThumbnailUrl(id, "thumbnail"),
    backgroundUrl = ApiUtil.getThumbnailUrl(id, "preview")
)

/**
 * Bucket rows only carry a thin payload — materialize a partial [Asset] so slider construction
 * shares [Asset.toSliderItem] with full album/search assets.
 *
 * [AssetExifInfo] width/height are ratio placeholders (not real pixel dimensions) so aspect
 * helpers like panorama detection still work without EXIF from the bucket API.
 */
fun TimelineAsset.toPartialAsset(): Asset = Asset(
    id = id,
    type = if (isImage) "IMAGE" else "VIDEO",
    deviceAssetId = null,
    exifInfo = ratioPlaceholderExif(),
    fileCreatedAt = Date.from(fileCreatedAt.toInstant()),
    fileModifiedAt = null,
    albumName = null,
    people = null,
    tags = null,
    originalPath = null,
    originalFileName = null,
    isFavorite = isFavorite
)

fun TimelineAsset.toSliderItem(): SliderItem = toPartialAsset().toSliderItem()

fun TimelineAsset.toSliderItemViewHolder(): SliderItemViewHolder =
    SliderItemViewHolder(toSliderItem())

private fun TimelineAsset.ratioPlaceholderExif(): AssetExifInfo? {
    if (ratio <= 0.0) return null
    val height = 1000
    val width = (height * ratio).roundToInt().coerceAtLeast(1)
    return AssetExifInfo(
        description = null,
        orientation = null,
        exifImageWidth = width,
        exifImageHeight = height,
        city = null,
        country = null,
        dateTimeOriginal = null,
        make = null,
        model = null
    )
}
