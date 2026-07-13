package nl.giejay.android.tv.immich.api.model

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.annotations.JsonAdapter
import java.lang.reflect.Type
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeParseException

data class TimeBucketSummary(
    val timeBucket: String, // "2026-07-01"
    val count: Int
)

/**
 * Struct-of-arrays response from GET /timeline/bucket.
 * Mirror of server TimeBucketAssetResponseSchema (server/src/dtos/time-bucket.dto.ts).
 *
 * `duration` varies by Immich version: older servers send "HH:MM:SS.mmm" strings;
 * newer ones send millisecond ints. [FlexibleNullableStringListAdapter] accepts both.
 */
data class TimeBucketAssetsResponse(
    val id: List<String>,
    val ownerId: List<String>,
    val ratio: List<Double>,
    val isFavorite: List<Boolean>,
    val visibility: List<String>,
    val isTrashed: List<Boolean>,
    val isImage: List<Boolean>,
    val thumbhash: List<String?>,
    val fileCreatedAt: List<String>,
    val localOffsetHours: List<Double>,
    @JsonAdapter(FlexibleNullableStringListAdapter::class)
    val duration: List<String?>
)

/**
 * Per-item shape the Timeline UI works with, produced by unzipping [TimeBucketAssetsResponse].
 * Intentionally distinct from [Asset] — bucket responses omit EXIF/people/path data.
 */
data class TimelineAsset(
    val id: String,
    val ratio: Double,
    val isFavorite: Boolean,
    val isImage: Boolean,
    val thumbhash: String?,
    val fileCreatedAt: OffsetDateTime,
    val duration: String?
)

fun TimeBucketAssetsResponse.toTimelineAssets(): List<TimelineAsset> =
    id.indices.map { i ->
        TimelineAsset(
            id = id[i],
            ratio = ratio.getOrElse(i) { 1.0 },
            isFavorite = isFavorite.getOrElse(i) { false },
            isImage = isImage.getOrElse(i) { true },
            thumbhash = thumbhash.getOrNull(i),
            fileCreatedAt = parseTimelineTimestamp(fileCreatedAt[i]),
            duration = duration.getOrNull(i)
        )
    }

/**
 * Immich often returns UTC timestamps with the offset stripped (e.g. `2024-05-12T15:30:00.123`),
 * which [OffsetDateTime.parse] rejects. Treat offset-less values as UTC.
 */
internal fun parseTimelineTimestamp(value: String): OffsetDateTime {
    val trimmed = value.trim()
    return try {
        OffsetDateTime.parse(trimmed)
    } catch (_: DateTimeParseException) {
        try {
            Instant.parse(trimmed).atOffset(ZoneOffset.UTC)
        } catch (_: DateTimeParseException) {
            LocalDateTime.parse(trimmed).atOffset(ZoneOffset.UTC)
        }
    }
}

/** Accepts JSON null, string, or number elements so duration works across Immich versions. */
class FlexibleNullableStringListAdapter : JsonDeserializer<List<String?>> {
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): List<String?> {
        if (!json.isJsonArray) return emptyList()
        return json.asJsonArray.map { element ->
            when {
                element == null || element.isJsonNull -> null
                element.isJsonPrimitive && element.asJsonPrimitive.isNumber ->
                    element.asNumber.toLong().toString()
                else -> element.asString
            }
        }
    }
}
