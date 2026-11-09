package nl.giejay.android.tv.immich.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cold start without and with the baseline profile. Run on the real TV, emulator numbers say little:
 *   ./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest
 * The app has to be signed in (the benchmarks start the app and wait for the timeline).
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmarks {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun startupWithoutProfile() = startup(CompilationMode.None())

    @Test
    fun startupWithBaselineProfile() = startup(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun startup(mode: CompilationMode) = rule.measureRepeated(
        packageName = targetAppId,
        metrics = listOf(StartupTimingMetric()),
        iterations = 8,
        startupMode = StartupMode.COLD,
        compilationMode = mode,
    ) {
        pressHome()
        startApp()
        signInIfNeeded()
        device.waitForIdle()
    }
}

/** Frame timing (jank) while D-pad scrolling the Compose timeline, without and with the profile. */
@RunWith(AndroidJUnit4::class)
class TimelineScrollBenchmarks {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun scrollWithoutProfile() = scroll(CompilationMode.None())

    @Test
    fun scrollWithBaselineProfile() = scroll(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun scroll(mode: CompilationMode) = rule.measureRepeated(
        packageName = targetAppId,
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.WARM,
        compilationMode = mode,
        setupBlock = {
            pressHome()
            startApp()
            signInIfNeeded()
            device.waitForIdle()
            Thread.sleep(3_000) // timeline loads
            device.pressDPadRight() // into the content
        },
    ) {
        repeat(40) { device.pressDPadDown(); Thread.sleep(120) }
        repeat(20) { device.pressDPadUp(); Thread.sleep(120) }
        device.waitForIdle()
    }
}

/** Cold start with the View based (classic) timeline, without and with the profile. Run on a fresh install. */
@RunWith(AndroidJUnit4::class)
class LegacyStartupBenchmarks {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun startupWithoutProfile() = startup(CompilationMode.None())

    @Test
    fun startupWithBaselineProfile() = startup(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun startup(mode: CompilationMode) = rule.measureRepeated(
        packageName = targetAppId,
        metrics = listOf(StartupTimingMetric()),
        iterations = 8,
        startupMode = StartupMode.COLD,
        compilationMode = mode,
        setupBlock = {
            pressHome()
            startApp()
            signInIfNeeded()
            enableClassicTimeline()
            pressHome()
        },
    ) {
        startApp()
        device.waitForIdle()
    }
}

/** Frame timing while D-pad scrolling the View based (classic) timeline, without and with the profile. */
@RunWith(AndroidJUnit4::class)
class LegacyTimelineScrollBenchmarks {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun scrollWithoutProfile() = scroll(CompilationMode.None())

    @Test
    fun scrollWithBaselineProfile() = scroll(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun scroll(mode: CompilationMode) = rule.measureRepeated(
        packageName = targetAppId,
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.WARM,
        compilationMode = mode,
        setupBlock = {
            pressHome()
            startApp()
            signInIfNeeded()
            enableClassicTimeline()
            startApp()
            device.waitForIdle()
            Thread.sleep(3_000) // timeline loads
            device.pressDPadRight() // into the content
        },
    ) {
        repeat(40) { device.pressDPadDown(); Thread.sleep(120) }
        repeat(20) { device.pressDPadUp(); Thread.sleep(120) }
        device.waitForIdle()
    }
}
