package nl.giejay.android.tv.immich.timeline

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineFocusActionsTest {

    private val neighbors = TimelineFocusNeighbors(
        leftAssetId = "left",
        rightAssetId = "right",
        upAssetId = "up",
        downAssetId = "down"
    )

    private val edgeNeighbors = TimelineFocusNeighbors(
        leftAssetId = null,
        rightAssetId = null,
        upAssetId = null,
        downAssetId = null
    )

    @Test
    fun `resolve moves to neighbor when present`() {
        assertEquals(
            TimelineFocusAction.Move("left"),
            TimelineFocusActions.resolve(KeyEvent.KEYCODE_DPAD_LEFT, neighbors)
        )
        assertEquals(
            TimelineFocusAction.Move("right"),
            TimelineFocusActions.resolve(KeyEvent.KEYCODE_DPAD_RIGHT, neighbors)
        )
        assertEquals(
            TimelineFocusAction.Move("up"),
            TimelineFocusActions.resolve(KeyEvent.KEYCODE_DPAD_UP, neighbors)
        )
        assertEquals(
            TimelineFocusAction.Move("down"),
            TimelineFocusActions.resolve(KeyEvent.KEYCODE_DPAD_DOWN, neighbors)
        )
    }

    @Test
    fun `resolve edge keys load or exit or pass`() {
        assertEquals(
            TimelineFocusAction.ExitScrubber,
            TimelineFocusActions.resolve(KeyEvent.KEYCODE_DPAD_RIGHT, edgeNeighbors)
        )
        assertEquals(
            TimelineFocusAction.LoadOlder,
            TimelineFocusActions.resolve(KeyEvent.KEYCODE_DPAD_DOWN, edgeNeighbors)
        )
        assertEquals(
            TimelineFocusAction.LoadNewer,
            TimelineFocusActions.resolve(KeyEvent.KEYCODE_DPAD_UP, edgeNeighbors)
        )
        assertEquals(
            TimelineFocusAction.Pass,
            TimelineFocusActions.resolve(KeyEvent.KEYCODE_DPAD_LEFT, edgeNeighbors)
        )
    }

    @Test
    fun `resolve ignores unknown key and missing neighbors`() {
        assertEquals(
            TimelineFocusAction.Ignore,
            TimelineFocusActions.resolve(KeyEvent.KEYCODE_ENTER, neighbors)
        )
        assertEquals(
            TimelineFocusAction.Ignore,
            TimelineFocusActions.resolve(KeyEvent.KEYCODE_DPAD_UP, null)
        )
    }

    @Test
    fun `LoadNewer is the Up-gap bridge action`() {
        val action = TimelineFocusActions.resolve(KeyEvent.KEYCODE_DPAD_UP, edgeNeighbors)
        assertTrue(action is TimelineFocusAction.LoadNewer)
    }
}
