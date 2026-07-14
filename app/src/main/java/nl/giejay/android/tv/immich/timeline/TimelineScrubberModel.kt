package nl.giejay.android.tv.immich.timeline

import nl.giejay.android.tv.immich.api.model.TimeBucketSummary
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.max

/**
 * One stop on the Immich-style timeline scrubber rail.
 *
 * [fraction] midpoint of the month band (accent / D-pad).
 * [fractionBottom] bottom of the band — Immich draws labels/dots with `bottom-0`.
 *
 * [isYearLabel] / [hasDot] follow Immich web Scrubber.svelte `calculateSegments`.
 */
data class TimelineScrubberStop(
    val monthKey: String,
    val year: Int,
    val isYearLabel: Boolean,
    val hasDot: Boolean,
    val fraction: Float,
    val fractionBottom: Float
)

object TimelineScrubberModel {

    /** Matches Immich web MIN_YEAR_LABEL_DISTANCE (px at scrubber content height). */
    const val DEFAULT_MIN_YEAR_LABEL_PX = 16

    /** Matches Immich web MIN_DOT_DISTANCE. */
    const val DEFAULT_MIN_DOT_PX = 8

    private const val MIN_WEIGHT = 0.35

    /**
     * @param buckets newest-first Immich month buckets
     * @param railContentHeightPx drawable height of the scrubber content area (excluding padding)
     *
     * Label/dot gating mirrors Immich Scrubber.svelte `calculateSegments`, except we do **not**
     * require `segment.height > 5` for dots: Immich uses laid-out timeline pixel heights (often
     * large); our count-weighted bands are often only a few px tall, and that Immich threshold
     * would wipe almost every tick.
     */
    fun buildStops(
        buckets: List<TimeBucketSummary>,
        railContentHeightPx: Int,
        minYearLabelPx: Int = DEFAULT_MIN_YEAR_LABEL_PX,
        minDotPx: Int = DEFAULT_MIN_DOT_PX
    ): List<TimelineScrubberStop> {
        if (buckets.isEmpty() || railContentHeightPx <= 0) return emptyList()

        val weights = buckets.map { max(MIN_WEIGHT, it.count.toDouble().coerceAtLeast(0.0)) }
        val totalWeight = weights.sum().coerceAtLeast(1.0)

        // Immich walks oldest→newest when assigning labels, then reverses for display (newest on top).
        data class MutableSeg(
            val monthKey: String,
            val year: Int,
            val heightPx: Double,
            var hasLabel: Boolean = false,
            var hasDot: Boolean = false
        )

        val oldestFirst = buckets.indices.reversed().map { index ->
            val bucket = buckets[index]
            val ym = parseYearMonth(bucket.timeBucket)
            MutableSeg(
                monthKey = TimelineViewModel.monthBucketKey(ym.atDay(1)),
                year = ym.year,
                heightPx = (weights[index] / totalWeight) * railContentHeightPx
            )
        }

        var verticalSpanWithoutLabel = 0.0
        var verticalSpanWithoutDot = 0.0
        var previousLabeled: MutableSeg? = null

        for (segment in oldestFirst) {
            if (previousLabeled != null) {
                if (previousLabeled!!.year != segment.year &&
                    verticalSpanWithoutLabel > minYearLabelPx
                ) {
                    verticalSpanWithoutLabel = 0.0
                    segment.hasLabel = true
                    previousLabeled = segment
                }
                // Place a tick every ~minDotPx of accumulated rail (Immich uses the same span
                // counter; we skip their height>5 gate — see KDoc).
                if (verticalSpanWithoutDot > minDotPx) {
                    segment.hasDot = true
                    verticalSpanWithoutDot = 0.0
                }
            } else {
                segment.hasDot = true
                segment.hasLabel = true
                previousLabeled = segment
            }
            verticalSpanWithoutLabel += segment.heightPx
            verticalSpanWithoutDot += segment.heightPx
        }

        // Newest-first for UI / D-pad (index 0 = top of scrubber).
        val newestFirst = oldestFirst.asReversed()
        var cumulative = 0.0
        return newestFirst.map { seg ->
            val start = cumulative
            cumulative += seg.heightPx
            val mid = ((start + cumulative) / 2.0 / railContentHeightPx)
                .toFloat()
                .coerceIn(0f, 1f)
            val bottom = (cumulative / railContentHeightPx)
                .toFloat()
                .coerceIn(0f, 1f)
            TimelineScrubberStop(
                monthKey = seg.monthKey,
                year = seg.year,
                isYearLabel = seg.hasLabel,
                hasDot = seg.hasDot,
                fraction = mid,
                fractionBottom = bottom
            )
        }
    }

    fun indexForMonth(stops: List<TimelineScrubberStop>, monthKey: String): Int {
        val exact = stops.indexOfFirst { it.monthKey == monthKey }
        if (exact >= 0) return exact
        val prefix = monthKey.take(7)
        return stops.indexOfFirst { it.monthKey.startsWith(prefix) }
    }

    fun indexNearestFraction(stops: List<TimelineScrubberStop>, fraction: Float): Int {
        if (stops.isEmpty()) return -1
        var best = 0
        var bestDist = Float.MAX_VALUE
        stops.forEachIndexed { i, stop ->
            val d = kotlin.math.abs(stop.fraction - fraction)
            if (d < bestDist) {
                bestDist = d
                best = i
            }
        }
        return best
    }

    private fun parseYearMonth(timeBucket: String): YearMonth =
        try {
            YearMonth.from(LocalDate.parse(timeBucket))
        } catch (_: Exception) {
            YearMonth.parse(timeBucket.take(7))
        }
}
