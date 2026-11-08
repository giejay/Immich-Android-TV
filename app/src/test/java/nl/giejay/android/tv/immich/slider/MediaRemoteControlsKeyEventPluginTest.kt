package nl.giejay.android.tv.immich.slider

import android.view.KeyEvent
import androidx.media3.exoplayer.ExoPlayer
import nl.giejay.mediaslider.config.MediaSliderConfiguration
import nl.giejay.mediaslider.model.SliderItemType
import nl.giejay.mediaslider.plugin.MediaRemoteControlsKeyEventPlugin
import nl.giejay.mediaslider.plugin.SliderKeyEventResult
import nl.giejay.mediaslider.plugin.SliderKeyEventState
import nl.giejay.mediaslider.view.MediaSliderController
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class MediaRemoteControlsKeyEventPluginTest {
    private val player = mock<ExoPlayer>()
    private val controller = mock<MediaSliderController> { on { currentPlayer } doReturn player }
    private val plugin = MediaRemoteControlsKeyEventPlugin()

    private fun state(type: SliderItemType) = SliderKeyEventState(
        isControllerVisible = true,
        isSlideshowPlaying = false,
        currentItemType = type,
        controller = controller,
        config = mock<MediaSliderConfiguration>(),
    )

    private fun playPause(repeat: Int) =
        mock<KeyEvent> {
            on { keyCode } doReturn KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            on { repeatCount } doReturn repeat
        }

    @Test
    fun `video play-pause toggles once on key down`() {
        whenever(player.isPlaying).thenReturn(true)
        val result = plugin.onKeyDown(playPause(0), state(SliderItemType.VIDEO))
        assertEquals(SliderKeyEventResult.HANDLED_CONSUME, result)
        verify(player, times(1)).pause()
    }

    @Test
    fun `held play-pause key does not toggle the video again on repeats`() {
        whenever(player.isPlaying).thenReturn(true)
        val result = plugin.onKeyDown(playPause(1), state(SliderItemType.VIDEO))
        assertEquals(SliderKeyEventResult.HANDLED_CONSUME, result)
        verify(player, never()).pause()
        verify(player, never()).play()
    }

    @Test
    fun `held play-pause key does not toggle the slideshow on repeats`() {
        plugin.onKeyDown(playPause(2), state(SliderItemType.IMAGE))
        verify(controller, never()).toggleSlideshow(true, false)
    }
}
