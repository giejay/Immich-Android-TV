package nl.giejay.android.tv.immich.timeline

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

object TimelineDateFormatter {

    /**
     * Row header label for a month bucket (`YYYY-MM-01`).
     * - Current month → [thisMonthLabel] (default `"This month"`)
     * - Same year, other months → month name only
     * - Prior years → `"March 2024"`
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
