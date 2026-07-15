package nl.giejay.android.tv.immich.timeline

import nl.giejay.android.tv.immich.api.model.Asset
import nl.giejay.android.tv.immich.api.model.TimelineAsset
import nl.giejay.android.tv.immich.api.util.ApiUtil
import nl.giejay.android.tv.immich.card.Card
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_FORCE_ORIGINAL_VIDEO
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_LOAD_EDITED_PHOTO
import nl.giejay.android.tv.immich.shared.util.AssetMetaDataMapping
import nl.giejay.mediaslider.model.SliderItem
import nl.giejay.mediaslider.model.SliderItemType
import nl.giejay.mediaslider.model.SliderItemViewHolder
import java.util.Date

fun TimelineAsset.toCard(): Card = Card(
    title = "",
    description = null,
    id = id,
    thumbnailUrl = ApiUtil.getThumbnailUrl(id, "thumbnail"),
    backgroundUrl = ApiUtil.getThumbnailUrl(id, "preview")
)

/**
 * Bucket rows only carry a thin payload — materialize a partial [Asset] so slider construction
 * shares [AssetMetaDataMapping] with full album/search assets.
 */
fun TimelineAsset.toPartialAsset(): Asset = Asset(
    id = id,
    type = if (isImage) "IMAGE" else "VIDEO",
    deviceAssetId = null,
    exifInfo = null,
    fileCreatedAt = Date.from(fileCreatedAt.toInstant()),
    fileModifiedAt = null,
    albumName = null,
    people = null,
    tags = null,
    originalPath = null,
    originalFileName = null,
    isFavorite = isFavorite
)

fun TimelineAsset.toSliderItem(): SliderItem {
    val asset = toPartialAsset()
    val type = SliderItemType.valueOf(asset.type)
    // ScreenSlidePagerAdapter uses TextureView when orientation != 1. Bucket responses have
    // no EXIF orientation — prefer TextureView for all timeline videos.
    val orientation = if (isImage) 1 else 6
    return SliderItem(
        asset.id,
        ApiUtil.getFileUrl(
            asset.id,
            asset.type,
            PreferenceManager.get(SLIDER_FORCE_ORIGINAL_VIDEO),
            PreferenceManager.get(SLIDER_LOAD_EDITED_PHOTO)
        ),
        type,
        orientation,
        AssetMetaDataMapping.providersFor(asset),
        ApiUtil.getThumbnailUrl(asset.id, "preview"),
        isPanorama = isPanoramaTimeline(),
        isFavorite = asset.isFavorite
    )
}

fun TimelineAsset.toSliderItemViewHolder(): SliderItemViewHolder =
    SliderItemViewHolder(toSliderItem())

fun TimelineAsset.isPanoramaTimeline(): Boolean =
    isImage && (ratio <= 0.56 || ratio > 2.0)
