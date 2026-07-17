package nl.giejay.mediaslider.plugin

import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout

/**
 * Utility base plugin for rendering custom content in the top-right slider slot.
 */
abstract class TopRightSlotViewPlugin : SliderViewPlugin<Any?> {
    private var mountedView: View? = null

    final override fun attachView(rootView: ConstraintLayout, state: Any?) {
        val customView = createView(rootView)
        mountedView = customView
        val params = ConstraintLayout.LayoutParams(
            ConstraintLayout.LayoutParams.WRAP_CONTENT,
            ConstraintLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
        }
        rootView.addView(customView, params)
    }

    final override fun onDestroy(context: SliderViewPluginContext, state: Any?) {
        mountedView?.let { view ->
            (view.parent as? ConstraintLayout)?.removeView(view)
        }
        mountedView = null
        onSlotDestroyed()
    }

    abstract fun createView(rootView: ConstraintLayout): View

    open fun onSlotDestroyed() {}
}

