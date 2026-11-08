package nl.giejay.android.tv.immich.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records the classes and methods of the main user journeys; see build.gradle for how to run it.
 * The startup profile only gets the start (cold start up to the loaded timeline), the baseline profile
 * additionally the rest of the journey, so the startup profile stays small and focused.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startup() = rule.collect(packageName = targetAppId, includeInStartupProfile = true) {
        pressHome()
        startApp()
        signInIfNeeded()
        device.waitForIdle()
        Thread.sleep(3_000) // timeline and memories load
    }

    @Test
    fun userJourney() = rule.collect(packageName = targetAppId, includeInStartupProfile = false) {
        pressHome()
        startApp()
        signInIfNeeded()
        scrollTimeline()
        browseViewer()
        visitPages()
    }
}
