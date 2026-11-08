package nl.giejay.android.tv.immich.slider

import androidx.compose.runtime.mutableStateOf
import com.google.common.truth.Truth.assertThat
import nl.giejay.mediaslider.controller.ControllerIds
import nl.giejay.mediaslider.controller.ControllerLayout
import nl.giejay.mediaslider.plugin.ControllerButtonPlacement
import nl.giejay.mediaslider.plugin.ControllerButtonSpec
import org.junit.Test

class ControllerLayoutTest {
    private fun plugin(placement: ControllerButtonPlacement, anchor: String? = null, id: String = "p") =
        ControllerButtonSpec("d", mutableStateOf(0), {}, placement, anchor, id)

    @Test
    fun `photo row has no video controls and no seek bar`() {
        val chain = ControllerLayout.focusChain(isVideo = false, plugins = emptyList())
        assertThat(chain).containsExactly(
            ControllerIds.PREVIOUS, ControllerIds.SLIDESHOW_REVERSE, ControllerIds.SLIDESHOW, ControllerIds.NEXT,
        ).inOrder()
    }

    @Test
    fun `video row adds mute, play pause and the seek bar`() {
        val chain = ControllerLayout.focusChain(isVideo = true, plugins = emptyList())
        assertThat(chain).containsExactly(
            ControllerIds.MUTE, ControllerIds.PREVIOUS, ControllerIds.PLAY_PAUSE, ControllerIds.SLIDESHOW_REVERSE,
            ControllerIds.SLIDESHOW, ControllerIds.NEXT, ControllerIds.SEEK,
        ).inOrder()
    }

    @Test
    fun `plugin buttons are placed next to their anchor`() {
        val favorite = plugin(ControllerButtonPlacement.RIGHT_OF, ControllerIds.PLAY_PAUSE, "fav")
        val external = plugin(ControllerButtonPlacement.RIGHT_OF, ControllerIds.SLIDESHOW, "ext")
        val chain = ControllerLayout.focusChain(true, listOf(favorite, external))
        assertThat(chain.indexOf("fav")).isEqualTo(chain.indexOf(ControllerIds.PLAY_PAUSE) + 1)
        assertThat(chain.indexOf("ext")).isEqualTo(chain.indexOf(ControllerIds.SLIDESHOW) + 1)
    }

    @Test
    fun `plugin with a missing anchor goes to the end of the row`() {
        val favorite = plugin(ControllerButtonPlacement.RIGHT_OF, ControllerIds.PLAY_PAUSE, "fav")
        val chain = ControllerLayout.focusChain(false, listOf(favorite))
        assertThat(chain.last()).isEqualTo("fav")
    }

    @Test
    fun `start and end placements`() {
        val chain = ControllerLayout.focusChain(
            false, listOf(plugin(ControllerButtonPlacement.START, id = "s"), plugin(ControllerButtonPlacement.END, id = "e")),
        )
        assertThat(chain.first()).isEqualTo("s")
        assertThat(chain.last()).isEqualTo("e")
    }

    @Test
    fun `initial focus is play pause for video and the slideshow button for photos`() {
        assertThat(ControllerLayout.initialFocus(true, false)).isEqualTo(ControllerIds.PLAY_PAUSE)
        assertThat(ControllerLayout.initialFocus(false, false)).isEqualTo(ControllerIds.SLIDESHOW)
        assertThat(ControllerLayout.initialFocus(false, true)).isEqualTo(ControllerIds.SLIDESHOW_REVERSE)
    }

    @Test
    fun `focus is kept on the same control across pages`() {
        val chain = ControllerLayout.focusChain(false, emptyList())
        assertThat(ControllerLayout.restoreFocus(ControllerIds.NEXT, 3, chain, false, false)).isEqualTo(ControllerIds.NEXT)
    }

    @Test
    fun `video to photo maps play pause to slideshow and mute to previous`() {
        val photo = ControllerLayout.focusChain(false, emptyList())
        assertThat(ControllerLayout.restoreFocus(ControllerIds.PLAY_PAUSE, 2, photo, false, false)).isEqualTo(ControllerIds.SLIDESHOW)
        assertThat(ControllerLayout.restoreFocus(ControllerIds.MUTE, 0, photo, false, false)).isEqualTo(ControllerIds.PREVIOUS)
        assertThat(ControllerLayout.restoreFocus(ControllerIds.SEEK, 6, photo, false, false)).isEqualTo(ControllerIds.SLIDESHOW)
    }

    @Test
    fun `photo to video keeps the slideshow button and falls back to the same slot for a missing plugin button`() {
        val video = ControllerLayout.focusChain(true, emptyList())
        assertThat(ControllerLayout.restoreFocus(ControllerIds.SLIDESHOW, 2, video, true, false)).isEqualTo(ControllerIds.SLIDESHOW)
        assertThat(ControllerLayout.restoreFocus("plugin_gone", 1, video, true, false)).isEqualTo(video[1])
    }
}
