package nl.giejay.android.tv.immich.shared.util

import nl.giejay.mediaslider.model.SliderItem
import nl.giejay.mediaslider.model.SliderItemType
import nl.giejay.mediaslider.model.SliderItemViewHolder
import nl.giejay.android.tv.immich.api.util.ApiUtil
import nl.giejay.android.tv.immich.api.model.Asset
import nl.giejay.android.tv.immich.card.Card
import nl.giejay.mediaslider.model.MetaDataType
import nl.giejay.mediaslider.model.StaticMetaDataProvider
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_FORCE_ORIGINAL_VIDEO
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

fun List<Asset>.toSliderItems(keepOrder: Boolean, mergePortrait: Boolean): List<SliderItemViewHolder> {
    if (!mergePortrait) {
        return this.map { SliderItemViewHolder(it.toSliderItem()) }
    }
    if (!keepOrder) {
        val portraits = this.filter { it.isPortraitImage() }
        val queue = portraits.toMutableList()
        val portraitSliders = mutableListOf<SliderItemViewHolder>()

        while (queue.isNotEmpty()) {
            val first = queue.removeAt(0)
            val firstDate = first.fileModifiedAt?.time

            // pick the second portrait image that is most furthest away in time to the first one
            val second = if (first.people?.isNotEmpty() == true && firstDate != null) {
                // Try strict match: same people size and city
                queue.filter { candidate ->
                    candidate.people?.size == first.people.size &&
                            candidate.people.firstOrNull()?.id == first.people.first().id &&
                            candidate.exifInfo?.city != null && first.exifInfo?.city != null &&
                            candidate.exifInfo.city == first.exifInfo.city &&
                            candidate.exifInfo.dateTimeOriginal != null
                }.maxByOrNull { candidate ->
                    kotlin.math.abs(candidate.exifInfo!!.dateTimeOriginal!!.time - firstDate)
                } ?: // Fallback: only same people id
                queue.filter { candidate ->
                    candidate.people?.firstOrNull()?.id == first.people.first().id &&
                            candidate.exifInfo?.dateTimeOriginal != null
                }.maxByOrNull { candidate ->
                    kotlin.math.abs(candidate.exifInfo!!.dateTimeOriginal!!.time - firstDate)
                }
            } else null // no people or date info, or simply no match with the first, just add a random one next to it

            if (second != null) {
                portraitSliders.add(SliderItemViewHolder(first.toSliderItem(), second.toSliderItem()))
                queue.remove(second)
            } else {
                portraitSliders.add(SliderItemViewHolder(first.toSliderItem(), queue.removeFirstOrNull()?.toSliderItem()))
            }
        }

        val landscapeItems = this - portraits.toSet()
        return (landscapeItems.map { SliderItemViewHolder(it.toSliderItem()) } + portraitSliders).shuffled()
    }

    val queue = this.toMutableList()
    val items = mutableListOf<SliderItemViewHolder>()

    while (queue.isNotEmpty()) {
        val first = queue.removeAt(0)
        val second = queue.firstOrNull()

        if (first.isPortraitImage() && second?.isPortraitImage() == true) {
            items.add(SliderItemViewHolder(first.toSliderItem(), second.toSliderItem()))
            queue.remove(second)
        } else {
            items.add(SliderItemViewHolder(first.toSliderItem()))
        }
    }
    return items
}

fun Asset.toSliderItem(): SliderItem {
    return SliderItem(this.id,
        ApiUtil.getFileUrl(this.id, this.type, PreferenceManager.get(SLIDER_FORCE_ORIGINAL_VIDEO)),
        SliderItemType.valueOf(this.type.uppercase()),
        this.exifInfo?.orientation ?: 1,
        mapOf(MetaDataType.DATE to this.exifInfo?.dateTimeOriginal?.let { formatDate(it) },
            MetaDataType.CITY to this.exifInfo?.city,
            MetaDataType.COUNTRY to this.exifInfo?.country,
            MetaDataType.DESCRIPTION to this.exifInfo?.description,
            MetaDataType.FILENAME to this.originalFileName,
            MetaDataType.PEOPLE to this.people?.map { it.name }?.filter { it?.isNotBlank() == true }?.joinToString(", "),
            MetaDataType.FILEPATH to this.originalPath,
            MetaDataType.CAMERA to (listOf(this.exifInfo?.make, this.exifInfo?.model)).filterNotNull().joinToString(" "))
            .mapValues { StaticMetaDataProvider(it.value) } +
                mapOf(MetaDataType.ALBUM_NAME to AlbumMetaDataProvider(this.id)),
        ApiUtil.getThumbnailUrl(this.id, "preview"))
}

private fun formatDate(date: Date): String {
    val calendar = Calendar.getInstance()
    calendar.time = date
    val locale = Locale.getDefault(Locale.Category.FORMAT)
    val isEnglish = locale.language == "en"
    val day = calendar[Calendar.DATE]
    val formatString = if (isEnglish) {
        // English: Friday, 7th April 2006
        when (day) {
            1, 21, 31 -> "EEEE, d'st' MMMM yyyy"
            2, 22 -> "EEEE, d'nd' MMMM yyyy"
            3, 23 -> "EEEE, d'rd' MMMM yyyy"
            else -> "EEEE, d'th' MMMM yyyy"
        }
    } else {
        // All other locales: Freitag, 7. April 2006
        "EEEE, d. MMMM yyyy"
    }
    return SimpleDateFormat(formatString, locale).format(date)
}

fun Asset.isPortraitImage(): Boolean {
    return (this.exifInfo?.orientation == 6 || (this.exifInfo?.exifImageWidth != null && this.exifInfo.exifImageHeight != null && this.exifInfo.exifImageWidth - 100 < this.exifInfo.exifImageHeight)) && this.type == SliderItemType.IMAGE.toString()
}

fun List<Asset>.toCards(): List<Card> {
    return this.map {
        it.toCard()
    }
}

fun Asset.toCard(): Card {
    return Card(this.deviceAssetId ?: "",
        this.exifInfo?.description ?: "",
        this.id,
        ApiUtil.getThumbnailUrl(this.id, "thumbnail"),
        ApiUtil.getThumbnailUrl(this.id, "preview"))
}
