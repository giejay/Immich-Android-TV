package nl.giejay.mediaslider.view

import android.content.Context
import android.util.AttributeSet
import android.widget.ListView

/**
 * [ListView] that expands to its children when height is [android.view.ViewGroup.LayoutParams.WRAP_CONTENT].
 * Needed for the non-scrolling EXIF columns in the slider details overlay.
 */
class WrapContentListView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ListView(context, attrs, defStyleAttr) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val expandedHeight = MeasureSpec.makeMeasureSpec(Integer.MAX_VALUE shr 2, MeasureSpec.AT_MOST)
        super.onMeasure(widthMeasureSpec, expandedHeight)
        layoutParams.height = measuredHeight
    }
}
