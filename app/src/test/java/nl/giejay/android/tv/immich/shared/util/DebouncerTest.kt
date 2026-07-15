package nl.giejay.android.tv.immich.shared.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class DebouncerTest {

    @Test
    fun `cancel removes pending runnable so it never runs`() {
        val latch = CountDownLatch(1)
        Debouncer.debounce("test-cancel", { latch.countDown() }, 50, TimeUnit.MILLISECONDS)
        Debouncer.cancel("test-cancel")
        assertFalse(latch.await(200, TimeUnit.MILLISECONDS))
    }

    @Test
    fun `debounce after cancel still schedules new work`() {
        val ran = AtomicBoolean(false)
        Debouncer.debounce("test-rearm", { /* cancelled */ }, 500, TimeUnit.MILLISECONDS)
        Debouncer.cancel("test-rearm")
        val latch = CountDownLatch(1)
        Debouncer.debounce("test-rearm", {
            ran.set(true)
            latch.countDown()
        }, 50, TimeUnit.MILLISECONDS)
        assertTrue(latch.await(1, TimeUnit.SECONDS))
        assertTrue(ran.get())
    }
}
