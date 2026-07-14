package nl.giejay.android.tv.immich.timeline

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
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
 * Label/dot density matches Immich web Scrubber.svelte (min pixel distances).
 */
class TimelineScrubberView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var onStopSelected: ((monthKey: String) -> Unit)? = null
    var onExitLeft: (() -> Unit)? = null

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
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.timeline_scrubber_dot)
        style = Paint.Style.FILL
    }
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.timeline_scrubber_accent)
        strokeWidth = dp(2f)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val paddingV = dp(16f)
    /** Immich: labels at end-5 (~20px), dots at end-3 (~12px) — separate columns. */
    private val labelEndPad = dp(20f)
    private val dotEndPad = dp(10f)
    private val accentInset = dp(4f)
    private val minYearLabelPx = dp(20f).toInt().coerceAtLeast(TimelineScrubberModel.DEFAULT_MIN_YEAR_LABEL_PX)
    private val minDotPx = dp(8f).toInt().coerceAtLeast(TimelineScrubberModel.DEFAULT_MIN_DOT_PX)

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
            minDotPx = minDotPx
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
        // Immich layout: year text further from the edge; dots in their own column at the rail edge.
        val labelX = width - labelEndPad
        val dotX = width - dotEndPad

        stops.forEachIndexed { index, stop ->
            // Immich anchors markers at bottom-0 of each segment.
            val y = paddingV + stop.fractionBottom * contentH
            if (stop.isYearLabel) {
                val paint = if (hasFocus() && index == selectedIndex) labelFocusedPaint else labelPaint
                canvas.drawText(stop.year.toString(), labelX, y + paint.textSize * 0.35f, paint)
            }
            // Separate X column (Immich end-3); years and ticks can share a stop.
            if (stop.hasDot) {
                canvas.drawCircle(dotX, y, dp(2f), dotPaint)
            }
        }

        val accentStop = stops.getOrNull(if (hasFocus()) selectedIndex else indicatorIndex) ?: return
        val ay = paddingV + accentStop.fraction * contentH
        canvas.drawLine(accentInset, ay, width - accentInset, ay, accentPaint)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (stops.isEmpty()) return super.onKeyDown(keyCode, event)
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (selectedIndex > 0) {
                    selectIndex(selectedIndex - 1)
                    return true
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (selectedIndex < stops.lastIndex) {
                    selectIndex(selectedIndex + 1)
                    return true
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                onExitLeft?.invoke()
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
        }
        invalidate()
    }

    private fun selectIndex(index: Int) {
        selectedIndex = index.coerceIn(0, stops.lastIndex)
        indicatorIndex = selectedIndex
        invalidate()
        stops.getOrNull(selectedIndex)?.monthKey?.let { onStopSelected?.invoke(it) }
    }

    private fun dp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

    private fun sp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)
}
