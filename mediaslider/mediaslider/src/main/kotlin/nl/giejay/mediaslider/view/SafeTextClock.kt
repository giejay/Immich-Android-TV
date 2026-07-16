package nl.giejay.mediaslider.view

import android.content.Context
import android.util.AttributeSet
import android.widget.TextClock
import timber.log.Timber

/**
 * A TextClock that catches the "Receiver not registered" IllegalArgumentException
 * that can occur during onDetachedFromWindow in some Android versions.
 */
class SafeTextClock @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : TextClock(context, attrs, defStyleAttr, defStyleRes) {

    override fun onDetachedFromWindow() {
        try {
            super.onDetachedFromWindow()
        } catch (e: IllegalArgumentException) {
            // This is a known Android bug where TextClock tries to unregister a receiver it didn't register
            Timber.e(e, "SafeTextClock: Caught Receiver not registered bug in onDetachedFromWindow")
        }
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        try {
            super.onVisibilityAggregated(isVisible)
        } catch (e: NullPointerException) {
            // This is a known Android bug where TextClock tries to access handler when it's null
            Timber.e(e, "SafeTextClock: Caught NPE in onVisibilityAggregated")
        }
    }
}
