package nl.giejay.mediaslider.view

import android.animation.ValueAnimator
import android.content.Context
import android.view.KeyEvent
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.giejay.mediaslider.model.MetaDataType
import nl.giejay.mediaslider.model.SliderItem
import nl.giejay.mediaslider.model.SliderItemType
import com.zeuskartik.mediaslider.R

/**
 * Immich timeline / memories slider: story progress strip, date chrome, and
 * Left/Right scrubbing during autoplay without pausing the slideshow.
 *
 * Base transport/favorites live in [MediaSliderView] + [MediaSliderController].
 */
class TimelineSliderView(context: Context) : MediaSliderView(context) {

    private lateinit var storyProgress: StoryProgressView
    private lateinit var storyProgressRow: View
    private lateinit var storyProgressCount: TextView
    private lateinit var dateView: TextView

    private var storyProgressEnabled = false
    private var storyProgressAnimator: ValueAnimator? = null
    private var pendingDateAssetId: String? = null
    private val dateScope = CoroutineScope(Job() + Dispatchers.IO)

    private val videoStoryProgressRunnable = object : Runnable {
        override fun run() {
            if (!storyProgressEnabled || !controller.slideShowPlaying) return
            if (currentSliderItemType() != SliderItemType.VIDEO) return
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
        storyProgress = findViewById(R.id.story_progress)
        storyProgressRow = findViewById(R.id.story_progress_row)
        storyProgressCount = findViewById(R.id.story_progress_count)
        dateView = findViewById(R.id.metadata_date)

        controller.onAssetTimerStarted = { intervalMs ->
            if (storyProgressEnabled) startStoryProgressAnimation(intervalMs)
        }
        controller.onAssetTimerCancelled = {
            stopStoryProgressAnimation()
        }
        controller.onControllerVisibilityChanged = { visible ->
            if (storyProgressEnabled) {
                // Keep story chrome above the unified controller.
                storyProgressRow.visibility = if (storyProgressEnabled) View.VISIBLE else View.GONE
            }
            if (!visible && storyProgressEnabled) {
                dateView.visibility = if (dateView.text.isNullOrBlank()) View.GONE else View.VISIBLE
            }
        }
    }

    override fun loadMediaSliderView(config: nl.giejay.mediaslider.config.MediaSliderConfiguration) {
        super.loadMediaSliderView(config)
        onPageSettled(mPager.currentItem)
    }

    fun setStoryProgressEnabled(enabled: Boolean) {
        storyProgressEnabled = enabled
        if (!enabled) {
            stopStoryProgressAnimation()
            storyProgressRow.visibility = View.GONE
            return
        }
        storyProgressRow.visibility = View.VISIBLE
        if (isConfigReady) {
            updateStoryProgressChrome(mPager.currentItem.coerceAtLeast(0), progress = 0f)
        }
    }

    override fun handleSlideshowImageKey(keyCode: Int): Boolean {
        if (!storyProgressEnabled) return false
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
     * Memories call this after load with autoplay. Image pages get story progress via
     * [MediaSliderController.startTimerNextAsset]; video pages must start the Exo-driven
     * poller here — [onPageSettled] already ran while slideshow was still off.
     */
    override fun toggleSlideshow(showPlayIndicator: Boolean) {
        super.toggleSlideshow(showPlayIndicator)
        if (storyProgressEnabled &&
            controller.slideShowPlaying &&
            currentSliderItemType() == SliderItemType.VIDEO
        ) {
            startVideoStoryProgress()
        }
    }

    override fun onPageSettled(index: Int) {
        if (!isConfigReady) return
        if (storyProgressEnabled) {
            updateStoryProgressChrome(index, progress = 0f)
            if (controller.slideShowPlaying && currentSliderItemType() == SliderItemType.VIDEO) {
                startVideoStoryProgress()
            }
        }
        if (index in config.items.indices) {
            updateDateOverlay(config.items[index].mainItem)
        }
    }

    override fun onDetachedFromWindow() {
        stopStoryProgressAnimation()
        mainHandler.removeCallbacks(videoStoryProgressRunnable)
        super.onDetachedFromWindow()
    }

    private fun updateStoryProgressChrome(index: Int, progress: Float) {
        if (!storyProgressEnabled || !isConfigReady) return
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
        if (!storyProgressEnabled) return
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
        if (!storyProgressEnabled || !controller.slideShowPlaying) return
        mainHandler.post(videoStoryProgressRunnable)
    }

    private fun stopStoryProgressAnimation() {
        storyProgressAnimator?.cancel()
        storyProgressAnimator = null
        mainHandler.removeCallbacks(videoStoryProgressRunnable)
    }

    private fun updateDateOverlay(sliderItem: SliderItem) {
        pendingDateAssetId = sliderItem.id
        dateScope.launch {
            val value = sliderItem.get(MetaDataType.DATE).orEmpty().trim()
            withContext(Dispatchers.Main) {
                if (pendingDateAssetId != sliderItem.id) return@withContext
                dateView.text = value
                dateView.visibility = if (value.isNotEmpty()) View.VISIBLE else View.GONE
            }
        }
    }
}
