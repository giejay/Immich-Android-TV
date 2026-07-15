package nl.giejay.android.tv.immich.timeline

/**
 * Parses/formats [nl.giejay.android.tv.immich.api.model.TimelineAsset.duration] for the
 * mosaic video badge (Immich-style "0:07" overlay).
 *
 * Immich sends either legacy `"HH:MM:SS.mmm"` strings or, on newer servers, a plain
 * millisecond integer (see [nl.giejay.android.tv.immich.api.model.TimeBucketAssetsResponse]).
 */
object TimelineVideoDuration {

    private val HMS_REGEX = Regex("""^(\d+):(\d{2}):(\d{2})(\.\d+)?$""")

    fun parseSeconds(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        HMS_REGEX.matchEntire(raw)?.let { match ->
            val (h, m, s, _) = match.destructured
            return h.toLong() * 3600 + m.toLong() * 60 + s.toLong()
        }
        return raw.toLongOrNull()?.let { millis -> millis / 1000 }
    }

    /** Immich-style compact duration, e.g. "0:07", "12:34", "1:02:03". */
    fun format(totalSeconds: Long): String {
        val clamped = totalSeconds.coerceAtLeast(0)
        val hours = clamped / 3600
        val minutes = (clamped % 3600) / 60
        val seconds = clamped % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%d:%02d".format(minutes, seconds)
        }
    }
}
