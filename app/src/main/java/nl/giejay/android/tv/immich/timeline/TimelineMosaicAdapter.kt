package nl.giejay.android.tv.immich.timeline

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HorizontalGridView
import androidx.leanback.widget.ItemBridgeAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.api.model.Memory
import nl.giejay.android.tv.immich.api.util.ApiUtil

class TimelineMosaicAdapter(
    private val gapPx: Int,
    private val onCellClick: (TimelineMosaicCell) -> Unit,
    private val onCellFocus: (TimelineMosaicCell, View) -> Unit,
    private val onCellBlur: (TimelineMosaicCell, View) -> Unit,
    private val onCellDetached: (View) -> Unit,
    private val onCellKey: (View, Int, android.view.KeyEvent) -> Boolean,
    private val onMemoryClicked: (Memory) -> Unit = {}
) : ListAdapter<TimelineMosaicItem, RecyclerView.ViewHolder>(Diff) {

    private var itemsSnapshot: List<TimelineMosaicItem> = emptyList()

    override fun submitList(list: List<TimelineMosaicItem>?) {
        submitList(list, null)
    }

    override fun submitList(list: List<TimelineMosaicItem>?, commitCallback: Runnable?) {
        itemsSnapshot = list.orEmpty()
        super.submitList(list, commitCallback)
    }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is TimelineMosaicItem.Header -> VIEW_TYPE_HEADER
        is TimelineMosaicItem.Row -> VIEW_TYPE_ROW
        is TimelineMosaicItem.MemoriesRow -> VIEW_TYPE_MEMORIES
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_HEADER -> HeaderVH(
                inflater.inflate(R.layout.timeline_day_header, parent, false)
            )
            VIEW_TYPE_MEMORIES -> MemoriesRowVH(
                inflater.inflate(R.layout.timeline_memories_row, parent, false) as HorizontalGridView
            )
            else -> RowVH(inflater.inflate(R.layout.timeline_mosaic_row, parent, false) as LinearLayout)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is TimelineMosaicItem.Header -> (holder as HeaderVH).bind(item)
            is TimelineMosaicItem.Row -> (holder as RowVH).bind(item)
            is TimelineMosaicItem.MemoriesRow -> (holder as MemoriesRowVH).bind(item)
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        if (holder is RowVH) {
            holder.clearImages()
        }
        super.onViewRecycled(holder)
    }

    fun positionOfAsset(assetId: String): Int {
        itemsSnapshot.forEachIndexed { index, item ->
            if (item is TimelineMosaicItem.Row && item.cells.any { it.asset.id == assetId }) {
                return index
            }
        }
        return RecyclerView.NO_POSITION
    }

    fun hasMemoriesRow(): Boolean =
        itemsSnapshot.firstOrNull() is TimelineMosaicItem.MemoriesRow

    /**
     * Focus the first "N years ago" card. Returns false if the row isn't laid out yet
     * (caller may scroll to 0 and retry).
     */
    fun focusFirstMemory(recyclerView: RecyclerView): Boolean {
        if (itemsSnapshot.firstOrNull() !is TimelineMosaicItem.MemoriesRow) return false
        val holder = recyclerView.findViewHolderForAdapterPosition(0) as? MemoriesRowVH
            ?: return false
        return holder.focusFirstCard()
    }

    fun firstAssetId(): String? =
        itemsSnapshot.filterIsInstance<TimelineMosaicItem.Row>()
            .firstOrNull()
            ?.cells
            ?.firstOrNull()
            ?.asset
            ?.id

    /** Right-most cell in the first row for [dayKey] — natural entry from the scrubber. */
    fun rightmostAssetIdForDay(dayKey: String): String? =
        itemsSnapshot.filterIsInstance<TimelineMosaicItem.Row>()
            .firstOrNull { it.dayKey == dayKey }
            ?.cells
            ?.lastOrNull()
            ?.asset
            ?.id

    /** Right-most cell of the first mosaic row in the list. */
    fun firstRowRightmostAssetId(): String? =
        itemsSnapshot.filterIsInstance<TimelineMosaicItem.Row>()
            .firstOrNull()
            ?.cells
            ?.lastOrNull()
            ?.asset
            ?.id

    fun findCellView(recyclerView: RecyclerView, assetId: String): View? {
        val position = positionOfAsset(assetId)
        if (position == RecyclerView.NO_POSITION) return null
        val holder = recyclerView.findViewHolderForAdapterPosition(position) as? RowVH ?: return null
        return holder.cellViews[assetId]
    }

    fun lastRowAdapterPosition(): Int {
        for (i in itemsSnapshot.indices.reversed()) {
            if (itemsSnapshot[i] is TimelineMosaicItem.Row) return i
        }
        return RecyclerView.NO_POSITION
    }

    /** Approximate countdown of remaining day headers from [adapterPosition]. */
    fun remainingDayHeadersFrom(adapterPosition: Int): Int {
        if (adapterPosition < 0) return 0
        return itemsSnapshot.drop(adapterPosition).count { it is TimelineMosaicItem.Header }
    }

    /**
     * Adapter position for a scrubber jump.
     *
     * Prefer [preferredDayKey] (a local day that actually came from the Immich UTC month
     * bucket). Fall back to the first header whose day sits in the same YYYY-MM as
     * [monthKey] — which can miss when local dates straddle the UTC month boundary.
     */
    fun positionForScrubberMonth(monthKey: String, preferredDayKey: String?): Int {
        if (preferredDayKey != null) {
            val exact = positionOfDay(preferredDayKey)
            if (exact != RecyclerView.NO_POSITION) return exact
        }
        val prefix = monthKey.take(7) // YYYY-MM
        itemsSnapshot.forEachIndexed { index, item ->
            if (item is TimelineMosaicItem.Header && item.dayKey.startsWith(prefix)) {
                return index
            }
        }
        return RecyclerView.NO_POSITION
    }

    fun positionOfDay(dayKey: String): Int {
        itemsSnapshot.forEachIndexed { index, item ->
            if (item is TimelineMosaicItem.Header && item.dayKey == dayKey) {
                return index
            }
        }
        return RecyclerView.NO_POSITION
    }

    /** @see positionForScrubberMonth */
    fun positionOfMonth(monthKey: String): Int =
        positionForScrubberMonth(monthKey, preferredDayKey = null)

    fun dayKeyAtAdapterPosition(position: Int): String? =
        when (val item = itemsSnapshot.getOrNull(position)) {
            is TimelineMosaicItem.Header -> item.dayKey
            is TimelineMosaicItem.Row -> item.dayKey
            is TimelineMosaicItem.MemoriesRow -> null
            null -> null
        }

    fun firstAssetIdAtAdapterPosition(position: Int): String? {
        // Prefer the row itself; if landing on a header, use the following row.
        for (i in position until itemsSnapshot.size) {
            val item = itemsSnapshot[i]
            if (item is TimelineMosaicItem.Row) {
                return item.cells.firstOrNull()?.asset?.id
            }
        }
        return null
    }

    private class HeaderVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val text: TextView = itemView as TextView
        fun bind(header: TimelineMosaicItem.Header) {
            text.text = header.label
            text.tag = header.dayKey
        }
    }

    private inner class RowVH(private val row: LinearLayout) : RecyclerView.ViewHolder(row) {
        val cellViews = linkedMapOf<String, View>()

        fun clearImages() {
            cellViews.values.forEach { cellRoot ->
                onCellDetached(cellRoot)
                val image = cellRoot.findViewById<ImageView>(R.id.timeline_mosaic_image)
                Glide.with(image).clear(image)
            }
            cellViews.clear()
            row.removeAllViews()
        }

        fun bind(rowItem: TimelineMosaicItem.Row) {
            clearImages()
            val inflater = LayoutInflater.from(row.context)
            rowItem.cells.forEachIndexed { index, cell ->
                val cellRoot = inflater.inflate(R.layout.timeline_mosaic_cell, row, false)
                val image = cellRoot.findViewById<ImageView>(R.id.timeline_mosaic_image)
                val videoBadge = cellRoot.findViewById<View>(R.id.timeline_mosaic_video_badge)
                val videoDuration = cellRoot.findViewById<TextView>(R.id.timeline_mosaic_video_duration)
                val videoPlayPause = cellRoot.findViewById<ImageView>(R.id.timeline_mosaic_video_play_pause)
                val lp = LinearLayout.LayoutParams(cell.widthPx, cell.heightPx)
                if (index > 0) lp.marginStart = gapPx
                cellRoot.layoutParams = lp
                cellRoot.setTag(R.id.timeline_mosaic_cell_asset_id, cell.asset.id)
                cellRoot.setTag(R.id.timeline_mosaic_cell_day_key, cell.dayKey)
                cellRoot.isFocusable = true
                cellRoot.isFocusableInTouchMode = true
                cellRoot.setOnClickListener { onCellClick(cell) }
                cellRoot.setOnFocusChangeListener { v, hasFocus ->
                    v.animate().scaleX(if (hasFocus) 1.14f else 1f)
                        .scaleY(if (hasFocus) 1.14f else 1f)
                        .setDuration(120)
                        .start()
                    v.elevation = if (hasFocus) 24f else 0f
                    if (!cell.asset.isImage) {
                        videoPlayPause.setImageResource(
                            if (hasFocus) R.drawable.ic_video_badge_pause else R.drawable.ic_video_badge_play
                        )
                    }
                    if (hasFocus) onCellFocus(cell, v) else onCellBlur(cell, v)
                }
                cellRoot.setOnKeyListener { v, keyCode, event -> onCellKey(v, keyCode, event) }

                if (cell.asset.isImage) {
                    videoBadge.visibility = View.GONE
                } else {
                    videoBadge.visibility = View.VISIBLE
                    val totalSeconds = TimelineVideoDuration.parseSeconds(cell.asset.duration) ?: 0L
                    videoDuration.text = TimelineVideoDuration.format(totalSeconds)
                    videoPlayPause.setImageResource(R.drawable.ic_video_badge_play)
                }

                val url = ApiUtil.getThumbnailUrl(cell.asset.id, "thumbnail")
                Glide.with(image)
                    .load(url)
                    .centerCrop()
                    .into(image)

                row.addView(cellRoot)
                cellViews[cell.asset.id] = cellRoot
            }
        }
    }

    private inner class MemoriesRowVH(private val gridView: HorizontalGridView) : RecyclerView.ViewHolder(gridView) {
        private val arrayAdapter = ArrayObjectAdapter(MemoryPresenter(gridView.context, onMemoryClicked))

        init {
            gridView.adapter = ItemBridgeAdapter(arrayAdapter)
        }

        fun bind(item: TimelineMosaicItem.MemoriesRow) {
            arrayAdapter.setItems(item.memories, null)
        }

        fun focusFirstCard(): Boolean {
            if (arrayAdapter.size() == 0) return false
            gridView.setSelectedPosition(0)
            val child = gridView.layoutManager?.findViewByPosition(0)
            return if (child != null) {
                child.requestFocus()
            } else {
                gridView.requestFocus()
            }
        }
    }

    private object Diff : DiffUtil.ItemCallback<TimelineMosaicItem>() {
        override fun areItemsTheSame(oldItem: TimelineMosaicItem, newItem: TimelineMosaicItem): Boolean =
            when {
                oldItem is TimelineMosaicItem.Header && newItem is TimelineMosaicItem.Header ->
                    oldItem.dayKey == newItem.dayKey
                oldItem is TimelineMosaicItem.Row && newItem is TimelineMosaicItem.Row ->
                    oldItem.rowId == newItem.rowId
                oldItem is TimelineMosaicItem.MemoriesRow && newItem is TimelineMosaicItem.MemoriesRow -> true
                else -> false
            }

        override fun areContentsTheSame(oldItem: TimelineMosaicItem, newItem: TimelineMosaicItem): Boolean =
            oldItem == newItem
    }

    companion object {
        private const val VIEW_TYPE_HEADER = 1
        private const val VIEW_TYPE_ROW = 2
        private const val VIEW_TYPE_MEMORIES = 3
    }
}
