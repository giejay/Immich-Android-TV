package nl.giejay.android.tv.immich.timeline

import nl.giejay.android.tv.immich.api.model.TimeBucketSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineScrubberModelTest {

    @Test
    fun `buildStops fractions are monotonic newest to oldest`() {
        val buckets = listOf(
            TimeBucketSummary("2026-07-01", 50),
            TimeBucketSummary("2026-06-01", 10),
            TimeBucketSummary("2025-12-01", 20),
            TimeBucketSummary("2025-01-01", 5),
            TimeBucketSummary("2024-06-01", 100)
        )
        val stops = TimelineScrubberModel.buildStops(buckets, railContentHeightPx = 800)
        assertEquals(5, stops.size)
        for (i in 1 until stops.size) {
            assertTrue(stops[i].fraction >= stops[i - 1].fraction)
        }
        assertTrue(stops.first().fraction in 0f..1f)
        assertTrue(stops.last().fraction in 0f..1f)
    }

    @Test
    fun `crowded years omit overlapping labels like Immich`() {
        // Many consecutive years with tiny buckets on a short rail → most year labels suppressed.
        val buckets = (2026 downTo 2000).map { year ->
            TimeBucketSummary("$year-06-01", 2)
        }
        val stops = TimelineScrubberModel.buildStops(
            buckets = buckets,
            railContentHeightPx = 400,
            minYearLabelPx = 16,
            minDotPx = 8
        )
        val labeledYears = stops.filter { it.isYearLabel }.map { it.year }
        // Not every year can fit a label; Immich skips when span ≤ min distance.
        assertTrue(labeledYears.size < stops.size)
        assertTrue(labeledYears.size < buckets.size)
        // Oldest segment is always labeled (Immich walks oldest→newest).
        assertTrue(stops.last().isYearLabel)
        // Newest-first display: first labeled after reverse is the most recent that fit —
        // the very oldest always has a label.
        assertEquals(2000, stops.last().year)
    }

    @Test
    fun `dots appear along the rail even with many tiny month bands`() {
        val buckets = (1..48).map { i ->
            val year = 2026 - (i - 1) / 12
            val month = ((i - 1) % 12) + 1
            TimeBucketSummary("%04d-%02d-01".format(year, month), 3)
        }
        // Tall rail + Immich 8px cadence → ticks even when each month band is only a few px.
        val stops = TimelineScrubberModel.buildStops(
            buckets = buckets,
            railContentHeightPx = 400,
            minYearLabelPx = 16,
            minDotPx = 8
        )
        val dotted = stops.count { it.hasDot }
        // ~400/8 ≈ 50 possible ticks; with equal bands we get dense but not necessarily every month.
        assertTrue("expected plentiful dots, got $dotted of ${stops.size}", dotted >= 20)
        assertTrue(stops.first().hasDot || stops.last().hasDot)
    }

    @Test
    fun `empty buckets or zero height yields empty stops`() {
        assertTrue(TimelineScrubberModel.buildStops(emptyList(), 400).isEmpty())
        assertTrue(
            TimelineScrubberModel.buildStops(
                listOf(TimeBucketSummary("2026-01-01", 1)),
                railContentHeightPx = 0
            ).isEmpty()
        )
    }

    @Test
    fun `indexForMonth finds month key`() {
        val stops = TimelineScrubberModel.buildStops(
            listOf(
                TimeBucketSummary("2026-07-01", 10),
                TimeBucketSummary("2026-06-01", 10)
            ),
            railContentHeightPx = 400
        )
        assertEquals(1, TimelineScrubberModel.indexForMonth(stops, "2026-06-01"))
        assertEquals(-1, TimelineScrubberModel.indexForMonth(stops, "2020-01-01"))
    }
}
