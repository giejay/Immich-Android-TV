package nl.giejay.mediaslider.pager

import android.graphics.drawable.Drawable
import android.widget.Toast
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
import androidx.compose.runtime.mutableIntStateOf
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
import nl.giejay.mediaslider.player.AmlogicSafeRenderersFactory
import nl.giejay.mediaslider.view.ExoPlayerView
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

/** A video page keeps the existing [ExoPlayerView]; playback is driven by the controller. */
@Composable
internal fun SliderVideoPage(pager: ComposeSliderPager, page: Int, item: SliderItemViewHolder) {
    val config = LocalSliderConfig.current
    // bumped when a SurfaceView page failed, so the page is rebuilt with a TextureView
    var rebuild by remember(item.url) { mutableIntStateOf(0) }
    key(rebuild) {
        val useTextureView = item.mainItem.orientation != 1 || pager.failedVideoUrls.contains(item.url)
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                ExoPlayerView(context, if (useTextureView) R.layout.video_item_texture_view else R.layout.video_item).also { view ->
                    view.setupPlayer(config, AmlogicSafeRenderersFactory(context), pager.exoListener) { player, error ->
                        val shouldRetry = !useTextureView && !pager.failedVideoUrls.contains(item.url)
                        Timber.e(error, "Player error at page $page for ${item.url}. Retry with TextureView: $shouldRetry. ${error.message}")
                        if (shouldRetry) {
                            item.url?.let { pager.failedVideoUrls.add(it) }
                            player.release()
                            rebuild++
                            true
                        } else {
                            Toast.makeText(context, "Cannot play video: ${error.message}", Toast.LENGTH_LONG).show()
                            false
                        }
                    }
                    pager.videoViews[page] = view
                }
            },
            onRelease = { view ->
                view.releasePlayer()
                if (pager.videoViews[page] === view) pager.videoViews.remove(page)
            },
        )
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
