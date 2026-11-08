package nl.giejay.android.tv.immich.timeline

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout

/**
 * A [FrameLayout] that lets the owner take over D-Pad focus search for its descendants: the
 * listener is asked first and a non-null result wins over the default search.
 */
class FocusSearchFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    fun interface OnFocusSearchListener {
        /** Returns the view that should get focus, or null to fall back to the default search. */
        fun onFocusSearch(focused: View?, direction: Int): View?
    }

    var onFocusSearchListener: OnFocusSearchListener? = null

    override fun focusSearch(focused: View?, direction: Int): View? {
        onFocusSearchListener?.onFocusSearch(focused, direction)?.let { return it }
        return super.focusSearch(focused, direction)
    }
}
