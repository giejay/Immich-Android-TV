package nl.giejay.android.tv.immich.timeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contracts for menu / memory / mosaic leave-off restore. These regressed when sticky
 * memory ids outlived mosaic focus, and when stray focus overwritten mosaic return targets.
 */
class TimelineLeaveOffTest {

    @Test
    fun `opening memory restores that memory`() {
        val snap = TimelineLeaveOff.afterOpeningMemory("mem-3", lastAssetId = "asset-a")
        assertEquals(
            TimelineLeaveOff.Target.Memory("mem-3"),
            TimelineLeaveOff.resolveRestore(snap)
        )
    }

    @Test
    fun `menu capture on focused mosaic clears stale memory sticky`() {
        val menu = TimelineLeaveOff.captureForMenu(
            focusedMemoryId = null,
            focusedMosaicAssetId = "asset-b",
            stickyMemoryId = "mem-stale",
            lastAssetId = "asset-b"
        )
        assertEquals(
            TimelineLeaveOff.Target.Mosaic("asset-b", allowScrollAdjust = true),
            TimelineLeaveOff.resolveRestore(menu)
        )
        assertEquals(null, menu.memoryId)
    }

    @Test
    fun `menu capture keeps sticky memory when Left stole focus`() {
        // Leftmost memory → Left opens headers; findFocus is already null/non-memory.
        val menu = TimelineLeaveOff.captureForMenu(
            focusedMemoryId = null,
            focusedMosaicAssetId = null,
            stickyMemoryId = "mem-left",
            lastAssetId = "asset-a"
        )
        assertEquals(
            TimelineLeaveOff.Target.Memory("mem-left"),
            TimelineLeaveOff.resolveRestore(menu)
        )
    }

    @Test
    fun `menu capture on another memory uses focused card`() {
        val menu = TimelineLeaveOff.captureForMenu(
            focusedMemoryId = "mem-1",
            focusedMosaicAssetId = null,
            stickyMemoryId = "mem-old",
            lastAssetId = "asset-a"
        )
        assertEquals(
            TimelineLeaveOff.Target.Memory("mem-1"),
            TimelineLeaveOff.resolveRestore(menu)
        )
        assertEquals(null, menu.pendingAssetId)
    }

    @Test
    fun `opening mosaic sticks pending asset against stray lastSelected`() {
        val snap = TimelineLeaveOff.afterOpeningMosaic("asset-exact")
        val polluted = snap.copy(lastAssetId = "asset-wrong-visible-row")
        assertEquals(
            TimelineLeaveOff.Target.Mosaic("asset-exact", allowScrollAdjust = false),
            TimelineLeaveOff.resolveRestore(polluted)
        )
    }

    @Test
    fun `slider advance moves mosaic restore target`() {
        val opened = TimelineLeaveOff.afterOpeningMosaic("asset-open")
        assertEquals(
            TimelineLeaveOff.Target.Mosaic("asset-open", allowScrollAdjust = false),
            TimelineLeaveOff.resolveRestore(opened)
        )
        val advanced = TimelineLeaveOff.afterOpeningMosaic("asset-later")
        assertEquals(
            TimelineLeaveOff.Target.Mosaic("asset-later", allowScrollAdjust = false),
            TimelineLeaveOff.resolveRestore(advanced)
        )
        assertFalse(advanced.allowScrollAdjust)
    }

    @Test
    fun `slider leave-off disallows scroll adjust menu allows it`() {
        assertFalse(TimelineLeaveOff.afterOpeningMosaic("a").allowScrollAdjust)
        assertTrue(
            TimelineLeaveOff.captureForMenu(
                focusedMemoryId = null,
                focusedMosaicAssetId = "a",
                stickyMemoryId = null,
                lastAssetId = "a"
            ).allowScrollAdjust
        )
    }

    @Test
    fun `live leave-off updates only after restore settled`() {
        assertFalse(TimelineLeaveOff.shouldUpdateLiveLeaveOff(selectionRestored = false))
        assertTrue(TimelineLeaveOff.shouldUpdateLiveLeaveOff(selectionRestored = true))
    }

    @Test
    fun `resolve prefers memory over pending mosaic`() {
        val snap = TimelineLeaveOff.Snapshot(
            memoryId = "mem-9",
            pendingAssetId = "asset-a",
            lastAssetId = "asset-a"
        )
        assertEquals(
            TimelineLeaveOff.Target.Memory("mem-9"),
            TimelineLeaveOff.resolveRestore(snap)
        )
    }

    @Test
    fun `resolve falls back to last asset when pending cleared`() {
        val snap = TimelineLeaveOff.Snapshot(
            memoryId = null,
            pendingAssetId = null,
            lastAssetId = "asset-z"
        )
        assertEquals(
            TimelineLeaveOff.Target.Mosaic("asset-z", allowScrollAdjust = true),
            TimelineLeaveOff.resolveRestore(snap)
        )
    }

    @Test
    fun `same-item slider exit restores saved scroll advanced does not`() {
        assertTrue(
            TimelineLeaveOff.shouldRestoreSavedScroll(
                allowScrollAdjust = false,
                savedForAssetId = "asset-open",
                restoreAssetId = "asset-open"
            )
        )
        assertFalse(
            TimelineLeaveOff.shouldRestoreSavedScroll(
                allowScrollAdjust = false,
                savedForAssetId = "asset-open",
                restoreAssetId = "asset-later"
            )
        )
        assertFalse(
            TimelineLeaveOff.shouldRestoreSavedScroll(
                allowScrollAdjust = true,
                savedForAssetId = "asset-open",
                restoreAssetId = "asset-open"
            )
        )
    }

    @Test
    fun `mosaic focus mode avoids scroll adjust when cell already visible`() {
        assertEquals(
            TimelineLeaveOff.MosaicFocusMode.RequestFocusOnly,
            TimelineLeaveOff.mosaicFocusMode(
                cellBound = true,
                rowVisible = true,
                allowScrollAdjust = false
            )
        )
        assertEquals(
            TimelineLeaveOff.MosaicFocusMode.RequestFocusOnly,
            TimelineLeaveOff.mosaicFocusMode(
                cellBound = true,
                rowVisible = true,
                allowScrollAdjust = true
            )
        )
    }

    @Test
    fun `mosaic focus mode menu may jump slider binds without height quarter`() {
        assertEquals(
            TimelineLeaveOff.MosaicFocusMode.AdjustScrollIntoView,
            TimelineLeaveOff.mosaicFocusMode(
                cellBound = false,
                rowVisible = false,
                allowScrollAdjust = true
            )
        )
        assertEquals(
            TimelineLeaveOff.MosaicFocusMode.BindWithoutAdjust,
            TimelineLeaveOff.mosaicFocusMode(
                cellBound = false,
                rowVisible = false,
                allowScrollAdjust = false
            )
        )
        assertEquals(
            TimelineLeaveOff.MosaicFocusMode.BindWithoutAdjust,
            TimelineLeaveOff.mosaicFocusMode(
                cellBound = true,
                rowVisible = false,
                allowScrollAdjust = false
            )
        )
    }

    @Test
    fun `focus lock release outlives cell focus scale animation`() {
        assertTrue(TimelineLeaveOff.MOSAIC_FOCUS_LOCK_RELEASE_MS > 120L)
    }

    @Test
    fun `bindDays defers anchor scroll while slider viewport is pinned`() {
        assertTrue(TimelineLeaveOff.shouldDeferBindAnchorScroll(allowScrollAdjust = false))
        assertFalse(TimelineLeaveOff.shouldDeferBindAnchorScroll(allowScrollAdjust = true))
    }
}
