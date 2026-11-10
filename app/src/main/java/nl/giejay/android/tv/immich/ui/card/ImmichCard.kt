package nl.giejay.android.tv.immich.ui.card

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.Canvas
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
                .background(if (card.isFolder) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant)
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

// Folder colours: a warm amber, the classic folder colour, toned for the dark theme (back, inside, front flap,
// the flap's light edge, and the dark pill with the count).
private val FolderBack = Color(0xFFC2861F)
private val FolderInside = Color(0xFF9A6512)
private val FolderFront = Color(0xFFE6AA3A)
private val FolderEdge = Color(0xFFF4C466)
private val FolderPill = Color(0xCC1B1D22)
private val FolderPillText = Color(0xFFE4E6EC)
private val SheetPaper = Color(0xFFECEFF6)
private val SheetFold = Color(0xFFC9CEDB)

// The folder is designed on a 156 x 108 grid and scaled to the card.
private const val FOLDER_W = 156f
private const val FOLDER_H = 108f

private fun backPath(k: Float) = Path().apply {
    moveTo(0f, 12f * k)
    quadraticTo(0f, 4f * k, 7f * k, 4f * k)
    lineTo(48f * k, 4f * k)
    quadraticTo(54f * k, 4f * k, 57f * k, 9f * k)
    lineTo(61f * k, 14f * k)
    lineTo(149f * k, 14f * k)
    quadraticTo(156f * k, 14f * k, 156f * k, 21f * k)
    lineTo(156f * k, 100f * k)
    quadraticTo(156f * k, 108f * k, 148f * k, 108f * k)
    lineTo(8f * k, 108f * k)
    quadraticTo(0f, 108f * k, 0f, 100f * k)
    close()
}

private fun frontPath(k: Float) = Path().apply {
    moveTo(0f, 66f * k)
    lineTo(44f * k, 66f * k)
    cubicTo(52f * k, 66f * k, 55f * k, 60f * k, 63f * k, 60f * k)
    lineTo(149f * k, 60f * k)
    quadraticTo(156f * k, 60f * k, 156f * k, 66f * k)
    lineTo(156f * k, 100f * k)
    quadraticTo(156f * k, 108f * k, 148f * k, 108f * k)
    lineTo(8f * k, 108f * k)
    quadraticTo(0f, 108f * k, 0f, 100f * k)
    close()
}

private fun frontEdgePath(k: Float) = Path().apply {
    moveTo(1f * k, 66f * k)
    lineTo(44f * k, 66f * k)
    cubicTo(52f * k, 66f * k, 55f * k, 60f * k, 63f * k, 60f * k)
    lineTo(149f * k, 60f * k)
    quadraticTo(155f * k, 60f * k, 155.5f * k, 66f * k)
}

/**
 * A folder card in the style of a desktop folder: the back with its tab, the cover photo standing upright inside it
 * (a sheet of paper when the folder has no photo), and the front flap over it with the number of assets. It keeps
 * the folder's proportions and sits centred in the card.
 */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun FolderCardArt(coverUrl: String?, badge: String?) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val scale = minOf(maxWidth / FOLDER_W.dp, maxHeight / FOLDER_H.dp) * 0.94f
        Box(Modifier.size(FOLDER_W.dp * scale, FOLDER_H.dp * scale)) {
            Canvas(Modifier.fillMaxSize()) {
                val k = size.width / FOLDER_W
                drawPath(backPath(k), FolderBack)
                drawRoundRect(FolderInside, Offset(7f * k, 20f * k), Size(142f * k, 76f * k), CornerRadius(4f * k))
                if (coverUrl == null) {
                    drawRoundRect(SheetPaper, Offset(40f * k, 18f * k), Size(76f * k, 76f * k), CornerRadius(3f * k))
                    drawPath(
                        Path().apply {
                            moveTo(98f * k, 18f * k)
                            lineTo(116f * k, 36f * k)
                            lineTo(102f * k, 36f * k)
                            quadraticTo(98f * k, 36f * k, 98f * k, 32f * k)
                            close()
                        },
                        SheetFold,
                    )
                }
            }
            if (coverUrl != null) {
                GlideImage(
                    model = coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .offset(9.dp * scale, 22.dp * scale)
                        .size(138.dp * scale, 72.dp * scale)
                        .clip(RoundedCornerShape(3.dp * scale)),
                )
            }
            Canvas(Modifier.fillMaxSize()) {
                val k = size.width / FOLDER_W
                drawPath(frontPath(k), FolderFront)
                drawPath(frontEdgePath(k), FolderEdge, style = Stroke(width = 1.5f * k))
            }
            if (badge != null) {
                // the count as a small dark pill, bottom left on the flap (the folder shape already says "folder")
                Text(
                    badge,
                    style = MaterialTheme.typography.labelMedium,
                    color = FolderPillText,
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 8.dp * scale, bottom = 8.dp * scale)
                        .background(FolderPill, RoundedCornerShape(50))
                        .padding(horizontal = 9.dp, vertical = 3.dp),
                )
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
    "about" -> ImmichIcons.About
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
