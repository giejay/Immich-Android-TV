package nl.giejay.mediaslider.pager

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import nl.giejay.mediaslider.model.SliderItem
import nl.giejay.mediaslider.model.SliderItemViewHolder
import timber.log.Timber

/** One photo: the thumbnail shows at once and the full image replaces it; a spinner covers the wait. */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
internal fun SliderImagePage(item: SliderItem, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val config = LocalSliderConfig.current
    var loaded by remember(item.id) { mutableStateOf(false) }
    Box(modifier) {
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
