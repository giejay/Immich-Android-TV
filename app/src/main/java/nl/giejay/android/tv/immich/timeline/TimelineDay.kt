package nl.giejay.android.tv.immich.timeline

import nl.giejay.android.tv.immich.api.model.TimelineAsset
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.roundToInt

/**
 * One calendar day of timeline assets (newest-first within the day).
 * [dayKey] is ISO-8601 `YYYY-MM-DD`.
 */
data class TimelineDay(
    val dayKey: String,
    val date: LocalDate,
    val assets: List<TimelineAsset>
)

/**
 * Photographer-local calendar day: apply [TimelineAsset.localOffsetHours] to UTC
 * [TimelineAsset.fileCreatedAt], matching Immich's timeline grouping.
 */
fun TimelineAsset.localCaptureDate(): LocalDate {
    val offsetSeconds = (localOffsetHours * 3600.0).roundToInt()
        .coerceIn(ZoneOffset.MIN.totalSeconds, ZoneOffset.MAX.totalSeconds)
    return fileCreatedAt.toInstant()
        .atOffset(ZoneOffset.ofTotalSeconds(offsetSeconds))
        .toLocalDate()
}

/**
 * Groups assets into day rows (newest day first; within each day newest-first).
 */
fun List<TimelineAsset>.toTimelineDays(): List<TimelineDay> =
    groupBy { it.localCaptureDate() }
        .entries
        .sortedByDescending { it.key }
        .map { (date, assets) ->
            TimelineDay(
                dayKey = date.toString(),
                date = date,
                assets = assets.sortedByDescending { it.fileCreatedAt }
            )
        }
