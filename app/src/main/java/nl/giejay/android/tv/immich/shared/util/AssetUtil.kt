package nl.giejay.android.tv.immich.shared.util

import com.zeuskartik.mediaslider.SliderItem
import com.zeuskartik.mediaslider.SliderItemType
import com.zeuskartik.mediaslider.SliderItemViewHolder
import nl.giejay.android.tv.immich.api.util.ApiUtil
import nl.giejay.android.tv.immich.api.model.Asset
import nl.giejay.android.tv.immich.card.Card

fun List<Asset>.toSliderItems(keepOrder: Boolean, mergePortrait: Boolean): List<SliderItemViewHolder> {
    if(!mergePortrait){
        return this.map{SliderItemViewHolder(it.toSliderItem())}
    }
    if (!keepOrder) {
        val portraitItems = this.filter { it.isPortraitImage() }.sortedWith(compareBy<Asset> { it.people?.firstOrNull()?.id }.thenBy { it.people?.size }.thenBy { it.exifInfo?.city})
        val landscapeItems = this.minus(portraitItems.toSet())

        val portraitSliders = portraitItems.chunked(2).map { SliderItemViewHolder(it.first().toSliderItem(), it.getOrNull(1)?.toSliderItem()) }
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
    return SliderItem(
        this.id,
        ApiUtil.getFileUrl(this.id),
        SliderItemType.valueOf(this.type.uppercase()),
        if (this.exifInfo?.description?.isNotBlank() == true) this.exifInfo.description else this.exifInfo?.country,
        this.albumName ?: this.exifInfo?.city,
        this.exifInfo?.dateTimeOriginal,
        ApiUtil.getThumbnailUrl(this.id, "preview")
    )
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
    return Card(
        this.deviceAssetId ?: "",
        this.exifInfo?.description ?: "",
        this.id,
        ApiUtil.getThumbnailUrl(this.id, "thumbnail"),
        ApiUtil.getThumbnailUrl(this.id, "preview")
    )
}

