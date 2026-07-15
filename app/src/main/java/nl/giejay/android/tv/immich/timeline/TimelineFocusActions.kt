package nl.giejay.android.tv.immich.timeline

import android.view.KeyEvent

/**
 * Pure D-pad outcome for a mosaic cell, independent of RecyclerView focus.
 * Used by [TimelineFocusNavigator] and covered by unit tests for island Up/Down contracts.
 */
sealed class TimelineFocusAction {
    data class Move(val assetId: String) : TimelineFocusAction()
    data object ExitScrubber : TimelineFocusAction()
    data object LoadOlder : TimelineFocusAction()
    data object LoadNewer : TimelineFocusAction()
    /** Left at edge — allow Browse headers to take focus. */
    data object Pass : TimelineFocusAction()
    data object Ignore : TimelineFocusAction()
}

object TimelineFocusActions {

    fun resolve(keyCode: Int, neighbors: TimelineFocusNeighbors?): TimelineFocusAction {
        if (neighbors == null) return TimelineFocusAction.Ignore
        val targetId = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> neighbors.leftAssetId
            KeyEvent.KEYCODE_DPAD_RIGHT -> neighbors.rightAssetId
            KeyEvent.KEYCODE_DPAD_UP -> neighbors.upAssetId
            KeyEvent.KEYCODE_DPAD_DOWN -> neighbors.downAssetId
            else -> return TimelineFocusAction.Ignore
        }
        if (targetId != null) return TimelineFocusAction.Move(targetId)
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT -> TimelineFocusAction.ExitScrubber
            KeyEvent.KEYCODE_DPAD_DOWN -> TimelineFocusAction.LoadOlder
            KeyEvent.KEYCODE_DPAD_UP -> TimelineFocusAction.LoadNewer
            else -> TimelineFocusAction.Pass
        }
    }
}
