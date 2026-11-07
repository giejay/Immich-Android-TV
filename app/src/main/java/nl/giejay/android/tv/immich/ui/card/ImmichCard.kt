package nl.giejay.android.tv.immich.ui.card

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import nl.giejay.android.tv.immich.card.ICard
import nl.giejay.android.tv.immich.ui.theme.ImmichFocus
import nl.giejay.android.tv.immich.ui.theme.ImmichIcons

object ImmichCardDefaults {
    val ImageWidth = 206.dp
    val ImageHeight = 116.dp

    /** Room around the card so the focus scale never gets clipped by the host view. */
    val FocusMargin = 8.dp
}

/**
 * Stateless photo/album/person card: rounded thumbnail, optional title + subtitle underneath.
 * Focus is passed in (not read from Compose focus) so it works both inside Leanback grids, where
 * the host view owns focus, and inside native Compose grids.
 *
 * [ICard.thumbnailUrl] is an http(s) url for photos, or a symbolic icon name (e.g. "folder").
 */
@Composable
fun ImmichCard(card: ICard, focused: Boolean, modifier: Modifier = Modifier) {
    val scale by animateFloatAsState(
        targetValue = if (focused) ImmichFocus.CardScale else 1f,
        animationSpec = tween(ImmichFocus.AnimationMillis),
        label = "cardScale",
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            focused -> MaterialTheme.colorScheme.border
            card.selected -> MaterialTheme.colorScheme.primaryContainer
            else -> Color.Transparent
        },
        animationSpec = tween(ImmichFocus.AnimationMillis),
        label = "cardBorder",
    )
    val shape = MaterialTheme.shapes.medium

    Column(
        modifier
            .padding(ImmichCardDefaults.FocusMargin)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
    ) {
        Box(
            Modifier
                .size(ImmichCardDefaults.ImageWidth, ImmichCardDefaults.ImageHeight)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(ImmichFocus.BorderWidth, borderColor, shape),
        ) {
            CardImage(card.thumbnailUrl)
            if (card.selected) {
                Icon(
                    imageVector = ImmichIcons.Selected,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(24.dp)
                        .background(MaterialTheme.colorScheme.background, CircleShape),
                )
            }
        }
        Text(
            text = card.title,
            style = MaterialTheme.typography.titleSmall,
            color = if (focused) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(top = 8.dp)
                .size(width = ImmichCardDefaults.ImageWidth, height = 22.dp),
        )
        val subtitle = card.description
        Text(
            text = if (subtitle.isNullOrBlank()) " " else subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.size(width = ImmichCardDefaults.ImageWidth, height = 20.dp),
        )
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun BoxScope.CardImage(thumbnailUrl: String?) {
    when {
        thumbnailUrl == null -> Unit
        thumbnailUrl.startsWith("http") -> GlideImage(
            model = thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(ImmichCardDefaults.ImageWidth, ImmichCardDefaults.ImageHeight),
        )
        else -> Icon(
            imageVector = iconForName(thumbnailUrl),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.align(Alignment.Center).size(56.dp),
        )
    }
}

private fun iconForName(name: String) = when (name) {
    "folder_up" -> ImmichIcons.FolderUp
    else -> ImmichIcons.Folders
}
