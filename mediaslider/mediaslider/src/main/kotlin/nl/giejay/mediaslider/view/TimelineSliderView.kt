package nl.giejay.mediaslider.view

import android.animation.ValueAnimator
import android.content.Context
import android.view.KeyEvent
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.TextView
import com.zeuskartik.mediaslider.R
import nl.giejay.mediaslider.config.MediaSliderConfiguration
import nl.giejay.mediaslider.model.SliderItemType

/**
 * Immich timeline / memories slider: story progress strip and Left/Right scrubbing
 * during autoplay without pausing the slideshow.
 *
 * Only inflate this view for memories/timeline playback; story progress is always on.
 * Shared image/video controls stay on [MediaSliderView] / [MediaSliderController].
 */
class TimelineSliderView(context: Context) : MediaSliderView(context) {

    private val storyProgress: StoryProgressView = findViewById(R.id.story_progress)
    private val storyProgressRow: View = findViewById(R.id.story_progress_row)
    private val storyProgressCount: TextView = findViewById(R.id.story_progress_count)

    private var storyProgressAnimator: ValueAnimator? = null

    private val videoStoryProgressRunnable = object : Runnable {
        override fun run() {
            if (!controller.slideShowPlaying) return
            if (currentItemType() != SliderItemType.VIDEO) return
            // Keep polling until the player is bound and duration is known — first-page
            // memories often start slideshow before ExoPlayer reports duration.
            val player = controller.currentPlayer
            if (player != null) {
                val duration = player.duration
                if (duration > 0) {
                    updateStoryProgressChrome(
                        mPager.currentItem,
                        player.currentPosition.toFloat() / duration.toFloat()
                    )
                }
            }
            mainHandler.postDelayed(this, 50)
        }
    }

    init {
        storyProgressRow.visibility = View.VISIBLE
        controller.onAssetTimerStarted = { intervalMs ->
            startStoryProgressAnimation(intervalMs)
        }
        controller.onSlideshowTimerCancelled = {
            stopStoryProgressAnimation()
        }
        controller.onVideoSlideshowStarted = {
            startVideoStoryProgress()
        }
        controller.onControllerVisibilityChanged = { _ ->
            storyProgressRow.visibility = View.VISIBLE
        }
    }

    override fun loadMediaSliderView(config: MediaSliderConfiguration) {
        super.loadMediaSliderView(config)
        onPageSettled(mPager.currentItem)
    }

    override fun handleSlideshowImageKey(keyCode: Int): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                controller.skipToNextAndRestartTimer()
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                controller.skipToPreviousAndRestartTimer()
                true
            }
            else -> false
        }
    }

    /**
     * Memories start autoplay after load. Image pages get story progress via
     * [MediaSliderController.startTimerNextAsset]; video pages must start the Exo-driven
     * poller here — [onPageSettled] already ran while slideshow was still off.
     */
    override fun toggleSlideshow(showPlayIndicator: Boolean) {
        super.toggleSlideshow(showPlayIndicator)
        if (controller.slideShowPlaying && currentItemType() == SliderItemType.VIDEO) {
            startVideoStoryProgress()
        }
    }

    override fun onPageSettled(index: Int) {
        if (!isConfigReady) return
        updateStoryProgressChrome(index, progress = 0f)
        if (controller.slideShowPlaying && currentItemType() == SliderItemType.VIDEO) {
            startVideoStoryProgress()
        }
    }

    override fun onVideoPageBoundWhileSlideshow() {
        startVideoStoryProgress()
    }

    override fun onDetachedFromWindow() {
        stopStoryProgressAnimation()
        mainHandler.removeCallbacks(videoStoryProgressRunnable)
        super.onDetachedFromWindow()
    }

    private fun updateStoryProgressChrome(index: Int, progress: Float) {
        if (!isConfigReady) return
        val total = config.items.size
        if (total <= 0) {
            storyProgressCount.text = ""
            return
        }
        val safeIndex = index.coerceIn(0, total - 1)
        storyProgress.setSegmentCount(total)
        storyProgress.setProgress(safeIndex, progress)
        storyProgressCount.text = "${safeIndex + 1}/$total"
    }

    private fun startStoryProgressAnimation(intervalMs: Long) {
        stopStoryProgressAnimation()
        val index = mPager.currentItem
        updateStoryProgressChrome(index, progress = 0f)
        storyProgressAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = intervalMs.coerceAtLeast(1L)
            interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                updateStoryProgressChrome(index, anim.animatedValue as Float)
            }
            start()
        }
    }

    private fun startVideoStoryProgress() {
        mainHandler.removeCallbacks(videoStoryProgressRunnable)
        if (!controller.slideShowPlaying) return
        mainHandler.post(videoStoryProgressRunnable)
    }

    private fun stopStoryProgressAnimation() {
        storyProgressAnimator?.cancel()
        storyProgressAnimator = null
        mainHandler.removeCallbacks(videoStoryProgressRunnable)
    }
}
