package nl.giejay.android.tv.immich.timeline

import nl.giejay.android.tv.immich.api.model.TimelineAsset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.OffsetDateTime
import kotlin.math.abs

class TimelineMosaicLayoutEngineTest {

    private fun asset(id: String, ratio: Double) = TimelineAsset(
        id = id,
        ratio = ratio,
        isFavorite = false,
        isImage = true,
        thumbhash = null,
        fileCreatedAt = OffsetDateTime.parse("2026-07-01T12:00:00Z"),
        localOffsetHours = 0.0,
        duration = null
    )

    private fun day(key: String, vararg assets: TimelineAsset) = TimelineDay(
        dayKey = key,
        date = LocalDate.parse(key),
        assets = assets.toList()
    )

    @Test
    fun `clampRatio clamps extremes`() {
        assertEquals(0.35, TimelineMosaicLayoutEngine.clampRatio(0.1), 0.0)
        assertEquals(3.0, TimelineMosaicLayoutEngine.clampRatio(9.0), 0.0)
        assertEquals(1.5, TimelineMosaicLayoutEngine.clampRatio(1.5), 0.0)
    }

    @Test
    fun `layout emits header then justified rows preserving aspect`() {
        val days = listOf(
            day(
                "2026-07-13",
                asset("a", 1.0),
                asset("b", 1.0),
                asset("c", 1.0)
            )
        )
        // 3 squares at h=100 → natural 100 each; width 250 forces one overflowing row of 3
        val items = TimelineMosaicLayoutEngine.layout(
            days = days,
            contentWidthPx = 250,
            rowHeightPx = 100,
            gapPx = 10,
            dayLabel = { "Today" }
        )
        assertTrue(items[0] is TimelineMosaicItem.Header)
        assertEquals("Today", (items[0] as TimelineMosaicItem.Header).label)
        val rows = items.filterIsInstance<TimelineMosaicItem.Row>()
        assertEquals(1, rows.size)
        assertEquals(listOf("a", "b", "c"), rows[0].cells.map { it.asset.id })

        val cells = rows[0].cells
        val widthsSum = cells.sumOf { it.widthPx } + 20
        assertEquals(250, widthsSum)
        // Uniform scale on both axes → shorter than target height, still square cells
        val h = cells[0].heightPx
        assertTrue(h < 100)
        cells.forEach { cell ->
            assertEquals(h, cell.heightPx)
            // Last cell may absorb ±1px rounding drift vs height.
            assertEquals(cell.widthPx.toDouble(), cell.heightPx.toDouble(), 2.0)
        }
    }

    @Test
    fun `two landscapes scale height down instead of stretching wide`() {
        // At h=100, two 16:9 → ~178+178+gap=366 > 300 → one row, scale both axes
        val items = TimelineMosaicLayoutEngine.layout(
            days = listOf(
                day(
                    "2026-07-13",
                    asset("v1", 16.0 / 9.0),
                    asset("v2", 16.0 / 9.0)
                )
            ),
            contentWidthPx = 300,
            rowHeightPx = 100,
            gapPx = 10,
            dayLabel = { "Today" }
        )
        val cells = items.filterIsInstance<TimelineMosaicItem.Row>().single().cells
        assertEquals(2, cells.size)
        assertEquals(300, cells.sumOf { it.widthPx } + 10)
        val expectedRatio = 16.0 / 9.0
        cells.forEach { cell ->
            val actual = cell.widthPx.toDouble() / cell.heightPx.toDouble()
            assertTrue(
                "aspect $actual should stay near $expectedRatio",
                abs(actual - expectedRatio) < 0.05
            )
            assertTrue(cell.heightPx < 100)
        }
    }

    @Test
    fun `last incomplete row stays left-aligned at target height`() {
        val items = TimelineMosaicLayoutEngine.layout(
            days = listOf(day("2026-07-13", asset("only", 1.0))),
            contentWidthPx = 400,
            rowHeightPx = 100,
            gapPx = 8,
            dayLabel = { "Today" }
        )
        val cell = items.filterIsInstance<TimelineMosaicItem.Row>().single().cells.single()
        assertEquals(100, cell.widthPx)
        assertEquals(100, cell.heightPx)
        assertEquals(0, cell.xPx)
    }

    @Test
    fun `empty day emits header only`() {
        val items = TimelineMosaicLayoutEngine.layout(
            days = listOf(day("2026-07-13")),
            contentWidthPx = 400,
            rowHeightPx = 100,
            gapPx = 8,
            dayLabel = { "Empty" }
        )
        assertEquals(1, items.size)
        assertTrue(items[0] is TimelineMosaicItem.Header)
        assertTrue(TimelineMosaicLayoutEngine.buildFocusNeighbors(items).isEmpty())
    }

    @Test
    fun `buildFocusNeighbors wires left right up down by overlap`() {
        val row0 = TimelineMosaicItem.Row(
            "r0",
            "2026-07-13",
            listOf(
                TimelineMosaicCell("2026-07-13", asset("a", 1.0), 100, 100, "r0", 0, 0),
                TimelineMosaicCell("2026-07-13", asset("b", 1.0), 100, 100, "r0", 1, 110)
            )
        )
        val row1 = TimelineMosaicItem.Row(
            "r1",
            "2026-07-13",
            listOf(
                TimelineMosaicCell("2026-07-13", asset("c", 1.0), 100, 100, "r1", 0, 50)
            )
        )
        val map = TimelineMosaicLayoutEngine.buildFocusNeighbors(listOf(row0, row1))
        assertEquals("b", map["a"]?.rightAssetId)
        assertNull(map["a"]?.leftAssetId)
        assertEquals("c", map["a"]?.downAssetId)
        assertEquals("a", map["b"]?.leftAssetId)
        assertEquals("a", map["c"]?.upAssetId)
        assertNull(map["c"]?.downAssetId)
    }

    @Test
    fun `buildFocusNeighbors refuses Up across multi-year loading gaps`() {
        val recent = TimelineMosaicItem.Row(
            "r-recent",
            "2026-02-15",
            listOf(TimelineMosaicCell("2026-02-15", asset("recent", 1.0), 100, 100, "r-recent", 0, 0))
        )
        val historic = TimelineMosaicItem.Row(
            "r-old",
            "2006-07-01",
            listOf(TimelineMosaicCell("2006-07-01", asset("old", 1.0), 100, 100, "r-old", 0, 0))
        )
        val map = TimelineMosaicLayoutEngine.buildFocusNeighbors(listOf(recent, historic))
        assertNull(map["old"]?.upAssetId)
        assertNull(map["recent"]?.downAssetId)
    }

    @Test
    fun `isVerticalNeighborAllowed allows nearby months but not decades`() {
        assertTrue(
            TimelineMosaicLayoutEngine.isVerticalNeighborAllowed("2006-07-01", "2006-01-15")
        )
        assertFalse(
            TimelineMosaicLayoutEngine.isVerticalNeighborAllowed("2006-07-01", "2026-02-15")
        )
    }

    @Test
    fun `isVerticalNeighborAllowed at MAX_VERTICAL_MONTH_GAP boundary`() {
        assertTrue(
            TimelineMosaicLayoutEngine.isVerticalNeighborAllowed("2026-07-01", "2026-01-01")
        )
        assertFalse(
            TimelineMosaicLayoutEngine.isVerticalNeighborAllowed("2026-07-01", "2025-12-01")
        )
    }
}
