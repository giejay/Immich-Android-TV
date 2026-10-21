package nl.giejay.android.tv.immich.screensaver

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.ImmichApplication
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import nl.giejay.mediaslider.config.MediaSliderConfiguration
import nl.giejay.mediaslider.model.SliderItem
import nl.giejay.mediaslider.model.SliderItemType
import nl.giejay.mediaslider.model.SliderItemViewHolder
import nl.giejay.mediaslider.transformations.GlideTransformations
import nl.giejay.mediaslider.view.MediaSliderController
import nl.giejay.mediaslider.view.MediaSliderView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.time.Duration

class ScreenSaverShuffleControllerTest {
    @Test
    fun `only enabled playlists invoke repeat handler at forward and reverse boundaries`() {
        for (shuffle in listOf(false, true)) {
            val pager: ViewPager = mock()
            val adapter: PagerAdapter = mock()
            val config: MediaSliderConfiguration = mock()
            whenever(pager.adapter).thenReturn(adapter)
            whenever(adapter.count).thenReturn(3)
            whenever(config.shuffleOnRepeat).thenReturn(shuffle)
            val repeats = mutableListOf<Boolean>()
            val controller = MediaSliderController(mock(), mock(), pager, mock(), mock()) {
                repeats += it
                true
            }
            controller.initialize(config)
            whenever(pager.currentItem).thenReturn(1)
            controller.goToNextAsset()
            verify(pager).setCurrentItem(2, false)
            whenever(pager.currentItem).thenReturn(2)
            controller.goToNextAsset()
            whenever(pager.currentItem).thenReturn(0)
            controller.goToPreviousAsset()
            assertEquals(if (shuffle) listOf(false, true) else emptyList<Boolean>(), repeats)
            if (!shuffle) verify(pager).setCurrentItem(0, false)
        }
    }

    @Test
    fun `queued video end event after player replacement cannot advance twice`() {
        val config: MediaSliderConfiguration = mock()
        val pager: ViewPager = mock()
        val adapter: PagerAdapter = mock()
        val player: ExoPlayer = mock()
        whenever(config.shuffleOnRepeat).thenReturn(true)
        whenever(config.controllerPlugins).thenReturn(emptyList())
        whenever(pager.adapter).thenReturn(adapter)
        whenever(adapter.count).thenReturn(1)
        whenever(player.playbackState).thenReturn(Player.STATE_ENDED)
        var repeats = 0
        lateinit var controller: MediaSliderController
        controller = MediaSliderController(mock<Context>(), mock<Handler>(), pager, mock(), mock()) {
            repeats++
            controller.setCurrentPlayer(null)
            true
        }
        controller.initialize(config)
        controller.setCurrentPlayer(player)
        controller.toggleSlideshow(false)
        repeat(2) { controller.onPlaybackStateChanged(Player.STATE_ENDED) }
        assertEquals(1, repeats)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class ScreenSaverShuffleViewTest {
    private class Slider(context: Context) : MediaSliderView(context) {
        fun next() = controller.goToNextAsset()
        fun previous() = controller.goToPreviousAsset()
        fun ids() = config.items.flatMap { it.ids() }
        fun index() = mPager.currentItem
    }

    private fun checkPlayback(count: Int = 5, shuffle: Boolean = true, check: (Slider, MutableList<String>) -> Unit) {
        val application = RuntimeEnvironment.getApplication()
        ImmichApplication.appContext = application
        PreferenceManager.init(application)
        val activityController = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = activityController.get()
        activity.setTheme(R.style.AppTheme)
        val slider = Slider(activity)
        val selected = mutableListOf<String>()
        val items = (1..count).map { id -> SliderItemViewHolder(SliderItem(
            id.toString(), "file:///shuffle-test-$id.png", SliderItemType.IMAGE, 1,
            emptyMap(), null, false
        )) }
        val config = MediaSliderConfiguration(
            0, 3, false, false, items, null, { selected += it.mainItem.id },
            0, 0, 0, GlideTransformations.CENTER_INSIDE, false, false, emptyList(),
            false, 0, 0, shuffleOnRepeat = shuffle
        )
        try {
            activity.setContentView(slider)
            slider.loadMediaSliderView(config)
            settle(slider)
            check(slider, selected)
        } finally {
            slider.onDestroy()
            activityController.pause().stop().destroy()
            ImmichApplication.appContext = null
        }
    }

    private fun settle(slider: View) {
        slider.measure(View.MeasureSpec.makeMeasureSpec(1280, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY))
        slider.layout(0, 0, 1280, 720)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `real pager presents every photo once per round and does not repeat the boundary photo`() = checkPlayback { slider, selected ->
        repeat(4) {
            val round = mutableListOf(selected.last())
            repeat(4) {
                slider.next()
                settle(slider)
                round += selected.last()
            }
            assertEquals(setOf("1", "2", "3", "4", "5"), round.toSet())
            assertEquals(5, round.distinct().size)
            val last = selected.last()
            slider.next()
            settle(slider)
            assertEquals(0, slider.index())
            assertNotEquals(last, selected.last())
            assertEquals(5, slider.ids().size)
        }
    }

    @Test
    fun `reverse repeat presents a new last page`() = checkPlayback { slider, selected ->
        val first = selected.last()
        slider.previous()
        settle(slider)
        assertEquals(4, slider.index())
        assertNotEquals(first, selected.last())
    }

    @Test
    fun `one photo playlist keeps its slideshow timer across repeats`() = checkPlayback(count = 1) { slider, selected ->
        slider.toggleSlideshow(false)
        repeat(3) {
            val before = selected.size
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
            settle(slider)
            assertEquals(before + 1, selected.size)
            assertEquals(listOf("1"), slider.ids())
        }
    }

    @Test
    fun `ordered viewer keeps its original playlist on repeat`() = checkPlayback(shuffle = false) { slider, _ ->
        repeat(5) {
            slider.next()
            settle(slider)
        }
        assertEquals(0, slider.index())
        assertEquals(listOf("1", "2", "3", "4", "5"), slider.ids())
    }
}
