package nl.giejay.android.tv.immich.timeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimelineVideoDurationTest {

    @Test
    fun `parseSeconds reads HHMMSS with optional fraction`() {
        assertEquals(48L, TimelineVideoDuration.parseSeconds("00:00:48.113"))
        assertEquals(3723L, TimelineVideoDuration.parseSeconds("01:02:03"))
        assertEquals(7L, TimelineVideoDuration.parseSeconds("0:00:07"))
    }

    @Test
    fun `parseSeconds reads raw milliseconds`() {
        assertEquals(48L, TimelineVideoDuration.parseSeconds("48113"))
        assertEquals(0L, TimelineVideoDuration.parseSeconds("0"))
        assertEquals(1L, TimelineVideoDuration.parseSeconds("1500"))
    }

    @Test
    fun `parseSeconds rejects blank`() {
        assertNull(TimelineVideoDuration.parseSeconds(null))
        assertNull(TimelineVideoDuration.parseSeconds(""))
        assertNull(TimelineVideoDuration.parseSeconds("   "))
        assertNull(TimelineVideoDuration.parseSeconds("not-a-duration"))
    }

    @Test
    fun `format matches Immich compact style`() {
        assertEquals("0:07", TimelineVideoDuration.format(7))
        assertEquals("12:34", TimelineVideoDuration.format(754))
        assertEquals("1:02:03", TimelineVideoDuration.format(3723))
        assertEquals("0:00", TimelineVideoDuration.format(-5))
    }
}
