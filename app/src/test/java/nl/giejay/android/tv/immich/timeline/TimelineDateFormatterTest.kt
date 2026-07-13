package nl.giejay.android.tv.immich.timeline

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class TimelineDateFormatterTest {

    private val locale = Locale.US

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
}
