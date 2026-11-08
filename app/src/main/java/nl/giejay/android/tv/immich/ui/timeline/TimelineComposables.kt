package nl.giejay.android.tv.immich.ui.timeline

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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
        if (thumbnailUrl != null) {
            GlideImage(
                model = thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
        if (!cell.asset.isImage) {
            VideoBadge(durationText = durationText(cell.asset.duration), focused = focused, modifier = Modifier.align(Alignment.TopEnd))
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
