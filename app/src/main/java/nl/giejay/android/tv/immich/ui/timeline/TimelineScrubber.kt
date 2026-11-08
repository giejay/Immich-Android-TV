package nl.giejay.android.tv.immich.ui.timeline

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.api.model.TimeBucketSummary
import nl.giejay.android.tv.immich.timeline.TimelineScrubberModel

/**
 * Immich-style year/month rail (Compose port of the View scrubber). Up/Down only move the preview
 * badge, Enter jumps and keeps focus here, Left jumps and hands focus back to the mosaic.
 *
 * @param indicatorMonthKey bucket key of the month currently on screen (passive accent line)
 * @param onFocusedChange reports whether the rail owns focus (the host raises it above the mosaic)
 */
@Composable
fun TimelineScrubber(
    buckets: List<TimeBucketSummary>,
    indicatorMonthKey: String?,
    onCommit: (monthKey: String, exitToMosaic: Boolean) -> Unit,
    focusRequester: FocusRequester,
    onFocusedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    var heightPx by remember { mutableIntStateOf(0) }
    var hasFocus by remember { mutableStateOf(false) }
    var selectedIndex by remember { mutableIntStateOf(0) }

    val labelColor = colorResource(R.color.timeline_scrubber_label)
    val dotColor = colorResource(R.color.timeline_scrubber_dot)
    val accent = colorResource(R.color.timeline_scrubber_accent)

    val paddingV = with(androidx.compose.ui.platform.LocalDensity.current) { RailPaddingV.toPx() }
    val stops = remember(buckets, heightPx) {
        // same TV scaling of Immich's px constants as the View scrubber (2x)
        TimelineScrubberModel.buildStops(
            buckets = buckets,
            railContentHeightPx = (heightPx - 2 * paddingV).toInt(),
            minYearLabelPx = TimelineScrubberModel.DEFAULT_MIN_YEAR_LABEL_PX * 2,
            minDotPx = TimelineScrubberModel.DEFAULT_MIN_DOT_PX * 2,
            minSegmentHeightForDotPx = TimelineScrubberModel.DEFAULT_MIN_SEGMENT_HEIGHT_FOR_DOT_PX * 2,
        )
    }
    val indicatorIndex = indicatorMonthKey?.let { TimelineScrubberModel.indexForMonth(stops, it) }?.takeIf { it >= 0 } ?: 0

    // Without focus the rail follows the visible month; gaining focus starts the preview there.
    LaunchedEffect(indicatorIndex, hasFocus) {
        if (!hasFocus) selectedIndex = indicatorIndex
    }

    fun move(delta: Int) {
        if (stops.isEmpty()) return
        selectedIndex = (selectedIndex + delta).coerceIn(0, stops.lastIndex)
    }

    fun commit(exit: Boolean) {
        stops.getOrNull(selectedIndex)?.monthKey?.let { onCommit(it, exit) }
    }

    Box(
        modifier
            .onSizeChanged { heightPx = it.height }
            .focusRequester(focusRequester)
            .onFocusChanged {
                hasFocus = it.isFocused
                onFocusedChange(it.isFocused)
            }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || stops.isEmpty()) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionUp -> { move(-1); true }
                    Key.DirectionDown -> { move(1); true }
                    Key.DirectionLeft -> { commit(exit = true); true }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { commit(exit = false); true }
                    Key.DirectionRight -> true
                    else -> false
                }
            }
            .focusable(),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            if (stops.isEmpty()) return@Canvas
            val contentH = (size.height - 2 * paddingV).coerceAtLeast(1f)
            val labelRight = size.width - LabelEndPad.toPx()
            val dotX = size.width - DotEndPad.toPx()
            val labelStyle = TextStyle(color = labelColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            val labelFocusedStyle = labelStyle.copy(fontSize = 15.sp)

            stops.forEachIndexed { index, stop ->
                val y = paddingV + stop.fractionBottom * contentH
                if (stop.isYearLabel) {
                    val layout = measurer.measure(stop.year.toString(), if (hasFocus && index == selectedIndex) labelFocusedStyle else labelStyle)
                    drawText(layout, topLeft = Offset(labelRight - layout.size.width, y - layout.size.height / 2f))
                }
                if (stop.hasDot) drawCircle(dotColor, radius = 2.dp.toPx(), center = Offset(dotX, y))
            }

            val accentStop = stops.getOrNull(if (hasFocus) selectedIndex else indicatorIndex) ?: return@Canvas
            val ay = paddingV + accentStop.fraction * contentH
            val stroke = 2.dp.toPx()
            if (hasFocus) {
                // Immich #time-label: end-anchored dark pill with a blue bottom border
                val text = measurer.measure(
                    TimelineScrubberModel.formatMonthYear(accentStop.monthKey),
                    TextStyle(color = labelColor, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                )
                val padH = 10.dp.toPx()
                val padV = 5.dp.toPx()
                val badgeW = text.size.width + 2 * padH
                val badgeH = text.size.height + 2 * padV
                val bottom = ay + stroke
                val top = bottom - badgeH
                val left = size.width - badgeW
                drawRoundRect(Color(0xE62A2A2A), Offset(left, top), Size(badgeW, badgeH), CornerRadius(6.dp.toPx()))
                drawLine(accent, Offset(left, bottom), Offset(size.width, bottom), stroke)
                drawText(text, topLeft = Offset(left + padH, top + padV))
            } else {
                // Immich: short w-10 indicator at the trailing edge
                drawLine(accent, Offset(size.width - PassiveLineWidth.toPx(), ay), Offset(size.width, ay), stroke)
            }
        }
    }
}

private val RailPaddingV = 16.dp
private val LabelEndPad = 20.dp
private val DotEndPad = 10.dp
private val PassiveLineWidth = 40.dp
