package nl.giejay.mediaslider.plugin

import android.view.View
import android.widget.LinearLayout
import android.widget.ListView
import androidx.constraintlayout.widget.ConstraintLayout
import com.zeuskartik.mediaslider.R
import android.os.Handler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.giejay.mediaslider.adapter.AlignOption
import nl.giejay.mediaslider.adapter.MetaDataAdapter
import nl.giejay.mediaslider.adapter.MetaDataClock
import nl.giejay.mediaslider.adapter.MetaDataMediaCount
import nl.giejay.mediaslider.config.MediaSliderConfiguration
import nl.giejay.mediaslider.model.SliderItem
import nl.giejay.mediaslider.model.SliderItemType
import nl.giejay.mediaslider.model.SliderItemViewHolder
import nl.giejay.mediaslider.util.MediaSliderListener
import nl.giejay.mediaslider.view.MediaSliderController

/**
 * Bottom metadata lists (description, city, DATE rows, etc.).
 * Viewer details visibility is driven by [DetailsOverlayKeyPlugin] via [setDetailsVisible].
 * Screensaver ([MediaSliderListener]) keeps metadata always on.
 */
class MetadataViewPlugin : SliderViewPlugin<MetadataRenderState>, SliderControllerPlugin {
    private var pluginLayer: ConstraintLayout? = null
    private var lastConfig: MediaSliderConfiguration? = null
    private var controllerVisible: Boolean = false

    /** Viewer: Enter/Back toggle. Screensaver: always show. */
    var detailsToggleEnabled: Boolean = false
        private set
    var detailsVisible: Boolean = true
        private set
    private var hasBottomDetails: Boolean = false

    override fun createState(context: SliderViewPluginContext, config: MediaSliderConfiguration): MetadataRenderState {
        detailsToggleEnabled = context.context !is MediaSliderListener && !config.detailsAlwaysOn
        detailsVisible = !detailsToggleEnabled
        hasBottomDetails = config.metaDataConfig.isNotEmpty()
        lastConfig = config

        val rightAdapter = MetaDataAdapter(
            context.context,
            config.metaDataConfig.filter { it.align == AlignOption.RIGHT },
            config.metaDataConfig.map { it.withAlign(align = AlignOption.RIGHT) }.distinct(),
            {
                val currentItem = context.currentItemProvider()
                if (currentItem.hasSecondaryItem()) currentItem.secondaryItem!! else currentItem.mainItem
            },
            { context.currentItemProvider().hasSecondaryItem() }
        )

        val leftAdapter = MetaDataAdapter(
            context.context,
            config.metaDataConfig.filter { it.align == AlignOption.LEFT },
            config.metaDataConfig.filterNot { it is MetaDataClock || it is MetaDataMediaCount }
                .map { it.withAlign(align = AlignOption.LEFT) }
                .distinct(),
            { context.currentItemProvider().mainItem },
            { context.currentItemProvider().hasSecondaryItem() }
        )

        return MetadataRenderState(leftAdapter, rightAdapter)
    }

    override fun attachView(rootView: ConstraintLayout, state: MetadataRenderState?) {
        pluginLayer = rootView
        val view = View.inflate(rootView.context, R.layout.metadata_holder, null)
        val params = ConstraintLayout.LayoutParams(
            ConstraintLayout.LayoutParams.MATCH_CONSTRAINT,
            ConstraintLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
        }
        rootView.addView(view, params)
    }

    override fun onLoadConfig(
        context: SliderViewPluginContext,
        config: MediaSliderConfiguration,
        state: MetadataRenderState?
    ) {
        lastConfig = config
        hasBottomDetails = config.metaDataConfig.isNotEmpty()
        detailsToggleEnabled = context.context !is MediaSliderListener && !config.detailsAlwaysOn
        if (!detailsToggleEnabled) {
            detailsVisible = true
        }
        val pluginState = state ?: return
        val listViewRight = context.rootView.findViewById<ListView>(R.id.metadata_view_right) ?: return
        listViewRight.divider = null
        listViewRight.adapter = pluginState.rightAdapter

        val listViewLeft = context.rootView.findViewById<ListView>(R.id.metadata_view_left) ?: return
        listViewLeft.divider = null
        listViewLeft.adapter = pluginState.leftAdapter
        applyDetailsChrome()
    }

    override fun onPageSettled(
        context: SliderViewPluginContext,
        config: MediaSliderConfiguration,
        sliderItem: SliderItemViewHolder,
        sliderItemIndex: Int,
        handler: Handler,
        state: MetadataRenderState?
    ) {
        lastConfig = config
        val currentState = state ?: return
        updateMetaData(context, currentState.leftAdapter, sliderItem.mainItem, sliderItemIndex, config)
        updateMetaData(
            context,
            currentState.rightAdapter,
            if (sliderItem.hasSecondaryItem()) sliderItem.secondaryItem!! else sliderItem.mainItem,
            sliderItemIndex,
            config
        )
        // Video gradient handling only for always-on (screensaver) metadata.
        if (!detailsToggleEnabled) {
            val metaDataHolderView = context.rootView.findViewById<LinearLayout>(R.id.meta_data_holder)
            if (sliderItem.type == SliderItemType.VIDEO) {
                if (config.isGradiantOverlayVisible) {
                    metaDataHolderView?.background = null
                }
            } else if (config.isGradiantOverlayVisible) {
                metaDataHolderView?.setBackgroundResource(R.drawable.gradient_overlay)
            }
        } else {
            applyDetailsChrome()
        }
    }

    override fun onControllerVisibilityChanged(
        isVisible: Boolean,
        rootView: View,
        controller: MediaSliderController,
        config: MediaSliderConfiguration
    ) {
        controllerVisible = isVisible
        lastConfig = config
        if (!detailsToggleEnabled) {
            if (isVisible && config.isGradiantOverlayVisible) {
                rootView.findViewById<LinearLayout>(R.id.meta_data_holder)?.background = null
            } else if (!isVisible && config.isGradiantOverlayVisible) {
                rootView.findViewById<LinearLayout>(R.id.meta_data_holder)
                    ?.setBackgroundResource(R.drawable.gradient_overlay)
            }
            return
        }
        if (isVisible) {
            detailsVisible = true
        }
        applyDetailsChrome()
    }

    override fun onPageSelected(context: SliderViewPluginContext, sliderItemIndex: Int, state: MetadataRenderState?) {
        val currentState = state ?: return
        currentState.leftAdapter.notifyDataSetChanged()
        currentState.rightAdapter.notifyDataSetChanged()
    }

    /** Called by [DetailsOverlayKeyPlugin] for Enter/Back. */
    fun setDetailsVisible(visible: Boolean) {
        detailsVisible = visible
        applyDetailsChrome()
    }

    private fun applyDetailsChrome() {
        val holder = pluginLayer?.findViewById<LinearLayout>(R.id.meta_data_holder) ?: return
        val detailsOn = !detailsToggleEnabled || detailsVisible
        val show = detailsOn && hasBottomDetails
        holder.visibility = if (show) View.VISIBLE else View.GONE
        if (!show) return

        val config = lastConfig
        if (!detailsToggleEnabled) {
            if (config?.isGradiantOverlayVisible == true && !controllerVisible) {
                holder.setBackgroundResource(R.drawable.gradient_overlay)
            } else if (config?.isGradiantOverlayVisible == true && controllerVisible) {
                holder.background = null
            }
        } else {
            holder.setBackgroundResource(R.drawable.metadata_details_scrim)
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
            }
        }
    }
}
