package nl.giejay.android.tv.immich.timeline

/**
 * Pure index lookups over a mosaic item list (headers + rows + optional memories).
 * [TimelineMosaicAdapter] delegates here so scrubber jumps and load-more thresholds
 * stay unit-testable without RecyclerView.
 */
object TimelineMosaicIndex {

    const val NO_POSITION = -1

    fun positionOfAsset(items: List<TimelineMosaicItem>, assetId: String): Int {
        items.forEachIndexed { index, item ->
            if (item is TimelineMosaicItem.Row && item.cells.any { it.asset.id == assetId }) {
                return index
            }
        }
        return NO_POSITION
    }

    fun positionOfDay(items: List<TimelineMosaicItem>, dayKey: String): Int {
        items.forEachIndexed { index, item ->
            if (item is TimelineMosaicItem.Header && item.dayKey == dayKey) {
                return index
            }
        }
        return NO_POSITION
    }

    /**
     * Prefer [preferredDayKey] (local day from the Immich UTC month bucket). Fall back to the
     * first header whose day sits in the same YYYY-MM as [monthKey].
     */
    fun positionForScrubberMonth(
        items: List<TimelineMosaicItem>,
        monthKey: String,
        preferredDayKey: String?
    ): Int {
        if (preferredDayKey != null) {
            val exact = positionOfDay(items, preferredDayKey)
            if (exact != NO_POSITION) return exact
        }
        val prefix = monthKey.take(7) // YYYY-MM
        items.forEachIndexed { index, item ->
            if (item is TimelineMosaicItem.Header && item.dayKey.startsWith(prefix)) {
                return index
            }
        }
        return NO_POSITION
    }

    fun remainingDayHeadersFrom(items: List<TimelineMosaicItem>, adapterPosition: Int): Int {
        if (adapterPosition < 0) return 0
        return items.drop(adapterPosition).count { it is TimelineMosaicItem.Header }
    }

    fun rightmostAssetIdForDay(items: List<TimelineMosaicItem>, dayKey: String): String? =
        items.filterIsInstance<TimelineMosaicItem.Row>()
            .firstOrNull { it.dayKey == dayKey }
            ?.cells
            ?.lastOrNull()
            ?.asset
            ?.id
}
