package nl.giejay.mediaslider.config

import android.os.Parcel
import android.os.Parcelable
import nl.giejay.mediaslider.adapter.MetaDataItem
import nl.giejay.mediaslider.model.SliderItemViewHolder
import nl.giejay.mediaslider.transformations.GlideTransformations
import nl.giejay.mediaslider.util.LoadMore
import nl.giejay.mediaslider.util.MetaDataConverter


class MediaSliderConfiguration : Parcelable {
    val startPosition: Int
    val interval: Int
    val isOnlyUseThumbnails: Boolean
    var isVideoSoundEnable: Boolean
    val animationSpeedMillis: Int
    val maxCutOffHeight: Int
    val maxCutOffWidth: Int
    val glideTransformation: GlideTransformations
    val debugEnabled: Boolean
    val metaDataConfig: List<MetaDataItem>
    private val gradiantOverlay: Boolean
    val enableSlideAnimation: Boolean
    val zoomAndScrollPanorama: Boolean
    val zoomEffectPercent: Int
    val panEffectPercent: Int
    val useLargeVideoBuffer: Boolean

    constructor(startPosition: Int,
                interval: Int,
                onlyUseThumbnails: Boolean,
                isVideoSoundEnable: Boolean,
                assets: List<SliderItemViewHolder>,
                loadMore: LoadMore?,
                onAssetSelected: (SliderItemViewHolder) -> Unit = {},
                animationSpeedMillis: Int,
                maxCutOffHeight: Int,
                maxCutOffWidth: Int,
                transformation: GlideTransformations,
                debugEnabled: Boolean,
                gradiantOverlay: Boolean,
                enableSlideAnimation: Boolean,
                metaDataConfig: List<MetaDataItem>,
                zoomAndScrollPanorama: Boolean,
                zoomEffectPercent: Int,
                panEffectPercent: Int,
                useLargeVideoBuffer: Boolean = false) {
        this.startPosition = startPosition
        this.interval = interval
        this.isOnlyUseThumbnails = onlyUseThumbnails
        this.isVideoSoundEnable = isVideoSoundEnable
        this.metaDataConfig = metaDataConfig
        Companion.loadMore = loadMore
        Companion.assets = assets
        Companion.onAssetSelected = onAssetSelected
        this.animationSpeedMillis = animationSpeedMillis
        this.maxCutOffHeight = maxCutOffHeight
        this.maxCutOffWidth = maxCutOffWidth
        this.glideTransformation = transformation
        this.debugEnabled = debugEnabled
        this.gradiantOverlay = gradiantOverlay
        this.enableSlideAnimation = enableSlideAnimation
        this.zoomAndScrollPanorama = zoomAndScrollPanorama
        this.zoomEffectPercent = zoomEffectPercent
        this.panEffectPercent = panEffectPercent
        this.useLargeVideoBuffer = useLargeVideoBuffer
    }

    private constructor(`in`: Parcel) {
        startPosition = `in`.readInt()
        interval = `in`.readInt()
        isOnlyUseThumbnails = `in`.readByte().toInt() != 0
        isVideoSoundEnable = `in`.readByte().toInt() != 0
        this.animationSpeedMillis = `in`.readInt()
        this.maxCutOffHeight = `in`.readInt()
        this.maxCutOffWidth = `in`.readInt()
        this.glideTransformation = GlideTransformations.valueOfSafe(`in`.readString()!!, GlideTransformations.CENTER_INSIDE)
        this.debugEnabled = `in`.readInt() == 1
        this.gradiantOverlay = `in`.readInt() == 1
        this.enableSlideAnimation = `in`.readInt() == 1
        metaDataConfig = MetaDataConverter.metaDataListFromJson(`in`.readString()!!)
        zoomAndScrollPanorama = `in`.readByte().toInt() != 0
        this.zoomEffectPercent = `in`.readInt()
        this.panEffectPercent = `in`.readInt()
        this.useLargeVideoBuffer = `in`.readByte().toInt() != 0
    }

    val isGradiantOverlayVisible: Boolean
        get() = (metaDataConfig.isNotEmpty()) && this.gradiantOverlay

    var items: List<SliderItemViewHolder>
        get() = assets
        set(value) {
            assets = value
        }

    val loadMore: LoadMore?
        get() = Companion.loadMore

    val onAssetSelected: (SliderItemViewHolder) -> Unit
        get() = Companion.onAssetSelected

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeInt(startPosition)
        dest.writeInt(interval)
        dest.writeByte((if (isOnlyUseThumbnails) 1 else 0).toByte())
        dest.writeByte((if (isVideoSoundEnable) 1 else 0).toByte())
        dest.writeInt(animationSpeedMillis)
        dest.writeInt(maxCutOffHeight)
        dest.writeInt(maxCutOffWidth)
        dest.writeString(glideTransformation.toString())
        dest.writeInt(if (debugEnabled) 1 else 0)
        dest.writeInt(if (gradiantOverlay) 1 else 0)
        dest.writeInt(if (enableSlideAnimation) 1 else 0)
        dest.writeString(MetaDataConverter.metaDataListToJson(metaDataConfig))
        dest.writeByte((if (zoomAndScrollPanorama) 1 else 0).toByte())
        dest.writeInt(zoomEffectPercent)
        dest.writeInt(panEffectPercent)
        dest.writeByte((if (useLargeVideoBuffer) 1 else 0).toByte())
    }

    companion object {
        // Cant be serializable so this "workaround" for these two fields
        var assets: List<SliderItemViewHolder> = emptyList()
        var loadMore: LoadMore? = null
        var onAssetSelected: (SliderItemViewHolder) -> Unit = { _ -> }
        var onFavoriteToggle: (String, Boolean) -> Unit = { _, _ -> }

        @JvmField
        val CREATOR: Parcelable.Creator<MediaSliderConfiguration> =
            object : Parcelable.Creator<MediaSliderConfiguration> {
                override fun createFromParcel(`in`: Parcel): MediaSliderConfiguration {
                    return MediaSliderConfiguration(`in`)
                }

                override fun newArray(size: Int): Array<MediaSliderConfiguration?> {
                    return arrayOfNulls(size)
                }
            }
    }
}
