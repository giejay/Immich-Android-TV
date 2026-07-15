package nl.giejay.android.tv.immich.timeline

/**
 * Pure leave-off / restore targeting for Timeline menu and slider round-trips.
 * Keeps sticky targets stable against stray Leanback focus before restore runs.
 */
object TimelineLeaveOff {

    data class Snapshot(
        val memoryId: String?,
        val pendingAssetId: String?,
        val lastAssetId: String?,
        /**
         * Menu re-entry may reposition the viewport. Slider round-trips should not —
         * only re-focus (and scroll just enough if the cell was unbound).
         */
        val allowScrollAdjust: Boolean = true
    )

    sealed class Target {
        data class Memory(val id: String) : Target()
        data class Mosaic(val assetId: String, val allowScrollAdjust: Boolean) : Target()
        data object None : Target()
    }

    /** What restore should focus for the current sticky snapshot. */
    fun resolveRestore(snapshot: Snapshot): Target {
        val memoryId = snapshot.memoryId
        if (!memoryId.isNullOrBlank()) return Target.Memory(memoryId)
        val assetId = snapshot.pendingAssetId ?: snapshot.lastAssetId
        if (!assetId.isNullOrBlank()) {
            return Target.Mosaic(assetId, allowScrollAdjust = snapshot.allowScrollAdjust)
        }
        return Target.None
    }

    /**
     * Menu open: prefer the focused memory card; else a focused mosaic cell; else a sticky
     * memory (Left to the side menu steals focus before capture); else the last mosaic asset.
     */
    fun captureForMenu(
        focusedMemoryId: String?,
        focusedMosaicAssetId: String?,
        stickyMemoryId: String?,
        lastAssetId: String?
    ): Snapshot = when {
        !focusedMemoryId.isNullOrBlank() -> Snapshot(
            memoryId = focusedMemoryId,
            pendingAssetId = null,
            lastAssetId = lastAssetId,
            allowScrollAdjust = true
        )
        !focusedMosaicAssetId.isNullOrBlank() -> Snapshot(
            memoryId = null,
            pendingAssetId = focusedMosaicAssetId,
            lastAssetId = focusedMosaicAssetId,
            allowScrollAdjust = true
        )
        !stickyMemoryId.isNullOrBlank() -> Snapshot(
            memoryId = stickyMemoryId,
            pendingAssetId = null,
            lastAssetId = lastAssetId,
            allowScrollAdjust = true
        )
        else -> Snapshot(
            memoryId = null,
            pendingAssetId = lastAssetId,
            lastAssetId = lastAssetId,
            allowScrollAdjust = true
        )
    }

    /** Opening a memory slider — sticky memory until restore (or menu recapture). */
    fun afterOpeningMemory(memoryId: String, lastAssetId: String?): Snapshot =
        Snapshot(
            memoryId = memoryId,
            pendingAssetId = null,
            lastAssetId = lastAssetId,
            allowScrollAdjust = false
        )

    /** Opening / advancing a mosaic slider — leave-off tracks the current asset (exit lands here). */
    fun afterOpeningMosaic(assetId: String): Snapshot =
        Snapshot(
            memoryId = null,
            pendingAssetId = assetId,
            lastAssetId = assetId,
            allowScrollAdjust = false
        )

    /**
     * After restore has settled, navigating within the timeline updates leave-off freely.
     * Before restore, sticky fields must not be clobbered by interim focus.
     */
    fun shouldUpdateLiveLeaveOff(selectionRestored: Boolean): Boolean = selectionRestored

    /**
     * How to land focus on a mosaic restore target without jank.
     * Visible cell → requestFocus only; menu may jump; slider binds without height/4.
     */
    fun mosaicFocusMode(
        cellBound: Boolean,
        rowVisible: Boolean,
        allowScrollAdjust: Boolean
    ): MosaicFocusMode = when {
        cellBound && rowVisible -> MosaicFocusMode.RequestFocusOnly
        allowScrollAdjust -> MosaicFocusMode.AdjustScrollIntoView
        else -> MosaicFocusMode.BindWithoutAdjust
    }

    sealed class MosaicFocusMode {
        /** Cell already on screen — requestFocus only; do not reposition. */
        data object RequestFocusOnly : MosaicFocusMode()

        /** Menu re-entry — may jump viewport (height/4). */
        data object AdjustScrollIntoView : MosaicFocusMode()

        /**
         * Slider return to an off-screen asset — [LinearLayoutManager.scrollToPosition] to bind,
         * then focus; no height/4 jump. Implement via focusAsset(adjustScroll=false, lockScroll=true).
         */
        data object BindWithoutAdjust : MosaicFocusMode()
    }

    /**
     * Hold [TimelineMosaicLayoutManager.suppressFocusScroll] through cell focus scale (120ms)
     * so Leanback cannot inch the page after unlock.
     */
    const val MOSAIC_FOCUS_LOCK_RELEASE_MS = 160L

    /**
     * While slider leave-off has the viewport pinned (`allowScrollAdjust == false`),
     * [TimelineFragment.bindDays] must not `scrollToPositionWithOffset` — that races focus
     * restore after the slider.
     */
    fun shouldDeferBindAnchorScroll(allowScrollAdjust: Boolean): Boolean = !allowScrollAdjust

    /**
     * After returning from the mosaic slider, Leanback often restores focus to the cell that
     * *opened* the slider. That must not skip leave-off when [pendingResumeAssetId] still
     * points at the asset last viewed in the slider.
     */
    fun shouldForceLeaveOffRestore(pendingResumeAssetId: String?): Boolean =
        !pendingResumeAssetId.isNullOrBlank()
}
