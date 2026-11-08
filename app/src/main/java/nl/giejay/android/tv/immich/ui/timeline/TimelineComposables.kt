package nl.giejay.android.tv.immich.ui.timeline

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import android.net.Uri
import android.view.LayoutInflater
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import nl.giejay.android.tv.immich.shared.prefs.API_KEY
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_FORCE_ORIGINAL_VIDEO
import timber.log.Timber
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.api.util.ApiUtil
import nl.giejay.android.tv.immich.timeline.TimelineMosaicCell
import nl.giejay.android.tv.immich.timeline.TimelineVideoDuration
import nl.giejay.android.tv.immich.ui.theme.ImmichFocus

/** Building blocks of the Compose timeline (step K); the View timeline stays the default meanwhile. */
object TimelineMosaicDefaults {
    /** Same scale the View mosaic uses, so rows keep the padding that clears the focus overflow. */
    const val FocusScale = 1.14f
    val CellCorner = 2.dp
}

/** Date heading above the rows of one day. */
@Composable
fun TimelineDayHeader(label: String, modifier: Modifier = Modifier) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
    )
}

/**
 * One photo/video cell of the mosaic. Size comes from the layout engine (pixels, so rows add up
 * exactly as in the View mosaic); focus is passed in so the host decides who owns focus.
 */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun TimelineMosaicCell(
    cell: TimelineMosaicCell,
    focused: Boolean,
    modifier: Modifier = Modifier,
    /** Overrides the thumbnail url (previews, tests); null loads the asset's thumbnail from the server. */
    thumbnailUrl: String? = ApiUtil.getThumbnailUrl(cell.asset.id, "thumbnail"),
    /** Shared muted player for the focus preview of video cells; null disables the preview. */
    previewPlayer: ExoPlayer? = null,
) {
    val density = LocalDensity.current
    val scale by animateFloatAsState(
        targetValue = if (focused) TimelineMosaicDefaults.FocusScale else 1f,
        animationSpec = tween(ImmichFocus.AnimationMillis),
        label = "mosaicCellScale",
    )
    val shape = RoundedCornerShape(TimelineMosaicDefaults.CellCorner)
    Box(
        modifier
            .size(with(density) { cell.widthPx.toDp() }, with(density) { cell.heightPx.toDp() })
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(
                if (focused) Modifier.border(ImmichFocus.BorderWidth, MaterialTheme.colorScheme.border, shape)
                else Modifier
            ),
    ) {
        // a failed preview falls back to the poster until the cell is focused again
        var previewFailed by remember(cell.asset.id, focused) { mutableStateOf(false) }
        val previewing = focused && !cell.asset.isImage && previewPlayer != null && !previewFailed
        var videoReady by remember(cell.asset.id, previewing) { mutableStateOf(false) }
        var remainingSeconds by remember(cell.asset.id, previewing) { mutableStateOf<Long?>(null) }
        if (previewing) {
            // decodes under the poster until the first frame is rendered, so there is no grey flash
            FocusVideoPreview(cell.asset.id, previewPlayer!!, onReady = { videoReady = true }, onError = { previewFailed = true })
            val total = TimelineVideoDuration.parseSeconds(cell.asset.duration) ?: 0L
            LaunchedEffect(cell.asset.id) {
                while (true) {
                    if (previewPlayer.isPlaying) remainingSeconds = total - previewPlayer.currentPosition / 1000
                    delay(COUNTDOWN_INTERVAL_MS)
                }
            }
        }
        if (thumbnailUrl != null && !(previewing && videoReady)) {
            GlideImage(
                model = thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
        if (!cell.asset.isImage) {
            VideoBadge(
                durationText = remainingSeconds?.let { TimelineVideoDuration.format(it) } ?: durationText(cell.asset.duration),
                focused = focused,
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
    }
}

private fun durationText(duration: String?): String =
    TimelineVideoDuration.format(TimelineVideoDuration.parseSeconds(duration) ?: 0L)

@Composable
private fun VideoBadge(durationText: String, focused: Boolean, modifier: Modifier = Modifier) {
    Row(modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = durationText,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(end = 6.dp),
        )
        Icon(
            painter = painterResource(if (focused) R.drawable.ic_video_badge_pause else R.drawable.ic_video_badge_play),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(26.dp),
        )
    }
}

/** "N years ago" card of the memories row: cover image with the title on a bottom scrim. */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun TimelineMemoryCard(
    coverUrl: String?,
    label: String,
    focused: Boolean,
    modifier: Modifier = Modifier,
) {
    val scale by animateFloatAsState(
        targetValue = if (focused) TimelineMosaicDefaults.FocusScale else 1f,
        animationSpec = tween(ImmichFocus.AnimationMillis),
        label = "memoryCardScale",
    )
    Box(
        modifier
            .size(280.dp, 160.dp)
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (coverUrl != null) {
            GlideImage(
                model = coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .size(72.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000)))),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.align(Alignment.BottomStart).padding(14.dp),
        )
    }
}

private const val COUNTDOWN_INTERVAL_MS = 250L

/** Media source factory that sends the API key, like the View timeline's preview does. */
private val previewDataSourceFactory: DefaultHttpDataSource.Factory
    get() = DefaultHttpDataSource.Factory()
        .setDefaultRequestProperties(mapOf("x-api-key" to PreferenceManager.get(API_KEY)))

/** Plays [assetId]'s video on the shared [player] while this composable is in the composition. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun FocusVideoPreview(assetId: String, player: ExoPlayer, onReady: () -> Unit, onError: () -> Unit) {
    DisposableEffect(assetId, player) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() = onReady()
            override fun onPlayerError(error: PlaybackException) {
                Timber.w(error, "Timeline focus video preview failed for %s", assetId)
                player.stop()
                onError()
            }
        }
        player.addListener(listener)
        ApiUtil.getFileUrl(assetId, "VIDEO", PreferenceManager.get(SLIDER_FORCE_ORIGINAL_VIDEO))?.let { url ->
            player.setMediaSource(
                ProgressiveMediaSource.Factory(previewDataSourceFactory).createMediaSource(MediaItem.fromUri(Uri.parse(url))),
                0L,
            )
            player.prepare()
            player.playWhenReady = true
        }
        onDispose {
            player.removeListener(listener)
            player.playWhenReady = false
            player.stop()
            player.clearMediaItems()
        }
    }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            (LayoutInflater.from(context).inflate(R.layout.timeline_mosaic_video_preview, null, false) as PlayerView).also {
                it.player = player
            }
        },
        onRelease = { it.player = null },
    )
}
