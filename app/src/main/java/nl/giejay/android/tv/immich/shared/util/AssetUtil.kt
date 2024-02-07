package nl.giejay.android.tv.immich.shared.util

import com.zeuskartik.mediaslider.SliderItem
import com.zeuskartik.mediaslider.SliderItemType
import nl.giejay.android.tv.immich.api.ApiUtil
import nl.giejay.android.tv.immich.api.model.Asset
import nl.giejay.android.tv.immich.card.Card

fun List<Asset>.toSliderItems(): List<SliderItem> {
    return this.map {
        SliderItem(
            ApiUtil.getFileUrl(it.id),
            SliderItemType.valueOf(it.type.uppercase()),
            it.exifInfo?.description?.ifEmpty { it.deviceAssetId } ?: it.deviceAssetId,
            ApiUtil.getThumbnailUrl(it.id)
        )
    }
}

fun Asset.toCard(): Card {
    return Card(
        this.deviceAssetId,
        this.exifInfo?.description ?: "",
        this.id,
        ApiUtil.getThumbnailUrl(this.id),
        ApiUtil.getFileUrl(this.id)
    )
}

