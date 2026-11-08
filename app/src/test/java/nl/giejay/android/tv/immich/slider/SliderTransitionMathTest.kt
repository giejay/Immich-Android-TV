package nl.giejay.android.tv.immich.slider

import com.google.common.truth.Truth.assertThat
import nl.giejay.mediaslider.transitions.PageTransform
import nl.giejay.mediaslider.transitions.SliderTransition
import nl.giejay.mediaslider.transitions.pageTransform
import nl.giejay.mediaslider.transitions.randomTransitionChoices
import org.junit.Test

class SliderTransitionMathTest {
    @Test
    fun `slide and random leave pages untouched`() {
        assertThat(SliderTransition.SLIDE.pageTransform(0.4f)).isEqualTo(PageTransform.Identity)
        assertThat(SliderTransition.RANDOM.pageTransform(0.4f)).isEqualTo(PageTransform.Identity)
    }

    @Test
    fun `pages beyond one page distance are hidden for every real transition`() {
        randomTransitionChoices.forEach {
            assertThat(it.pageTransform(1f)).isEqualTo(PageTransform.Hidden)
            assertThat(it.pageTransform(-1.5f)).isEqualTo(PageTransform.Hidden)
        }
    }

    @Test
    fun `the page in the middle is fully visible and unscaled`() {
        randomTransitionChoices.forEach {
            val t = it.pageTransform(0f)
            assertThat(t.alpha).isEqualTo(1f)
            assertThat(t.scale).isEqualTo(1f)
            assertThat(t.rotationY).isEqualTo(0f)
        }
    }

    @Test
    fun `crossfade fades the incoming page in and keeps the outgoing one opaque`() {
        assertThat(SliderTransition.CROSSFADE.pageTransform(0.25f).alpha).isWithin(1e-6f).of(0.75f)
        assertThat(SliderTransition.CROSSFADE.pageTransform(-0.25f).alpha).isEqualTo(1f)
        // held in place instead of sliding: translation cancels the pager's own movement
        assertThat(SliderTransition.CROSSFADE.pageTransform(0.25f).translationXFraction).isEqualTo(-0.25f)
    }

    @Test
    fun `fade through is invisible half way and shrinks slightly`() {
        assertThat(SliderTransition.FADE_THROUGH.pageTransform(0.5f).alpha).isEqualTo(0f)
        assertThat(SliderTransition.FADE_THROUGH.pageTransform(0.25f).scale).isWithin(1e-6f).of(0.98f)
    }

    @Test
    fun `depth only affects the incoming page`() {
        assertThat(SliderTransition.DEPTH.pageTransform(-0.5f)).isEqualTo(PageTransform.Identity)
        assertThat(SliderTransition.DEPTH.pageTransform(0.5f).scale).isWithin(1e-6f).of(0.875f)
    }

    @Test
    fun `cube turns pages around the edge they share`() {
        val right = SliderTransition.CUBE.pageTransform(0.5f)
        val left = SliderTransition.CUBE.pageTransform(-0.5f)
        assertThat(right.rotationY).isEqualTo(45f)
        assertThat(right.pivotXFraction).isEqualTo(0f)
        assertThat(left.rotationY).isEqualTo(-45f)
        assertThat(left.pivotXFraction).isEqualTo(1f)
        assertThat(right.perspective).isTrue()
    }

    @Test
    fun `random never picks slide or random itself`() {
        assertThat(randomTransitionChoices).containsNoneOf(SliderTransition.SLIDE, SliderTransition.RANDOM)
    }
}
