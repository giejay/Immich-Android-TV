package nl.giejay.android.tv.immich.timeline

import org.junit.Assert.assertEquals
import org.junit.Test

class MemoryLabelsTest {

    @Test
    fun `yearsAgo delta edges`() {
        assertEquals(0, MemoryLabels.yearsAgo(2026, 2026))
        assertEquals(1, MemoryLabels.yearsAgo(2026, 2025))
        assertEquals(20, MemoryLabels.yearsAgo(2026, 2006))
        assertEquals(0, MemoryLabels.yearsAgo(2020, 2025))
    }
}
