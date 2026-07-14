package nl.giejay.android.tv.immich.timeline

import android.content.Context
import android.graphics.Outline
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.ImageView
import android.widget.TextView
import androidx.leanback.widget.Presenter
import com.bumptech.glide.Glide
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.api.model.Memory
import nl.giejay.android.tv.immich.api.util.ApiUtil
import java.time.Year

/**
 * Renders one "N years ago" memory card in the timeline's horizontal memories row.
 * Plain [Presenter] (not Leanback's [androidx.leanback.widget.ImageCardView]) since Immich's
 * design overlays the title directly on the cover image rather than in a region below it.
 */
class MemoryPresenter(
    private val context: Context,
    private val onMemoryClicked: (Memory) -> Unit
) : Presenter() {

    private val cornerRadiusPx =
        context.resources.getDimension(R.dimen.memory_card_corner_radius)

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.memory_card, parent, false)
        // Explicit outline so the cover ImageView is clipped to the same rounded rect as Immich.
        view.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, cornerRadiusPx)
            }
        }
        view.clipToOutline = true
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val memory = item as Memory
        val view = viewHolder.view
        val image = view.findViewById<ImageView>(R.id.memory_card_image)
        val title = view.findViewById<TextView>(R.id.memory_card_title)

        title.text = yearsAgoLabel(context, memory)

        val coverId = memory.assets.firstOrNull()?.id
        Glide.with(image)
            .load(ApiUtil.getThumbnailUrl(coverId, "thumbnail"))
            .centerCrop()
            .into(image)

        view.setOnClickListener { onMemoryClicked(memory) }
        view.setOnFocusChangeListener { v, hasFocus ->
            v.animate().scaleX(if (hasFocus) 1.14f else 1f)
                .scaleY(if (hasFocus) 1.14f else 1f)
                .setDuration(120)
                .start()
            v.elevation = if (hasFocus) 8f else 0f
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val image = viewHolder.view.findViewById<ImageView>(R.id.memory_card_image)
        Glide.with(image).clear(image)
    }

    companion object {
        fun yearsAgoLabel(context: Context, memory: Memory): String {
            val years = Year.now().value - memory.data.year
            return context.resources.getQuantityString(
                R.plurals.years_ago, years, years
            )
        }
    }
}
