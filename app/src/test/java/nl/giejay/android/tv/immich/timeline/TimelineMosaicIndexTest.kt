package nl.giejay.android.tv.immich.timeline

import nl.giejay.android.tv.immich.api.model.TimelineAsset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime

class TimelineMosaicIndexTest {

    private fun asset(id: String) = TimelineAsset(
        id = id,
        ratio = 1.0,
        isFavorite = false,
        isImage = true,
        thumbhash = null,
        fileCreatedAt = OffsetDateTime.parse("2022-01-01T12:00:00Z"),
        localOffsetHours = 0.0,
        duration = null
    )

    private fun cell(dayKey: String, id: String, index: Int, x: Int) =
        TimelineMosaicCell(dayKey, asset(id), 100, 100, "r-$dayKey", index, x)

    private val items = listOf(
        TimelineMosaicItem.Header("2022-01-15", "15 Jan"),
        TimelineMosaicItem.Row(
            "r-jan15",
            "2022-01-15",
            listOf(cell("2022-01-15", "a", 0, 0), cell("2022-01-15", "b", 1, 110))
        ),
        TimelineMosaicItem.Header("2021-12-31", "31 Dec"),
        TimelineMosaicItem.Row(
            "r-dec31",
            "2021-12-31",
            listOf(cell("2021-12-31", "c", 0, 0), cell("2021-12-31", "d", 1, 110))
        )
    )

    @Test
    fun `positionForScrubberMonth prefers local day over YYYY-MM prefix`() {
        // UTC Jan bucket's preferred local day is Dec 31 — must not land on Jan 15 prefix.
        assertEquals(
            2,
            TimelineMosaicIndex.positionForScrubberMonth(
                items,
                monthKey = "2022-01-01",
                preferredDayKey = "2021-12-31"
            )
        )
        assertEquals(
            0,
            TimelineMosaicIndex.positionForScrubberMonth(
                items,
                monthKey = "2022-01-01",
                preferredDayKey = null
            )
        )
    }

    @Test
    fun `remainingDayHeadersFrom counts headers at and below position`() {
        assertEquals(2, TimelineMosaicIndex.remainingDayHeadersFrom(items, 0))
        assertEquals(1, TimelineMosaicIndex.remainingDayHeadersFrom(items, 2))
        assertEquals(0, TimelineMosaicIndex.remainingDayHeadersFrom(items, 3))
        assertEquals(0, TimelineMosaicIndex.remainingDayHeadersFrom(items, -1))
    }

    @Test
    fun `rightmostAssetIdForDay and positionOfAsset`() {
        assertEquals("b", TimelineMosaicIndex.rightmostAssetIdForDay(items, "2022-01-15"))
        assertEquals("d", TimelineMosaicIndex.rightmostAssetIdForDay(items, "2021-12-31"))
        assertNull(TimelineMosaicIndex.rightmostAssetIdForDay(items, "2020-01-01"))
        assertEquals(1, TimelineMosaicIndex.positionOfAsset(items, "b"))
        assertEquals(TimelineMosaicIndex.NO_POSITION, TimelineMosaicIndex.positionOfAsset(items, "missing"))
    }

    @Test
    fun `isInFirstMosaicRow only matches newest mosaic row`() {
        val withMemories = listOf(TimelineMosaicItem.MemoriesRow(emptyList())) + items
        assertTrue(TimelineMosaicIndex.hasMemoriesRow(withMemories))
        assertTrue(TimelineMosaicIndex.isInFirstMosaicRow(withMemories, "a"))
        assertTrue(TimelineMosaicIndex.isInFirstMosaicRow(withMemories, "b"))
        assertFalse(TimelineMosaicIndex.isInFirstMosaicRow(withMemories, "c"))
        assertFalse(TimelineMosaicIndex.isInFirstMosaicRow(withMemories, "missing"))
    }
}
