package nl.giejay.mediaslider.plugin

import android.content.Context
import android.os.Handler
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.zeuskartik.mediaslider.R
import kotlinx.coroutines.CoroutineScope
import nl.giejay.mediaslider.adapter.MetaDataAdapter
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

interface ControllerButtonHost {
    fun placeButton(button: ImageButton, placement: ControllerButtonPlacement, anchorViewId: Int? = null)
}

data class ControllerButtonSpec(
    val button: ImageButton,
    val placement: ControllerButtonPlacement,
    val anchorViewId: Int? = null
)

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
    val controller: MediaSliderController
)

interface SliderKeyEventPlugin {
    fun onKeyDown(event: KeyEvent, state: SliderKeyEventState): SliderKeyEventResult =
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

    fun createDefaultControllerButton(
        context: ControllerPluginContext,
        @StringRes contentDescriptionRes: Int,
        @DrawableRes iconRes: Int
    ): ImageButton {
        val row = context.rootView.findViewById<ViewGroup>(R.id.media_controller_button_row)
        return (LayoutInflater.from(context.context).inflate(R.layout.controller_plugin_button, row, false) as ImageButton).apply {
            id = View.generateViewId()
            tag = "controller_plugin_button"
            contentDescription = context.context.getString(contentDescriptionRes)
            setImageResource(iconRes)
        }
    }

    fun onConfigureController(context: ControllerPluginContext) {}
    fun onControllerVisibilityChanged(isVisible: Boolean, controller: MediaSliderController) {}
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

data class MetadataRenderState(
    val leftAdapter: MetaDataAdapter,
    val rightAdapter: MetaDataAdapter
)

