package nl.giejay.mediaslider.transitions

import kotlin.math.abs

/**
 * Graphics-layer values of one page during a transition. [translationXFraction] is a fraction of the
 * page width and [pivotXFraction] of its width, so the math stays independent of any view size.
 */
data class PageTransform(
    val alpha: Float = 1f,
    val translationXFraction: Float = 0f,
    val scale: Float = 1f,
    val rotationY: Float = 0f,
    val pivotXFraction: Float = 0.5f,
    /** The cube needs perspective; the others are flat. */
    val perspective: Boolean = false,
) {
    companion object {
        val Identity = PageTransform()
        val Hidden = PageTransform(alpha = 0f)
    }
}

/** Concrete transitions [RANDOM] may pick from. */
val randomTransitionChoices: List<SliderTransition> = listOf(
    SliderTransition.CROSSFADE,
    SliderTransition.FADE_THROUGH,
    SliderTransition.ZOOM,
    SliderTransition.DEPTH,
    SliderTransition.PARALLAX,
    SliderTransition.CUBE,
)

/**
 * Transform of a page for [position]: 0 for the page in the middle, 1 for the page waiting on the
 * right, -1 for the one that left to the left. Same values as the ViewPager transformers; [SLIDE] and
 * [RANDOM] (resolve it to a concrete choice first) are the plain pager movement.
 */
fun SliderTransition.pageTransform(position: Float): PageTransform {
    if (this == SliderTransition.SLIDE || this == SliderTransition.RANDOM) return PageTransform.Identity
    if (position <= -1f || position >= 1f) return PageTransform.Hidden
    // "hold" keeps the page where it is while the pager scrolls, so pages can be layered instead of moved
    val hold = -position
    return when (this) {
        SliderTransition.CROSSFADE -> PageTransform(
            alpha = if (position > 0f) 1f - position else 1f,
            translationXFraction = hold,
        )
        SliderTransition.FADE_THROUGH -> {
            val distance = abs(position)
            PageTransform(
                alpha = (1f - 2f * distance).coerceIn(0f, 1f),
                translationXFraction = hold,
                scale = 1f - 0.08f * distance,
            )
        }
        SliderTransition.ZOOM -> if (position > 0f) {
            PageTransform(alpha = 1f - position, translationXFraction = hold, scale = 1f + 0.12f * position)
        } else {
            PageTransform(translationXFraction = hold, scale = 1f - 0.06f * abs(position))
        }
        SliderTransition.DEPTH -> if (position <= 0f) {
            PageTransform.Identity
        } else {
            PageTransform(alpha = 1f - position, translationXFraction = hold, scale = 0.75f + 0.25f * (1f - position))
        }
        SliderTransition.PARALLAX -> if (position < 0f) {
            PageTransform(alpha = 1f - 0.5f * abs(position), translationXFraction = -position * 0.5f)
        } else {
            PageTransform.Identity
        }
        SliderTransition.CUBE -> PageTransform(
            rotationY = 90f * position,
            pivotXFraction = if (position < 0f) 1f else 0f,
            perspective = true,
        )
        else -> PageTransform.Identity
    }
}
