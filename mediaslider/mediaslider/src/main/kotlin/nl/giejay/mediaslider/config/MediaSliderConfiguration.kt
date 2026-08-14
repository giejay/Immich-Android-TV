package nl.giejay.mediaslider.config

import nl.giejay.mediaslider.adapter.MetaDataItem
import nl.giejay.mediaslider.model.SliderItemViewHolder
import nl.giejay.mediaslider.plugin.SliderControllerPlugin
import nl.giejay.mediaslider.plugin.SliderKeyEventPlugin
import nl.giejay.mediaslider.plugin.SliderViewPlugin
import nl.giejay.mediaslider.transformations.GlideTransformations
import nl.giejay.mediaslider.util.LoadMore

class MediaSliderConfiguration(
    val startPosition: Int,
    val interval: Int,
    val isOnlyUseThumbnails: Boolean,
    var isVideoSoundEnable: Boolean,
    var items: List<SliderItemViewHolder>,
    var loadMore: LoadMore?,
    var onAssetSelected: (SliderItemViewHolder) -> Unit = {},
    val animationSpeedMillis: Int,
    val maxCutOffHeight: Int,
    val maxCutOffWidth: Int,
    val glideTransformation: GlideTransformations,
    val gradiantOverlay: Boolean,
    val enableSlideAnimation: Boolean,
    val metaDataConfig: List<MetaDataItem>,
    val zoomAndScrollPanorama: Boolean,
    val zoomEffectPercent: Int,
    val panEffectPercent: Int,
    val useLargeVideoBuffer: Boolean = false,
    /** When true, D-pad Left/Right seek in video; when false (default), they change assets. */
    val dpadSeeksInVideo: Boolean = false,
    var controllerPlugins: List<SliderControllerPlugin> = emptyList(),
    var viewPlugins: List<SliderViewPlugin<*>> = emptyList(),
    var keyEventPlugins: List<SliderKeyEventPlugin> = emptyList()
) {
    val isGradiantOverlayVisible: Boolean
        get() = (metaDataConfig.isNotEmpty()) && this.gradiantOverlay
}
