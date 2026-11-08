package nl.giejay.android.tv.immich.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until

internal const val DEFAULT_APP_ID = "io.github.daniii88.immichandroidtvnext"
private const val MAIN_ACTIVITY = "nl.giejay.android.tv.immich.MainActivity"

internal val targetAppId: String
    get() = InstrumentationRegistry.getArguments().getString("targetAppId") ?: DEFAULT_APP_ID

/** The app only has a LEANBACK_LAUNCHER entry, so the default launch intent is null: start the activity by name. */
internal fun MacrobenchmarkScope.startApp() {
    startActivityAndWait { intent -> intent.setClassName(targetAppId, MAIN_ACTIVITY) }
}

private fun MacrobenchmarkScope.pause(ms: Long) = Thread.sleep(ms)

/**
 * Signs in on a fresh install. Pass the server and key as instrumentation arguments:
 * -Pandroid.testInstrumentationRunnerArguments.serverUrl=http://10.0.2.2:2283 -Pandroid.testInstrumentationRunnerArguments.apiKey=...
 * An app that is already signed in skips this.
 */
internal fun MacrobenchmarkScope.signInIfNeeded() {
    val loginText = signInByApiKeyText()
    if (!device.wait(Until.hasObject(By.text(loginText)), 3_000)) return
    val args = InstrumentationRegistry.getArguments()
    val url = args.getString("serverUrl")
    val key = args.getString("apiKey")
    check(url != null && key != null) { "Login screen shown: pass serverUrl and apiKey as instrumentation arguments" }
    // step 1: pick "Sign in by API key" (preselected) and continue
    repeat(3) { device.pressDPadDown() }
    device.pressDPadCenter()
    pause(1_500)
    // step 2: URL, key, then the submit button
    device.pressDPadCenter()
    pause(500)
    device.executeShellCommand("input text $url")
    device.pressEnter()
    pause(500)
    device.pressDPadDown()
    device.pressDPadCenter()
    pause(500)
    device.executeShellCommand("input text $key")
    device.pressEnter()
    pause(500)
    repeat(4) { device.pressDPadDown() }
    device.pressDPadCenter()
    // the journey must not run on the login screen: fail loudly if signing in did not work
    check(device.wait(Until.gone(By.text(loginText)), 30_000)) { "Still on the login screen after signing in with $url" }
    pause(3_000)
}

/** The login option's label in the device language, read from the app's own resources (English as the fallback). */
private fun signInByApiKeyText(): String = try {
    val resources = InstrumentationRegistry.getInstrumentation().context.packageManager.getResourcesForApplication(targetAppId)
    val id = resources.getIdentifier("auth_sign_in_by_api_key", "string", targetAppId)
    if (id != 0) resources.getString(id) else "Sign in by API key"
} catch (e: Exception) {
    "Sign in by API key"
}

/** Timeline: let it load, scroll down and up through the mosaic. */
internal fun MacrobenchmarkScope.scrollTimeline(rows: Int = 25) {
    device.waitForIdle()
    pause(3_000)
    device.pressDPadRight() // from the navigation rail into the content
    pause(1_000)
    repeat(rows) { device.pressDPadDown(); pause(120) }
    repeat(rows / 2) { device.pressDPadUp(); pause(120) }
    device.waitForIdle()
}

/** Opens the focused asset in the viewer, pages through a few assets (videos included) and shows the controller. */
internal fun MacrobenchmarkScope.browseViewer(pages: Int = 8) {
    device.pressDPadCenter()
    pause(2_500)
    repeat(pages) { device.pressDPadRight(); pause(1_400) }
    device.pressDPadDown() // controller bar
    pause(800)
    device.pressDPadRight()
    pause(300)
    device.pressDPadLeft()
    pause(300)
    device.pressBack() // hide the controller
    pause(500)
    device.pressBack() // leave the viewer
    pause(1_500)
}

/** Visits a few pages of the navigation rail and returns to the timeline. */
internal fun MacrobenchmarkScope.visitPages() {
    device.pressBack() // content -> rail
    pause(800)
    repeat(3) {
        device.pressDPadDown()
        pause(300)
        device.pressDPadCenter()
        pause(2_500)
        device.pressDPadRight()
        pause(500)
        repeat(4) { device.pressDPadDown(); pause(150) }
        device.pressBack()
        pause(800)
    }
}
