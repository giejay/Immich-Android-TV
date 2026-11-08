package nl.giejay.mediaslider.config

import nl.giejay.mediaslider.adapter.MetaDataItem
import nl.giejay.mediaslider.model.SliderItemViewHolder
import nl.giejay.mediaslider.plugin.SliderControllerPlugin
import nl.giejay.mediaslider.plugin.SliderKeyEventPlugin
import nl.giejay.mediaslider.plugin.SliderViewPlugin
import nl.giejay.mediaslider.transformations.GlideTransformations
import nl.giejay.mediaslider.transitions.SliderTransition
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
    /** When true, [nl.giejay.mediaslider.plugin.DateOverlayViewPlugin] shows DATE at the top. */
    val showDateTopLeft: Boolean = false,
    /** Transition between assets; only used while [enableSlideAnimation] is on. */
    val transition: SliderTransition = SliderTransition.SLIDE,
    /** Duration of every transition except [SliderTransition.SLIDE], which uses [animationSpeedMillis]. */
    val transitionDurationMillis: Int = 1200,
    var controllerPlugins: List<SliderControllerPlugin> = emptyList(),
    var viewPlugins: List<SliderViewPlugin<*>> = emptyList(),
    var keyEventPlugins: List<SliderKeyEventPlugin> = emptyList(),
    /** Pages with the Compose pager instead of the ViewPager (experimental until it replaces it). */
    var useComposePager: Boolean = false
) {
    val isGradiantOverlayVisible: Boolean
        get() = (metaDataConfig.isNotEmpty()) && this.gradiantOverlay
}
