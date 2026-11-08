package nl.giejay.mediaslider.plugin

import android.os.Handler
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.giejay.mediaslider.config.MediaSliderConfiguration
import nl.giejay.mediaslider.model.MetaDataType
import nl.giejay.mediaslider.model.SliderItem
import nl.giejay.mediaslider.model.SliderItemViewHolder
import nl.giejay.mediaslider.overlay.DateOverlay
import nl.giejay.mediaslider.overlay.SliderOverlayState
import nl.giejay.mediaslider.overlay.createOverlayView

/**
 * Optional top-of-screen date overlay with a dark-to-transparent scrim.
 * Controlled by [MediaSliderConfiguration.showDateTopLeft]; bottom DATE metadata is unchanged.
 */
class DateOverlayViewPlugin : SliderViewPlugin<Unit?> {
    private val overlay = SliderOverlayState()
    private var overlayView: View? = null
    private var pendingDateAssetId: String? = null

    override fun attachView(rootView: ConstraintLayout, state: Unit?) {
        val view = createOverlayView(rootView.context) { DateOverlay(overlay) }
        val params = ConstraintLayout.LayoutParams(
            ConstraintLayout.LayoutParams.MATCH_CONSTRAINT,
            ConstraintLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            topToTop = ConstraintLayout.LayoutParams.PARENT_ID
        }
        rootView.addView(view, params)
        overlayView = view
    }

    override fun onPageSelected(context: SliderViewPluginContext, sliderItemIndex: Int, state: Unit?) {
        // Clear immediately while paging so the previous asset's date does not linger.
        overlay.dateText = ""
    }

    override fun onPageSettled(
        context: SliderViewPluginContext,
        config: MediaSliderConfiguration,
        sliderItem: SliderItemViewHolder,
        sliderItemIndex: Int,
        handler: Handler,
        state: Unit?
    ) {
        if (!config.showDateTopLeft) {
            overlay.dateText = ""
            return
        }
        updateDateOverlay(context, sliderItem.mainItem)
    }

    override fun onDestroy(context: SliderViewPluginContext, state: Unit?) {
        overlayView?.let { (it.parent as? ConstraintLayout)?.removeView(it) }
        overlayView = null
        pendingDateAssetId = null
    }

    private fun updateDateOverlay(context: SliderViewPluginContext, sliderItem: SliderItem) {
        pendingDateAssetId = sliderItem.id
        overlay.dateText = ""
        context.ioScope.launch {
            val value = sliderItem.get(MetaDataType.DATE).orEmpty().trim()
            withContext(Dispatchers.Main) {
                if (pendingDateAssetId != sliderItem.id) return@withContext
                overlay.dateText = value
            }
        }
    }
}
