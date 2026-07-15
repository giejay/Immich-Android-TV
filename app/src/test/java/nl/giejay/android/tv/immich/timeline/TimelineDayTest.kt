package nl.giejay.android.tv.immich.timeline

import nl.giejay.android.tv.immich.api.model.TimelineAsset
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.OffsetDateTime

class TimelineDayTest {

    private fun asset(
        id: String,
        createdAt: String,
        localOffsetHours: Double = 0.0
    ) = TimelineAsset(
        id = id,
        ratio = 1.0,
        isFavorite = false,
        isImage = true,
        thumbhash = null,
        fileCreatedAt = OffsetDateTime.parse(createdAt),
        localOffsetHours = localOffsetHours,
        duration = null
    )

    @Test
    fun `localCaptureDate applies photographer offset`() {
        // 23:30 UTC + 2h → next calendar day locally
        val a = asset("a", "2026-07-01T23:30:00Z", localOffsetHours = 2.0)
        assertEquals("2026-07-02", a.localCaptureDate().toString())
    }

    @Test
    fun `toTimelineDays groups by local day newest first`() {
        val days = listOf(
            asset("late", "2026-07-02T08:00:00Z"),
            asset("early-same-day", "2026-07-02T02:00:00Z"),
            asset("prev", "2026-07-01T20:00:00Z")
        ).toTimelineDays()

        assertEquals(listOf("2026-07-02", "2026-07-01"), days.map { it.dayKey })
        assertEquals(listOf("late", "early-same-day"), days[0].assets.map { it.id })
        assertEquals(listOf("prev"), days[1].assets.map { it.id })
    }
}
