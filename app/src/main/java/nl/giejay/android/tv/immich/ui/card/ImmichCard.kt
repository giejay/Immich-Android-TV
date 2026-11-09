package nl.giejay.android.tv.immich.ui.card

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.lerp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
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
    val borderColor = animateColorAsState(
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
                .animatedBorder(borderColor, ImmichFocus.BorderWidth, shape),
        ) {
            if (card.isFolder) FolderCardArt(card.coverUrl, card.badge) else CardImage(card.thumbnailUrl)
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

/**
 * A folder card: its cover photo fills the card, a tab at the top left in the primary container tone marks the folder,
 * and a small pill at the bottom left holds the folder symbol and the number of assets. Without a photo the card is
 * a plain tonal folder surface.
 */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun FolderCardArt(coverUrl: String?, badge: String?) {
    val tone = MaterialTheme.colorScheme.primaryContainer
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.size(56.dp, 18.dp).background(tone, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)))
        Box(
            Modifier.offset(0.dp, 14.dp).fillMaxSize()
                .clip(RoundedCornerShape(topStart = 0.dp, topEnd = 12.dp, bottomStart = 12.dp, bottomEnd = 12.dp))
                .background(tone),
        ) {
            if (coverUrl != null) {
                GlideImage(model = coverUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(
                    imageVector = ImmichIcons.Folders,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.align(Alignment.Center).size(44.dp),
                )
            }
        }
        if (badge != null) {
            Row(
                Modifier.align(Alignment.BottomStart).padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(13.dp))
                    .padding(start = 7.dp, end = 10.dp, top = 3.dp, bottom = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(ImmichIcons.Folders, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                Text(badge, style = MaterialTheme.typography.labelMedium, color = Color.White, modifier = Modifier.padding(start = 5.dp))
            }
        }
    }
}

private fun iconForName(name: String) = when (name) {
    "folder_up" -> ImmichIcons.FolderUp
    "tag" -> ImmichIcons.Tags
    "server" -> ImmichIcons.Server
    "view_settings" -> ImmichIcons.ViewSettings
    "screensaver" -> ImmichIcons.Screensaver
    "home_screen_channels" -> ImmichIcons.HomeChannels
    "debug" -> ImmichIcons.Debug
    else -> ImmichIcons.Folders
}

/**
 * Border whose colour animates in the draw phase, so focus changes do not recompose the card.
 * The parent clips to [shape], so a stroke centered on the edge only shows its inner half; it is
 * drawn twice as wide to get a visible border of [width].
 */
private fun Modifier.animatedBorder(color: State<Color>, width: Dp, shape: Shape): Modifier =
    drawWithContent {
        drawContent()
        val c = color.value
        if (c != Color.Transparent) {
            val outline = shape.createOutline(Size(size.width, size.height), layoutDirection, this)
            drawOutline(outline, c, style = Stroke(width.toPx() * 2))
        }
    }

/**
 * [ImmichCard] that takes Compose focus and clicks, for native Compose grids.
 * [onFocused] fires when the card gains focus.
 */
@Composable
fun ImmichFocusableCard(
    card: ICard,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    ImmichCard(
        card = card,
        focused = focused,
        modifier = modifier
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .clickable(
                interactionSource = null,
                indication = null,
                onClick = onClick,
            ),
    )
}
