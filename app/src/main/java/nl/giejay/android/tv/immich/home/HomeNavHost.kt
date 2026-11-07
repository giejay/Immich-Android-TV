package nl.giejay.android.tv.immich.home

/** Receives events when the side navigation gains or loses D-Pad focus. */
abstract class NavTransitionListener {
    /** Focus is moving to ([withHeaders] = true) or away from the navigation. */
    open fun onHeadersTransitionStart(withHeaders: Boolean) {}

    /** Focus has arrived at ([withHeaders] = true) or left the navigation. */
    open fun onHeadersTransitionStop(withHeaders: Boolean) {}
}

/** Implemented by the home screen so hosted pages can react to the side navigation. */
interface HomeNavHost {
    /** True while the side navigation owns focus; pages must not pull focus away from it. */
    val isNavFocused: Boolean

    fun setNavTransitionListener(listener: NavTransitionListener?)
}
