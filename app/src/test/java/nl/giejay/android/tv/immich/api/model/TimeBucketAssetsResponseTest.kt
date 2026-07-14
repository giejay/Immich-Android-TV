package nl.giejay.android.tv.immich.api.model

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class TimeBucketAssetsResponseTest {

    private val gson = Gson()

    @Test
    fun `toTimelineAssets unzips struct-of-arrays by index`() {
        val response = TimeBucketAssetsResponse(
            id = listOf("a", "b"),
            ownerId = listOf("o1", "o2"),
            ratio = listOf(1.5, 0.75),
            isFavorite = listOf(true, false),
            visibility = listOf("timeline", "timeline"),
            isTrashed = listOf(false, false),
            isImage = listOf(true, false),
            thumbhash = listOf("hash", null),
            fileCreatedAt = listOf("2026-07-01T10:00:00Z", "2026-07-02T11:00:00Z"),
            localOffsetHours = listOf(2.0, -5.0),
            duration = listOf(null, "00:00:48.113")
        )

        val assets = response.toTimelineAssets()
        assertEquals(2, assets.size)
        assertEquals("a", assets[0].id)
        assertEquals(1.5, assets[0].ratio, 0.0)
        assertTrue(assets[0].isFavorite)
        assertTrue(assets[0].isImage)
        assertEquals("hash", assets[0].thumbhash)
        assertEquals(2.0, assets[0].localOffsetHours, 0.0)
        assertNull(assets[0].duration)

        assertEquals("b", assets[1].id)
        assertFalse(assets[1].isImage)
        assertEquals(-5.0, assets[1].localOffsetHours, 0.0)
        assertEquals("00:00:48.113", assets[1].duration)
    }

    @Test
    fun `parseTimelineTimestamp accepts offset-less Immich UTC timestamps`() {
        val parsed = parseTimelineTimestamp("2024-05-12T15:30:00.123")
        assertEquals(2024, parsed.year)
        assertEquals(5, parsed.monthValue)
        assertEquals(12, parsed.dayOfMonth)
        assertEquals(15, parsed.hour)
        assertEquals(30, parsed.minute)
        assertEquals(ZoneOffset.UTC, parsed.offset)
    }

    @Test
    fun `parseTimelineTimestamp accepts Zulu timestamps`() {
        val parsed = parseTimelineTimestamp("2024-05-12T15:30:00.123Z")
        assertEquals(ZoneOffset.UTC, parsed.offset)
        assertEquals(15, parsed.hour)
    }

    @Test
    fun `gson deserializes bucket with offset-less fileCreatedAt and string duration`() {
        val json = bucketJson(durationJson = """["00:00:48.113"]""")
        val parsed = gson.fromJson(json, TimeBucketAssetsResponse::class.java)
        assertEquals("00:00:48.113", parsed.duration.single())
        assertEquals(1, parsed.toTimelineAssets().size)
    }

    @Test
    fun `gson deserializes bucket response with millisecond durations`() {
        val json = bucketJson(durationJson = """[48113]""")
        val parsed = gson.fromJson(json, TimeBucketAssetsResponse::class.java)
        assertEquals("48113", parsed.duration.single())
    }

    private fun bucketJson(durationJson: String): String = """
        {
          "id":["a"],
          "ownerId":["o"],
          "ratio":[1.0],
          "isFavorite":[false],
          "visibility":["timeline"],
          "isTrashed":[false],
          "isImage":[false],
          "thumbhash":[null],
          "fileCreatedAt":["2026-07-01T10:00:00.000"],
          "localOffsetHours":[0.0],
          "duration":$durationJson
        }
    """.trimIndent()
}
