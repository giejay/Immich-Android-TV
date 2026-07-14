package nl.giejay.android.tv.immich.timeline

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

object TimelineDateFormatter {

    /**
     * Immich-style day row header:
     * - Today / Yesterday
     * - Last 6 days (exclusive of today/yesterday): weekday only ("Saturday")
     * - Same calendar year: "Sun, Jul 5"
     * - Older: "Sun, Jul 5, 2024"
     */
    fun dayLabel(
        date: LocalDate,
        now: LocalDate = LocalDate.now(),
        todayLabel: String = "Today",
        yesterdayLabel: String = "Yesterday",
        locale: Locale = Locale.getDefault()
    ): String {
        val daysAgo = ChronoUnit.DAYS.between(date, now)
        return when {
            date == now -> todayLabel
            date == now.minusDays(1) -> yesterdayLabel
            daysAgo in 2..6 -> date.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
            date.year == now.year ->
                date.format(DateTimeFormatter.ofPattern("EEE, MMM d", locale))
            else ->
                date.format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", locale))
        }
    }

    /**
     * Row header label for a month bucket (`YYYY-MM-01`). Kept for compatibility.
     */
    fun label(
        timeBucket: String,
        now: LocalDate = LocalDate.now(),
        thisMonthLabel: String = "This month",
        locale: Locale = Locale.getDefault()
    ): String {
        val bucketMonth = YearMonth.from(LocalDate.parse(timeBucket))
        val currentMonth = YearMonth.from(now)
        return when {
            bucketMonth == currentMonth -> thisMonthLabel
            bucketMonth.year == currentMonth.year ->
                bucketMonth.month.getDisplayName(TextStyle.FULL, locale)
            else ->
                "${bucketMonth.month.getDisplayName(TextStyle.FULL, locale)} ${bucketMonth.year}"
        }
    }
}
