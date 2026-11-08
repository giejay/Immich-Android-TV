package nl.giejay.mediaslider.plugin

import android.content.Context
import android.os.Handler
import android.view.KeyEvent
import android.view.View
import androidx.compose.runtime.State
import androidx.constraintlayout.widget.ConstraintLayout
import kotlinx.coroutines.CoroutineScope
import nl.giejay.mediaslider.adapter.MetaDataItem
import nl.giejay.mediaslider.overlay.SliderOverlayState
import nl.giejay.mediaslider.config.MediaSliderConfiguration
import nl.giejay.mediaslider.model.SliderItemType
import nl.giejay.mediaslider.model.SliderItemViewHolder
import nl.giejay.mediaslider.view.MediaSliderController

enum class ControllerButtonPlacement {
    START,
    END,
    LEFT_OF,
    RIGHT_OF
}

/**
 * A button a plugin adds to the controller row. [icon] is observable so a click can swap it (favorite);
 * [anchorId] is a [nl.giejay.mediaslider.controller.ControllerIds] value for LEFT_OF / RIGHT_OF.
 */
class ControllerButtonSpec(
    val description: String,
    val icon: State<Int>,
    val onClick: () -> Unit,
    val placement: ControllerButtonPlacement,
    val anchorId: String? = null,
    val id: String = "plugin_" + nextPluginButtonId.incrementAndGet(),
)

private val nextPluginButtonId = java.util.concurrent.atomic.AtomicInteger()

enum class SliderKeyEventResult {
    UNHANDLED,
    HANDLED_CONTINUE,
    HANDLED_CONSUME,
    DISPATCH_TO_SUPER
}

data class SliderKeyEventState(
    val isControllerVisible: Boolean,
    val isSlideshowPlaying: Boolean,
    val currentItemType: SliderItemType?,
    val controller: MediaSliderController,
    val config: MediaSliderConfiguration
)

interface SliderKeyEventPlugin {
    fun onKeyDown(event: KeyEvent, state: SliderKeyEventState): SliderKeyEventResult =
        SliderKeyEventResult.UNHANDLED

    /** Called for [KeyEvent.ACTION_UP]. Needed for tap-vs-hold seek and similar gestures. */
    fun onKeyUp(event: KeyEvent, state: SliderKeyEventState): SliderKeyEventResult =
        SliderKeyEventResult.UNHANDLED
}

data class SliderViewPluginContext(
    val context: Context,
    val rootView: ConstraintLayout,
    val controller: MediaSliderController,
    val ioScope: CoroutineScope,
    val currentItemProvider: () -> SliderItemViewHolder
)

interface SliderViewPlugin<T> {
    fun createState(context: SliderViewPluginContext, config: MediaSliderConfiguration): T? = null
    fun attachView(rootView: ConstraintLayout, state: T?) {}
    fun onLoadConfig(context: SliderViewPluginContext, config: MediaSliderConfiguration, state: T?) {}
    fun onPageSettled(
        context: SliderViewPluginContext,
        config: MediaSliderConfiguration,
        sliderItem: SliderItemViewHolder,
        sliderItemIndex: Int,
        handler: Handler,
        state: T?
    ) {
    }

    fun onPageSelected(context: SliderViewPluginContext, sliderItemIndex: Int, state: T?) {}
    fun onDestroy(context: SliderViewPluginContext, state: T?) {}
}

data class ControllerPluginContext(
    val context: Context,
    val rootView: View,
    val config: MediaSliderConfiguration,
    val sliderItem: SliderItemViewHolder,
    val sliderItemIndex: Int,
    val isVideo: Boolean,
    val hasSecondaryItem: Boolean,
    val controller: MediaSliderController
)

interface SliderControllerPlugin {
    fun provideControllerButton(context: ControllerPluginContext): ControllerButtonSpec? = null

    fun onConfigureController(context: ControllerPluginContext) {}
    fun onControllerVisibilityChanged(isVisible: Boolean, rootView: View, controller: MediaSliderController, config: MediaSliderConfiguration) {}
    fun onAssetTimerStarted(
        intervalMs: Long,
        controller: MediaSliderController,
        config: MediaSliderConfiguration,
        context: Context,
        handler: Handler
    ) {
    }

    fun onSlideshowStarted(
        itemType: SliderItemType?,
        controller: MediaSliderController,
        config: MediaSliderConfiguration,
        context: Context,
        handler: Handler
    ) {
    }

    fun onSlideShowStopped(controller: MediaSliderController, handler: Handler) {}
    fun onDestroy(controller: MediaSliderController) {}
}

class MetadataRenderState(
    val overlay: SliderOverlayState,
    val leftItems: List<MetaDataItem>,
    val leftPortraitItems: List<MetaDataItem>,
    val rightItems: List<MetaDataItem>,
    val rightPortraitItems: List<MetaDataItem>,
    /** Resolved text per asset, side and item index; filled asynchronously and kept for paging back. */
    val values: MutableMap<String, String> = mutableMapOf()
)
