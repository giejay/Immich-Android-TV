package nl.giejay.mediaslider.plugin

import android.os.Handler
import android.util.TypedValue
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
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
import nl.giejay.mediaslider.model.SliderItemType
import nl.giejay.mediaslider.model.SliderItemViewHolder
import nl.giejay.mediaslider.view.MediaSliderController

/**
 * Layered date + EXIF details overlay (scrims, readiness gate, transport reparented over EXIF).
 * Also handles Enter/Back details toggle when not in screensaver.
 */
class MetadataViewPlugin : SliderViewPlugin<MetadataRenderState>, SliderControllerPlugin, SliderKeyEventPlugin {

    private var detailsHolderMinHeightPx = 0
    private var pendingDateAssetId: String? = null

    override fun createState(context: SliderViewPluginContext, config: MediaSliderConfiguration): MetadataRenderState {
        val detailsConfig = config.metaDataConfig.filterNot { it.type == MetaDataType.DATE }
        context.controller.hasBottomDetails = detailsConfig.isNotEmpty()

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

        val holder = View.inflate(rootView.context, R.layout.metadata_holder, null) as FrameLayout
        val holderParams = ConstraintLayout.LayoutParams(
            ConstraintLayout.LayoutParams.MATCH_CONSTRAINT,
            ConstraintLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
        }
        rootView.addView(holder, holderParams)

        // Nest transport over EXIF so the dark panel overlays rows instead of pushing them.
        val transport = rootView.findViewById<View>(R.id.image_controller)
        if (transport != null) {
            (transport.parent as? ViewGroup)?.removeView(transport)
            holder.addView(
                transport,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    android.view.Gravity.BOTTOM
                )
            )
            transport.elevation = 4f
        }
    }

    override fun onLoadConfig(
        context: SliderViewPluginContext,
        config: MediaSliderConfiguration,
        state: MetadataRenderState?
    ) {
        val pluginState = state ?: return
        context.rootView.findViewById<ListView>(R.id.metadata_view_right)?.adapter = pluginState.rightAdapter
        context.rootView.findViewById<ListView>(R.id.metadata_view_left)?.adapter = pluginState.leftAdapter
        applyChrome(context.rootView, context.controller, config, metadataReady = false)
    }

    override fun onPageSelected(context: SliderViewPluginContext, sliderItemIndex: Int, state: MetadataRenderState?) {
        val currentState = state ?: return
        val holder = context.rootView.findViewById<FrameLayout>(R.id.meta_data_holder)
        if (holder != null && holder.height > 0) {
            detailsHolderMinHeightPx = holder.height
        }
        currentState.leftAdapter.notifyDataSetChanged()
        currentState.rightAdapter.notifyDataSetChanged()
        context.rootView.findViewById<TextView>(R.id.metadata_date)?.text = ""
        val config = context.controller.mediaConfigOrNull() ?: return
        applyChrome(
            context.rootView,
            context.controller,
            config,
            metadataReady = false
        )
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
        if (isVisible && controller.detailsOverlayToggleEnabled) {
            controller.detailsOverlayVisible = true
        }
        applyChrome(
            rootView,
            controller,
            config,
            metadataReady = isBottomMetadataReady(rootView, controller)
        )
        if (isVisible) {
            controller.scheduleTransportAutoHide()
        }
    }

    override fun onKeyDown(event: KeyEvent, state: SliderKeyEventState): SliderKeyEventResult {
        val controller = state.controller
        if (!controller.detailsOverlayToggleEnabled) {
            return SliderKeyEventResult.UNHANDLED
        }
        val root = controller.controllerRootViewPublic()
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (controller.isControllerVisible && controller.transportHasFocus()) {
                    return SliderKeyEventResult.DISPATCH_TO_SUPER
                }
                if (controller.isControllerVisible) {
                    // Details already up: ensure transport is showing.
                    return SliderKeyEventResult.DISPATCH_TO_SUPER
                }
                controller.detailsOverlayVisible = true
                controller.showOverlayControlsPublic()
                return SliderKeyEventResult.HANDLED_CONSUME
            }
            KeyEvent.KEYCODE_BACK -> {
                if (controller.isControllerVisible) {
                    controller.hideOverlayControls()
                    applyChrome(
                        root,
                        controller,
                        controller.mediaConfigOrNull() ?: return SliderKeyEventResult.HANDLED_CONSUME,
                        metadataReady = isBottomMetadataReady(root, controller)
                    )
                    return SliderKeyEventResult.HANDLED_CONSUME
                }
                if (controller.detailsOverlayVisible) {
                    controller.detailsOverlayVisible = false
                    applyChrome(
                        root,
                        controller,
                        controller.mediaConfigOrNull() ?: return SliderKeyEventResult.HANDLED_CONSUME,
                        metadataReady = true
                    )
                    return SliderKeyEventResult.HANDLED_CONSUME
                }
            }
        }
        return SliderKeyEventResult.UNHANDLED
    }

    private fun updateDateOverlay(context: SliderViewPluginContext, sliderItem: SliderItem) {
        val dateView = context.rootView.findViewById<TextView>(R.id.metadata_date) ?: return
        pendingDateAssetId = sliderItem.id
        dateView.text = ""
        val showOverlay = !context.controller.detailsOverlayToggleEnabled || context.controller.detailsOverlayVisible
        if (showOverlay) dateView.visibility = View.VISIBLE else dateView.visibility = View.GONE
        context.ioScope.launch {
            val value = sliderItem.get(MetaDataType.DATE).orEmpty().trim()
            withContext(Dispatchers.Main) {
                if (pendingDateAssetId != sliderItem.id) return@withContext
                dateView.text = value
                val stillShow =
                    !context.controller.detailsOverlayToggleEnabled || context.controller.detailsOverlayVisible
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
        val items = adapter.getItemsToShow()
        if (items.isEmpty() || adapter.isFullyFetched(sliderItem.id)) {
            adapter.notifyDataSetChanged()
            applyChrome(
                context.rootView,
                context.controller,
                config,
                metadataReady = isBottomMetadataReady(context.rootView, context.controller)
            )
            return
        }
        context.ioScope.launch {
            try {
                val values = items.map { item ->
                    item.getValue(context.context, sliderItem, sliderItemIndex, config.items.size)
                }
                withContext(Dispatchers.Main) {
                    if (!context.currentItemProvider().ids().contains(sliderItem.id)) return@withContext
                    values.forEachIndexed { i, value ->
                        adapter.updateState(sliderItem.id, i, value)
                    }
                    adapter.notifyDataSetChanged()
                    applyChrome(
                        context.rootView,
                        context.controller,
                        config,
                        metadataReady = isBottomMetadataReady(context.rootView, context.controller)
                    )
                }
            } catch (_: Exception) {
                // Leave scrim up; rows stay empty.
            }
        }
    }

    private fun isBottomMetadataReady(rootView: View, controller: MediaSliderController): Boolean {
        if (!controller.hasBottomDetails) return true
        val left = rootView.findViewById<ListView>(R.id.metadata_view_left)?.adapter as? MetaDataAdapter
        val right = rootView.findViewById<ListView>(R.id.metadata_view_right)?.adapter as? MetaDataAdapter
        if (left == null || right == null) return false
        val item = try {
            // Prefer controller pager item via config
            controller.currentSliderItemOrNull() ?: return false
        } catch (_: Exception) {
            return false
        }
        val leftId = item.mainItem.id
        val rightId = if (item.hasSecondaryItem()) item.secondaryItem!!.id else item.mainItem.id
        return left.isReadyFor(leftId) && right.isReadyFor(rightId)
    }

    private fun applyChrome(
        rootView: View,
        controller: MediaSliderController,
        config: MediaSliderConfiguration,
        metadataReady: Boolean
    ) {
        val detailsOn = !controller.detailsOverlayToggleEnabled || controller.detailsOverlayVisible
        val showTransport = controller.isControllerVisible
        val showDetailsArea = detailsOn && controller.hasBottomDetails
        val showHolder = showDetailsArea || showTransport

        val holder = rootView.findViewById<FrameLayout>(R.id.meta_data_holder)
        val rows = rootView.findViewById<LinearLayout>(R.id.metadata_rows)
        val dateView = rootView.findViewById<TextView>(R.id.metadata_date)
        val transport = rootView.findViewById<View>(R.id.image_controller)

        holder?.visibility =
            if (showHolder && (controller.hasBottomDetails || showTransport)) View.VISIBLE else View.GONE
        transport?.visibility = if (showTransport) View.VISIBLE else View.GONE

        rows?.visibility = if (showDetailsArea && metadataReady) View.VISIBLE else View.GONE
        rows?.alpha = if (showTransport) METADATA_DIMMED_WHILE_TRANSPORT_ALPHA else 1f
        rows?.descendantFocusability =
            if (showTransport) ViewGroup.FOCUS_BLOCK_DESCENDANTS
            else ViewGroup.FOCUS_AFTER_DESCENDANTS

        if (showDetailsArea && !metadataReady) {
            holder?.minimumHeight = detailsHolderMinHeightPx.takeIf { it > 0 }
                ?: defaultMinHeight(rootView)
        } else {
            holder?.minimumHeight = 0
            if (showDetailsArea && metadataReady) {
                holder?.post {
                    val height = holder.height
                    if (height > 0) detailsHolderMinHeightPx = height
                }
            }
        }

        if (!detailsOn) {
            dateView?.visibility = View.GONE
        } else if (!dateView?.text.isNullOrBlank()) {
            dateView?.visibility = View.VISIBLE
        }

        if (showHolder && (controller.hasBottomDetails || showTransport)) {
            if (!controller.detailsOverlayToggleEnabled && config.isGradiantOverlayVisible && !showTransport) {
                holder?.setBackgroundResource(R.drawable.gradient_overlay)
            } else {
                holder?.setBackgroundResource(R.drawable.metadata_details_scrim)
            }
        }
    }

    private fun defaultMinHeight(rootView: View): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            DETAILS_HOLDER_FALLBACK_MIN_HEIGHT_DP,
            rootView.resources.displayMetrics
        ).toInt()

    companion object {
        private const val METADATA_DIMMED_WHILE_TRANSPORT_ALPHA = 0.35f
        private const val DETAILS_HOLDER_FALLBACK_MIN_HEIGHT_DP = 120f
    }
}
