package nl.giejay.mediaslider.overlay

import android.content.Context
import android.graphics.Typeface
import android.view.View
import android.text.format.DateFormat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One line of slideshow metadata, already resolved to the text that is shown. */
class OverlayLine(val text: String, val fontSize: Int, val padding: Int, val isClock: Boolean)

/** Everything the overlays draw; plugins write to it, the composables read it. */
class SliderOverlayState {
    var left by mutableStateOf<List<OverlayLine>>(emptyList())
    var right by mutableStateOf<List<OverlayLine>>(emptyList())
    var gradientVisible by mutableStateOf(false)

    /** Whether the current page may show the gradient at all (images yes, videos no). */
    var gradientAllowed: Boolean = false
    var dateText by mutableStateOf("")
}

// same look as before: sans-serif-light, bold, white with a soft black shadow
private val OverlayFont = FontFamily(Typeface.create("sans-serif-light", Typeface.BOLD))
private val Scrim = Color(0xC0000000)

private fun overlayTextStyle(sizeSp: Int, align: TextAlign) = TextStyle(
    color = Color.White,
    fontFamily = OverlayFont,
    fontSize = sizeSp.sp,
    textAlign = align,
    shadow = Shadow(Color.Black, Offset.Zero, 6f),
)

/** Bottom metadata block: left and right column with an optional dark gradient behind them. */
@Composable
fun MetadataOverlay(state: SliderOverlayState, modifier: Modifier = Modifier) {
    val gradient by animateFloatAsState(if (state.gradientVisible) 1f else 0f, label = "metadataGradient")
    Row(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Scrim.copy(alpha = Scrim.alpha * gradient))))
            .padding(bottom = 30.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        MetadataColumn(state.left, alignEnd = false, Modifier.weight(1f))
        MetadataColumn(state.right, alignEnd = true, Modifier.weight(1f))
    }
}

@Composable
private fun MetadataColumn(lines: List<OverlayLine>, alignEnd: Boolean, modifier: Modifier) {
    Column(modifier, horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        lines.forEach { line ->
            val vertical = with(LocalDensity.current) { line.padding.toDp() }
            val style = overlayTextStyle(line.fontSize, if (alignEnd) TextAlign.End else TextAlign.Start)
            Box(Modifier.padding(horizontal = 30.dp, vertical = vertical)) {
                if (line.isClock) ClockText(style) else BasicText(line.text, style = style)
            }
        }
    }
}

/** Time of day in the system's 12/24 hour format, updated on every minute boundary. */
@Composable
private fun ClockText(style: TextStyle) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000L - now % 60_000L)
        }
    }
    val format = remember(context) {
        val skeleton = if (DateFormat.is24HourFormat(context)) "Hm" else "hm"
        SimpleDateFormat(DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton), Locale.getDefault())
    }
    BasicText(format.format(Date(now)), style = style)
}

/** Optional date at the top of the screen with a dark-to-transparent scrim. */
@Composable
fun DateOverlay(state: SliderOverlayState, modifier: Modifier = Modifier) {
    val text = state.dateText
    if (text.isEmpty()) return
    BasicText(
        text = text,
        style = overlayTextStyle(22, TextAlign.Start),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Scrim, Color.Transparent)))
            .padding(start = 32.dp, top = 36.dp, end = 48.dp, bottom = 36.dp),
    )
}

/**
 * A Compose host view that also works where nothing provides a lifecycle (the screensaver runs in
 * a DreamService window): without a host owner it installs its own while attached.
 */
fun createOverlayView(context: Context, content: @Composable () -> Unit): View = OverlayComposeView(context, content)

private class OverlayComposeView(context: Context, private val content: @Composable () -> Unit) : AbstractComposeView(context) {
    private var installedOn: View? = null
    private var fallback: FallbackOwner? = null

    @Composable
    override fun Content() = content()

    override fun onAttachedToWindow() {
        // Compose creates its window recomposer from the lifecycle owner of the window's ROOT view,
        // so a missing owner (DreamService window) has to be provided there, not on this view.
        val root = rootView
        if (root.findViewTreeLifecycleOwner() == null) {
            installedOn = root
            fallback = FallbackOwner().also {
                root.setViewTreeLifecycleOwner(it)
                root.setViewTreeSavedStateRegistryOwner(it)
                it.start()
            }
        }
        super.onAttachedToWindow()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        fallback?.stop()
        installedOn?.let {
            it.setViewTreeLifecycleOwner(null)
            it.setViewTreeSavedStateRegistryOwner(null)
        }
        fallback = null
        installedOn = null
    }
}

private class FallbackOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val controller = SavedStateRegistryController.create(this)

    init {
        controller.performAttach()
        controller.performRestore(null)
    }

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = controller.savedStateRegistry

    fun start() {
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun stop() {
        registry.currentState = Lifecycle.State.DESTROYED
    }
}
