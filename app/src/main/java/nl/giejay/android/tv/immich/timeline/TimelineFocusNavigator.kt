package nl.giejay.android.tv.immich.timeline

import android.view.KeyEvent
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import nl.giejay.android.tv.immich.R

/**
 * D-pad navigation over mosaic cells using an explicit neighbor map.
 * Left/Right stay in the visual row; Up/Down use max horizontal overlap.
 * Right at the end of a row can hand off to the year scrubber.
 */
class TimelineFocusNavigator(
    private val recyclerView: RecyclerView,
    private val adapter: TimelineMosaicAdapter,
    private val onFocused: (dayKey: String, assetId: String) -> Unit,
    private val onExitRightToScrubber: (() -> Unit)? = null,
    /** Fired when Down is pressed with no neighbor — load older months / extend the list. */
    private val onReachContentEnd: (() -> Unit)? = null,
    /** Fired when Up is pressed with no neighbor — load newer months across a sparse gap. */
    private val onReachContentStart: (() -> Unit)? = null
) {
    var neighbors: Map<String, TimelineFocusNeighbors> = emptyMap()
        private set

    fun updateNeighbors(map: Map<String, TimelineFocusNeighbors>) {
        neighbors = map
    }

    fun onCellKey(view: View, keyCode: Int, event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false
        val assetId = view.getTag(R.id.timeline_mosaic_cell_asset_id) as? String ?: return false
        val action = TimelineFocusActions.resolve(keyCode, neighbors[assetId])
        return when (action) {
            is TimelineFocusAction.Move -> {
                focusAsset(action.assetId, adjustScroll = false, lockScroll = false)
                true
            }
            TimelineFocusAction.ExitScrubber -> {
                onExitRightToScrubber?.invoke()
                true
            }
            TimelineFocusAction.LoadOlder -> {
                // End of currently built mosaic — request older months (focus doesn't scroll,
                // so onScrolled alone never fires load-more here).
                onReachContentEnd?.invoke()
                true
            }
            TimelineFocusAction.LoadNewer -> {
                // Gap above current island (unloaded months) or true top — try bridging newer.
                onReachContentStart?.invoke()
                // Consume so Browse headers don't steal focus while we fill the gap.
                onReachContentStart != null
            }
            TimelineFocusAction.Pass -> false
            TimelineFocusAction.Ignore -> false
        }
    }

    fun onCellFocused(dayKey: String, assetId: String) {
        onFocused(dayKey, assetId)
    }

    /**
     * @param adjustScroll jump-scroll so the asset sits near the upper quarter (slider restore, etc.)
     * @param lockScroll ignore focus bring-into-view entirely (menu re-entry must not inch)
     */
    fun focusAsset(
        assetId: String,
        smooth: Boolean = false,
        adjustScroll: Boolean = false,
        lockScroll: Boolean = false
    ) {
        val position = adapter.positionOfAsset(assetId)
        if (position < 0) return
        val lm = recyclerView.layoutManager as? LinearLayoutManager
        if (adjustScroll) {
            if (smooth) {
                recyclerView.smoothScrollToPosition(position)
            } else {
                lm?.scrollToPositionWithOffset(position, recyclerView.height / 4)
            }
        } else if (!lockScroll && lm != null) {
            val first = lm.findFirstVisibleItemPosition()
            val last = lm.findLastVisibleItemPosition()
            // Off-screen neighbor has no ViewHolder yet — scroll just enough to bind it so
            // requestFocus can succeed. On-screen moves rely on requestChildRectangleOnScreen.
            if (first == RecyclerView.NO_POSITION || position !in first..last) {
                if (smooth) {
                    recyclerView.smoothScrollToPosition(position)
                } else {
                    lm.scrollToPosition(position)
                }
            }
        }
        val mosaicLm = recyclerView.layoutManager as? TimelineMosaicLayoutManager
        val alreadyLocked = mosaicLm?.suppressFocusScroll == true
        if (lockScroll) {
            mosaicLm?.suppressFocusScroll = true
        }
        fun clearLock() {
            if (lockScroll && !alreadyLocked) {
                recyclerView.post { mosaicLm?.suppressFocusScroll = false }
            }
        }
        recyclerView.post {
            val cell = adapter.findCellView(recyclerView, assetId)
            if (cell != null) {
                cell.requestFocus()
                clearLock()
            } else {
                recyclerView.post {
                    adapter.findCellView(recyclerView, assetId)?.requestFocus()
                    clearLock()
                }
            }
        }
    }
}
