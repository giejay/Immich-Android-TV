package nl.giejay.android.tv.immich.screensaver

import nl.giejay.android.tv.immich.ImmichApplication
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.shared.prefs.EnumWithTitle
import nl.giejay.mediaslider.transitions.SliderTransition

/** Screensaver transition styles as shown in the settings; each maps to a [SliderTransition]. */
enum class ScreenSaverTransition(private val titleRes: Int, val sliderTransition: SliderTransition) : EnumWithTitle {
    SLIDE(R.string.transition_slide, SliderTransition.SLIDE),
    CROSSFADE(R.string.transition_crossfade, SliderTransition.CROSSFADE),
    FADE_THROUGH(R.string.transition_fade_through, SliderTransition.FADE_THROUGH),
    ZOOM(R.string.transition_zoom, SliderTransition.ZOOM),
    DEPTH(R.string.transition_depth, SliderTransition.DEPTH),
    PARALLAX(R.string.transition_parallax, SliderTransition.PARALLAX),
    CUBE(R.string.transition_cube, SliderTransition.CUBE),
    RANDOM(R.string.transition_random, SliderTransition.RANDOM);

    override fun getTitle(): String = ImmichApplication.appContext!!.getString(titleRes)

    companion object {
        fun valueOfSafe(value: String, default: ScreenSaverTransition): ScreenSaverTransition =
            entries.firstOrNull { it.name == value } ?: default
    }
}
