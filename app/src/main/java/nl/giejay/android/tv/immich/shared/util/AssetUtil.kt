package nl.giejay.android.tv.immich.shared.util

import com.zeuskartik.mediaslider.SliderItem
import com.zeuskartik.mediaslider.SliderItemType
import nl.giejay.android.tv.immich.api.util.ApiUtil
import nl.giejay.android.tv.immich.api.model.Asset
import nl.giejay.android.tv.immich.card.Card

fun List<Asset>.toSliderItems(): List<SliderItem> {
    return this.map {
        SliderItem(
            ApiUtil.getFileUrl(it.id),
            SliderItemType.valueOf(it.type.uppercase()),
            it.exifInfo?.description?.ifEmpty { it.deviceAssetId } ?: it.deviceAssetId,
            ApiUtil.getThumbnailUrl(it.id, "JPEG")
        )
    }
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
        ApiUtil.getThumbnailUrl(this.id, "WEBP"),
        ApiUtil.getThumbnailUrl(this.id, "JPEG")
    )
}

