package nl.giejay.mediaslider.plugin

import android.os.Handler
import android.view.View
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.zeuskartik.mediaslider.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.giejay.mediaslider.config.MediaSliderConfiguration
import nl.giejay.mediaslider.model.MetaDataType
import nl.giejay.mediaslider.model.SliderItem
import nl.giejay.mediaslider.model.SliderItemViewHolder

/**
 * Top-of-screen date overlay with a dark-to-transparent scrim.
 * DATE is stripped from [MetadataViewPlugin]'s bottom lists so it only appears here.
 */
class DateOverlayViewPlugin : SliderViewPlugin<Unit?> {
    private var dateView: TextView? = null
    private var pendingDateAssetId: String? = null

    override fun attachView(rootView: ConstraintLayout, state: Unit?) {
        val view = View.inflate(rootView.context, R.layout.metadata_date_overlay, null) as TextView
        dateView = view
        val params = ConstraintLayout.LayoutParams(
            ConstraintLayout.LayoutParams.MATCH_CONSTRAINT,
            ConstraintLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            topToTop = ConstraintLayout.LayoutParams.PARENT_ID
        }
        rootView.addView(view, params)
    }

    override fun onPageSelected(context: SliderViewPluginContext, sliderItemIndex: Int, state: Unit?) {
        // Clear immediately while paging so the previous asset's date does not linger.
        dateView?.apply {
            text = ""
            visibility = View.GONE
        }
    }

    override fun onPageSettled(
        context: SliderViewPluginContext,
        config: MediaSliderConfiguration,
        sliderItem: SliderItemViewHolder,
        sliderItemIndex: Int,
        handler: Handler,
        state: Unit?
    ) {
        updateDateOverlay(context, sliderItem.mainItem)
    }

    override fun onDestroy(context: SliderViewPluginContext, state: Unit?) {
        dateView?.let { view ->
            (view.parent as? ConstraintLayout)?.removeView(view)
        }
        dateView = null
        pendingDateAssetId = null
    }

    private fun updateDateOverlay(context: SliderViewPluginContext, sliderItem: SliderItem) {
        val view = dateView ?: return
        pendingDateAssetId = sliderItem.id
        view.text = ""
        view.visibility = View.GONE

        context.ioScope.launch {
            val value = sliderItem.get(MetaDataType.DATE).orEmpty().trim()
            withContext(Dispatchers.Main) {
                if (pendingDateAssetId != sliderItem.id) return@withContext
                view.text = value
                view.visibility = if (value.isNotEmpty()) View.VISIBLE else View.GONE
            }
        }
    }
}
