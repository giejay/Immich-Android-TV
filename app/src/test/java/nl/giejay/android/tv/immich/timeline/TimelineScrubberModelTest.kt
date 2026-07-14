package nl.giejay.android.tv.immich.timeline

import nl.giejay.android.tv.immich.api.model.TimeBucketSummary
import org.junit.Assert.assertEquals
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
        val buckets = (2026 downTo 2000).map { year ->
            TimeBucketSummary("$year-06-01", 2)
        }
        val stops = TimelineScrubberModel.buildStops(
            buckets = buckets,
            railContentHeightPx = 400,
            minYearLabelPx = 16,
            minDotPx = 8,
            minSegmentHeightForDotPx = 5
        )
        val labeledYears = stops.filter { it.isYearLabel }.map { it.year }
        assertTrue(labeledYears.size < buckets.size)
        assertTrue(stops.last().isYearLabel)
        assertEquals(2000, stops.last().year)
    }

    @Test
    fun `dots follow Immich height and span gates`() {
        // Many equal thin months on a short rail → each band < 5px → only the seeded first ticks.
        val thin = (1..60).map { i ->
            TimeBucketSummary("2015-%02d-01".format(((i - 1) % 12) + 1), 1)
        }
        val thinStops = TimelineScrubberModel.buildStops(
            buckets = thin,
            railContentHeightPx = 200,
            minYearLabelPx = 16,
            minDotPx = 8,
            minSegmentHeightForDotPx = 5
        )
        // 200/60 ≈ 3.3px < 5 → no additional dots beyond the oldest seed.
        assertEquals(1, thinStops.count { it.hasDot })

        // A few fat months on a tall rail → enough height + span for multiple ticks.
        val fat = listOf(
            TimeBucketSummary("2026-06-01", 200),
            TimeBucketSummary("2025-06-01", 200),
            TimeBucketSummary("2024-06-01", 200),
            TimeBucketSummary("2023-06-01", 200),
            TimeBucketSummary("2022-06-01", 200)
        )
        val fatStops = TimelineScrubberModel.buildStops(
            buckets = fat,
            railContentHeightPx = 400,
            minYearLabelPx = 16,
            minDotPx = 8,
            minSegmentHeightForDotPx = 5
        )
        assertTrue(fatStops.count { it.hasDot } >= 2)
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

    @Test
    fun `formatMonthYear matches Immich hover style`() {
        assertEquals(
            "Jul 2022",
            TimelineScrubberModel.formatMonthYear("2022-07-01", java.util.Locale.US)
        )
    }

    @Test
    fun `indexNearestFraction picks closest stop`() {
        val stops = TimelineScrubberModel.buildStops(
            listOf(
                TimeBucketSummary("2026-07-01", 10),
                TimeBucketSummary("2026-06-01", 10),
                TimeBucketSummary("2026-05-01", 10)
            ),
            railContentHeightPx = 300
        )
        assertTrue(stops.size >= 2)
        val mid = (stops[0].fraction + stops[1].fraction) / 2f
        val nearest = TimelineScrubberModel.indexNearestFraction(stops, mid)
        assertTrue(nearest == 0 || nearest == 1)
        // Slightly closer to first stop → index 0.
        val closerToFirst = stops[0].fraction + (stops[1].fraction - stops[0].fraction) * 0.25f
        assertEquals(0, TimelineScrubberModel.indexNearestFraction(stops, closerToFirst))
        assertEquals(-1, TimelineScrubberModel.indexNearestFraction(emptyList(), 0.5f))
    }
}
