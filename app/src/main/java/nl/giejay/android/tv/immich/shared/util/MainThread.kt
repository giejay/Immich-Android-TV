package nl.giejay.android.tv.immich.shared.util

import android.os.Handler
import android.os.Looper

private val mainHandler = Handler(Looper.getMainLooper())

/**
 * Runs [block] on the main thread: right away when already there, otherwise posted.
 *
 * Views must only be touched from the main thread. A view call from another thread throws in
 * `ViewRootImpl.checkThread`, but only after `View.requestLayout()` has flagged every ancestor as
 * "layout requested". From then on no child can get a layout pass through, so the whole screen stays
 * as it was (e.g. a blank page). Glide delivers a memory-cache hit on the calling thread, which is
 * how a debounce thread ended up there.
 */
fun runOnMainThread(block: () -> Unit) {
    if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
}
