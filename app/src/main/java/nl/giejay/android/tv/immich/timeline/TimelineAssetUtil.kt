package nl.giejay.android.tv.immich.timeline

import nl.giejay.android.tv.immich.api.model.TimelineAsset
import nl.giejay.android.tv.immich.api.util.ApiUtil
import nl.giejay.android.tv.immich.card.Card
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_FORCE_ORIGINAL_VIDEO
import nl.giejay.android.tv.immich.shared.util.AlbumMetaDataProvider
import nl.giejay.android.tv.immich.shared.util.AssetDetailMetaDataProvider
import nl.giejay.mediaslider.model.MetaDataType
import nl.giejay.mediaslider.model.SliderItem
import nl.giejay.mediaslider.model.SliderItemType
import nl.giejay.mediaslider.model.SliderItemViewHolder
import nl.giejay.mediaslider.model.StaticMetaDataProvider
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

fun TimelineAsset.toCard(): Card = Card(
    title = "",
    description = null,
    id = id,
    thumbnailUrl = ApiUtil.getThumbnailUrl(id, "thumbnail"),
    backgroundUrl = ApiUtil.getThumbnailUrl(id, "preview")
)

fun TimelineAsset.toSliderItem(): SliderItem {
    val type = if (isImage) SliderItemType.IMAGE else SliderItemType.VIDEO
    // ScreenSlidePagerAdapter uses TextureView when orientation != 1. That's required for
    // portrait/rotated video and is more reliable under Leanback than SurfaceView (which
    // often shows a black frame with edge artifacts on Android TV). Bucket responses have
    // no EXIF orientation — prefer TextureView for all timeline videos.
    val orientation = if (isImage) 1 else 6
    return SliderItem(
        id,
        ApiUtil.getFileUrl(id, type.name, PreferenceManager.get(SLIDER_FORCE_ORIGINAL_VIDEO)),
        type,
        orientation,
        mapOf(
            MetaDataType.DATE to StaticMetaDataProvider(formatTimelineDate(fileCreatedAt)),
            MetaDataType.CITY to AssetDetailMetaDataProvider(id, MetaDataType.CITY),
            MetaDataType.COUNTRY to AssetDetailMetaDataProvider(id, MetaDataType.COUNTRY),
            MetaDataType.DESCRIPTION to AssetDetailMetaDataProvider(id, MetaDataType.DESCRIPTION),
            MetaDataType.FILENAME to AssetDetailMetaDataProvider(id, MetaDataType.FILENAME),
            MetaDataType.PEOPLE to AssetDetailMetaDataProvider(id, MetaDataType.PEOPLE),
            MetaDataType.FILEPATH to AssetDetailMetaDataProvider(id, MetaDataType.FILEPATH),
            MetaDataType.CAMERA to AssetDetailMetaDataProvider(id, MetaDataType.CAMERA),
            MetaDataType.ALBUM_NAME to AlbumMetaDataProvider(id)
        ),
        ApiUtil.getThumbnailUrl(id, "preview"),
        isPanoramaTimeline()
    )
}

fun TimelineAsset.toSliderItemViewHolder(): SliderItemViewHolder =
    SliderItemViewHolder(toSliderItem())

fun TimelineAsset.isPanoramaTimeline(): Boolean =
    isImage && (ratio <= 0.56 || ratio > 2.0)

private fun formatTimelineDate(date: OffsetDateTime): String {
    val locale = Locale.getDefault(Locale.Category.FORMAT)
    val day = date.dayOfMonth
    val pattern = if (locale.language == "en") {
        when (day) {
            1, 21, 31 -> "EEEE, d'st' MMMM yyyy"
            2, 22 -> "EEEE, d'nd' MMMM yyyy"
            3, 23 -> "EEEE, d'rd' MMMM yyyy"
            else -> "EEEE, d'th' MMMM yyyy"
        }
    } else {
        "EEEE, d. MMMM yyyy"
    }
    return DateTimeFormatter.ofPattern(pattern, locale).format(date)
}
