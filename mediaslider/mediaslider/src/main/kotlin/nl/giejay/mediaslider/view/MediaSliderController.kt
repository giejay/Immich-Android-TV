package nl.giejay.mediaslider.view

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.viewpager.widget.ViewPager
import com.zeuskartik.mediaslider.R
import nl.giejay.mediaslider.config.MediaSliderConfiguration
import nl.giejay.mediaslider.model.SliderItemType
import nl.giejay.mediaslider.model.SliderItemViewHolder
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Handles all control logic for the media slider:
 * slideshow management, page navigation, unified controller show/hide/configure
 * (for both images and videos), video playback controls, progress updates
 * and screen wake-lock.
 *
 * [MediaSliderView] is responsible only for the view hierarchy; all interactive
 * behaviour is delegated here.
 */
class MediaSliderController(
    private val context: Context,
    private val mainHandler: Handler,
    private val pager: ViewPager,
    /** The centre play/pause overlay indicator shown briefly on slideshow toggle. */
    private val playButtonView: View,
    private val controllerRootView: View
) {
    private lateinit var config: MediaSliderConfiguration

    var slideShowPlaying: Boolean = false
        private set

    private var _isControllerVisible = false
    val isControllerVisible: Boolean get() = _isControllerVisible

    var onControllerVisibilityChanged: ((Boolean) -> Unit)? = null

    /** Currently active ExoPlayer (for the video page that is in view). */
    var currentPlayer: ExoPlayer? = null
        private set

    /** True while the user is actively dragging the seek bar. */
    private var isSeeking = false

    private val goToNextAssetRunnable = Runnable { goToNextAsset() }

    private val progressUpdateRunnable = object : Runnable {
        override fun run() {
            updateVideoProgress()
            if (currentPlayer?.isPlaying == true) {
                mainHandler.postDelayed(this, PROGRESS_UPDATE_INTERVAL_MS)
            }
        }
    }

    // -------------------------------------------------------------------------
    // Initialisation
    // -------------------------------------------------------------------------

    fun initialize(config: MediaSliderConfiguration) {
        this.config = config
    }

    fun setCurrentPlayer(player: ExoPlayer?) {
        currentPlayer = player
    }

    // -------------------------------------------------------------------------
    // ExoPlayer listener callbacks (forwarded from ExoPlayerListener)
    // -------------------------------------------------------------------------

    fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_ENDED && slideShowPlaying) {
            goToNextAsset()
        }
    }

    fun onIsPlayingChanged(isPlaying: Boolean) {
        controllerRootView.findViewById<ImageButton>(R.id.media_play_pause)
            ?.let { updatePlayPauseIcon(it, isPlaying) }

        if (currentItemTypeOrNull() == SliderItemType.VIDEO) {
            if (isPlaying) {
                setKeepScreenOnFlags()
                if (_isControllerVisible) startProgressUpdates()
            } else {
                stopProgressUpdates()
            }
        }
    }

    fun onPlayerError(error: PlaybackException) {
        Timber.e(error, "Player error")
        if (slideShowPlaying) {
            goToNextAsset()
        }
        clearKeepScreenOnFlags()
        stopProgressUpdates()
    }

    // -------------------------------------------------------------------------
    // Slideshow
    // -------------------------------------------------------------------------

    fun toggleSlideshow(showPlayIndicator: Boolean) {
        slideShowPlaying = !slideShowPlaying
        if (slideShowPlaying) {
            if (currentItemTypeOrNull() == SliderItemType.IMAGE) {
                startTimerNextAsset()
            }
            setKeepScreenOnFlags()
        } else {
            clearKeepScreenOnFlags()
            mainHandler.removeCallbacks(goToNextAssetRunnable)
            onAssetTimerCancelled?.invoke()
        }
        if (showPlayIndicator) {
            showTemporaryPlayIndicator()
        }
    }

    /** Fired when the image slideshow timer is armed (interval milliseconds). */
    var onAssetTimerStarted: ((intervalMs: Long) -> Unit)? = null

    /** Fired when the pending auto-advance timer is cancelled. */
    var onAssetTimerCancelled: (() -> Unit)? = null

    fun startTimerNextAsset() {
        mainHandler.removeCallbacks(goToNextAssetRunnable)
        val intervalMs = (config.interval * 1000).toLong()
        mainHandler.postDelayed(goToNextAssetRunnable, intervalMs)
        onAssetTimerStarted?.invoke(intervalMs)
    }

    /** Cancels any pending auto-advance timer without starting a new one. */
    fun cancelNextAssetTimer() {
        mainHandler.removeCallbacks(goToNextAssetRunnable)
        onAssetTimerCancelled?.invoke()
    }

    /** Cancels any pending auto-advance and immediately moves to the next asset. */
    fun skipToNextAndRestartTimer() {
        mainHandler.removeCallbacks(goToNextAssetRunnable)
        onAssetTimerCancelled?.invoke()
        goToNextAsset()
    }

    /** Same as [skipToNextAndRestartTimer] but steps backward (memories / story scrub). */
    fun skipToPreviousAndRestartTimer() {
        mainHandler.removeCallbacks(goToNextAssetRunnable)
        onAssetTimerCancelled?.invoke()
        goToPreviousAsset()
    }

    // -------------------------------------------------------------------------
    // Navigation
    // -------------------------------------------------------------------------

    fun goToNextAsset() {
        hideController()
        if (pager.currentItem < pager.adapter!!.count - 1) {
            pager.setCurrentItem(pager.currentItem + 1, config.enableSlideAnimation)
        } else {
            pager.setCurrentItem(0, config.enableSlideAnimation)
        }
        restorePagerFocus()
    }

    fun goToPreviousAsset() {
        hideController()
        pager.setCurrentItem(
            (if (0 == pager.currentItem) pager.adapter!!.count else pager.currentItem) - 1,
            config.enableSlideAnimation
        )
        restorePagerFocus()
    }

    // -------------------------------------------------------------------------
    // Unified controller show / hide / configure
    // -------------------------------------------------------------------------

    /**
     * Toggles the unified overlay controller for the current page.
     * Returns true if the event was consumed (controller shown or already visible).
     */
    fun toggleController(): Boolean {
        val controller = controllerRootView.findViewById<View>(R.id.image_controller) ?: return false
        if (controller.isVisible) {
            hideController()
            return true
        }
        if (slideShowPlaying) {
            toggleSlideshow(true)
        }
        controller.visibility = View.VISIBLE
        _isControllerVisible = true
        onControllerVisibilityChanged?.invoke(true)

        // Initial focus
        if (currentItemTypeOrNull() == SliderItemType.VIDEO) {
            controllerRootView.findViewById<ImageButton>(R.id.media_play_pause)?.requestFocus()
        } else {
            val focusId = if (currentItemOrNull()?.hasSecondaryItem() == true)
                R.id.image_slideshow else R.id.image_favorite
            controllerRootView.findViewById<ImageButton>(focusId)?.requestFocus()
        }

        if (currentItemTypeOrNull() == SliderItemType.VIDEO) {
            startProgressUpdates()
        }
        return true
    }

    fun hideController() {
        controllerRootView.findViewById<View>(R.id.image_controller)?.visibility = View.GONE
        _isControllerVisible = false
        onControllerVisibilityChanged?.invoke(false)
        stopProgressUpdates()
        restorePagerFocus()
    }

    /**
     * Wires up all button listeners for the unified controller embedded in the
     * view at [sliderItemIndex].  Hides the controller and resets visibility of
     * video-only widgets based on [sliderItem].
     */
    fun configureController(sliderItem: SliderItemViewHolder, sliderItemIndex: Int) {
        val controller = controllerRootView.findViewById<View>(R.id.image_controller) ?: run {
            _isControllerVisible = false
            return
        }

        controller.visibility = View.GONE
        _isControllerVisible = false
        onControllerVisibilityChanged?.invoke(false)
        stopProgressUpdates()

        val previousButton = controllerRootView.findViewById<ImageButton>(R.id.image_previous) ?: return
        val favoriteButton = controllerRootView.findViewById<ImageButton>(R.id.image_favorite) ?: return
        val slideshowButton = controllerRootView.findViewById<ImageButton>(R.id.image_slideshow) ?: return
        val nextButton = controllerRootView.findViewById<ImageButton>(R.id.image_next) ?: return
        val muteButton = controllerRootView.findViewById<ImageButton>(R.id.media_mute)
        val playPauseButton = controllerRootView.findViewById<ImageButton>(R.id.media_play_pause)
        val progressLayout = controllerRootView.findViewById<View>(R.id.media_progress_layout)
        val seekBar = controllerRootView.findViewById<SeekBar>(R.id.media_seek_bar)

        val isVideo = sliderItem.type == SliderItemType.VIDEO
        val hasSecondaryItem = sliderItem.hasSecondaryItem()

        // Show / hide video-specific widgets
        muteButton?.visibility = if (isVideo) View.VISIBLE else View.GONE
        playPauseButton?.visibility = if (isVideo) View.VISIBLE else View.GONE
        progressLayout?.visibility = if (isVideo) View.VISIBLE else View.GONE

        // Favorite is hidden for double-image items; shown for all single items (incl. video)
        favoriteButton.visibility = if (hasSecondaryItem) View.GONE else View.VISIBLE

        // Common navigation listeners
        previousButton.setOnClickListener { goToNextAsset() } // previousButton.setOnClickListener { goToPreviousAsset() }
        slideshowButton.setOnClickListener { toggleSlideshow(true) }
        nextButton.setOnClickListener { goToNextAsset() }

        // Actually previousButton should go to previous asset
        previousButton.setOnClickListener { goToPreviousAsset() }

        // Favorite
        updateFavoriteIcon(favoriteButton, sliderItem.mainItem.isFavorite)
        favoriteButton.setOnClickListener {
            val newValue = !sliderItem.mainItem.isFavorite
            sliderItem.mainItem.isFavorite = newValue
            updateFavoriteIcon(favoriteButton, newValue)
            MediaSliderConfiguration.onFavoriteToggle(sliderItem.mainItem.id, newValue)
        }

        // Video-only controls
        if (isVideo && muteButton != null && playPauseButton != null) {
            val soundEnabled = (currentPlayer?.volume ?: 0f) > 0f
            updateMuteIcon(muteButton, soundEnabled)
            muteButton.setOnClickListener {
                currentPlayer?.let { player ->
                    if (player.volume == 0f) {
                        player.volume = 1f
                        config.isVideoSoundEnable = true
                        updateMuteIcon(muteButton, true)
                    } else {
                        player.volume = 0f
                        config.isVideoSoundEnable = false
                        updateMuteIcon(muteButton, false)
                    }
                }
            }

            updatePlayPauseIcon(playPauseButton, currentPlayer?.isPlaying == true)
            playPauseButton.setOnClickListener {
                currentPlayer?.let { player ->
                    if (player.isPlaying) {
                        player.pause()
                    } else {
                        if (player.currentPosition >= player.contentDuration) {
                            player.seekToDefaultPosition()
                        }
                        player.play()
                    }
                }
            }

            // SeekBar: allow the user to seek
            seekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(seekBar: SeekBar) {
                    isSeeking = true
                }

                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        currentPlayer?.seekTo(progress.toLong() * 1000)
                    }
                }

                override fun onStopTrackingTouch(seekBar: SeekBar) {
                    isSeeking = false
                }
            })
        }

        setupFocusNavigation(
            previousButton, muteButton, playPauseButton,
            favoriteButton, slideshowButton, nextButton,
            isVideo, hasSecondaryItem,
            seekBar
        )
    }

    // -------------------------------------------------------------------------
    // Mute helper (called from volume-change broadcast receiver)
    // -------------------------------------------------------------------------

    fun toggleMute() {
        controllerRootView.findViewById<ImageButton>(R.id.media_mute)?.performClick()
    }

    // -------------------------------------------------------------------------
    // Player management
    // -------------------------------------------------------------------------

    fun stopPlayer() {
        currentPlayer?.let {
            if (it.isPlaying || it.isLoading) it.stop()
        }
        clearKeepScreenOnFlags()
        stopProgressUpdates()
    }

    fun onDestroy() {
        currentPlayer?.release()
        currentPlayer = null
        clearKeepScreenOnFlags()
        mainHandler.removeCallbacks(goToNextAssetRunnable)
        stopProgressUpdates()
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private fun setupFocusNavigation(
        previousButton: ImageButton,
        muteButton: ImageButton?,
        playPauseButton: ImageButton?,
        favoriteButton: ImageButton,
        slideshowButton: ImageButton,
        nextButton: ImageButton,
        isVideo: Boolean,
        hasSecondaryItem: Boolean,
        seekBar: SeekBar? = null
    ) {
        val chain = mutableListOf<ImageButton>()
        if (isVideo) muteButton?.let { chain.add(it) }
        chain.add(previousButton)
        if (isVideo) playPauseButton?.let { chain.add(it) }
        if (!hasSecondaryItem) chain.add(favoriteButton)
        chain.add(slideshowButton)
        chain.add(nextButton)

        for (i in chain.indices) {
            if (i > 0) chain[i].nextFocusLeftId = chain[i - 1].id
            if (i < chain.size - 1) chain[i].nextFocusRightId = chain[i + 1].id
        }

        // Wire up/down navigation between the buttons row and the seekbar
        if (isVideo && seekBar != null) {
            chain.forEach { button -> button.nextFocusDownId = R.id.media_seek_bar }
            seekBar.nextFocusUpId = (playPauseButton ?: previousButton).id
        }
    }

    private fun updateFavoriteIcon(button: ImageButton, isFavorite: Boolean) {
        button.setImageResource(if (isFavorite) R.drawable.ic_favorite else R.drawable.ic_favorite_border)
    }

    private fun updateMuteIcon(button: ImageButton, soundEnabled: Boolean) {
        button.setImageResource(if (soundEnabled) R.drawable.unmute_icon else R.drawable.mute_icon)
    }

    private fun updatePlayPauseIcon(button: ImageButton, isPlaying: Boolean) {
        button.setImageResource(
            if (isPlaying) android.R.drawable.ic_media_pause
            else android.R.drawable.ic_media_play
        )
    }

    private fun showTemporaryPlayIndicator() {
        playButtonView.visibility = View.VISIBLE
        playButtonView.setBackgroundResource(
            if (slideShowPlaying) android.R.drawable.ic_media_play
            else android.R.drawable.ic_media_pause
        )
        mainHandler.postDelayed({
            playButtonView.visibility = View.GONE
            restorePagerFocus()
        }, 2000)
    }

    private fun startProgressUpdates() {
        mainHandler.removeCallbacks(progressUpdateRunnable)
        mainHandler.post(progressUpdateRunnable)
    }

    private fun stopProgressUpdates() {
        mainHandler.removeCallbacks(progressUpdateRunnable)
    }

    private fun updateVideoProgress() {
        val player = currentPlayer ?: return
        val duration = player.contentDuration
        if (duration <= 0) return

        val seekBar = controllerRootView.findViewById<SeekBar>(R.id.media_seek_bar) ?: return
        val positionText = controllerRootView.findViewById<TextView>(R.id.media_position) ?: return
        val durationText = controllerRootView.findViewById<TextView>(R.id.media_duration) ?: return

        val position = player.currentPosition
        if (!isSeeking) {
            val durationInSeconds = (duration / 1000).toInt()
            if (seekBar.max != durationInSeconds) {
                seekBar.max = durationInSeconds
            }
            seekBar.progress = (position / 1000).toInt()
        }
        positionText.text = formatDuration(position)
        durationText.text = formatDuration(duration)
    }

    private fun formatDuration(ms: Long): String {
        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(ms)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%d:%02d", minutes, seconds)
    }

    fun setKeepScreenOnFlags() {
        if (context is Activity) {
            val window = context.window
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            Log.d(TAG, "set FLAG_KEEP_SCREEN_ON")
        }
    }

    fun clearKeepScreenOnFlags() {
        if (context is Activity) {
            val window = context.window
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            Log.d(TAG, "clear FLAG_KEEP_SCREEN_ON")
        }
    }

    fun restorePagerFocus() {
        pager.post {
            if (!pager.hasFocus()) {
                pager.requestFocus()
            }
        }
    }

    private fun currentItemTypeOrNull(): SliderItemType? {
        if (!::config.isInitialized || config.items.isEmpty()) return null
        val idx = pager.currentItem
        return if (idx < config.items.size) config.items[idx].type else null
    }

    private fun currentItemOrNull(): SliderItemViewHolder? {
        if (!::config.isInitialized || config.items.isEmpty()) return null
        val idx = pager.currentItem
        return if (idx < config.items.size) config.items[idx] else null
    }

    private companion object {
        const val PROGRESS_UPDATE_INTERVAL_MS = 500L
        const val TAG = "MediaSliderController"
    }
}
