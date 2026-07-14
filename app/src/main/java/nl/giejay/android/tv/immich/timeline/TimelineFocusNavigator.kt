package nl.giejay.android.tv.immich.timeline

import android.view.KeyEvent
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import nl.giejay.android.tv.immich.R

/**
 * D-pad navigation over mosaic cells using an explicit neighbor map.
 * Left/Right stay in the visual row; Up/Down use max horizontal overlap.
 */
class TimelineFocusNavigator(
    private val recyclerView: RecyclerView,
    private val adapter: TimelineMosaicAdapter,
    private val onFocused: (dayKey: String, assetId: String) -> Unit
) {
    var neighbors: Map<String, TimelineFocusNeighbors> = emptyMap()
        private set

    fun updateNeighbors(map: Map<String, TimelineFocusNeighbors>) {
        neighbors = map
    }

    fun onCellKey(view: View, keyCode: Int, event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false
        val assetId = view.getTag(R.id.timeline_mosaic_cell_asset_id) as? String ?: return false
        val n = neighbors[assetId] ?: return false
        val targetId = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> n.leftAssetId
            KeyEvent.KEYCODE_DPAD_RIGHT -> n.rightAssetId
            KeyEvent.KEYCODE_DPAD_UP -> n.upAssetId
            KeyEvent.KEYCODE_DPAD_DOWN -> n.downAssetId
            else -> return false
        }
        if (targetId == null) {
            // Let Browse steal Left/Up at the edge; keep Right/Down from wandering.
            return keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_DPAD_DOWN
        }
        focusAsset(targetId)
        return true
    }

    fun onCellFocused(dayKey: String, assetId: String) {
        onFocused(dayKey, assetId)
    }

    fun focusAsset(assetId: String, smooth: Boolean = true) {
        val position = adapter.positionOfAsset(assetId)
        if (position < 0) return
        val lm = recyclerView.layoutManager as? LinearLayoutManager
        if (smooth) {
            recyclerView.smoothScrollToPosition(position)
        } else {
            lm?.scrollToPositionWithOffset(position, recyclerView.height / 4)
        }
        recyclerView.post {
            val cell = adapter.findCellView(recyclerView, assetId)
            if (cell != null) {
                cell.requestFocus()
            } else {
                recyclerView.post { adapter.findCellView(recyclerView, assetId)?.requestFocus() }
            }
        }
    }
}
