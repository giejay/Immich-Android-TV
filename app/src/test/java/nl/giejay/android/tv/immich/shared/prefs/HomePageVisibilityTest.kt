package nl.giejay.android.tv.immich.shared.prefs

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HomePageVisibilityTest {
    @Test
    fun `showing removes the page from the hidden list, hiding adds it`() {
        val hidden = setOf("Tags", "Archive")

        assertThat(withPageVisibility(hidden, "Tags", visible = true)).containsExactly("Archive")
        assertThat(withPageVisibility(hidden, "Videos", visible = false)).containsExactly("Tags", "Archive", "Videos")
    }

    @Test
    fun `showing a visible page or hiding a hidden one changes nothing`() {
        val hidden = setOf("Tags")

        assertThat(withPageVisibility(hidden, "Albums", visible = true)).isEqualTo(hidden)
        assertThat(withPageVisibility(hidden, "Tags", visible = false)).isEqualTo(hidden)
    }
}
