package nl.giejay.mediaslider.plugin

import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.zeuskartik.mediaslider.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.giejay.mediaslider.adapter.AlignOption
import nl.giejay.mediaslider.adapter.MetaDataAdapter
import nl.giejay.mediaslider.adapter.MetaDataClock
import nl.giejay.mediaslider.adapter.MetaDataMediaCount
import nl.giejay.mediaslider.config.MediaSliderConfiguration
import nl.giejay.mediaslider.model.MetaDataType
import nl.giejay.mediaslider.model.SliderItem
import nl.giejay.mediaslider.model.SliderItemViewHolder
import nl.giejay.mediaslider.util.MediaSliderListener
import nl.giejay.mediaslider.view.MediaSliderController

/**
 * Layered date + EXIF details overlay (scrims).
 * Controller overlay stays on the slider shell ([R.id.image_controller]); EXIF dims while it is up.
 * Handles Enter/Back details toggle when not in screensaver ([MediaSliderListener]).
 *
 * Register the **same instance** on view, controller, and key plugin lists so chrome state is shared.
 */
class MetadataViewPlugin : SliderViewPlugin<MetadataRenderState>, SliderControllerPlugin, SliderKeyEventPlugin {

    private var pendingDateAssetId: String? = null
    private var detailsOverlayVisible = false
    private var hasBottomDetails = false
    /** Viewer: Enter toggles details. Screensaver keeps always-on metadata. */
    private var detailsOverlayToggleEnabled = true
    private var pluginLayer: ConstraintLayout? = null
    private var sliderRoot: ViewGroup? = null
    private var lastConfig: MediaSliderConfiguration? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val hideControllerRunnable = Runnable {
        activeController?.hideOverlayControls()
    }
    private var activeController: MediaSliderController? = null

    override fun createState(context: SliderViewPluginContext, config: MediaSliderConfiguration): MetadataRenderState {
        detailsOverlayToggleEnabled = context.context !is MediaSliderListener
        detailsOverlayVisible = !detailsOverlayToggleEnabled
        lastConfig = config
        activeController = context.controller

        val detailsConfig = config.metaDataConfig.filterNot { it.type == MetaDataType.DATE }
        hasBottomDetails = detailsConfig.isNotEmpty()

        val rightAdapter = MetaDataAdapter(
            context.context,
            detailsConfig.filter { it.align == AlignOption.RIGHT },
            detailsConfig.map { it.withAlign(align = AlignOption.RIGHT) }.distinct(),
            {
                val currentItem = context.currentItemProvider()
                if (currentItem.hasSecondaryItem()) currentItem.secondaryItem!! else currentItem.mainItem
            },
            { context.currentItemProvider().hasSecondaryItem() }
        )

        val leftAdapter = MetaDataAdapter(
            context.context,
            detailsConfig.filter { it.align == AlignOption.LEFT },
            detailsConfig.filterNot { it is MetaDataClock || it is MetaDataMediaCount }
                .map { it.withAlign(align = AlignOption.LEFT) }
                .distinct(),
            { context.currentItemProvider().mainItem },
            { context.currentItemProvider().hasSecondaryItem() }
        )

        return MetadataRenderState(leftAdapter, rightAdapter)
    }

    override fun attachView(rootView: ConstraintLayout, state: MetadataRenderState?) {
        pluginLayer = rootView
        // image_controller lives on the slider shell (sibling of plugin_layer), not inside it.
        sliderRoot = rootView.parent as? ViewGroup

        val dateView = View.inflate(rootView.context, R.layout.metadata_date_overlay, null)
        val dateParams = ConstraintLayout.LayoutParams(
            ConstraintLayout.LayoutParams.MATCH_CONSTRAINT,
            ConstraintLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            topToTop = ConstraintLayout.LayoutParams.PARENT_ID
        }
        rootView.addView(dateView, dateParams)

        val holder = View.inflate(rootView.context, R.layout.metadata_holder, null)
        val holderParams = ConstraintLayout.LayoutParams(
            ConstraintLayout.LayoutParams.MATCH_CONSTRAINT,
            ConstraintLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
        }
        rootView.addView(holder, holderParams)
    }

    override fun onLoadConfig(
        context: SliderViewPluginContext,
        config: MediaSliderConfiguration,
        state: MetadataRenderState?
    ) {
        val pluginState = state ?: return
        lastConfig = config
        activeController = context.controller
        context.rootView.findViewById<ListView>(R.id.metadata_view_right)?.adapter = pluginState.rightAdapter
        context.rootView.findViewById<ListView>(R.id.metadata_view_left)?.adapter = pluginState.leftAdapter
        applyChrome(context.controller, config)
    }

    override fun onPageSelected(context: SliderViewPluginContext, sliderItemIndex: Int, state: MetadataRenderState?) {
        val currentState = state ?: return
        currentState.leftAdapter.notifyDataSetChanged()
        currentState.rightAdapter.notifyDataSetChanged()
        context.rootView.findViewById<TextView>(R.id.metadata_date)?.text = ""
        val config = lastConfig ?: return
        applyChrome(context.controller, config)
    }

    override fun onPageSettled(
        context: SliderViewPluginContext,
        config: MediaSliderConfiguration,
        sliderItem: SliderItemViewHolder,
        sliderItemIndex: Int,
        handler: Handler,
        state: MetadataRenderState?
    ) {
        val currentState = state ?: return
        lastConfig = config
        activeController = context.controller
        updateDateOverlay(context, sliderItem.mainItem)
        updateMetaData(context, currentState.leftAdapter, sliderItem.mainItem, sliderItemIndex, config)
        updateMetaData(
            context,
            currentState.rightAdapter,
            if (sliderItem.hasSecondaryItem()) sliderItem.secondaryItem!! else sliderItem.mainItem,
            sliderItemIndex,
            config
        )
    }

    override fun onControllerVisibilityChanged(
        isVisible: Boolean,
        rootView: View,
        controller: MediaSliderController,
        config: MediaSliderConfiguration
    ) {
        activeController = controller
        lastConfig = config
        if (isVisible && detailsOverlayToggleEnabled) {
            detailsOverlayVisible = true
        }
        applyChrome(controller, config)
        if (isVisible) {
            scheduleControllerAutoHide()
        } else {
            cancelControllerAutoHide()
        }
    }

    override fun onDestroy(controller: MediaSliderController) {
        cancelControllerAutoHide()
        activeController = null
    }

    override fun onDestroy(context: SliderViewPluginContext, state: MetadataRenderState?) {
        cancelControllerAutoHide()
    }

    override fun onKeyDown(event: KeyEvent, state: SliderKeyEventState): SliderKeyEventResult {
        activeController = state.controller
        // Idle timeout: any key while the controller overlay is up resets the hide countdown.
        if (state.isControllerVisible) {
            scheduleControllerAutoHide()
        }
        if (!detailsOverlayToggleEnabled) {
            return SliderKeyEventResult.UNHANDLED
        }
        val controller = state.controller
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (controller.isControllerVisible) {
                    // Let focused controller buttons / main handle Enter.
                    return SliderKeyEventResult.DISPATCH_TO_SUPER
                }
                // Open details; fall through so main shows the shared controller overlay.
                detailsOverlayVisible = true
                applyChrome(controller, state.config)
                return SliderKeyEventResult.UNHANDLED
            }
            KeyEvent.KEYCODE_BACK -> {
                if (controller.isControllerVisible) {
                    // Main also hides the controller on Back; keep chrome in sync after.
                    return SliderKeyEventResult.UNHANDLED
                }
                if (detailsOverlayVisible) {
                    detailsOverlayVisible = false
                    applyChrome(controller, state.config)
                    return SliderKeyEventResult.HANDLED_CONSUME
                }
            }
        }
        return SliderKeyEventResult.UNHANDLED
    }

    private fun scheduleControllerAutoHide() {
        cancelControllerAutoHide()
        mainHandler.postDelayed(hideControllerRunnable, CONTROLLER_AUTO_HIDE_MS)
    }

    private fun cancelControllerAutoHide() {
        mainHandler.removeCallbacks(hideControllerRunnable)
    }

    private fun updateDateOverlay(context: SliderViewPluginContext, sliderItem: SliderItem) {
        val dateView = context.rootView.findViewById<TextView>(R.id.metadata_date) ?: return
        pendingDateAssetId = sliderItem.id
        dateView.text = ""
        val showOverlay = !detailsOverlayToggleEnabled || detailsOverlayVisible
        dateView.visibility = if (showOverlay) View.VISIBLE else View.GONE
        context.ioScope.launch {
            val value = sliderItem.get(MetaDataType.DATE).orEmpty().trim()
            withContext(Dispatchers.Main) {
                if (pendingDateAssetId != sliderItem.id) return@withContext
                dateView.text = value
                val stillShow = !detailsOverlayToggleEnabled || detailsOverlayVisible
                dateView.visibility = when {
                    !stillShow -> View.GONE
                    value.isNotEmpty() -> View.VISIBLE
                    else -> View.GONE
                }
            }
        }
    }

    private fun updateMetaData(
        context: SliderViewPluginContext,
        adapter: MetaDataAdapter,
        sliderItem: SliderItem,
        sliderItemIndex: Int,
        config: MediaSliderConfiguration
    ) {
        context.ioScope.launch {
            adapter.getItemsToShow().forEachIndexed { metaDataIndex, item ->
                if (adapter.hasStateForItem(sliderItem.id, metaDataIndex)) {
                    return@forEachIndexed
                }
                val value = item.getValue(context.context, sliderItem, sliderItemIndex, config.items.size)
                adapter.updateState(sliderItem.id, metaDataIndex, value ?: "")
            }
            withContext(Dispatchers.Main) {
                adapter.notifyDataSetChanged()
                applyChrome(context.controller, config)
            }
        }
    }

    private fun applyChrome(
        controller: MediaSliderController,
        config: MediaSliderConfiguration
    ) {
        val root = pluginLayer ?: return
        val detailsOn = !detailsOverlayToggleEnabled || detailsOverlayVisible
        val showController = controller.isControllerVisible
        val showDetailsArea = detailsOn && hasBottomDetails

        val holder = root.findViewById<LinearLayout>(R.id.meta_data_holder)
        val dateView = root.findViewById<TextView>(R.id.metadata_date)
        val imageController = (sliderRoot ?: root).findViewById<View>(R.id.image_controller)

        holder?.visibility = if (showDetailsArea) View.VISIBLE else View.GONE
        imageController?.visibility = if (showController) View.VISIBLE else View.GONE

        holder?.alpha = if (showController) METADATA_DIMMED_WHILE_CONTROLLER_ALPHA else 1f
        holder?.descendantFocusability =
            if (showController) ViewGroup.FOCUS_BLOCK_DESCENDANTS
            else ViewGroup.FOCUS_AFTER_DESCENDANTS

        if (!detailsOn) {
            dateView?.visibility = View.GONE
        } else if (!dateView?.text.isNullOrBlank()) {
            dateView?.visibility = View.VISIBLE
        }

        if (showDetailsArea) {
            if (!detailsOverlayToggleEnabled && config.isGradiantOverlayVisible && !showController) {
                holder?.setBackgroundResource(R.drawable.gradient_overlay)
            } else {
                holder?.setBackgroundResource(R.drawable.metadata_details_scrim)
            }
        }
    }

    private companion object {
        const val METADATA_DIMMED_WHILE_CONTROLLER_ALPHA = 0.35f
        const val CONTROLLER_AUTO_HIDE_MS = 4_000L
    }
}
