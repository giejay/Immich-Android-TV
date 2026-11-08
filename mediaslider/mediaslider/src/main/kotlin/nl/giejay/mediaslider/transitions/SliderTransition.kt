package nl.giejay.mediaslider.transitions

import android.view.View
import androidx.viewpager.widget.ViewPager
import kotlin.math.abs
import kotlin.random.Random

/** How the slideshow moves from one asset to the next. [SLIDE] is the classic pager behaviour. */
enum class SliderTransition {
    SLIDE,
    CROSSFADE,
    FADE_THROUGH,
    ZOOM,
    DEPTH,
    PARALLAX,
    CUBE,
    RANDOM;

    /** Transformer for this transition, or null for the plain slide. */
    fun createTransformer(): ViewPager.PageTransformer? = when (this) {
        SLIDE -> null
        CROSSFADE -> CrossfadeTransformer
        FADE_THROUGH -> FadeThroughTransformer
        ZOOM -> ZoomTransformer
        DEPTH -> DepthTransformer
        PARALLAX -> ParallaxTransformer
        CUBE -> CubeTransformer
        RANDOM -> RandomTransformer()
    }
}

/**
 * Base for all transformers. [position] is 0 for the page in the middle, 1 for the page waiting on
 * the right and -1 for the one that left to the left. Every call starts from a clean view so no
 * property of a previous transition (or a different random pick) is left behind.
 */
private abstract class ResettingTransformer : ViewPager.PageTransformer {
    final override fun transformPage(page: View, position: Float) {
        page.alpha = 1f
        page.translationX = 0f
        page.translationY = 0f
        page.scaleX = 1f
        page.scaleY = 1f
        page.rotationY = 0f
        page.pivotX = page.width / 2f
        page.pivotY = page.height / 2f
        if (position <= -1f || position >= 1f) {
            page.alpha = 0f
            return
        }
        apply(page, position)
    }

    abstract fun apply(page: View, position: Float)

    /** Keeps the page where it is while the pager scrolls, so pages can be layered instead of moved. */
    protected fun hold(page: View, position: Float) {
        page.translationX = -position * page.width
    }
}

/** The incoming page fades in over the outgoing one, which stays opaque so the picture never dips to black. */
private object CrossfadeTransformer : ResettingTransformer() {
    override fun apply(page: View, position: Float) {
        hold(page, position)
        page.alpha = if (position > 0f) 1f - position else 1f
    }
}

/** Material "fade through": the old picture shrinks away, then the new one grows in. */
private object FadeThroughTransformer : ResettingTransformer() {
    override fun apply(page: View, position: Float) {
        hold(page, position)
        val distance = abs(position)
        page.alpha = (1f - 2f * distance).coerceIn(0f, 1f)
        val scale = 1f - 0.08f * distance
        page.scaleX = scale
        page.scaleY = scale
    }
}

/** The new picture settles in from slightly larger while it fades; the old one gently shrinks. */
private object ZoomTransformer : ResettingTransformer() {
    override fun apply(page: View, position: Float) {
        hold(page, position)
        if (position > 0f) {
            page.alpha = 1f - position
            val scale = 1f + 0.12f * position
            page.scaleX = scale
            page.scaleY = scale
        } else {
            val scale = 1f - 0.06f * abs(position)
            page.scaleX = scale
            page.scaleY = scale
        }
    }
}

/** Android's classic "depth": the outgoing picture slides off, the incoming one rises from behind. */
private object DepthTransformer : ResettingTransformer() {
    override fun apply(page: View, position: Float) {
        if (position <= 0f) return
        hold(page, position)
        page.alpha = 1f - position
        val scale = 0.75f + 0.25f * (1f - position)
        page.scaleX = scale
        page.scaleY = scale
    }
}

/** The page below moves at half speed and dims while the one on top slides over it. */
private object ParallaxTransformer : ResettingTransformer() {
    override fun apply(page: View, position: Float) {
        if (position < 0f) {
            page.translationX = -position * page.width * 0.5f
            page.alpha = 1f - 0.5f * abs(position)
        }
    }
}

/** Pages are the sides of a cube that turns. */
private object CubeTransformer : ResettingTransformer() {
    override fun apply(page: View, position: Float) {
        page.cameraDistance = page.width * 8f
        page.pivotX = if (position < 0f) page.width.toFloat() else 0f
        page.pivotY = page.height / 2f
        page.rotationY = 90f * position
    }
}

/** Uses another transition every time the pager starts to move. */
class RandomTransformer : ViewPager.PageTransformer {
    private val choices = listOf(
        SliderTransition.CROSSFADE,
        SliderTransition.FADE_THROUGH,
        SliderTransition.ZOOM,
        SliderTransition.DEPTH,
        SliderTransition.PARALLAX,
        SliderTransition.CUBE,
    ).mapNotNull { it.createTransformer() }
    private var current: ViewPager.PageTransformer = choices.first()

    fun pickNext() {
        val others = choices.filterNot { it === current }
        current = others[Random.nextInt(others.size)]
    }

    override fun transformPage(page: View, position: Float) = current.transformPage(page, position)
}
