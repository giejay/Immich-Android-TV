package nl.giejay.android.tv.immich.timeline

import android.content.Context
import android.graphics.Outline
import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.api.model.Memory
import nl.giejay.android.tv.immich.api.util.ApiUtil
import java.time.Year

/**
 * Adapter for the timeline's horizontal "N years ago" memories row. Immich's design overlays the
 * title directly on the cover image rather than in a region below it.
 */
class MemoriesRowAdapter(
    private val onMemoryClicked: (Memory) -> Unit,
    private val onMemoryFocused: (Memory) -> Unit = {}
) : ListAdapter<Memory, MemoriesRowAdapter.MemoryVH>(Diff) {

    inner class MemoryVH(view: View) : RecyclerView.ViewHolder(view) {
        private val image: ImageView = view.findViewById(R.id.memory_card_image)
        private val title: TextView = view.findViewById(R.id.memory_card_title)

        init {
            val cornerRadiusPx = view.resources.getDimension(R.dimen.memory_card_corner_radius)
            // Explicit outline so the cover ImageView is clipped to the same rounded rect as Immich.
            view.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, cornerRadiusPx)
                }
            }
            view.clipToOutline = true
        }

        fun bind(memory: Memory) {
            title.text = yearsAgoLabel(itemView.context, memory)

            val coverId = memory.assets.firstOrNull()?.id
            Glide.with(image)
                .load(ApiUtil.getThumbnailUrl(coverId, "thumbnail"))
                .centerCrop()
                .into(image)

            itemView.setTag(R.id.timeline_memory_id, memory.id)
            itemView.setOnClickListener { onMemoryClicked(memory) }
            itemView.setOnFocusChangeListener { v, hasFocus ->
                // Resting cards stay clipped inside end clearance; focused scale may cover the scrubber.
                (v.parent as? RecyclerView)?.clipToPadding = !hasFocus
                v.animate().scaleX(if (hasFocus) 1.14f else 1f)
                    .scaleY(if (hasFocus) 1.14f else 1f)
                    .setDuration(120)
                    .start()
                v.elevation = if (hasFocus) 24f else 0f
                if (hasFocus) {
                    onMemoryFocused(memory)
                }
            }
        }

        fun clear() {
            Glide.with(image).clear(image)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemoryVH =
        MemoryVH(LayoutInflater.from(parent.context).inflate(R.layout.memory_card, parent, false))

    override fun onBindViewHolder(holder: MemoryVH, position: Int) = holder.bind(getItem(position))

    override fun onViewRecycled(holder: MemoryVH) {
        holder.clear()
        super.onViewRecycled(holder)
    }

    /** Gap between cards (the former HorizontalGridView `horizontalMargin`). */
    class SpacingDecoration(private val gapPx: Int) : RecyclerView.ItemDecoration() {
        override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
            if (parent.getChildAdapterPosition(view) > 0) outRect.left = gapPx
        }
    }

    private object Diff : DiffUtil.ItemCallback<Memory>() {
        override fun areItemsTheSame(oldItem: Memory, newItem: Memory) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Memory, newItem: Memory) = oldItem == newItem
    }

    companion object {
        fun yearsAgoLabel(context: Context, memory: Memory): String {
            val years = MemoryLabels.yearsAgo(Year.now().value, memory.data.year)
            return context.resources.getQuantityString(
                R.plurals.years_ago, years, years
            )
        }
    }
}
