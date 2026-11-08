package nl.giejay.mediaslider.plugin

import android.os.Handler
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.giejay.mediaslider.adapter.AlignOption
import nl.giejay.mediaslider.adapter.MetaDataClock
import nl.giejay.mediaslider.adapter.MetaDataItem
import nl.giejay.mediaslider.adapter.MetaDataMediaCount
import nl.giejay.mediaslider.config.MediaSliderConfiguration
import nl.giejay.mediaslider.model.SliderItem
import nl.giejay.mediaslider.model.SliderItemType
import nl.giejay.mediaslider.model.SliderItemViewHolder
import nl.giejay.mediaslider.overlay.MetadataOverlay
import nl.giejay.mediaslider.overlay.OverlayLine
import nl.giejay.mediaslider.overlay.SliderOverlayState
import nl.giejay.mediaslider.overlay.createOverlayView
import nl.giejay.mediaslider.view.MediaSliderController

/** Bottom metadata (left/right columns) drawn by the Compose [MetadataOverlay]. */
class MetadataViewPlugin : SliderViewPlugin<MetadataRenderState>, SliderControllerPlugin {
    private var overlayView: View? = null
    private var currentState: MetadataRenderState? = null
    private var controllerVisible = false

    override fun createState(context: SliderViewPluginContext, config: MediaSliderConfiguration): MetadataRenderState {
        return MetadataRenderState(
            overlay = SliderOverlayState(),
            leftItems = config.metaDataConfig.filter { it.align == AlignOption.LEFT },
            leftPortraitItems = config.metaDataConfig.filterNot { it is MetaDataClock || it is MetaDataMediaCount }
                .map { it.withAlign(align = AlignOption.LEFT) }
                .distinct(),
            rightItems = config.metaDataConfig.filter { it.align == AlignOption.RIGHT },
            rightPortraitItems = config.metaDataConfig.map { it.withAlign(align = AlignOption.RIGHT) }.distinct(),
        ).also { currentState = it }
    }

    override fun attachView(rootView: ConstraintLayout, state: MetadataRenderState?) {
        val pluginState = state ?: return
        val view = createOverlayView(rootView.context) { MetadataOverlay(pluginState.overlay) }
        val params = ConstraintLayout.LayoutParams(
            ConstraintLayout.LayoutParams.MATCH_CONSTRAINT,
            ConstraintLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
        }
        rootView.addView(view, params)
        overlayView = view
    }

    override fun onPageSettled(
        context: SliderViewPluginContext,
        config: MediaSliderConfiguration,
        sliderItem: SliderItemViewHolder,
        sliderItemIndex: Int,
        handler: Handler,
        state: MetadataRenderState?
    ) {
        val pluginState = state ?: return
        // The dark gradient is only used for images; videos keep their own picture untouched.
        pluginState.overlay.gradientAllowed = config.isGradiantOverlayVisible && sliderItem.type != SliderItemType.VIDEO
        pluginState.overlay.gradientVisible = pluginState.overlay.gradientAllowed && !controllerVisible

        val portrait = sliderItem.hasSecondaryItem()
        val rightItem = if (portrait) sliderItem.secondaryItem!! else sliderItem.mainItem
        context.ioScope.launch {
            resolve(context, pluginState, "left", sliderItem.mainItem, sliderItemIndex, config, itemsFor(pluginState, left = true, portrait))
            resolve(context, pluginState, "right", rightItem, sliderItemIndex, config, itemsFor(pluginState, left = false, portrait))
            // the user may have moved on while resolving: always show the item that is current now
            withContext(Dispatchers.Main) { publish(pluginState, context.currentItemProvider(), sliderItemIndex) }
        }
    }

    override fun onControllerVisibilityChanged(isVisible: Boolean, rootView: View, controller: MediaSliderController, config: MediaSliderConfiguration) {
        controllerVisible = isVisible
        val overlay = currentState?.overlay ?: return
        // the gradient is hidden while the controller bar is open, and comes back afterwards
        overlay.gradientVisible = config.isGradiantOverlayVisible && !isVisible && overlay.gradientAllowed
    }

    override fun onPageSelected(context: SliderViewPluginContext, sliderItemIndex: Int, state: MetadataRenderState?) {
        val pluginState = state ?: return
        publish(pluginState, context.currentItemProvider(), sliderItemIndex)
    }

    override fun onDestroy(context: SliderViewPluginContext, state: MetadataRenderState?) {
        overlayView?.let { (it.parent as? ConstraintLayout)?.removeView(it) }
        overlayView = null
        currentState = null
    }

    private fun itemsFor(state: MetadataRenderState, left: Boolean, portrait: Boolean): List<MetaDataItem> = when {
        left && portrait -> state.leftPortraitItems
        left -> state.leftItems
        portrait -> state.rightPortraitItems
        else -> state.rightItems
    }

    private fun key(assetId: String, side: String, index: Int) = "$assetId|$side|$index"

    private suspend fun resolve(
        context: SliderViewPluginContext,
        state: MetadataRenderState,
        side: String,
        item: SliderItem,
        sliderItemIndex: Int,
        config: MediaSliderConfiguration,
        items: List<MetaDataItem>
    ) {
        items.forEachIndexed { index, metaData ->
            val key = key(item.id, side, index)
            if (state.values.containsKey(key)) return@forEachIndexed
            val value = metaData.getValue(context.context, item, sliderItemIndex, config.items.size)
            state.values[key] = value ?: ""
        }
    }

    /** Shows the cached lines of the current item; lines whose value is empty or not yet known are skipped. */
    private fun publish(state: MetadataRenderState, holder: SliderItemViewHolder, sliderItemIndex: Int) {
        val portrait = holder.hasSecondaryItem()
        val rightItem = if (portrait) holder.secondaryItem!! else holder.mainItem
        state.overlay.left = lines(state, "left", holder.mainItem, itemsFor(state, left = true, portrait))
        state.overlay.right = lines(state, "right", rightItem, itemsFor(state, left = false, portrait))
    }

    private fun lines(state: MetadataRenderState, side: String, item: SliderItem, items: List<MetaDataItem>): List<OverlayLine> =
        items.mapIndexedNotNull { index, metaData ->
            val value = state.values[key(item.id, side, index)]
            if (value.isNullOrBlank()) null
            else OverlayLine(value, metaData.fontSize, metaData.padding, metaData is MetaDataClock)
        }
}
