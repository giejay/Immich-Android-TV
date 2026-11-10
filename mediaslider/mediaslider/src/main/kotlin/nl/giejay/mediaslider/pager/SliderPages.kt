package nl.giejay.mediaslider.pager

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.zeuskartik.mediaslider.R
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nl.giejay.mediaslider.config.MediaSliderConfiguration
import nl.giejay.mediaslider.effects.EffectParams
import nl.giejay.mediaslider.effects.EffectPlan
import nl.giejay.mediaslider.effects.SlideshowEffects
import nl.giejay.mediaslider.model.SliderItem
import nl.giejay.mediaslider.model.SliderItemViewHolder
import nl.giejay.mediaslider.transformations.BoxBlurTransformation
import timber.log.Timber

/** One photo: the thumbnail shows at once and the full image replaces it; a spinner covers the wait. */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
internal fun SliderImagePage(item: SliderItem, modifier: Modifier = Modifier, effectToken: Int? = null, hidden: Boolean = false) {
    val context = LocalContext.current
    val config = LocalSliderConfig.current
    var loaded by remember(item.id) { mutableStateOf(false) }
    var aspect by remember(item.id) { mutableFloatStateOf(0f) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    // the plan needs the shown photo size, so it waits until the image is loaded and measured
    val plan = remember(effectToken, aspect > 0f, size) {
        if (effectToken == null || aspect <= 0f || size == IntSize.Zero) null else slideshowEffectPlan(config, item, aspect, size)
    }
    Box(modifier.onSizeChanged { size = it }) {
        val backdropUrl = item.thumbnailUrl
        if (config.blurredBackdrop && backdropUrl != null && !photoFillsPage(aspect, size)) {
            BlurredBackdrop(backdropUrl)
        }
        Box(Modifier.fillMaxSize().slideshowEffect(plan, hidden)) {
        GlideImage(
            model = if (config.isOnlyUseThumbnails) item.thumbnailUrl else item.url,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
            requestBuilderTransform = { builder ->
                var request = builder
                    .transform(config.glideTransformation.transform(context, config))
                    .listener(object : RequestListener<Drawable> {
                        override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>, isFirstResource: Boolean): Boolean {
                            Timber.e(e, "Could not fetch image: %s", model)
                            loaded = true
                            return false
                        }

                        override fun onResourceReady(
                            resource: Drawable,
                            model: Any,
                            target: Target<Drawable>,
                            dataSource: DataSource,
                            isFirstResource: Boolean,
                        ): Boolean {
                            loaded = true
                            if (resource.intrinsicHeight > 0) aspect = resource.intrinsicWidth.toFloat() / resource.intrinsicHeight
                            return false
                        }
                    })
                if (!config.isOnlyUseThumbnails) {
                    request = request.thumbnail(Glide.with(context).load(item.thumbnailUrl))
                }
                request
            },
        )
        if (!loaded) SliderSpinner(Modifier.align(Alignment.Center))
        }
    }
}

/** True while the photo (shown with ContentScale.Fit) covers the page, so no backdrop is needed. */
internal fun photoFillsPage(aspect: Float, size: IntSize): Boolean {
    if (aspect <= 0f || size.width <= 0 || size.height <= 0) return true
    val pageAspect = size.width.toFloat() / size.height
    return kotlin.math.abs(aspect - pageAspect) / pageAspect < BACKDROP_ASPECT_TOLERANCE
}

private const val BACKDROP_ASPECT_TOLERANCE = 0.03f

/**
 * The thumbnail as a dimmed backdrop: decoded at a tiny size, blurred once on the CPU and scaled up by the GPU
 * (bilinear scaling of a blurred image looks smooth). Being a plain bitmap it costs nothing while the page slides;
 * a RenderEffect blur measured at roughly half the frames while paging on the Xiaomi Box S.
 */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun BlurredBackdrop(thumbnailUrl: String) {
    Box(Modifier.fillMaxSize()) {
        GlideImage(
            model = thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            requestBuilderTransform = {
                it.override(BACKDROP_DECODE_WIDTH, BACKDROP_DECODE_HEIGHT).transform(BoxBlurTransformation(BACKDROP_BLUR_RADIUS))
            },
        )
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)))
    }
}

private const val BACKDROP_BLUR_RADIUS = 5
private const val BACKDROP_DECODE_WIDTH = 160
private const val BACKDROP_DECODE_HEIGHT = 90

/**
 * A video page. The shared player is bound to the page that came to rest; every other page, and the
 * current one until its first frame, shows the poster. The poster sits above the video, so a
 * SurfaceView never flashes black.
 */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
internal fun SliderVideoPage(pager: ComposeSliderPager, page: Int, item: SliderItemViewHolder) {
    val isCurrent = page == pager.state.settledPage
    Box(Modifier.fillMaxSize()) {
        if (isCurrent && !pager.paused) {
            // rebuilt with a TextureView when the SurfaceView failed (see ComposeSliderPager.handleVideoError)
            key(pager.videoRebuild, pager.playerGeneration) {
                val useTextureView = item.mainItem.orientation != 1 || pager.failedVideoUrls.contains(item.url)
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        val layout = if (useTextureView) R.layout.slider_player_texture else R.layout.slider_player_surface
                        (LayoutInflater.from(context).inflate(layout, null, false) as PlayerView).also {
                            it.player = pager.requirePlayer()
                            pager.onPlayerViewBound()
                        }
                    },
                    onRelease = { it.player = null },
                )
            }
        }
        if (!isCurrent || !pager.firstVideoFrameRendered) {
            GlideImage(
                model = item.mainItem.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            if (isCurrent) SliderSpinner(Modifier.align(Alignment.Center))
        }
    }
}

/** Small arc spinner; avoids a Material dependency in this module. */
@Composable
internal fun SliderSpinner(modifier: Modifier = Modifier) {
    val rotation by rememberInfiniteTransition(label = "sliderSpinner").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Restart),
        label = "sliderSpinnerRotation",
    )
    Canvas(modifier.size(44.dp)) {
        val stroke = 4.dp.toPx()
        drawArc(
            color = Color(0xFFADC6FF),
            startAngle = rotation,
            sweepAngle = 100f,
            useCenter = false,
            topLeft = Offset(stroke / 2, stroke / 2),
            size = Size(size.width - stroke, size.height - stroke),
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
    }
}

/** Same gating as the ViewPager version: effects need the panorama/zoom setting; panoramas scroll, other photos zoom and pan. */
private fun slideshowEffectPlan(config: MediaSliderConfiguration, item: SliderItem, aspect: Float, size: IntSize): EffectPlan? {
    if (!config.zoomAndScrollPanorama) return null
    val viewWidth = size.width.toFloat()
    val viewHeight = size.height.toFloat()
    val params = EffectParams(
        intervalSeconds = config.interval,
        onlyUseThumbnails = config.isOnlyUseThumbnails,
        animationSpeedMillis = config.animationSpeedMillis,
        zoomEffectPercent = config.zoomEffectPercent,
        panEffectPercent = config.panEffectPercent,
        viewWidth = viewWidth,
        viewHeight = viewHeight,
        imageWidth = minOf(viewWidth, viewHeight * aspect),
        imageHeight = minOf(viewHeight, viewWidth / aspect),
    )
    return when {
        item.isPanorama -> if (config.interval >= 10) SlideshowEffects.panorama(params) else null
        else -> SlideshowEffects.zoomAndPan(params)
    }
}

/** Animates the photo through [plan]: waits, runs each leg from where the last ended, and returns to rest if asked; [hidden] pages are reset. */
@Composable
private fun Modifier.slideshowEffect(plan: EffectPlan?, hidden: Boolean): Modifier {
    val scale = remember { Animatable(1f) }
    val x = remember { Animatable(0f) }
    val y = remember { Animatable(0f) }
    // a page that is leaving keeps its current zoom/pan (no plan: the running animation just stops) and returns to rest only once it is out of sight
    LaunchedEffect(hidden) {
        if (hidden) {
            scale.snapTo(1f)
            x.snapTo(0f)
            y.snapTo(0f)
        }
    }
    LaunchedEffect(plan) {
        if (plan == null) return@LaunchedEffect
        scale.snapTo(1f)
        x.snapTo(0f)
        y.snapTo(0f)
        delay(plan.startDelayMs)
        suspend fun goTo(durationMs: Long, targetScale: Float, targetX: Float, targetY: Float) {
            if (durationMs <= 0) {
                scale.snapTo(targetScale)
                x.snapTo(targetX)
                y.snapTo(targetY)
                return
            }
            val spec = tween<Float>(durationMs.toInt(), easing = FastOutSlowInEasing)
            coroutineScope {
                launch { scale.animateTo(targetScale, spec) }
                launch { x.animateTo(targetX, spec) }
                launch { y.animateTo(targetY, spec) }
            }
        }
        plan.steps.forEach { goTo(it.durationMs, it.scale, it.x, it.y) }
        plan.resetDurationMs?.let { goTo(it, 1f, 0f, 0f) }
    }
    return graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
        translationX = x.value
        translationY = y.value
    }
}
