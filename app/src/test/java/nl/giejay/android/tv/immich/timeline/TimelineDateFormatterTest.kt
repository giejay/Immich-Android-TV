package nl.giejay.android.tv.immich.timeline

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class TimelineDateFormatterTest {

    private val locale = Locale.US
    private val now = LocalDate.of(2026, 7, 13) // Monday

    @Test
    fun `current month is labeled This month`() {
        val label = TimelineDateFormatter.label(
            timeBucket = "2026-07-01",
            now = LocalDate.of(2026, 7, 15),
            thisMonthLabel = "This month",
            locale = locale
        )
        assertEquals("This month", label)
    }

    @Test
    fun `same year other month uses month name only`() {
        val label = TimelineDateFormatter.label(
            timeBucket = "2026-03-01",
            now = LocalDate.of(2026, 7, 15),
            locale = locale
        )
        assertEquals("March", label)
    }

    @Test
    fun `prior year includes year`() {
        val label = TimelineDateFormatter.label(
            timeBucket = "2024-03-01",
            now = LocalDate.of(2026, 7, 15),
            locale = locale
        )
        assertEquals("March 2024", label)
    }

    @Test
    fun `year boundary uses month name only for January of current year`() {
        val label = TimelineDateFormatter.label(
            timeBucket = "2026-01-01",
            now = LocalDate.of(2026, 7, 15),
            locale = locale
        )
        assertEquals("January", label)
    }

    @Test
    fun `dayLabel uses Today and Yesterday`() {
        assertEquals(
            "Today",
            TimelineDateFormatter.dayLabel(now, now = now, locale = locale)
        )
        assertEquals(
            "Yesterday",
            TimelineDateFormatter.dayLabel(now.minusDays(1), now = now, locale = locale)
        )
    }

    @Test
    fun `dayLabel uses weekday name for last 6 days`() {
        // 2 days ago from Mon Jul 13 = Sat Jul 11
        assertEquals(
            "Saturday",
            TimelineDateFormatter.dayLabel(LocalDate.of(2026, 7, 11), now = now, locale = locale)
        )
        // 6 days ago = Tue Jul 7
        assertEquals(
            "Tuesday",
            TimelineDateFormatter.dayLabel(LocalDate.of(2026, 7, 7), now = now, locale = locale)
        )
    }

    @Test
    fun `dayLabel formats older same-year and prior-year dates`() {
        assertEquals(
            "Sun, Jul 5",
            TimelineDateFormatter.dayLabel(LocalDate.of(2026, 7, 5), now = now, locale = locale)
        )
        assertEquals(
            "Sat, Dec 20, 2025",
            TimelineDateFormatter.dayLabel(LocalDate.of(2025, 12, 20), now = now, locale = locale)
        )
    }
}
