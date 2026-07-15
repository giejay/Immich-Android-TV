package nl.giejay.android.tv.immich.timeline

import nl.giejay.android.tv.immich.api.model.TimeBucketSummary
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
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

/**
 * Port of Immich web [Scrubber.svelte](https://github.com/immich-app/immich/blob/main/web/src/lib/components/timeline/Scrubber.svelte)
 * `calculateSegments` (constants MIN_YEAR_LABEL_DISTANCE=16, MIN_DOT_DISTANCE=8, height>5).
 *
 * Immich sizes each scrubber segment from the month's laid-out timeline height; we approximate
 * that with [TimeBucketSummary.count]. Callers may scale the Immich px constants for TV.
 */
object TimelineScrubberModel {

    /** Immich Scrubber.svelte `MIN_YEAR_LABEL_DISTANCE`. */
    const val DEFAULT_MIN_YEAR_LABEL_PX = 16

    /** Immich Scrubber.svelte `MIN_DOT_DISTANCE`. */
    const val DEFAULT_MIN_DOT_PX = 8

    /** Immich Scrubber.svelte `segment.height > 5` gate before a month may show a dot. */
    const val DEFAULT_MIN_SEGMENT_HEIGHT_FOR_DOT_PX = 5

    private const val MIN_WEIGHT = 0.35

    /**
     * @param buckets newest-first Immich month buckets
     * @param railContentHeightPx drawable height of the scrubber content area (excluding padding)
     */
    fun buildStops(
        buckets: List<TimeBucketSummary>,
        railContentHeightPx: Int,
        minYearLabelPx: Int = DEFAULT_MIN_YEAR_LABEL_PX,
        minDotPx: Int = DEFAULT_MIN_DOT_PX,
        minSegmentHeightForDotPx: Int = DEFAULT_MIN_SEGMENT_HEIGHT_FOR_DOT_PX
    ): List<TimelineScrubberStop> {
        if (buckets.isEmpty() || railContentHeightPx <= 0) return emptyList()

        val weights = buckets.map { max(MIN_WEIGHT, it.count.toDouble().coerceAtLeast(0.0)) }
        val totalWeight = weights.sum().coerceAtLeast(1.0)

        // Immich walks oldest→newest when assigning labels, then reverses for display.
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
                // Keep the API key so loadBucket / recomputeDays share the same map key.
                monthKey = bucket.timeBucket,
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
                // Exact Immich gate: only "tall enough" months may host a tick, and only after
                // enough unused vertical span has accumulated since the last tick.
                if (segment.heightPx > minSegmentHeightForDotPx &&
                    verticalSpanWithoutDot > minDotPx
                ) {
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
        val target = runCatching { YearMonth.from(LocalDate.parse(monthKey.take(10))) }.getOrNull()
            ?: runCatching { YearMonth.parse(monthKey.take(7)) }.getOrNull()
            ?: return -1
        return stops.indexOfFirst {
            runCatching { YearMonth.from(LocalDate.parse(it.monthKey.take(10))) }.getOrNull() == target
        }
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

    /** Immich hover label style: "Jul 2022". */
    fun formatMonthYear(monthKey: String, locale: Locale = Locale.getDefault()): String =
        try {
            YearMonth.from(LocalDate.parse(monthKey))
                .format(DateTimeFormatter.ofPattern("MMM yyyy", locale))
        } catch (_: Exception) {
            monthKey.take(7)
        }

    private fun parseYearMonth(timeBucket: String): YearMonth =
        try {
            YearMonth.from(LocalDate.parse(timeBucket))
        } catch (_: Exception) {
            YearMonth.parse(timeBucket.take(7))
        }
}
