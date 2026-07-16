package nl.giejay.mediaslider.view

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.util.Log
import android.util.TypedValue
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.annotation.OptIn
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.viewpager.widget.ViewPager
import com.zeuskartik.mediaslider.R
import nl.giejay.mediaslider.config.MediaSliderConfiguration
import nl.giejay.mediaslider.model.SliderItem
import nl.giejay.mediaslider.model.SliderItemType
import nl.giejay.mediaslider.model.SliderItemViewHolder
import nl.giejay.mediaslider.util.MediaSliderListener
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Control logic for the layered details + shared image/video transport
 * ([R.id.image_controller]), slideshow, remote seek/play, and wake-lock.
 *
 * [MediaSliderView] owns the pager/metadata shell; timeline/memories chrome lives in
 * [TimelineSliderView].
 */
class MediaSliderController(
    private val context: Context,
    private val mainHandler: Handler,
    private val pager: ViewPager,
    private val playButton: View,
    private val sharedControls: View,
    private val metaDataHolder: FrameLayout,
    private val metadataRows: LinearLayout,
    private val dateView: TextView,
    private val host: Host
) {
    interface Host {
        fun mediaConfig(): MediaSliderConfiguration
        fun currentItem(): SliderItemViewHolder
        fun currentItemType(): SliderItemType
        fun refreshOverlayMetadata()
        fun bindMetadataAdapters()
        fun updateDateOverlay(sliderItem: SliderItem)
        fun applyDetailsOverlayVisibilityIfDetailsOpen()
        /** Bottom EXIF columns are ready for the current page (or there are none to show). */
        fun isBottomMetadataReady(): Boolean
        /** Called when slideshow starts on a video page (story progress). */
        fun onVideoSlideshowStarted()
        /** Called when slideshow pauses or the image timer is cancelled. */
        fun onSlideshowTimerCancelled()
    }

    private lateinit var config: MediaSliderConfiguration

    var slideShowPlaying: Boolean = false
        private set

    var currentPlayer: ExoPlayer? = null
        private set

    /** Accumulates rapid seek steps while ExoPlayer's reported position lags. */
    private var pendingSeekTargetMs: Long? = null

    /** Non-zero while a seek key is held; drives the slow continuous scrub ticks. */
    private var holdSeekStepMs: Long = 0L

    /**
     * Direction of a possible tap seek. Cleared when hold-scrub engages; applied on key-up
     * if the press was short enough that continuous scrub never started.
     */
    private var pendingSeekTapForward: Boolean? = null

    /** True once the first hold-scrub tick has run (so key-up must not also do a 10s tap). */
    private var holdSeekEngaged = false

    /** True after hold-scrub starts and we've paused to avoid seek/playback fighting. */
    private var pausedForHoldSeek = false

    var detailsOverlayVisible = false
        private set

    var hasBottomDetails = false

    var videoControllerVisible = false
        private set

    var imageTransportVisible = false
        private set

    var suppressTransportEnterUp = false

    private var isSeeking = false

    /**
     * Last non-zero height of the bottom details holder. Used as [View.setMinimumHeight]
     * while EXIF rows are cleared/loading so wrap_content doesn't collapse the scrim to 0.
     */
    private var detailsHolderMinHeightPx = 0

    val detailsOverlayToggleEnabled: Boolean
        get() = context !is MediaSliderListener

    val transportControlsVisible: Boolean
        get() = videoControllerVisible || imageTransportVisible

    val isControllerVisible: Boolean
        get() = transportControlsVisible

    /** Fired when the image slideshow timer is armed (interval milliseconds). */
    var onAssetTimerStarted: ((intervalMs: Long) -> Unit)? = null

    var onControllerVisibilityChanged: ((Boolean) -> Unit)? = null

    var onDetailsVisibilityChanged: ((Boolean) -> Unit)? = null

    private val goToNextAssetRunnable = Runnable { goToNextAsset() }
    private val hideTransportRunnable = Runnable { hideTransportControls() }
    private val holdSeekRunnable = object : Runnable {
        override fun run() {
            if (holdSeekStepMs == 0L) return
            if (!holdSeekEngaged) {
                holdSeekEngaged = true
                pendingSeekTapForward = null
            }
            beginHoldSeekScrub()
            seekOrSkipBy(holdSeekStepMs, allowAssetSkip = false)
            if (videoControllerVisible) {
                updateVideoProgress()
            }
            mainHandler.postDelayed(this, HOLD_SEEK_INTERVAL_MS)
        }
    }
    private val progressUpdateRunnable = object : Runnable {
        override fun run() {
            updateVideoProgress()
            if (currentPlayer?.isPlaying == true && videoControllerVisible) {
                mainHandler.postDelayed(this, PROGRESS_UPDATE_INTERVAL_MS)
            }
        }
    }

    fun initialize(config: MediaSliderConfiguration) {
        this.config = config
        detailsOverlayVisible = false
    }

    fun bindCurrentPlayer(player: ExoPlayer?) {
        stopHoldSeek()
        pendingSeekTargetMs = null
        currentPlayer = player
    }

    fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_ENDED && slideShowPlaying) {
            goToNextAsset()
        }
    }

    fun onIsPlayingChanged(isPlaying: Boolean) {
        sharedControls.findViewById<ImageButton>(R.id.media_play_pause)
            ?.let { updatePlayPauseIcon(it, isPlaying) }
        if (host.currentItemType() == SliderItemType.VIDEO) {
            if (isPlaying) {
                setKeepScreenOnFlags()
                if (videoControllerVisible) startProgressUpdates()
            } else {
                stopProgressUpdates()
            }
        }
    }

    fun onPlayerError(error: PlaybackException) {
        if (slideShowPlaying) {
            goToNextAsset()
        }
        if (host.currentItemType() == SliderItemType.VIDEO) {
            clearKeepScreenOnFlags()
        }
        stopProgressUpdates()
    }

    // -------------------------------------------------------------------------
    // Slideshow / navigation
    // -------------------------------------------------------------------------

    fun toggleSlideshow(showPlayIndicator: Boolean) {
        slideShowPlaying = !slideShowPlaying
        updateVideoRepeatMode()
        if (slideShowPlaying) {
            if (host.currentItemType() == SliderItemType.IMAGE) {
                startTimerNextAsset()
            } else if (host.currentItemType() == SliderItemType.VIDEO) {
                val player = currentPlayer
                if (player?.playbackState == Player.STATE_ENDED) {
                    player.seekTo(0)
                }
                player?.playWhenReady = true
                host.onVideoSlideshowStarted()
            }
            setKeepScreenOnFlags()
        } else {
            clearKeepScreenOnFlags()
            mainHandler.removeCallbacks(goToNextAssetRunnable)
            host.onSlideshowTimerCancelled()
            if (host.currentItemType() == SliderItemType.VIDEO &&
                currentPlayer?.playbackState == Player.STATE_ENDED
            ) {
                currentPlayer?.seekTo(0)
                currentPlayer?.play()
            }
        }
        syncSlideshowButton()
        if (showPlayIndicator) {
            showTemporaryPlayIndicator()
        }
    }

    fun startTimerNextAsset() {
        mainHandler.removeCallbacks(goToNextAssetRunnable)
        val intervalMs = (config.interval * 1000).toLong()
        mainHandler.postDelayed(goToNextAssetRunnable, intervalMs)
        onAssetTimerStarted?.invoke(intervalMs)
    }

    fun cancelNextAssetTimer() {
        mainHandler.removeCallbacks(goToNextAssetRunnable)
        host.onSlideshowTimerCancelled()
    }

    fun skipToNextAndRestartTimer() {
        cancelNextAssetTimer()
        goToNextAsset()
    }

    fun skipToPreviousAndRestartTimer() {
        cancelNextAssetTimer()
        goToPreviousAsset()
    }

    fun goToNextAsset() {
        // During slideshow, keep the shared bar up across advances so the user can
        // still reach pause before auto-hide / Back. Manual paging without slideshow
        // still dismisses transport.
        if (!slideShowPlaying || !transportControlsVisible) {
            hideTransportControls()
        }
        if (pager.currentItem < pager.adapter!!.count - 1) {
            pager.setCurrentItem(pager.currentItem + 1, config.enableSlideAnimation)
        } else {
            pager.setCurrentItem(0, config.enableSlideAnimation)
        }
        restorePagerFocus()
    }

    fun goToPreviousAsset() {
        if (!slideShowPlaying || !transportControlsVisible) {
            hideTransportControls()
        }
        pager.setCurrentItem(
            (if (0 == pager.currentItem) pager.adapter!!.count else pager.currentItem) - 1,
            config.enableSlideAnimation
        )
        restorePagerFocus()
    }

    // -------------------------------------------------------------------------
    // Details + transport overlay
    // -------------------------------------------------------------------------

    fun showDetailsOverlay() {
        if (detailsOverlayVisible) return
        detailsOverlayVisible = true
        applyDetailsOverlayVisibility()
        host.refreshOverlayMetadata()
        host.bindMetadataAdapters()
        syncSlideshowButton()
        onDetailsVisibilityChanged?.invoke(true)
    }

    fun hideDetailsOverlay() {
        if (!detailsOverlayVisible) return
        detailsOverlayVisible = false
        hideImageTransportControls()
        hideVideoController()
        applyDetailsOverlayVisibility()
        onDetailsVisibilityChanged?.invoke(false)
    }

    fun showVideoController() {
        val player = currentPlayer ?: return
        videoControllerVisible = true
        imageTransportVisible = false
        if (detailsOverlayToggleEnabled && !detailsOverlayVisible) {
            detailsOverlayVisible = true
            host.updateDateOverlay(host.currentItem().mainItem)
            onDetailsVisibilityChanged?.invoke(true)
        }
        configureSharedControls(host.currentItem())
        applyTransportChrome(isVideo = true)
        syncMuteButton()
        syncSlideshowButton()
        applyDetailsOverlayVisibility()
        scheduleTransportAutoHide()
        startProgressUpdates()
        onControllerVisibilityChanged?.invoke(true)
        sharedControls.post { focusSharedTransport(isVideo = true) }
        // Keep player reference used by SeekBar (player already bound).
        player.playWhenReady = player.playWhenReady
    }

    fun showImageTransportControls() {
        imageTransportVisible = true
        videoControllerVisible = false
        stopProgressUpdates()
        if (slideShowPlaying) {
            // Keep slideshow running; transport is observational chrome.
        }
        configureSharedControls(host.currentItem())
        applyTransportChrome(isVideo = false)
        syncSlideshowButton()
        applyDetailsOverlayVisibility()
        scheduleTransportAutoHide()
        onControllerVisibilityChanged?.invoke(true)
        sharedControls.post { focusSharedTransport(isVideo = false) }
    }

    fun hideImageTransportControls() {
        if (!imageTransportVisible && !sharedControls.isVisible) return
        cancelTransportAutoHide()
        imageTransportVisible = false
        if (!videoControllerVisible) {
            sharedControls.visibility = View.GONE
            stopProgressUpdates()
            onControllerVisibilityChanged?.invoke(false)
        }
        applyDetailsOverlayVisibility()
    }

    fun hideVideoController() {
        cancelTransportAutoHide()
        videoControllerVisible = false
        stopProgressUpdates()
        if (!imageTransportVisible) {
            sharedControls.visibility = View.GONE
            onControllerVisibilityChanged?.invoke(false)
        }
        applyDetailsOverlayVisibility()
    }

    fun hideTransportControls() {
        when {
            videoControllerVisible -> hideVideoController()
            imageTransportVisible -> hideImageTransportControls()
        }
    }

    /**
     * After a page binds: keep transport during slideshow if it was already up
     * (re-wire chrome for the new asset), otherwise clear it.
     */
    fun onPageMediaBound(sliderItem: SliderItemViewHolder) {
        configureSharedControls(sliderItem)
        if (slideShowPlaying && transportControlsVisible) {
            val isVideo = sliderItem.type == SliderItemType.VIDEO
            videoControllerVisible = isVideo && currentPlayer != null
            imageTransportVisible = !videoControllerVisible
            applyTransportChrome(isVideo = videoControllerVisible)
            syncMuteButton()
            syncSlideshowButton()
            applyDetailsOverlayVisibility()
            scheduleTransportAutoHide()
            if (videoControllerVisible) startProgressUpdates() else stopProgressUpdates()
            sharedControls.post { focusSharedTransport(isVideo) }
            return
        }
        dismissTransportForPageChange()
    }

    /** Clear transport when paging without auto-surfacing a new strip. */
    fun dismissTransportForPageChange() {
        imageTransportVisible = false
        videoControllerVisible = false
        cancelTransportAutoHide()
        stopProgressUpdates()
        sharedControls.visibility = View.GONE
        applyDetailsOverlayVisibility()
        onControllerVisibilityChanged?.invoke(false)
    }

    fun scheduleTransportAutoHide() {
        mainHandler.removeCallbacks(hideTransportRunnable)
        mainHandler.postDelayed(hideTransportRunnable, TRANSPORT_AUTO_HIDE_MS)
    }

    fun cancelTransportAutoHide() {
        mainHandler.removeCallbacks(hideTransportRunnable)
    }

    /** Snapshot holder height before EXIF rows are cleared on page change. */
    fun preserveDetailsHolderHeight() {
        val height = metaDataHolder.height
        if (height > 0) {
            detailsHolderMinHeightPx = height
        }
    }

    fun applyDetailsOverlayVisibility() {
        val detailsOn = !detailsOverlayToggleEnabled || detailsOverlayVisible
        val showTransport = transportControlsVisible
        val metadataReady = host.isBottomMetadataReady()
        // Keep the bottom scrim up while EXIF loads; only the rows wait on readiness.
        val showDetailsArea = detailsOn && hasBottomDetails
        val showHolder = showDetailsArea || showTransport
        metaDataHolder.visibility =
            if (showHolder && (hasBottomDetails || showTransport)) View.VISIBLE else View.GONE
        sharedControls.visibility = if (showTransport) View.VISIBLE else View.GONE
        if (showTransport) {
            applyTransportChrome(isVideo = videoControllerVisible)
        }
        metadataRows.visibility = if (showDetailsArea && metadataReady) View.VISIBLE else View.GONE
        metadataRows.alpha = if (showTransport) METADATA_DIMMED_WHILE_TRANSPORT_ALPHA else 1f
        metadataRows.descendantFocusability =
            if (showTransport) ViewGroup.FOCUS_BLOCK_DESCENDANTS
            else ViewGroup.FOCUS_AFTER_DESCENDANTS
        metaDataHolder.findViewById<LinearLayout>(R.id.metadata_view_left)?.apply {
            isFocusable = false
            isFocusableInTouchMode = false
        }
        metaDataHolder.findViewById<LinearLayout>(R.id.metadata_view_right)?.apply {
            isFocusable = false
            isFocusableInTouchMode = false
        }

        // wrap_content collapses to 0 when rows are cleared — lock min height while loading.
        if (showDetailsArea && !metadataReady) {
            metaDataHolder.minimumHeight = detailsHolderMinHeightPx.takeIf { it > 0 }
                ?: defaultDetailsHolderMinHeightPx()
        } else {
            metaDataHolder.minimumHeight = 0
            if (showDetailsArea && metadataReady) {
                metaDataHolder.post {
                    val height = metaDataHolder.height
                    if (height > 0) detailsHolderMinHeightPx = height
                }
            }
        }

        if (!detailsOn) {
            dateView.visibility = View.GONE
        } else if (dateView.text.isNotBlank()) {
            dateView.visibility = View.VISIBLE
        }
        // Empty text while loading: leave visibility as set by updateDateOverlay /
        // clearMetadataChromeForPageChange (scrim up). Empty after fetch: leave GONE.
        if (showHolder && (hasBottomDetails || showTransport)) {
            if (!detailsOverlayToggleEnabled && config.isGradiantOverlayVisible && !showTransport) {
                metaDataHolder.setBackgroundResource(R.drawable.gradient_overlay)
            } else {
                metaDataHolder.setBackgroundResource(R.drawable.metadata_details_scrim)
            }
        }
    }

    private fun defaultDetailsHolderMinHeightPx(): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            DETAILS_HOLDER_FALLBACK_MIN_HEIGHT_DP,
            context.resources.displayMetrics
        ).toInt()

    /** Wire listeners + video-only chrome for the current asset on the shared bar. */
    fun configureSharedControls(sliderItem: SliderItemViewHolder) {
        val previousButton = sharedControls.findViewById<ImageButton>(R.id.image_previous) ?: return
        val favoriteButton = sharedControls.findViewById<ImageButton>(R.id.image_favorite) ?: return
        val slideshowButton = sharedControls.findViewById<ImageButton>(R.id.image_slideshow) ?: return
        val nextButton = sharedControls.findViewById<ImageButton>(R.id.image_next) ?: return
        val externalPlayerButton = sharedControls.findViewById<ImageButton>(R.id.media_open_external)
        val muteButton = sharedControls.findViewById<ImageButton>(R.id.media_mute)
        val playPauseButton = sharedControls.findViewById<ImageButton>(R.id.media_play_pause)
        val progressLayout = sharedControls.findViewById<View>(R.id.media_progress_layout)
        val seekBar = sharedControls.findViewById<SeekBar>(R.id.media_seek_bar)

        val isVideo = sliderItem.type == SliderItemType.VIDEO
        val canOpenExternally = isVideo && !sliderItem.url.isNullOrBlank()
        val hasSecondaryItem = sliderItem.hasSecondaryItem()

        muteButton?.visibility = if (isVideo) View.VISIBLE else View.GONE
        playPauseButton?.visibility = if (isVideo) View.VISIBLE else View.GONE
        externalPlayerButton?.visibility = if (canOpenExternally) View.VISIBLE else View.GONE
        progressLayout?.visibility = if (isVideo) View.VISIBLE else View.GONE
        favoriteButton.visibility = if (hasSecondaryItem) View.GONE else View.VISIBLE

        previousButton.setOnClickListener { goToPreviousAsset() }
        nextButton.setOnClickListener { goToNextAsset() }
        slideshowButton.setOnClickListener {
            toggleSlideshow(showPlayIndicator = false)
            syncSlideshowButton()
        }

        updateFavoriteIcon(favoriteButton, sliderItem.mainItem.isFavorite)
        favoriteButton.setOnClickListener {
            val newValue = !sliderItem.mainItem.isFavorite
            sliderItem.mainItem.isFavorite = newValue
            updateFavoriteIcon(favoriteButton, newValue)
            MediaSliderConfiguration.onFavoriteToggle(sliderItem.mainItem.id, newValue)
        }

        externalPlayerButton?.setOnClickListener {
            openInExternalPlayer(sliderItem.url)
        }

        if (isVideo && muteButton != null && playPauseButton != null) {
            updateMuteIcon(muteButton, config.isVideoSoundEnable && (currentPlayer?.volume ?: 0f) > 0f)
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
                        if (player.playbackState == Player.STATE_ENDED ||
                            (player.contentDuration > 0 && player.currentPosition >= player.contentDuration)
                        ) {
                            player.seekToDefaultPosition()
                        }
                        player.play()
                    }
                }
            }

            seekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(seekBar: SeekBar) {
                    isSeeking = true
                    cancelTransportAutoHide()
                }

                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        currentPlayer?.seekTo(progress.toLong() * 1000)
                        scheduleTransportAutoHide()
                    }
                }

                override fun onStopTrackingTouch(seekBar: SeekBar) {
                    isSeeking = false
                    scheduleTransportAutoHide()
                }
            })
            seekBar?.setOnFocusChangeListener { _, hasFocus ->
                val color = if (hasFocus) 0xFFFFFFFF.toInt() else 0xFFBEBEBE.toInt()
                val sizeSp = if (hasFocus) 18f else 14f
                sharedControls.findViewById<TextView>(R.id.media_position)?.apply {
                    setTextColor(color)
                    textSize = sizeSp
                }
                sharedControls.findViewById<TextView>(R.id.media_duration)?.apply {
                    setTextColor(color)
                    textSize = sizeSp
                }
                if (hasFocus) cancelTransportAutoHide() else scheduleTransportAutoHide()
            }
        }

        setupFocusNavigation(
            previousButton,
            muteButton,
            playPauseButton,
            favoriteButton,
            slideshowButton,
            externalPlayerButton,
            nextButton,
            isVideo,
            canOpenExternally,
            hasSecondaryItem,
            seekBar
        )
    }

    fun syncMuteButton() {
        val muteButton = sharedControls.findViewById<ImageButton>(R.id.media_mute) ?: return
        updateMuteIcon(muteButton, config.isVideoSoundEnable)
    }

    fun toggleMute() {
        sharedControls.findViewById<ImageButton>(R.id.media_mute)?.performClick()
    }

    // -------------------------------------------------------------------------
    // Remote playback
    // -------------------------------------------------------------------------

    /**
     * Handles FF/RW (or opt-in D-pad seek) for video. A short press seeks
     * [REMOTE_SEEK_STEP_MS] on key-up; holding past [HOLD_SEEK_START_DELAY_MS] scrubs with
     * smaller steps and skips the initial 10s jump. Key-repeats are ignored.
     */
    fun onVideoSeekKeyDown(forward: Boolean, isRepeat: Boolean): Boolean {
        if (host.currentItemType() != SliderItemType.VIDEO) return false
        if (currentPlayer == null) return false
        if (isRepeat) return true
        pendingSeekTapForward = forward
        holdSeekEngaged = false
        val holdStep = if (forward) HOLD_SEEK_STEP_MS else -HOLD_SEEK_STEP_MS
        startHoldSeek(holdStep)
        return true
    }

    fun onSeekKeyUp(): Boolean {
        val tapForward = pendingSeekTapForward
        val engaged = holdSeekEngaged
        stopHoldSeek()
        if (!engaged && tapForward != null && host.currentItemType() == SliderItemType.VIDEO) {
            val tapDelta = if (tapForward) REMOTE_SEEK_STEP_MS else -REMOTE_SEEK_STEP_MS
            seekOrSkipBy(tapDelta, allowAssetSkip = true)
            return true
        }
        return engaged || tapForward != null
    }

    fun stopHoldSeek() {
        holdSeekStepMs = 0L
        pendingSeekTapForward = null
        holdSeekEngaged = false
        mainHandler.removeCallbacks(holdSeekRunnable)
        endHoldSeekScrub()
    }

    private fun startHoldSeek(stepMs: Long) {
        // Cancel pending ticks without endHoldSeekScrub() so a new hold doesn't resume early.
        holdSeekStepMs = 0L
        mainHandler.removeCallbacks(holdSeekRunnable)
        holdSeekStepMs = stepMs
        mainHandler.postDelayed(holdSeekRunnable, HOLD_SEEK_START_DELAY_MS)
    }

    @OptIn(UnstableApi::class)
    private fun beginHoldSeekScrub() {
        val player = currentPlayer ?: return
        if (!pausedForHoldSeek) {
            pausedForHoldSeek = player.isPlaying || player.playWhenReady
            if (pausedForHoldSeek) {
                player.pause()
            }
            player.setSeekParameters(SeekParameters.CLOSEST_SYNC)
        }
    }

    @OptIn(UnstableApi::class)
    private fun endHoldSeekScrub() {
        val player = currentPlayer
        if (player != null) {
            player.setSeekParameters(SeekParameters.DEFAULT)
            if (pausedForHoldSeek) {
                player.play()
            }
        }
        pausedForHoldSeek = false
        // Let Exo catch up; the next progress tick uses the real position.
        pendingSeekTargetMs = null
    }

    /**
     * Seeks within the current video. Asset skip at the edges only when [allowAssetSkip]
     * is true (fresh key press) — hold scrub stays inside the clip.
     */
    fun seekOrSkipBy(deltaMs: Long, allowAssetSkip: Boolean = true): Boolean {
        if (host.currentItemType() != SliderItemType.VIDEO) return false
        val player = currentPlayer ?: return false
        val duration = player.contentDuration.takeIf { it > 0 } ?: player.duration
        if (duration <= 0) return false
        val position = pendingSeekTargetMs ?: player.currentPosition
        if (deltaMs > 0) {
            if (allowAssetSkip && position >= duration - SEEK_EDGE_EPSILON_MS) {
                pendingSeekTargetMs = null
                cancelNextAssetTimer()
                goToNextAsset()
                return true
            }
            val target = (position + deltaMs).coerceAtMost(duration)
            pendingSeekTargetMs = target
            player.seekTo(target)
            return true
        }
        if (allowAssetSkip && position <= SEEK_EDGE_EPSILON_MS) {
            pendingSeekTargetMs = null
            cancelNextAssetTimer()
            goToPreviousAsset()
            return true
        }
        val target = (position + deltaMs).coerceAtLeast(0L)
        pendingSeekTargetMs = target
        player.seekTo(target)
        return true
    }

    fun togglePlayPause(): Boolean {
        if (host.currentItemType() != SliderItemType.VIDEO) return false
        val player = currentPlayer ?: return false
        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.contentDuration > 0 && player.currentPosition >= player.contentDuration) {
                player.seekToDefaultPosition()
            }
            player.play()
        }
        return true
    }

    fun playVideo(): Boolean {
        if (host.currentItemType() != SliderItemType.VIDEO) return false
        val player = currentPlayer ?: return false
        if (player.contentDuration > 0 && player.currentPosition >= player.contentDuration) {
            player.seekToDefaultPosition()
        }
        player.play()
        return true
    }

    fun pauseVideo(): Boolean {
        if (host.currentItemType() != SliderItemType.VIDEO) return false
        val player = currentPlayer ?: return false
        player.pause()
        return true
    }

    fun isRemoteSeekForward(keyCode: Int): Boolean =
        keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD ||
            keyCode == KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD ||
            keyCode == KeyEvent.KEYCODE_FORWARD

    fun isRemoteSeekRewind(keyCode: Int): Boolean =
        keyCode == KeyEvent.KEYCODE_MEDIA_REWIND ||
            keyCode == KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD

    // -------------------------------------------------------------------------
    // Player lifecycle / wake lock
    // -------------------------------------------------------------------------

    fun stopPlayer() {
        stopHoldSeek()
        pendingSeekTargetMs = null
        currentPlayer?.let {
            if (it.isPlaying || it.isLoading) it.stop()
        }
        if (host.currentItemType() == SliderItemType.VIDEO) {
            clearKeepScreenOnFlags()
        }
        stopProgressUpdates()
    }

    fun onDestroy() {
        stopHoldSeek()
        pendingSeekTargetMs = null
        currentPlayer?.release()
        currentPlayer = null
        clearKeepScreenOnFlags()
        mainHandler.removeCallbacks(goToNextAssetRunnable)
        cancelTransportAutoHide()
        stopProgressUpdates()
    }

    fun updateVideoRepeatMode() {
        currentPlayer?.repeatMode =
            if (slideShowPlaying) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ONE
    }

    fun setKeepScreenOnFlags() {
        if (context is Activity) {
            context.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            Log.d(TAG, "set FLAG_KEEP_SCREEN_ON")
        }
    }

    fun clearKeepScreenOnFlags() {
        if (context is Activity) {
            context.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            Log.d(TAG, "clear FLAG_KEEP_SCREEN_ON")
        }
    }

    fun restorePagerFocus() {
        // Don't steal focus from the shared bar (esp. while slideshow keeps it open).
        if (transportControlsVisible) return
        pager.post {
            if (!pager.hasFocus() && !transportControlsVisible) {
                pager.requestFocus()
            }
        }
    }

    private fun focusSharedTransport(isVideo: Boolean) {
        val target = if (isVideo) {
            sharedControls.findViewById<View>(R.id.media_play_pause)
                ?: sharedControls.findViewById<View>(R.id.media_seek_bar)
        } else {
            // Photos: land on slideshow (play/pause) rather than favorite.
            sharedControls.findViewById<View>(R.id.image_slideshow)
        }
        target?.requestFocus()
    }

    fun removeNextAssetCallbacks() {
        mainHandler.removeCallbacks(goToNextAssetRunnable)
    }

    fun transportHasFocus(): Boolean = sharedControls.findFocus() != null

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private fun applyTransportChrome(isVideo: Boolean) {
        val videoOnly = if (isVideo) View.VISIBLE else View.GONE
        sharedControls.findViewById<View>(R.id.media_mute)?.visibility = videoOnly
        sharedControls.findViewById<View>(R.id.media_play_pause)?.visibility = videoOnly
        sharedControls.findViewById<View>(R.id.media_progress_layout)?.visibility = videoOnly
        val favorite = sharedControls.findViewById<View>(R.id.image_favorite)
        if (favorite != null && !host.currentItem().hasSecondaryItem()) {
            favorite.visibility = View.VISIBLE
        }
    }

    private fun syncSlideshowButton() {
        val btn = sharedControls.findViewById<ImageButton>(R.id.image_slideshow) ?: return
        btn.setImageResource(
            if (slideShowPlaying) {
                android.R.drawable.ic_media_pause
            } else {
                R.drawable.slideshow
            }
        )
    }

    private fun setupFocusNavigation(
        previousButton: ImageButton,
        muteButton: ImageButton?,
        playPauseButton: ImageButton?,
        favoriteButton: ImageButton,
        slideshowButton: ImageButton,
        externalPlayerButton: ImageButton?,
        nextButton: ImageButton,
        isVideo: Boolean,
        canOpenExternally: Boolean,
        hasSecondaryItem: Boolean,
        seekBar: SeekBar?
    ) {
        val chain = mutableListOf<ImageButton>()
        if (isVideo) muteButton?.let { chain.add(it) }
        chain.add(previousButton)
        if (isVideo) playPauseButton?.let { chain.add(it) }
        if (!hasSecondaryItem) chain.add(favoriteButton)
        chain.add(slideshowButton)
        if (canOpenExternally) externalPlayerButton?.let { chain.add(it) }
        chain.add(nextButton)

        for (i in chain.indices) {
            if (i > 0) chain[i].nextFocusLeftId = chain[i - 1].id
            if (i < chain.size - 1) chain[i].nextFocusRightId = chain[i + 1].id
            chain[i].nextFocusDownId = View.NO_ID
            chain[i].nextFocusUpId = View.NO_ID
        }

        if (isVideo && seekBar != null) {
            chain.forEach { button -> button.nextFocusDownId = R.id.media_seek_bar }
            seekBar.nextFocusUpId = (playPauseButton ?: previousButton).id
        }
    }

    private fun showTemporaryPlayIndicator() {
        playButton.visibility = View.VISIBLE
        playButton.setBackgroundResource(
            if (slideShowPlaying) android.R.drawable.ic_media_play
            else android.R.drawable.ic_media_pause
        )
        mainHandler.postDelayed({ playButton.visibility = View.GONE }, 2000)
    }

    private fun updateFavoriteIcon(favoriteButton: ImageButton, isFavorite: Boolean) {
        favoriteButton.setImageResource(
            if (isFavorite) R.drawable.ic_favorite else R.drawable.ic_favorite_border
        )
    }

    private fun updateMuteIcon(button: ImageButton, soundEnabled: Boolean) {
        button.setImageResource(if (soundEnabled) R.drawable.unmute_icon else R.drawable.mute_icon)
    }

    private fun updatePlayPauseIcon(button: ImageButton, isPlaying: Boolean) {
        button.setImageResource(
            if (isPlaying) android.R.drawable.ic_media_pause
            else android.R.drawable.ic_media_play
        )
        button.contentDescription = context.getString(
            if (isPlaying) R.string.media_pause else R.string.media_play
        )
    }

    private fun openInExternalPlayer(videoUrl: String?) {
        if (videoUrl.isNullOrBlank()) return

        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(videoUrl), "video/*")
        }
        val chooserIntent = Intent.createChooser(
            viewIntent,
            context.getString(R.string.open_in_external_player)
        )
        if (context !is Activity) {
            chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(chooserIntent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, R.string.no_external_player_found, Toast.LENGTH_SHORT).show()
        }
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

        val seekBar = sharedControls.findViewById<SeekBar>(R.id.media_seek_bar) ?: return
        val positionText = sharedControls.findViewById<TextView>(R.id.media_position) ?: return
        val durationText = sharedControls.findViewById<TextView>(R.id.media_duration) ?: return

        val position = pendingSeekTargetMs ?: player.currentPosition
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
        return String.format(Locale.US, "%d:%02d", minutes, seconds)
    }

    companion object {
        /** Single-tap FF/RW jump. */
        const val REMOTE_SEEK_STEP_MS = 10_000L
        /** Delay after tap before continuous hold scrub starts. */
        private const val HOLD_SEEK_START_DELAY_MS = 400L
        /** Fewer, larger ticks reduce ExoPlayer rebuffer churn (~5× media speed). */
        private const val HOLD_SEEK_INTERVAL_MS = 400L
        private const val HOLD_SEEK_STEP_MS = 2_000L
        private const val SEEK_EDGE_EPSILON_MS = 750L
        private const val TRANSPORT_AUTO_HIDE_MS = 4_000L
        private const val METADATA_DIMMED_WHILE_TRANSPORT_ALPHA = 0.35f
        private const val PROGRESS_UPDATE_INTERVAL_MS = 500L
        private const val DETAILS_HOLDER_FALLBACK_MIN_HEIGHT_DP = 120f
        private const val TAG = "MediaSliderController"
    }
}
