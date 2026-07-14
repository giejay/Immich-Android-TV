package nl.giejay.android.tv.immich.timeline

import android.content.Context
import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Vertical mosaic layout manager that can refuse focus-driven auto-scroll.
 * Used when handing focus from the scrubber into an already-positioned cell.
 */
class TimelineMosaicLayoutManager(context: Context) :
    LinearLayoutManager(context, VERTICAL, false) {

    /** When true, focusing a child will not move the recycler. */
    var suppressFocusScroll: Boolean = false

    override fun requestChildRectangleOnScreen(
        parent: RecyclerView,
        child: View,
        rect: Rect,
        immediate: Boolean,
        focusedChildVisible: Boolean
    ): Boolean {
        if (suppressFocusScroll) return false
        return super.requestChildRectangleOnScreen(
            parent,
            child,
            rect,
            immediate,
            focusedChildVisible
        )
    }
}
