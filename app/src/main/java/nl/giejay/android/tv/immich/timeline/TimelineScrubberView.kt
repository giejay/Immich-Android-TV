package nl.giejay.android.tv.immich.timeline

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.KeyEvent
import android.view.View
import androidx.core.content.ContextCompat
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.api.model.TimeBucketSummary

/**
 * Immich-style vertical year/month scrubber for Android TV.
 *
 * Focus badge mirrors Immich Scrubber.svelte `#time-label`: dark pill anchored to the
 * rail's trailing edge (`end-0`), blue bottom border; passive scrubber line is Immich's
 * short `w-10` indicator — not a full-bleed line into the mosaic.
 *
 * D-pad Up/Down only moves the preview badge. Enter jumps the mosaic and keeps scrubber focus;
 * Left jumps and returns focus to the mosaic.
 */
class TimelineScrubberView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /**
     * @param monthKey Immich month bucket key
     * @param exitToMosaic true for Left (leave scrubber); false for Enter (stay on scrubber)
     */
    var onCommit: ((monthKey: String, exitToMosaic: Boolean) -> Unit)? = null

    /** Fired when Up/Down moves the preview badge without committing. */
    var onPreviewMoved: (() -> Unit)? = null

    private var buckets: List<TimeBucketSummary> = emptyList()
    private var stops: List<TimelineScrubberStop> = emptyList()
    private var selectedIndex: Int = 0
    private var indicatorIndex: Int = 0

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.timeline_scrubber_label)
        textAlign = Paint.Align.RIGHT
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textSize = sp(13f)
    }
    private val labelFocusedPaint = Paint(labelPaint).apply {
        textSize = sp(15f)
    }
    private val hoverPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.timeline_scrubber_label)
        textAlign = Paint.Align.LEFT
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textSize = sp(15f)
    }
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        // Immich dark: bg-subtle/90
        color = 0xE62A2A2A.toInt()
        style = Paint.Style.FILL
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.timeline_scrubber_dot)
        style = Paint.Style.FILL
    }
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.timeline_scrubber_accent)
        strokeWidth = dp(2f)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }

    private val paddingV = dp(16f)
    private val labelEndPad = dp(20f)
    private val dotEndPad = dp(10f)
    /** Immich scroll indicator `w-10` (~2.5rem). */
    private val passiveLineWidth = dp(40f)
    private val badgePadH = dp(10f)
    private val badgePadV = dp(5f)
    private val badgeRadius = dp(6f)
    private val badgeRect = RectF()

    private val tvScale = 2.0f
    private val minYearLabelPx =
        (TimelineScrubberModel.DEFAULT_MIN_YEAR_LABEL_PX * tvScale).toInt()
    private val minDotPx =
        (TimelineScrubberModel.DEFAULT_MIN_DOT_PX * tvScale).toInt()
    private val minSegmentHeightForDotPx =
        (TimelineScrubberModel.DEFAULT_MIN_SEGMENT_HEIGHT_FOR_DOT_PX * tvScale).toInt()

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = context.getString(R.string.timeline)
    }

    fun setBuckets(newBuckets: List<TimeBucketSummary>) {
        buckets = newBuckets
        rebuildStops()
    }

    private fun rebuildStops() {
        val contentH = (height - 2 * paddingV).toInt()
        val previousMonth = stops.getOrNull(indicatorIndex)?.monthKey
            ?: stops.getOrNull(selectedIndex)?.monthKey
        stops = TimelineScrubberModel.buildStops(
            buckets = buckets,
            railContentHeightPx = contentH,
            minYearLabelPx = minYearLabelPx,
            minDotPx = minDotPx,
            minSegmentHeightForDotPx = minSegmentHeightForDotPx
        )
        if (stops.isEmpty()) {
            selectedIndex = 0
            indicatorIndex = 0
            invalidate()
            return
        }
        val restored = previousMonth?.let { TimelineScrubberModel.indexForMonth(stops, it) } ?: -1
        if (restored >= 0) {
            indicatorIndex = restored
            if (!hasFocus()) selectedIndex = restored
        } else {
            selectedIndex = selectedIndex.coerceIn(0, stops.lastIndex)
            indicatorIndex = indicatorIndex.coerceIn(0, stops.lastIndex)
        }
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (h != oldh) rebuildStops()
    }

    fun setIndicatorMonthKey(monthKey: String?) {
        if (monthKey == null || stops.isEmpty()) return
        val idx = TimelineScrubberModel.indexForMonth(stops, monthKey)
        if (idx < 0) return
        indicatorIndex = idx
        if (!hasFocus()) selectedIndex = idx
        invalidate()
    }

    fun prepareFocusForMonth(monthKey: String?) {
        setIndicatorMonthKey(monthKey)
        if (stops.isNotEmpty()) {
            selectedIndex = indicatorIndex.coerceIn(0, stops.lastIndex)
        }
    }

    fun selectedMonthKey(): String? = stops.getOrNull(selectedIndex)?.monthKey

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (stops.isEmpty()) return
        val contentH = (height - 2 * paddingV).coerceAtLeast(1f)
        val labelX = width - labelEndPad
        val dotX = width - dotEndPad

        stops.forEachIndexed { index, stop ->
            val y = paddingV + stop.fractionBottom * contentH
            if (stop.isYearLabel) {
                val paint = if (hasFocus() && index == selectedIndex) labelFocusedPaint else labelPaint
                canvas.drawText(stop.year.toString(), labelX, y + paint.textSize * 0.35f, paint)
            }
            if (stop.hasDot) {
                canvas.drawCircle(dotX, y, dp(2f), dotPaint)
            }
        }

        val accentStop = stops.getOrNull(if (hasFocus()) selectedIndex else indicatorIndex) ?: return
        val ay = paddingV + accentStop.fraction * contentH

        if (hasFocus()) {
            drawFocusBadge(canvas, accentStop.monthKey, ay)
        } else {
            // Immich: absolute end-0 h-0.5 w-10 bg-primary
            val lineLeft = (width - passiveLineWidth).coerceAtLeast(0f)
            canvas.drawLine(lineLeft, ay, width.toFloat(), ay, accentPaint)
        }
    }

    /**
     * Immich `#time-label`: end-anchored pill with blue bottom border, covering the year column
     * and only spilling slightly into the mosaic.
     */
    private fun drawFocusBadge(canvas: Canvas, monthKey: String, accentY: Float) {
        val hover = TimelineScrubberModel.formatMonthYear(monthKey)
        contentDescription = hover
        val textW = hoverPaint.measureText(hover)
        val badgeW = textW + 2 * badgePadH
        val badgeH = hoverPaint.textSize + 2 * badgePadV
        val right = width.toFloat()
        val left = right - badgeW
        // Center badge vertically on the accent so the blue border sits on the scrub position.
        val bottom = accentY + accentPaint.strokeWidth
        val top = bottom - badgeH
        badgeRect.set(left, top, right, bottom)
        canvas.drawRoundRect(badgeRect, badgeRadius, badgeRadius, badgePaint)
        // Immich border-b-2 border-primary
        canvas.drawLine(left, bottom, right, bottom, accentPaint)
        val textBaseline = bottom - badgePadV - hoverPaint.descent()
        canvas.drawText(hover, left + badgePadH, textBaseline, hoverPaint)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (stops.isEmpty()) return super.onKeyDown(keyCode, event)
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (selectedIndex > 0) {
                    movePreview(selectedIndex - 1)
                    return true
                }
                return false // Let focus bubble up to the settings icon
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (selectedIndex < stops.lastIndex) {
                    movePreview(selectedIndex + 1)
                    return true
                }
                return false
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                commitSelection(exitToMosaic = true)
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER -> {
                commitSelection(exitToMosaic = false)
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: android.graphics.Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        if (gainFocus && stops.isNotEmpty()) {
            selectedIndex = indicatorIndex.coerceIn(0, stops.lastIndex)
        } else if (!gainFocus && stops.isNotEmpty()) {
            // Discard uncommitted preview if focus leaves without Enter/Left.
            selectedIndex = indicatorIndex.coerceIn(0, stops.lastIndex)
        }
        // Sibling mosaic recycler sits at 4dp so year labels stay under focused cells/memories.
        // Only raise above that while focused so the MMM yyyy badge can overlay them.
        // Don't bringToFront() — that reorders siblings and steals next entry focus.
        elevation = if (gainFocus) dp(32f) else 0f
        invalidate()
    }

    /** Move the focus badge only; do not scroll the mosaic until [commitSelection]. */
    private fun movePreview(index: Int) {
        selectedIndex = index.coerceIn(0, stops.lastIndex)
        stops.getOrNull(selectedIndex)?.monthKey?.let { key ->
            contentDescription = TimelineScrubberModel.formatMonthYear(key)
        }
        invalidate()
        onPreviewMoved?.invoke()
    }

    private fun commitSelection(exitToMosaic: Boolean) {
        if (stops.isEmpty()) return
        selectedIndex = selectedIndex.coerceIn(0, stops.lastIndex)
        indicatorIndex = selectedIndex
        invalidate()
        stops.getOrNull(selectedIndex)?.monthKey?.let { onCommit?.invoke(it, exitToMosaic) }
    }

    private fun dp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

    private fun sp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)
}
