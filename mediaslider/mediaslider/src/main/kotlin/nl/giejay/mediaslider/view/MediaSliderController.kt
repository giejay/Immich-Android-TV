package nl.giejay.mediaslider.view

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.core.view.isVisible
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerControlView
import androidx.viewpager.widget.ViewPager
import com.zeuskartik.mediaslider.R
import nl.giejay.mediaslider.config.MediaSliderConfiguration
import nl.giejay.mediaslider.model.SliderItem
import nl.giejay.mediaslider.model.SliderItemType
import nl.giejay.mediaslider.model.SliderItemViewHolder
import nl.giejay.mediaslider.util.MediaSliderListener

/**
 * Control logic for the layered details + transport overlay (shared [PlayerControlView]),
 * slideshow, image favorite strip, remote seek/play, and wake-lock.
 *
 * [MediaSliderView] owns the pager/metadata shell; timeline/memories chrome lives in
 * [TimelineSliderView].
 */
@OptIn(UnstableApi::class)
class MediaSliderController(
    private val context: Context,
    private val mainHandler: Handler,
    private val pager: ViewPager,
    private val playButton: View,
    private val videoControls: PlayerControlView,
    private val muteButton: ImageButton,
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

    var detailsOverlayVisible = false
        private set

    var hasBottomDetails = false

    var videoControllerVisible = false
        private set

    var imageTransportVisible = false
        private set

    var isImageControllerVisible = false
        private set

    var suppressTransportEnterUp = false

    val detailsOverlayToggleEnabled: Boolean
        get() = context !is MediaSliderListener

    val transportControlsVisible: Boolean
        get() = videoControllerVisible || imageTransportVisible

    val isControllerVisible: Boolean
        get() = videoControllerVisible || isImageControllerVisible

    /** Fired when the image slideshow timer is armed (interval milliseconds). */
    var onAssetTimerStarted: ((intervalMs: Long) -> Unit)? = null

    var onControllerVisibilityChanged: ((Boolean) -> Unit)? = null

    var onDetailsVisibilityChanged: ((Boolean) -> Unit)? = null

    private val goToNextAssetRunnable = Runnable { goToNextAsset() }
    private val hideTransportRunnable = Runnable { hideTransportControls() }

    fun initialize(config: MediaSliderConfiguration) {
        this.config = config
        detailsOverlayVisible = false
    }

    fun bindCurrentPlayer(player: ExoPlayer?) {
        currentPlayer = player
    }

    fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_ENDED && slideShowPlaying) {
            goToNextAsset()
        }
    }

    fun onIsPlayingChanged(isPlaying: Boolean) {
        videoControls.findViewById<ImageButton>(R.id.exo_pause)
            ?.setImageResource(
                if (isPlaying) R.drawable.exo_legacy_controls_pause
                else R.drawable.exo_legacy_controls_play
            )
        if (host.currentItemType() == SliderItemType.VIDEO) {
            if (isPlaying) setKeepScreenOnFlags() else clearKeepScreenOnFlags()
        }
    }

    fun onPlayerError(error: PlaybackException) {
        if (slideShowPlaying) {
            goToNextAsset()
        }
        if (host.currentItemType() == SliderItemType.VIDEO) {
            clearKeepScreenOnFlags()
        }
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
        hideImageController()
        if (pager.currentItem < pager.adapter!!.count - 1) {
            pager.setCurrentItem(pager.currentItem + 1, config.enableSlideAnimation)
        } else {
            pager.setCurrentItem(0, config.enableSlideAnimation)
        }
        restorePagerFocus()
    }

    fun goToPreviousAsset() {
        hideImageController()
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
        videoControls.player = player
        videoControls.showTimeoutMs = 0
        videoControls.show()
        wireSharedVideoControls()
        applyTransportChrome(isVideo = true)
        syncMuteButton()
        syncSlideshowButton()
        applyDetailsOverlayVisibility()
        scheduleTransportAutoHide()
        onControllerVisibilityChanged?.invoke(true)
        videoControls.post {
            val pause = videoControls.findViewById<View>(R.id.exo_pause)
            val progress = videoControls.findViewById<View>(R.id.exo_progress)
            val focusTarget = pause ?: progress
            focusTarget?.requestFocus()
            focusTarget?.invalidate()
        }
    }

    fun showImageTransportControls() {
        hideImageController()
        imageTransportVisible = true
        videoControllerVisible = false
        videoControls.player = null
        videoControls.showTimeoutMs = 0
        videoControls.show()
        wireSharedVideoControls()
        applyTransportChrome(isVideo = false)
        syncSlideshowButton()
        applyDetailsOverlayVisibility()
        scheduleTransportAutoHide()
        onControllerVisibilityChanged?.invoke(true)
        videoControls.post {
            videoControls.findViewById<View>(R.id.exo_slideshow)?.requestFocus()
        }
    }

    fun hideImageTransportControls() {
        if (!imageTransportVisible && videoControls.visibility != View.VISIBLE) return
        cancelTransportAutoHide()
        imageTransportVisible = false
        if (!videoControllerVisible) {
            videoControls.hide()
            videoControls.player = null
            onControllerVisibilityChanged?.invoke(false)
        }
        applyDetailsOverlayVisibility()
    }

    fun hideVideoController() {
        cancelTransportAutoHide()
        videoControllerVisible = false
        if (!imageTransportVisible) {
            videoControls.hide()
            videoControls.player = null
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

    /** Clear transport when paging without auto-surfacing a new strip. */
    fun dismissTransportForPageChange() {
        imageTransportVisible = false
        if (videoControllerVisible) {
            hideVideoController()
        } else {
            applyDetailsOverlayVisibility()
        }
    }

    fun scheduleTransportAutoHide() {
        mainHandler.removeCallbacks(hideTransportRunnable)
        mainHandler.postDelayed(hideTransportRunnable, TRANSPORT_AUTO_HIDE_MS)
    }

    fun cancelTransportAutoHide() {
        mainHandler.removeCallbacks(hideTransportRunnable)
    }

    fun applyDetailsOverlayVisibility() {
        val detailsOn = !detailsOverlayToggleEnabled || detailsOverlayVisible
        val showTransport = transportControlsVisible
        val showHolder = detailsOn || showTransport
        metaDataHolder.visibility =
            if (showHolder && (hasBottomDetails || showTransport)) View.VISIBLE else View.GONE
        videoControls.visibility = if (showTransport) View.VISIBLE else View.GONE
        if (showTransport) {
            applyTransportChrome(isVideo = videoControllerVisible)
        }
        metadataRows.visibility = if (detailsOn && hasBottomDetails) View.VISIBLE else View.GONE
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

        if (!detailsOn) {
            dateView.visibility = View.GONE
        } else if (dateView.text.isNotBlank()) {
            dateView.visibility = View.VISIBLE
        }
        if (showHolder && (hasBottomDetails || showTransport)) {
            if (!detailsOverlayToggleEnabled && config.isGradiantOverlayVisible && !showTransport) {
                metaDataHolder.setBackgroundResource(R.drawable.gradient_overlay)
            } else {
                metaDataHolder.setBackgroundResource(R.drawable.metadata_details_scrim)
            }
        }
    }

    fun wireSharedVideoControls() {
        videoControls.findViewById<ImageButton>(R.id.exo_pause)?.setOnClickListener {
            val player = currentPlayer ?: return@setOnClickListener
            if (player.isPlaying) {
                player.pause()
            } else {
                if (player.playbackState == Player.STATE_ENDED ||
                    player.currentPosition >= player.contentDuration
                ) {
                    player.seekToDefaultPosition()
                }
                player.play()
            }
        }
        muteButton.setOnClickListener {
            val player = currentPlayer ?: return@setOnClickListener
            if (player.volume == 0f) {
                player.volume = 1f
                config.isVideoSoundEnable = true
            } else {
                player.volume = 0f
                config.isVideoSoundEnable = false
            }
            syncMuteButton()
        }
        videoControls.findViewById<ImageButton>(R.id.exo_rewind)?.setOnClickListener {
            goToPreviousAsset()
        }
        videoControls.findViewById<ImageButton>(R.id.exo_forward)?.setOnClickListener {
            goToNextAsset()
        }
        videoControls.findViewById<ImageButton>(R.id.exo_slideshow)?.setOnClickListener {
            toggleSlideshow(showPlayIndicator = false)
            syncSlideshowButton()
        }
        val timeBar = videoControls.findViewById<View>(R.id.exo_progress)
        val positionView = videoControls.findViewById<TextView>(R.id.exo_position)
        val durationView = videoControls.findViewById<TextView>(R.id.exo_duration)
        timeBar?.setOnFocusChangeListener { _, hasFocus ->
            val color = if (hasFocus) 0xFFFFFFFF.toInt() else 0xFFBEBEBE.toInt()
            val sizeSp = if (hasFocus) 18f else 14f
            positionView?.setTextColor(color)
            durationView?.setTextColor(color)
            positionView?.textSize = sizeSp
            durationView?.textSize = sizeSp
        }
    }

    fun syncMuteButton() {
        muteButton.setImageResource(
            if (config.isVideoSoundEnable) R.drawable.unmute_icon else R.drawable.mute_icon
        )
    }

    fun toggleMute() {
        muteButton.performClick()
    }

    // -------------------------------------------------------------------------
    // Per-page image controller
    // -------------------------------------------------------------------------

    fun configureImageController(sliderItem: SliderItemViewHolder, sliderItemIndex: Int) {
        val imageRoot = pager.findViewWithTag<View>("view$sliderItemIndex") ?: run {
            isImageControllerVisible = false
            return
        }
        val imageController = imageRoot.findViewById<View>(R.id.image_controller) ?: run {
            isImageControllerVisible = false
            return
        }
        imageController.visibility = View.GONE
        isImageControllerVisible = false
        val previousButton = imageRoot.findViewById<ImageButton>(R.id.image_previous) ?: return
        val favoriteButton = imageRoot.findViewById<ImageButton>(R.id.image_favorite) ?: return
        val slideshowButton = imageRoot.findViewById<ImageButton>(R.id.image_slideshow) ?: return
        val nextButton = imageRoot.findViewById<ImageButton>(R.id.image_next) ?: return

        val hasSecondaryItem = sliderItem.hasSecondaryItem()
        favoriteButton.visibility = if (hasSecondaryItem) View.GONE else View.VISIBLE
        if (hasSecondaryItem) {
            previousButton.nextFocusRightId = R.id.image_slideshow
            slideshowButton.nextFocusLeftId = R.id.image_previous
        } else {
            previousButton.nextFocusRightId = R.id.image_favorite
            slideshowButton.nextFocusLeftId = R.id.image_favorite
        }

        updateFavoriteIcon(favoriteButton, sliderItem.mainItem.isFavorite)
        favoriteButton.setOnClickListener {
            val newFavoriteValue = !sliderItem.mainItem.isFavorite
            sliderItem.mainItem.isFavorite = newFavoriteValue
            updateFavoriteIcon(favoriteButton, newFavoriteValue)
            MediaSliderConfiguration.onFavoriteToggle(sliderItem.mainItem.id, newFavoriteValue)
        }
        previousButton.setOnClickListener { goToPreviousAsset() }
        slideshowButton.setOnClickListener { toggleSlideshow(true) }
        nextButton.setOnClickListener { goToNextAsset() }
    }

    fun toggleImageController(): Boolean {
        val imageRoot = pager.findViewWithTag<View>("view${pager.currentItem}") ?: return false
        val imageController = imageRoot.findViewById<View>(R.id.image_controller) ?: return false
        if (imageController.isVisible) {
            hideImageController()
            return true
        }
        if (slideShowPlaying) {
            toggleSlideshow(true)
        }
        hideImageTransportControls()
        imageController.visibility = View.VISIBLE
        isImageControllerVisible = true
        val sliderItem = host.currentItem()
        val initialFocusId = if (sliderItem.hasSecondaryItem()) R.id.image_slideshow else R.id.image_favorite
        imageRoot.findViewById<ImageButton>(initialFocusId)?.requestFocus()
        return true
    }

    fun hideImageController() {
        val imageRoot = pager.findViewWithTag<View>("view${pager.currentItem}") ?: run {
            isImageControllerVisible = false
            restorePagerFocus()
            return
        }
        imageRoot.findViewById<View>(R.id.image_controller)?.visibility = View.GONE
        isImageControllerVisible = false
        restorePagerFocus()
    }

    // -------------------------------------------------------------------------
    // Remote playback
    // -------------------------------------------------------------------------

    fun seekBy(deltaMs: Long): Boolean {
        if (host.currentItemType() != SliderItemType.VIDEO) return false
        val player = currentPlayer ?: return false
        val duration = player.contentDuration.takeIf { it > 0 } ?: player.duration
        if (duration <= 0) return false
        val target = (player.currentPosition + deltaMs).coerceIn(0L, duration)
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
        currentPlayer?.let {
            if (it.isPlaying || it.isLoading) it.stop()
        }
        if (host.currentItemType() == SliderItemType.VIDEO) {
            clearKeepScreenOnFlags()
        }
    }

    fun onDestroy() {
        currentPlayer?.release()
        currentPlayer = null
        clearKeepScreenOnFlags()
        mainHandler.removeCallbacks(goToNextAssetRunnable)
        cancelTransportAutoHide()
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
        pager.post {
            if (!pager.hasFocus()) {
                pager.requestFocus()
            }
        }
    }

    fun removeNextAssetCallbacks() {
        mainHandler.removeCallbacks(goToNextAssetRunnable)
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private fun applyTransportChrome(isVideo: Boolean) {
        val videoOnly = if (isVideo) View.VISIBLE else View.GONE
        muteButton.visibility = videoOnly
        videoControls.findViewById<View>(R.id.exo_rewind)?.visibility = videoOnly
        videoControls.findViewById<View>(R.id.exo_pause)?.visibility = videoOnly
        videoControls.findViewById<View>(R.id.exo_forward)?.visibility = videoOnly
        videoControls.findViewById<View>(R.id.exo_progress_layout)?.visibility = videoOnly
        val slideshow = videoControls.findViewById<View>(R.id.exo_slideshow) ?: return
        slideshow.visibility = View.VISIBLE
        if (isVideo) {
            slideshow.nextFocusLeftId = R.id.exo_forward
            slideshow.nextFocusDownId = R.id.exo_progress
        } else {
            slideshow.nextFocusLeftId = View.NO_ID
            slideshow.nextFocusDownId = View.NO_ID
        }
    }

    private fun syncSlideshowButton() {
        val btn = videoControls.findViewById<ImageButton>(R.id.exo_slideshow) ?: return
        btn.setImageResource(
            if (slideShowPlaying) {
                android.R.drawable.ic_media_pause
            } else {
                android.R.drawable.ic_media_play
            }
        )
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

    companion object {
        const val REMOTE_SEEK_STEP_MS = 10_000L
        private const val TRANSPORT_AUTO_HIDE_MS = 4_000L
        private const val METADATA_DIMMED_WHILE_TRANSPORT_ALPHA = 0.35f
        private const val TAG = "MediaSliderController"
    }
}
