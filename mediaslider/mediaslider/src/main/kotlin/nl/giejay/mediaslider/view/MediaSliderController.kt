package nl.giejay.mediaslider.view

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.viewpager.widget.ViewPager
import com.zeuskartik.mediaslider.R
import nl.giejay.mediaslider.config.MediaSliderConfiguration
import nl.giejay.mediaslider.controller.ControllerFocusRequest
import nl.giejay.mediaslider.controller.ControllerLayout
import nl.giejay.mediaslider.controller.SliderControllerUiState
import nl.giejay.mediaslider.model.SliderItemType
import nl.giejay.mediaslider.model.SliderItemViewHolder
import nl.giejay.mediaslider.plugin.ControllerPluginContext
import nl.giejay.mediaslider.plugin.SliderKeyEventResult
import nl.giejay.mediaslider.plugin.SliderKeyEventState
import nl.giejay.mediaslider.util.MediaSliderListener
import timber.log.Timber

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
    private val controllerRootView: View,
    /** State of the Compose controller bar (see [SliderControllerBar]); owned by [MediaSliderView]. */
    private val uiState: SliderControllerUiState
) {
    private lateinit var config: MediaSliderConfiguration

    var slideShowPlaying: Boolean = false
        private set

    private var _isControllerVisible = false
    val isControllerVisible: Boolean get() = _isControllerVisible

    /** Currently active ExoPlayer (for the video page that is in view). */
    var currentPlayer: ExoPlayer? = null
        private set

    private var reverseSlideShowDirection = false
    private val goToNextAssetRunnable = Runnable { goToNextAsset() }
    private val goToPreviousAssetRunnable = Runnable { goToPreviousAsset() }

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
            if(reverseSlideShowDirection) {
                goToPreviousAsset()
            }
            else{
                goToNextAsset()
            }
        }
    }

    fun onIsPlayingChanged(isPlaying: Boolean) {
        uiState.isPlaying = isPlaying

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

    fun toggleSlideshow(showPlayIndicator: Boolean, reverseSlideShowDirection: Boolean = false) {
        slideShowPlaying = !slideShowPlaying
        this.reverseSlideShowDirection = reverseSlideShowDirection
        val itemType = currentItemTypeOrNull()
        if (slideShowPlaying) {
            if (itemType == SliderItemType.IMAGE) {
                startTimerNextAsset()
            }
            config.controllerPlugins.forEach {
                it.onSlideshowStarted(itemType, this, config, context, mainHandler)
            }
            setKeepScreenOnFlags()
        } else {
            clearKeepScreenOnFlags()
            removeGoToAssetCallbackFromMainHandler()
            config.controllerPlugins.forEach { it.onSlideShowStopped(this, mainHandler) }
        }
        if (showPlayIndicator) {
            showTemporaryPlayIndicator()
        }
    }

    fun startTimerNextAsset() {
        removeGoToAssetCallbackFromMainHandler()
        val intervalMs = (config.interval * 1000).toLong()
        if(reverseSlideShowDirection) {
            mainHandler.postDelayed(goToPreviousAssetRunnable, intervalMs)
        }
        else{
            mainHandler.postDelayed(goToNextAssetRunnable, intervalMs)
        }
        config.controllerPlugins.forEach {
            it.onAssetTimerStarted(intervalMs, this, config, context, mainHandler)
        }
    }
    fun removeGoToAssetCallbackFromMainHandler(){
        if(reverseSlideShowDirection) {
            mainHandler.removeCallbacks(goToPreviousAssetRunnable)
        }
        else{
            mainHandler.removeCallbacks(goToNextAssetRunnable)
        }
    }

    /** Cancels any pending auto-advance timer without starting a new one. */
    fun cancelNextAssetTimer() {
        removeGoToAssetCallbackFromMainHandler()
        config.controllerPlugins.forEach { it.onSlideShowStopped(this, mainHandler) }
    }

    /** Cancels any pending auto-advance and immediately moves to the next asset. */
    fun skipToNextAndRestartTimer() {
        removeGoToAssetCallbackFromMainHandler()
        goToNextAsset()
    }

    /** Cancels any pending auto-advance and immediately moves to the previous asset. */
    fun skipToPreviousAndRestartTimer() {
        removeGoToAssetCallbackFromMainHandler()
        goToPreviousAsset()
    }

    // -------------------------------------------------------------------------
    // Navigation
    // -------------------------------------------------------------------------

    fun goToNextAsset() {
        if (pager.currentItem < pager.adapter!!.count - 1) {
            pager.setCurrentItem(pager.currentItem + 1, config.enableSlideAnimation)
        } else {
            pager.setCurrentItem(0, config.enableSlideAnimation)
        }
        // Keep controller focus if the overlay is open (e.g. mid D-pad to pause during slideshow).
        if (!isControllerVisible) {
            restorePagerFocus()
        }
    }

    fun goToPreviousAsset() {
        pager.setCurrentItem(
            (if (0 == pager.currentItem) pager.adapter!!.count else pager.currentItem) - 1,
            config.enableSlideAnimation
        )
        if (!isControllerVisible) {
            restorePagerFocus()
        }
    }

    // -------------------------------------------------------------------------
    // Unified controller show / hide / configure
    // -------------------------------------------------------------------------

    /**
     * Toggles the unified overlay controller for the current page.
     * Returns true if the event was consumed (controller shown or already visible).
     */
    private fun toggleOverlayControls(): Boolean {
        if (_isControllerVisible) {
            hideOverlayControls()
            return true
        }
        uiState.visible = true
        _isControllerVisible = true
        config.controllerPlugins.forEach { it.onControllerVisibilityChanged(true, controllerRootView, this, config) }

        val isVideo = currentItemTypeOrNull() == SliderItemType.VIDEO
        uiState.focusRequest = ControllerFocusRequest(ControllerLayout.initialFocus(isVideo, reverseSlideShowDirection))

        if (isVideo) {
            startProgressUpdates()
        }
        return true
    }

    fun hideOverlayControls() {
        uiState.visible = false
        _isControllerVisible = false
        config.controllerPlugins.forEach { it.onControllerVisibilityChanged(false, controllerRootView, this, config) }
        stopProgressUpdates()
        restorePagerFocus()
    }

    /**
     * Wires up all button listeners for the unified controller embedded in the
     * view at [sliderItemIndex]. Resets visibility of video-only widgets based
     * on [sliderItem]. If the controller was already showing, it stays showing
     * so D-pad navigation is not interrupted when the page changes.
     */
    fun configureController(sliderItem: SliderItemViewHolder, sliderItemIndex: Int) {
        val keepVisible = _isControllerVisible
        val previousFocusId = uiState.focusedId
        val previousFocusIndex = uiState.focusedIndex

        stopProgressUpdates()

        val isVideo = sliderItem.type == SliderItemType.VIDEO
        val hasSecondaryItem = sliderItem.hasSecondaryItem()

        uiState.isVideo = isVideo
        uiState.onPrevious = { goToPreviousAsset() }
        uiState.onNext = { goToNextAsset() }
        uiState.onSlideshow = { toggleSlideshow(true, false) }
        uiState.onSlideshowReverse = { toggleSlideshow(true, true) }
        uiState.isPlaying = currentPlayer?.isPlaying == true
        uiState.positionMs = 0L
        uiState.durationMs = 0L

        if (isVideo) {
            uiState.soundOn = (currentPlayer?.volume ?: 0f) > 0f
            uiState.onMute = {
                currentPlayer?.let { player ->
                    val soundOn = player.volume == 0f
                    player.volume = if (soundOn) 1f else 0f
                    config.isVideoSoundEnable = soundOn
                    uiState.soundOn = soundOn
                }
            }
            uiState.onPlayPause = {
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
            uiState.onSeekTo = { positionMs -> currentPlayer?.seekTo(positionMs) }
        } else {
            uiState.onMute = {}
            uiState.onPlayPause = {}
            uiState.onSeekTo = {}
        }

        val pluginContext = ControllerPluginContext(
            context = context,
            rootView = controllerRootView,
            config = config,
            sliderItem = sliderItem,
            sliderItemIndex = sliderItemIndex,
            isVideo = isVideo,
            hasSecondaryItem = hasSecondaryItem,
            controller = this
        )

        // Plugins contribute buttons for this page; placement is resolved by ControllerLayout.
        uiState.pluginButtons = config.controllerPlugins.mapNotNull { it.provideControllerButton(pluginContext) }
        config.controllerPlugins.forEach { it.onConfigureController(pluginContext) }

        if (keepVisible) {
            uiState.visible = true
            _isControllerVisible = true
            config.controllerPlugins.forEach {
                it.onControllerVisibilityChanged(true, controllerRootView, this, config)
            }
            val chain = ControllerLayout.focusChain(isVideo, uiState.pluginButtons)
            uiState.focusRequest = ControllerFocusRequest(
                ControllerLayout.restoreFocus(previousFocusId, previousFocusIndex, chain, isVideo, reverseSlideShowDirection)
            )
            if (isVideo) {
                startProgressUpdates()
            }
        } else {
            uiState.visible = false
            _isControllerVisible = false
        }
    }

    // -------------------------------------------------------------------------
    // Mute helper (called from volume-change broadcast receiver)
    // -------------------------------------------------------------------------

    fun toggleMute() {
        uiState.onMute()
    }

    fun dispatchKeyEvent(event: KeyEvent, superDispatch: () -> Boolean): Boolean {
        val itemType = currentItemTypeOrNull()
        val pluginState = SliderKeyEventState(
            isControllerVisible = isControllerVisible,
            isSlideshowPlaying = slideShowPlaying,
            currentItemType = itemType,
            controller = this,
            config = config
        )
        if (event.action == KeyEvent.ACTION_UP) {
            var pluginHandled = false
            config.keyEventPlugins.forEach { plugin ->
                when (plugin.onKeyUp(event, pluginState)) {
                    SliderKeyEventResult.UNHANDLED -> Unit
                    SliderKeyEventResult.HANDLED_CONTINUE -> pluginHandled = true
                    SliderKeyEventResult.HANDLED_CONSUME -> return true
                    SliderKeyEventResult.DISPATCH_TO_SUPER -> return superDispatch()
                }
            }
            if (pluginHandled) {
                return true
            }
        }
        if (event.action == KeyEvent.ACTION_DOWN) {
            var pluginHandled = false
            config.keyEventPlugins.forEach { plugin ->
                when (plugin.onKeyDown(event, pluginState)) {
                    SliderKeyEventResult.UNHANDLED -> Unit
                    SliderKeyEventResult.HANDLED_CONTINUE -> pluginHandled = true
                    SliderKeyEventResult.HANDLED_CONSUME -> return true
                    SliderKeyEventResult.DISPATCH_TO_SUPER -> return superDispatch()
                }
            }
            if (pluginHandled) {
                return true
            }

            if (context is MediaSliderListener && (context as MediaSliderListener).onButtonPressed(event)) {
                return false
            } else if (event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || event.keyCode == KeyEvent.KEYCODE_ENTER
                || event.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY
                || event.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            ) {
                // If the unified controller is already visible, let focused buttons handle the click.
                if (isControllerVisible) {
                    return superDispatch()
                }
                if (toggleOverlayControls()) {
                    return true
                }
                return false
            } else if (event.keyCode == KeyEvent.KEYCODE_BACK && isControllerVisible) {
                hideOverlayControls()
                return true
            } else if (event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN && !isControllerVisible) {
                // Down-arrow on a video opens the unified controller.
                if (toggleOverlayControls()) return true
                return superDispatch()
            } else if (slideShowPlaying && itemType == SliderItemType.IMAGE) {
                // Controller overlay is up: D-pad must move focus, not pause / skip slides.
                if (isControllerVisible) {
                    return superDispatch()
                }
                if (event.keyCode != KeyEvent.KEYCODE_DPAD_RIGHT) {
                    toggleSlideshow(true)
                } else {
                    skipToNextAndRestartTimer()
                    return false
                }
                return superDispatch()
            } else if (event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (isControllerVisible) {
                    return superDispatch()
                }
                goToNextAsset()
                return false
            } else if (event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (isControllerVisible) {
                    return superDispatch()
                }
                goToPreviousAsset()
                return false
            }
        }
        return if (isControllerVisible) superDispatch() else false
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
        if(this::config.isInitialized){
            config.controllerPlugins.forEach { it.onDestroy(this) }
        }
        currentPlayer?.release()
        currentPlayer = null
        clearKeepScreenOnFlags()
        removeGoToAssetCallbackFromMainHandler()
        stopProgressUpdates()
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private fun showTemporaryPlayIndicator() {
        playButtonView.visibility = View.VISIBLE
        playButtonView.setBackgroundResource(
            if (slideShowPlaying) R.drawable.indicator_play
            else R.drawable.indicator_pause
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
        uiState.durationMs = duration
        uiState.positionMs = player.currentPosition
    }

    fun setKeepScreenOnFlags() {
        if (context is Activity) {
            val window = context.window
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            Timber.d("set FLAG_KEEP_SCREEN_ON")
        }
    }

    fun clearKeepScreenOnFlags() {
        if (context is Activity) {
            val window = context.window
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            Timber.d("clear FLAG_KEEP_SCREEN_ON")
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

    private companion object {
        const val PROGRESS_UPDATE_INTERVAL_MS = 500L
    }
}
