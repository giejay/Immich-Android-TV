package nl.giejay.mediaslider.effects

import kotlin.random.Random

/** One animation leg: go to [scale] / [x] / [y] (pixels from the centered position) in [durationMs]; 0 jumps. */
data class EffectStep(val durationMs: Long, val scale: Float, val x: Float, val y: Float)

/**
 * A slideshow effect for one photo: wait [startDelayMs], run [steps] one after the other starting from the
 * photo at rest, then (panorama only) return to rest over [resetDurationMs].
 */
data class EffectPlan(val startDelayMs: Long, val steps: List<EffectStep>, val resetDurationMs: Long? = null)

/** Settings and the displayed photo size ([imageWidth]/[imageHeight] after fitting into the view) the effects depend on. */
data class EffectParams(
    val intervalSeconds: Int,
    val onlyUseThumbnails: Boolean,
    val animationSpeedMillis: Int,
    val zoomEffectPercent: Int,
    val panEffectPercent: Int,
    val viewWidth: Float,
    val viewHeight: Float,
    val imageWidth: Float,
    val imageHeight: Float,
)

/** The "Ken Burns" zoom/pan and the panorama scroll of the slideshow, as plain functions so they can be tested. */
object SlideshowEffects {
    private const val ZOOM_IN = 1
    private const val ZOOM_OUT = 2
    private const val ZOOM_IN_PAN_CENTER = 3
    private const val ZOOM_IN_PAN_OPPOSITE_XY = 4
    private const val ZOOM_IN_PAN_OPPOSITE_X = 5
    private const val ZOOM_IN_PAN_OPPOSITE_Y = 6
    private const val ZOOM_IN_HOLD = 7
    private const val ZOOM_IN_NEW_RANDOM = 8
    private const val ZOOMED_NEW_RANDOM = 9
    private const val ZOOMED_PAN_CENTER = 10
    private const val ZOOMED_PAN_OPPOSITE_XY = 11
    private const val ZOOMED_PAN_OPPOSITE_X = 12
    private const val ZOOMED_PAN_OPPOSITE_Y = 13

    /** Random zoom and pan over the whole interval, or null when the interval is too short for it. */
    fun zoomAndPan(p: EffectParams, random: Random = Random): EffectPlan? {
        if (!p.onlyUseThumbnails && p.intervalSeconds <= 3) return null

        val zoom = p.zoomEffectPercent / 100f
        val pan = p.panEffectPercent / 100f
        val xLimit = (p.imageWidth * 0.5).toInt()
        val yLimit = (p.imageHeight * 0.5).toInt()
        fun randomScale() = ((50..200).random(random) / 100f * zoom) + 1f
        fun randomPan(limit: Int) = (-limit..limit).random(random) * pan
        fun randomSubDuration(duration: Long) = (duration * ((20..80).random(random) / 100f)).toLong()

        val x = randomPan(xLimit)
        val y = randomPan(yLimit)
        val scale = randomScale()
        val scaleModifier = floatArrayOf(.85f, .90f, .95f, 1f, 1f, 1f, 1f, 1.05f, 1.10f, 1.15f).random(random)
        var x2 = x
        var y2 = y
        var scale2 = scale
        val actionDuration = (p.intervalSeconds * 0.98f * 1000).toLong()
        var phase1 = actionDuration
        var phase2 = actionDuration
        val onePhaseOnly = p.intervalSeconds <= 5
        val canZoomOut = p.animationSpeedMillis <= 100 &&
            ((p.onlyUseThumbnails && p.intervalSeconds < 10) || p.intervalSeconds >= 10)

        var action: Int
        if (onePhaseOnly) {
            action = (0..if (canZoomOut) 2 else 1).random(random)
        } else {
            action = (0..if (canZoomOut) 13 else 8).random(random)
            phase1 = randomSubDuration(actionDuration)
            phase2 = actionDuration - phase1
            if (action in ZOOM_IN_NEW_RANDOM..ZOOMED_NEW_RANDOM) {
                x2 = randomPan(xLimit)
                y2 = randomPan(yLimit)
                scale2 = randomScale()
            }
        }
        if (!canZoomOut && action == ZOOM_OUT) action = ZOOM_IN

        val zoomed = scale * scaleModifier
        fun leg(duration: Long, s: Float, px: Float, py: Float) = EffectStep(duration, s, px, py)
        val steps = when (action) {
            ZOOM_IN -> listOf(leg(0, 1f, 0f, 0f), leg(actionDuration, scale, x, y))
            ZOOM_OUT -> listOf(leg(0, scale, x, y), leg(actionDuration, 1f, 0f, 0f))
            ZOOM_IN_PAN_CENTER -> listOf(leg(phase1, scale, x, y), leg(phase2, zoomed, 0f, 0f))
            ZOOM_IN_PAN_OPPOSITE_XY -> listOf(leg(phase1, scale, x, y), leg(phase2, zoomed, -x, -y))
            ZOOM_IN_PAN_OPPOSITE_X -> listOf(leg(phase1, scale, x, y), leg(phase2, zoomed, -x, y))
            ZOOM_IN_PAN_OPPOSITE_Y -> listOf(leg(phase1, scale, x, y), leg(phase2, zoomed, x, -y))
            ZOOM_IN_HOLD -> listOf(leg(phase1, scale, x, y), leg(phase2, scale, x, y))
            ZOOM_IN_NEW_RANDOM -> listOf(leg(phase1, scale, x, y), leg(phase2, scale2, x2, y2))
            ZOOMED_NEW_RANDOM -> listOf(leg(0, scale, x, y), leg(actionDuration, scale2, x2, y2))
            ZOOMED_PAN_CENTER -> listOf(leg(0, scale, x, y), leg(actionDuration, zoomed, 0f, 0f))
            ZOOMED_PAN_OPPOSITE_XY -> listOf(leg(0, scale, x, y), leg(actionDuration, zoomed, -x, -y))
            ZOOMED_PAN_OPPOSITE_X -> listOf(leg(0, scale, x, y), leg(actionDuration, zoomed, -x, y))
            ZOOMED_PAN_OPPOSITE_Y -> listOf(leg(0, scale, x, y), leg(actionDuration, zoomed, x, -y))
            else -> return null // action 0 never animates
        }
        return EffectPlan(startDelayMs = 0, steps = steps)
    }

    /** Scale that makes a very wide or very tall photo fill the view in its short direction, 1 for ordinary photos. */
    fun panoramaScale(p: EffectParams): Float {
        val aspect = p.imageWidth / p.imageHeight
        return when {
            aspect > 2f -> p.viewHeight / p.imageHeight
            aspect <= 0.56f -> p.viewWidth / p.imageWidth
            else -> 1f
        }
    }

    /** Slow scroll across a panorama (wide: left/right, tall: up/down), or null when there is nothing to scroll. */
    fun panorama(p: EffectParams, random: Random = Random): EffectPlan? {
        val scaleUp = panoramaScale(p)
        if (scaleUp > 0.9f && scaleUp < 1.1f) return null

        val xPan = p.viewWidth * scaleUp * 0.5f - p.viewWidth * 0.5f
        val yPan = p.viewHeight * scaleUp * 0.5f - p.viewHeight * 0.5f
        val hold = ((p.intervalSeconds - 1) * 0.03f * 1000f).toLong()
        val scroll = ((p.intervalSeconds - 1) * 0.8f * 1000f).toLong()
        val direction = floatArrayOf(-1f, 1f).random(random)
        val aspect = p.imageWidth / p.imageHeight
        return when {
            aspect > 2f -> EffectPlan(
                startDelayMs = hold,
                steps = listOf(EffectStep(hold, scaleUp, direction * xPan, 0f), EffectStep(scroll, scaleUp, -direction * xPan, 0f)),
                resetDurationMs = hold,
            )
            aspect <= 0.56f -> EffectPlan(
                startDelayMs = hold,
                steps = listOf(EffectStep(hold, scaleUp, 0f, -direction * yPan), EffectStep(scroll, scaleUp, 0f, direction * yPan)),
                resetDurationMs = hold,
            )
            else -> null
        }
    }
}
