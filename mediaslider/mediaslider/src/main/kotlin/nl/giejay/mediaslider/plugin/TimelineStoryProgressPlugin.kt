package nl.giejay.mediaslider.plugin

import android.animation.ValueAnimator
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.zeuskartik.mediaslider.R
import nl.giejay.mediaslider.config.MediaSliderConfiguration
import nl.giejay.mediaslider.model.SliderItemType
import nl.giejay.mediaslider.model.SliderItemViewHolder
import nl.giejay.mediaslider.view.MediaSliderController
import nl.giejay.mediaslider.view.StoryProgressView

/**
 * Timeline/memories plugin: mounts story-progress chrome and controls autoplay progress behavior.
 */
class TimelineStoryProgressPlugin : SliderViewPlugin<Any>, SliderControllerPlugin, SliderKeyEventPlugin {
    private var storyProgress: StoryProgressView? = null
    private var storyProgressCount: TextView? = null
    private var storyProgressAnimator: ValueAnimator? = null
    private var videoStoryProgressRunnable: Runnable? = null
    private var currentIndex: Int = 0
    private var lastIntervalMs: Long = 0L

    override fun createState(context: SliderViewPluginContext, config: MediaSliderConfiguration): Any? = null

    override fun attachView(rootView: ConstraintLayout, state: Any?) {
        val view = View.inflate(rootView.context, R.layout.story_progress_row, null)
        val params = ConstraintLayout.LayoutParams(
            ConstraintLayout.LayoutParams.MATCH_CONSTRAINT,
            ConstraintLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
        }
        rootView.addView(view, params)
    }

    override fun onLoadConfig(context: SliderViewPluginContext, config: MediaSliderConfiguration, state: Any?) {
        val view = context.rootView.findViewById<View>(R.id.story_progress_row) ?: return
        view.visibility = View.VISIBLE
        storyProgress = view.findViewById(R.id.story_progress)
        storyProgressCount = view.findViewById(R.id.story_progress_count)
    }

    override fun onPageSettled(
        context: SliderViewPluginContext,
        config: MediaSliderConfiguration,
        sliderItem: SliderItemViewHolder,
        sliderItemIndex: Int,
        handler: Handler,
        state: Any?
    ) {
        currentIndex = sliderItemIndex
        updateStoryProgressChrome(config, context.context, sliderItemIndex, 0f)

        if (!context.controller.slideShowPlaying) return
        if (sliderItem.type == SliderItemType.VIDEO) {
            startVideoStoryProgress(config, context.context, context.controller, handler)
        } else {
            startStoryProgressAnimation(
                config,
                context.context,
                lastIntervalMs.takeIf { it > 0 } ?: (config.interval * 1000L),
                handler
            )
        }
    }

    override fun onDestroy(context: SliderViewPluginContext, state: Any?) {
        stopStoryProgressAnimation(Handler(Looper.getMainLooper()))
        context.rootView.findViewById<View>(R.id.story_progress_row)?.let { view ->
            (view.parent as? ViewGroup)?.removeView(view)
        }
        storyProgress = null
        storyProgressCount = null
        videoStoryProgressRunnable = null
    }

    override fun onAssetTimerStarted(
        intervalMs: Long,
        controller: MediaSliderController,
        config: MediaSliderConfiguration,
        context: Context,
        handler: Handler
    ) {
        lastIntervalMs = intervalMs
        startStoryProgressAnimation(config, context, intervalMs, handler)
    }

    override fun onSlideShowStopped(controller: MediaSliderController, handler: Handler) {
        stopStoryProgressAnimation(handler)
    }

    override fun onSlideshowStarted(
        itemType: SliderItemType?,
        controller: MediaSliderController,
        config: MediaSliderConfiguration,
        context: Context,
        handler: Handler
    ) {
        when (itemType) {
            SliderItemType.VIDEO -> startVideoStoryProgress(config, context, controller, handler)
            SliderItemType.IMAGE -> {
                if (lastIntervalMs > 0) {
                    startStoryProgressAnimation(config, context, lastIntervalMs, handler)
                }
            }
            else -> Unit
        }
    }

    override fun onKeyDown(event: KeyEvent, state: SliderKeyEventState): SliderKeyEventResult {
        if (!state.isSlideshowPlaying || state.currentItemType != SliderItemType.IMAGE) {
            return SliderKeyEventResult.UNHANDLED
        }
        // When the image/video controller overlay is open, leave D-pad for focus navigation.
        if (state.isControllerVisible) {
            return SliderKeyEventResult.UNHANDLED
        }
        return when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                state.controller.skipToNextAndRestartTimer()
                SliderKeyEventResult.HANDLED_CONSUME
            }

            KeyEvent.KEYCODE_DPAD_LEFT -> {
                state.controller.skipToPreviousAndRestartTimer()
                SliderKeyEventResult.HANDLED_CONSUME
            }

            else -> SliderKeyEventResult.UNHANDLED
        }
    }

    private fun updateStoryProgressChrome(config: MediaSliderConfiguration, context: Context, index: Int, progress: Float) {
        val progressView = storyProgress ?: return
        val countView = storyProgressCount ?: return

        val total = config.items.size
        if (total <= 0) {
            countView.text = ""
            return
        }

        val safeIndex = index.coerceIn(0, total - 1)
        progressView.setSegmentCount(total)
        progressView.setProgress(safeIndex, progress)
        countView.text = context.getString(R.string.story_progress_count, safeIndex + 1, total)
    }

    private fun startStoryProgressAnimation(
        config: MediaSliderConfiguration,
        context: Context,
        intervalMs: Long,
        handler: Handler
    ) {
        stopStoryProgressAnimation(handler)
        val durationMs = intervalMs.coerceAtLeast(1L)
        updateStoryProgressChrome(config, context, currentIndex, 0f)
        storyProgressAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                updateStoryProgressChrome(config, context, currentIndex, anim.animatedValue as Float)
            }
            start()
        }
    }

    private fun startVideoStoryProgress(
        config: MediaSliderConfiguration,
        context: Context,
        controller: MediaSliderController,
        handler: Handler
    ) {
        videoStoryProgressRunnable?.let { handler.removeCallbacks(it) }
        if (!controller.slideShowPlaying) return

        val runnable = object : Runnable {
            override fun run() {
                if (!controller.slideShowPlaying) return
                if (config.items.isEmpty()) return
                if (config.items[currentIndex].type != SliderItemType.VIDEO) return

                val player = controller.currentPlayer
                if (player != null) {
                    val duration = player.duration
                    if (duration > 0) {
                        updateStoryProgressChrome(
                            config,
                            context,
                            currentIndex,
                            player.currentPosition.toFloat() / duration.toFloat()
                        )
                    }
                }
                handler.postDelayed(this, 50)
            }
        }
        videoStoryProgressRunnable = runnable
        handler.post(runnable)
    }

    private fun stopStoryProgressAnimation(handler: Handler) {
        storyProgressAnimator?.cancel()
        storyProgressAnimator = null
        videoStoryProgressRunnable?.let { handler.removeCallbacks(it) }
    }
}
