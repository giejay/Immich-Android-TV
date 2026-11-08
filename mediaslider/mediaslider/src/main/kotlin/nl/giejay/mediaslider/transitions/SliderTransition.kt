package nl.giejay.mediaslider.transitions

/** How the slideshow moves from one asset to the next. [SLIDE] is the classic pager movement; the page math is in [pageTransform]. */
enum class SliderTransition {
    SLIDE,
    CROSSFADE,
    FADE_THROUGH,
    ZOOM,
    DEPTH,
    PARALLAX,
    CUBE,
    RANDOM
}
