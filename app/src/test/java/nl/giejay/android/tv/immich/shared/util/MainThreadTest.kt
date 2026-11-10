package nl.giejay.android.tv.immich.shared.util

import android.os.Looper
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MainThreadTest {
    @Test
    fun runsRightAwayOnTheMainThread() {
        var ran = false
        runOnMainThread { ran = true }
        assertThat(ran).isTrue()
    }

    @Test
    fun movesWorkFromABackgroundThreadToTheMainThread() {
        // e.g. the background debounce thread that loads the page background
        var ranOn: Thread? = null
        val started = CountDownLatch(1)
        val worker = Thread {
            runOnMainThread { ranOn = Thread.currentThread() }
            started.countDown()
        }
        worker.start()
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue()
        assertThat(ranOn).isNull()
        shadowOf(Looper.getMainLooper()).idle()
        assertThat(ranOn).isSameInstanceAs(Looper.getMainLooper().thread)
    }
}
