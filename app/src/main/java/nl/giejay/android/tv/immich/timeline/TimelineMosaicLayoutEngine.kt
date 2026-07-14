package nl.giejay.android.tv.immich.timeline

import nl.giejay.android.tv.immich.api.model.Memory
import nl.giejay.android.tv.immich.api.model.TimelineAsset
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * One laid-out mosaic cell inside a justified row.
 * [xPx] is the left edge within the row (for focus overlap).
 *
 * Immich [TimelineAsset.ratio] is width/height.
 */
data class TimelineMosaicCell(
    val dayKey: String,
    val asset: TimelineAsset,
    val widthPx: Int,
    val heightPx: Int,
    val rowId: String,
    val indexInRow: Int,
    val xPx: Int
)

sealed class TimelineMosaicItem {
    data class Header(
        val dayKey: String,
        val label: String
    ) : TimelineMosaicItem()

    data class Row(
        val rowId: String,
        val dayKey: String,
        val cells: List<TimelineMosaicCell>
    ) : TimelineMosaicItem()

    /** Horizontally-scrolling "N years ago" row, always the first item when non-empty. */
    data class MemoriesRow(
        val memories: List<Memory>
    ) : TimelineMosaicItem()
}

data class TimelineFocusNeighbors(
    val leftAssetId: String?,
    val rightAssetId: String?,
    val upAssetId: String?,
    val downAssetId: String?
)

object TimelineMosaicLayoutEngine {

    const val MIN_RATIO = 0.35
    const val MAX_RATIO = 3.0

    /**
     * Packs [days] into a vertical list of headers + justified mosaic rows.
     *
     * Full rows use the classic justified algorithm: accumulate assets at a target
     * row height until their natural widths overflow [contentWidthPx], then scale
     * **both** width and height so the row fills the width while preserving each
     * asset's aspect ratio. The last row of a day stays left-aligned at the target height.
     */
    fun layout(
        days: List<TimelineDay>,
        contentWidthPx: Int,
        rowHeightPx: Int,
        gapPx: Int,
        dayLabel: (TimelineDay) -> String
    ): List<TimelineMosaicItem> {
        if (contentWidthPx <= 0 || rowHeightPx <= 0) return emptyList()
        val items = mutableListOf<TimelineMosaicItem>()
        days.forEach { day ->
            items += TimelineMosaicItem.Header(day.dayKey, dayLabel(day))
            packDay(day, contentWidthPx, rowHeightPx, gapPx, items)
        }
        return items
    }

    fun clampRatio(ratio: Double): Double =
        ratio.coerceIn(MIN_RATIO, MAX_RATIO)

    /** Natural cell width at [rowHeightPx] given Immich width/height [ratio]. */
    fun naturalWidth(ratio: Double, rowHeightPx: Int): Int =
        max(1, (rowHeightPx * clampRatio(ratio)).roundToInt())

    /**
     * Builds L/R/U/D neighbors for every asset cell across [items].
     * Headers are ignored; Up/Down pick the cell with max horizontal overlap
     * (fallback: closest center X).
     */
    fun buildFocusNeighbors(items: List<TimelineMosaicItem>): Map<String, TimelineFocusNeighbors> {
        val rows = items.filterIsInstance<TimelineMosaicItem.Row>()
        val result = mutableMapOf<String, TimelineFocusNeighbors>()
        rows.forEachIndexed { rowIndex, row ->
            row.cells.forEachIndexed { cellIndex, cell ->
                val left = row.cells.getOrNull(cellIndex - 1)?.asset?.id
                val right = row.cells.getOrNull(cellIndex + 1)?.asset?.id
                val up = pickVerticalNeighbor(cell, rows.getOrNull(rowIndex - 1)?.cells)
                val down = pickVerticalNeighbor(cell, rows.getOrNull(rowIndex + 1)?.cells)
                result[cell.asset.id] = TimelineFocusNeighbors(left, right, up, down)
            }
        }
        return result
    }

    private fun pickVerticalNeighbor(
        from: TimelineMosaicCell,
        candidates: List<TimelineMosaicCell>?
    ): String? {
        if (candidates.isNullOrEmpty()) return null
        val fromLeft = from.xPx
        val fromRight = from.xPx + from.widthPx
        val fromCenter = fromLeft + from.widthPx / 2.0

        var bestId: String? = null
        var bestOverlap = -1
        var bestCenterDist = Double.MAX_VALUE
        candidates.forEach { other ->
            val otherLeft = other.xPx
            val otherRight = other.xPx + other.widthPx
            val overlap = max(0, minOf(fromRight, otherRight) - maxOf(fromLeft, otherLeft))
            val centerDist = kotlin.math.abs(fromCenter - (otherLeft + other.widthPx / 2.0))
            if (overlap > bestOverlap || (overlap == bestOverlap && centerDist < bestCenterDist)) {
                bestOverlap = overlap
                bestCenterDist = centerDist
                bestId = other.asset.id
            }
        }
        return bestId
    }

    private fun packDay(
        day: TimelineDay,
        contentWidthPx: Int,
        rowHeightPx: Int,
        gapPx: Int,
        out: MutableList<TimelineMosaicItem>
    ) {
        if (day.assets.isEmpty()) return
        val remaining = day.assets.toMutableList()
        var rowSerial = 0
        while (remaining.isNotEmpty()) {
            val batch = mutableListOf<TimelineAsset>()
            var used = 0
            while (remaining.isNotEmpty()) {
                val next = remaining.first()
                val w = naturalWidth(next.ratio, rowHeightPx)
                val extraGap = if (batch.isEmpty()) 0 else gapPx
                // Always take at least one; include the item that tips over, then scale down.
                remaining.removeAt(0)
                batch += next
                used += extraGap + w
                if (used > contentWidthPx) break
            }
            val isLastRow = remaining.isEmpty()
            val rowId = "${day.dayKey}-$rowSerial"
            rowSerial++
            out += TimelineMosaicItem.Row(
                rowId = rowId,
                dayKey = day.dayKey,
                cells = justifyRow(day.dayKey, rowId, batch, contentWidthPx, rowHeightPx, gapPx, isLastRow)
            )
        }
    }

    private fun justifyRow(
        dayKey: String,
        rowId: String,
        assets: List<TimelineAsset>,
        contentWidthPx: Int,
        rowHeightPx: Int,
        gapPx: Int,
        leftAlignLast: Boolean
    ): List<TimelineMosaicCell> {
        if (assets.isEmpty()) return emptyList()
        val natural = assets.map { naturalWidth(it.ratio, rowHeightPx).toDouble() }
        val gapsTotal = gapPx * (assets.size - 1).coerceAtLeast(0)
        val naturalSum = natural.sum()
        val available = (contentWidthPx - gapsTotal).coerceAtLeast(1)
        // Full rows (and single ultra-wide cells) scale both axes to preserve aspect.
        // Incomplete last rows stay at the target height and left-align.
        val scale = when {
            leftAlignLast && naturalSum <= available -> 1.0
            naturalSum <= 0.0 -> 1.0
            else -> available.toDouble() / naturalSum
        }
        val height = max(1, (rowHeightPx * scale).roundToInt())
        val rawWidths = natural.map { max(1, (it * scale).roundToInt()) }.toMutableList()
        if (scale != 1.0 && rawWidths.isNotEmpty()) {
            // Absorb rounding drift into the last cell so the row fills exactly.
            val drift = available - rawWidths.sum()
            rawWidths[rawWidths.lastIndex] = max(1, rawWidths.last() + drift)
        }
        var x = 0
        return assets.mapIndexed { index, asset ->
            val width = rawWidths[index]
            val cell = TimelineMosaicCell(
                dayKey = dayKey,
                asset = asset,
                widthPx = width,
                heightPx = height,
                rowId = rowId,
                indexInRow = index,
                xPx = x
            )
            x += width + gapPx
            cell
        }
    }
}
