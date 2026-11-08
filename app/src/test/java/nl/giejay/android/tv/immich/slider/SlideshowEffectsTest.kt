package nl.giejay.android.tv.immich.slider

import com.google.common.truth.Truth.assertThat
import nl.giejay.mediaslider.effects.EffectParams
import nl.giejay.mediaslider.effects.SlideshowEffects
import org.junit.Test
import kotlin.random.Random

class SlideshowEffectsTest {
    private fun params(
        interval: Int = 10,
        thumbnails: Boolean = false,
        animationMs: Int = 0,
        zoom: Int = 50,
        pan: Int = 50,
        imageWidth: Float = 1600f,
        imageHeight: Float = 900f,
    ) = EffectParams(interval, thumbnails, animationMs, zoom, pan, 1920f, 1080f, imageWidth, imageHeight)

    @Test
    fun `short intervals with full images get no effect`() {
        assertThat(SlideshowEffects.zoomAndPan(params(interval = 3))).isNull()
        // with thumbnails the effect is allowed (random action 0 is a deliberate "no effect")
        val plans = (0..50).map { SlideshowEffects.zoomAndPan(params(interval = 3, thumbnails = true), Random(it)) }
        assertThat(plans.filterNotNull()).isNotEmpty()
    }

    @Test
    fun `every plan stays inside the configured zoom and pan limits`() {
        repeat(300) { seed ->
            val p = params(interval = 12)
            val plan = SlideshowEffects.zoomAndPan(p, Random(seed)) ?: return@repeat
            val maxScale = (200 / 100f * p.zoomEffectPercent / 100f) + 1f
            plan.steps.forEach { step ->
                assertThat(step.scale).isAtLeast(0.5f)
                assertThat(step.scale).isAtMost(maxScale * 1.15f + 1e-3f)
                assertThat(Math.abs(step.x)).isAtMost(p.imageWidth * 0.5f * p.panEffectPercent / 100f + 1f)
                assertThat(Math.abs(step.y)).isAtMost(p.imageHeight * 0.5f * p.panEffectPercent / 100f + 1f)
            }
        }
    }

    @Test
    fun `legs of a plan add up to the interval`() {
        repeat(200) { seed ->
            val plan = SlideshowEffects.zoomAndPan(params(interval = 20), Random(seed)) ?: return@repeat
            val total = plan.steps.sumOf { it.durationMs }
            val expected = (20 * 0.98f * 1000).toLong()
            // two-phase actions split the interval; one-phase actions jump first, then take the whole interval
            assertThat(total).isEqualTo(expected)
        }
    }

    @Test
    fun `short intervals only use single phase actions without zoom out`() {
        repeat(200) { seed ->
            val plan = SlideshowEffects.zoomAndPan(params(interval = 5, animationMs = 500), Random(seed)) ?: return@repeat
            // zoom out ends at scale 1; not allowed here, so every plan ends zoomed in
            assertThat(plan.steps.last().scale).isGreaterThan(1f)
            assertThat(plan.steps).hasSize(2)
        }
    }

    @Test
    fun `same seed gives the same plan`() {
        assertThat(SlideshowEffects.zoomAndPan(params(), Random(7))).isEqualTo(SlideshowEffects.zoomAndPan(params(), Random(7)))
    }

    @Test
    fun `ordinary photos have no panorama scroll`() {
        assertThat(SlideshowEffects.panorama(params())).isNull()
        assertThat(SlideshowEffects.panoramaScale(params())).isEqualTo(1f)
    }

    @Test
    fun `a wide panorama scrolls sideways and returns to rest`() {
        val p = params(imageWidth = 1920f, imageHeight = 600f) // aspect 3.2
        assertThat(SlideshowEffects.panoramaScale(p)).isWithin(1e-4f).of(1080f / 600f)
        val plan = SlideshowEffects.panorama(p, Random(3))!!
        assertThat(plan.steps).hasSize(2)
        assertThat(plan.steps[0].x).isEqualTo(-plan.steps[1].x)
        assertThat(plan.steps[0].y).isEqualTo(0f)
        assertThat(plan.resetDurationMs).isEqualTo(plan.startDelayMs)
        assertThat(plan.steps[1].durationMs).isEqualTo(((10 - 1) * 0.8f * 1000f).toLong())
    }

    @Test
    fun `a tall panorama scrolls vertically`() {
        val p = params(imageWidth = 500f, imageHeight = 1080f) // aspect 0.46
        val plan = SlideshowEffects.panorama(p, Random(3))!!
        assertThat(plan.steps[0].x).isEqualTo(0f)
        assertThat(plan.steps[0].y).isEqualTo(-plan.steps[1].y)
    }
}
