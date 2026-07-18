package nl.giejay.mediaslider.view

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.view.children
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
import nl.giejay.mediaslider.model.SliderItemType
import nl.giejay.mediaslider.model.SliderItemViewHolder
import nl.giejay.mediaslider.plugin.ControllerButtonPlacement
import nl.giejay.mediaslider.plugin.ControllerPluginContext
import nl.giejay.mediaslider.plugin.SliderKeyEventResult
import nl.giejay.mediaslider.plugin.SliderKeyEventState
import nl.giejay.mediaslider.util.MediaSliderListener
import timber.log.Timber
import java.util.Locale
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

    /** Enter toggles details overlay (viewer); screensaver keeps details always-on. */
    val detailsOverlayToggleEnabled: Boolean
        get() = context !is MediaSliderListener

    var detailsOverlayVisible: Boolean = false

    var hasBottomDetails: Boolean = false

    /** Currently active ExoPlayer (for the video page that is in view). */
    var currentPlayer: ExoPlayer? = null
        private set

    /** True while the user is actively dragging the seek bar. */
    private var isSeeking = false

    /** Accumulates rapid seek steps while ExoPlayer's reported position lags. */
    private var pendingSeekTargetMs: Long? = null

    /** Non-zero while a seek key is held; drives the slow continuous scrub ticks. */
    private var holdSeekStepMs: Long = 0L
    private var holdSeekEngaged = false
    private var pendingSeekTapForward: Boolean? = null
    private var pausedForHoldSeek = false

    private val goToNextAssetRunnable = Runnable { goToNextAsset() }

    private val hideTransportRunnable = Runnable { hideOverlayControls() }

    private val progressUpdateRunnable = object : Runnable {
        override fun run() {
            updateVideoProgress()
            if (currentPlayer?.isPlaying == true) {
                mainHandler.postDelayed(this, PROGRESS_UPDATE_INTERVAL_MS)
            }
        }
    }

    private val holdSeekRunnable = object : Runnable {
        override fun run() {
            if (holdSeekStepMs == 0L) return
            holdSeekEngaged = true
            pendingSeekTapForward = null
            beginHoldSeekScrub()
            seekOrSkipBy(holdSeekStepMs, allowAssetSkip = false)
            mainHandler.postDelayed(this, HOLD_SEEK_TICK_MS)
        }
    }

    // -------------------------------------------------------------------------
    // Initialisation
    // -------------------------------------------------------------------------

    fun initialize(config: MediaSliderConfiguration) {
        this.config = config
        detailsOverlayVisible = false
        hasBottomDetails = config.metaDataConfig.any { it.type != nl.giejay.mediaslider.model.MetaDataType.DATE }
    }

    fun setCurrentPlayer(player: ExoPlayer?) {
        stopHoldSeek()
        pendingSeekTargetMs = null
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
            mainHandler.removeCallbacks(goToNextAssetRunnable)
            config.controllerPlugins.forEach { it.onSlideShowStopped(this, mainHandler) }
        }
        if (showPlayIndicator) {
            showTemporaryPlayIndicator()
        }
    }

    fun startTimerNextAsset() {
        mainHandler.removeCallbacks(goToNextAssetRunnable)
        val intervalMs = (config.interval * 1000).toLong()
        mainHandler.postDelayed(goToNextAssetRunnable, intervalMs)
        config.controllerPlugins.forEach {
            it.onAssetTimerStarted(intervalMs, this, config, context, mainHandler)
        }
    }

    /** Cancels any pending auto-advance timer without starting a new one. */
    fun cancelNextAssetTimer() {
        mainHandler.removeCallbacks(goToNextAssetRunnable)
        config.controllerPlugins.forEach { it.onSlideShowStopped(this, mainHandler) }
    }

    /** Cancels any pending auto-advance and immediately moves to the next asset. */
    fun skipToNextAndRestartTimer() {
        mainHandler.removeCallbacks(goToNextAssetRunnable)
        goToNextAsset()
    }

    /** Cancels any pending auto-advance and immediately moves to the previous asset. */
    fun skipToPreviousAndRestartTimer() {
        mainHandler.removeCallbacks(goToNextAssetRunnable)
        goToPreviousAsset()
    }

    // -------------------------------------------------------------------------
    // Navigation
    // -------------------------------------------------------------------------

    fun goToNextAsset() {
        hideOverlayControls()
        if (pager.currentItem < pager.adapter!!.count - 1) {
            pager.setCurrentItem(pager.currentItem + 1, config.enableSlideAnimation)
        } else {
            pager.setCurrentItem(0, config.enableSlideAnimation)
        }
        restorePagerFocus()
    }

    fun goToPreviousAsset() {
        hideOverlayControls()
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
    private fun toggleOverlayControls(): Boolean {
        val controller = controllerRootView.findViewById<View>(R.id.image_controller) ?: return false
        if (controller.isVisible) {
            hideOverlayControls()
            return true
        }
        return showOverlayControlsPublic()
    }

    /** Shows the unified transport bar (and marks details visible for layered overlay). */
    fun showOverlayControlsPublic(): Boolean {
        val controller = controllerRootView.findViewById<View>(R.id.image_controller) ?: return false
        if (slideShowPlaying) {
            toggleSlideshow(true)
        }
        if (detailsOverlayToggleEnabled) {
            detailsOverlayVisible = true
        }
        controller.visibility = View.VISIBLE
        _isControllerVisible = true
        config.controllerPlugins.forEach { it.onControllerVisibilityChanged(true, controllerRootView, this, config) }

        requestInitialControllerFocus()
        scheduleTransportAutoHide()

        if (currentItemTypeOrNull() == SliderItemType.VIDEO) {
            startProgressUpdates()
        }
        return true
    }

    fun hideOverlayControls() {
        cancelTransportAutoHide()
        controllerRootView.findViewById<View>(R.id.image_controller)?.visibility = View.GONE
        _isControllerVisible = false
        config.controllerPlugins.forEach { it.onControllerVisibilityChanged(false, controllerRootView, this, config) }
        stopProgressUpdates()
        restorePagerFocus()
    }

    fun scheduleTransportAutoHide() {
        mainHandler.removeCallbacks(hideTransportRunnable)
        mainHandler.postDelayed(hideTransportRunnable, TRANSPORT_AUTO_HIDE_MS)
    }

    fun cancelTransportAutoHide() {
        mainHandler.removeCallbacks(hideTransportRunnable)
    }

    fun transportHasFocus(): Boolean =
        controllerRootView.findViewById<View>(R.id.image_controller)?.findFocus() != null

    fun mediaConfigOrNull(): MediaSliderConfiguration? =
        if (::config.isInitialized) config else null

    fun controllerRootViewPublic(): View = controllerRootView

    fun currentSliderItemOrNull(): SliderItemViewHolder? {
        if (!::config.isInitialized || config.items.isEmpty()) return null
        val idx = pager.currentItem
        return if (idx in config.items.indices) config.items[idx] else null
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
        config.controllerPlugins.forEach { it.onControllerVisibilityChanged(false, controllerRootView, this,config) }
        stopProgressUpdates()

        val previousButton = controllerRootView.findViewById<ImageButton>(R.id.image_previous) ?: return
        val slideshowButton = controllerRootView.findViewById<ImageButton>(R.id.image_slideshow) ?: return
        val nextButton = controllerRootView.findViewById<ImageButton>(R.id.image_next) ?: return
        val muteButton = controllerRootView.findViewById<ImageButton>(R.id.media_mute)
        val playPauseButton = controllerRootView.findViewById<ImageButton>(R.id.media_play_pause)
        val progressLayout = controllerRootView.findViewById<View>(R.id.media_progress_layout)
        val seekBar = controllerRootView.findViewById<SeekBar>(R.id.media_seek_bar)
        val buttonRow = controllerRootView.findViewById<LinearLayout>(R.id.media_controller_button_row)

        // clear any existing plugin buttons from the previous page before adding new ones for this page
        buttonRow.children.filter { it.tag == "controller_plugin_button" }.forEach { buttonRow.removeView(it) }

        val isVideo = sliderItem.type == SliderItemType.VIDEO
        val hasSecondaryItem = sliderItem.hasSecondaryItem()

        previousButton.setOnClickListener { goToPreviousAsset() }
        slideshowButton.setOnClickListener { toggleSlideshow(true) }
        nextButton.setOnClickListener { goToNextAsset() }

        muteButton?.visibility = if (isVideo) View.VISIBLE else View.GONE
        playPauseButton?.visibility = if (isVideo) View.VISIBLE else View.GONE
        progressLayout?.visibility = if (isVideo) View.VISIBLE else View.GONE

        if (isVideo) {
            val soundEnabled = (currentPlayer?.volume ?: 0f) > 0f
            muteButton?.let { button ->
                updateMuteIcon(button, soundEnabled)
                button.setOnClickListener {
                    currentPlayer?.let { player ->
                        if (player.volume == 0f) {
                            player.volume = 1f
                            config.isVideoSoundEnable = true
                            updateMuteIcon(button, true)
                        } else {
                            player.volume = 0f
                            config.isVideoSoundEnable = false
                            updateMuteIcon(button, false)
                        }
                    }
                }
            }

            playPauseButton?.let { button ->
                updatePlayPauseIcon(button, currentPlayer?.isPlaying == true)
                button.setOnClickListener {
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
            }
            seekBar?.setOnSeekBarChangeListener(defaultSeekBarChangeListener())
        } else {
            seekBar?.setOnSeekBarChangeListener(null)
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

        // Let plugins add/remove optional controls for this page before configuring behavior.
        config.controllerPlugins.forEach { plugin ->
            val buttonSpec = plugin.provideControllerButton(pluginContext) ?: return@forEach
            placeMediaControllerButton(
                buttonSpec.button,
                buttonSpec.placement,
                buttonSpec.anchorViewId
            )
        }
        config.controllerPlugins.forEach { it.onConfigureController(pluginContext) }

        setupFocusNavigation(buttonRow, seekBar, isVideo, playPauseButton, previousButton)
    }

    // -------------------------------------------------------------------------
    // Mute helper (called from volume-change broadcast receiver)
    // -------------------------------------------------------------------------

    fun toggleMute() {
        controllerRootView.findViewById<ImageButton>(R.id.media_mute)?.performClick()
    }

    fun dispatchKeyEvent(event: KeyEvent, superDispatch: () -> Boolean): Boolean {
        val itemType = currentItemTypeOrNull()
        if (event.action == KeyEvent.ACTION_UP) {
            if (isRemoteSeekForward(event.keyCode) || isRemoteSeekRewind(event.keyCode) ||
                (config.dpadSeeksInVideo && itemType == SliderItemType.VIDEO &&
                    (event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT || event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT))
            ) {
                return onSeekKeyUp()
            }
        }
        if (event.action == KeyEvent.ACTION_DOWN) {
            val pluginState = SliderKeyEventState(
                isControllerVisible = isControllerVisible,
                isSlideshowPlaying = slideShowPlaying,
                currentItemType = itemType,
                controller = this
            )
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
                // Photos: media play/pause toggles slideshow; Enter still opens overlay.
                if (itemType == SliderItemType.IMAGE &&
                    (event.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY ||
                        event.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                ) {
                    toggleSlideshow(true)
                    return true
                }
                if (itemType == SliderItemType.VIDEO &&
                    event.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                ) {
                    return togglePlayPause()
                }
                if (toggleOverlayControls()) {
                    return true
                }
                return false
            } else if (event.keyCode == KeyEvent.KEYCODE_BACK && isControllerVisible) {
                hideOverlayControls()
                return true
            } else if (event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN && !isControllerVisible) {
                // Down-arrow opens the unified controller.
                if (toggleOverlayControls()) return true
                return superDispatch()
            } else if (itemType == SliderItemType.VIDEO && isRemoteSeekForward(event.keyCode)) {
                return onVideoSeekKeyDown(forward = true, isRepeat = event.repeatCount > 0)
            } else if (itemType == SliderItemType.VIDEO && isRemoteSeekRewind(event.keyCode)) {
                return onVideoSeekKeyDown(forward = false, isRepeat = event.repeatCount > 0)
            } else if (slideShowPlaying && itemType == SliderItemType.IMAGE) {
                if (event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    skipToNextAndRestartTimer()
                    return false
                }
                // Back exits the viewer — don't pause the slideshow (or flash the center glyph) first.
                if (event.keyCode != KeyEvent.KEYCODE_BACK) {
                    toggleSlideshow(true)
                }
                return superDispatch()
            } else if (event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (isControllerVisible) {
                    return superDispatch()
                }
                if (itemType == SliderItemType.VIDEO && config.dpadSeeksInVideo) {
                    return onVideoSeekKeyDown(forward = true, isRepeat = event.repeatCount > 0)
                }
                goToNextAsset()
                return false
            } else if (event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (isControllerVisible) {
                    return superDispatch()
                }
                if (itemType == SliderItemType.VIDEO && config.dpadSeeksInVideo) {
                    return onVideoSeekKeyDown(forward = false, isRepeat = event.repeatCount > 0)
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
        stopHoldSeek()
        pendingSeekTargetMs = null
        currentPlayer?.let {
            if (it.isPlaying || it.isLoading) it.stop()
        }
        clearKeepScreenOnFlags()
        stopProgressUpdates()
    }

    fun onDestroy() {
        stopHoldSeek()
        pendingSeekTargetMs = null
        config.controllerPlugins.forEach { it.onDestroy(this) }
        currentPlayer?.release()
        currentPlayer = null
        clearKeepScreenOnFlags()
        mainHandler.removeCallbacks(goToNextAssetRunnable)
        stopProgressUpdates()
    }

    // -------------------------------------------------------------------------
    // Remote FF/RW / opt-in D-pad seek (from feat/mediaslider-controls)
    // -------------------------------------------------------------------------

    fun onVideoSeekKeyDown(forward: Boolean, isRepeat: Boolean): Boolean {
        if (currentItemTypeOrNull() != SliderItemType.VIDEO) return false
        if (currentPlayer == null) return false
        if (isRepeat) return true
        pendingSeekTapForward = forward
        holdSeekEngaged = false
        startHoldSeek(if (forward) HOLD_SEEK_STEP_MS else -HOLD_SEEK_STEP_MS)
        return true
    }

    fun onSeekKeyUp(): Boolean {
        val tapForward = pendingSeekTapForward
        val engaged = holdSeekEngaged
        stopHoldSeek()
        if (!engaged && tapForward != null && currentItemTypeOrNull() == SliderItemType.VIDEO) {
            seekOrSkipBy(
                if (tapForward) REMOTE_SEEK_STEP_MS else -REMOTE_SEEK_STEP_MS,
                allowAssetSkip = true
            )
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
        pendingSeekTargetMs = null
    }

    fun seekOrSkipBy(deltaMs: Long, allowAssetSkip: Boolean = true): Boolean {
        if (currentItemTypeOrNull() != SliderItemType.VIDEO) return false
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
        if (currentItemTypeOrNull() != SliderItemType.VIDEO) return false
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

    fun isRemoteSeekForward(keyCode: Int): Boolean =
        keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD ||
            keyCode == KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD ||
            keyCode == KeyEvent.KEYCODE_FORWARD

    fun isRemoteSeekRewind(keyCode: Int): Boolean =
        keyCode == KeyEvent.KEYCODE_MEDIA_REWIND ||
            keyCode == KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private fun setupFocusNavigation(
        buttonRow: LinearLayout?,
        seekBar: SeekBar?,
        isVideo: Boolean,
        playPauseButton: ImageButton?,
        fallbackUpButton: ImageButton
    ) {
        val row = buttonRow ?: return
        val chain = mutableListOf<ImageButton>()
        for (i in 0 until row.childCount) {
            val view = row.getChildAt(i)
            if (view is ImageButton && view.isVisible) {
                chain.add(view)
            }
        }

        for (i in chain.indices) {
            if (i > 0) chain[i].nextFocusLeftId = chain[i - 1].id
            if (i < chain.size - 1) chain[i].nextFocusRightId = chain[i + 1].id
        }

        // Wire up/down navigation between the buttons row and the seekbar
        if (isVideo && seekBar != null && chain.isNotEmpty()) {
            chain.forEach { button -> button.nextFocusDownId = R.id.media_seek_bar }
            seekBar.nextFocusUpId = (playPauseButton ?: fallbackUpButton).id
        }
    }

    private fun findChildIndexById(row: LinearLayout, viewId: Int): Int {
        for (i in 0 until row.childCount) {
            if (row.getChildAt(i).id == viewId) return i
        }
        return -1
    }

    private fun requestInitialControllerFocus() {
        val row = controllerRootView.findViewById<LinearLayout>(R.id.media_controller_button_row) ?: return
        val isVideo = currentItemTypeOrNull() == SliderItemType.VIDEO
        if (isVideo) {
            val playPause = controllerRootView.findViewById<ImageButton>(R.id.media_play_pause)
            if (playPause?.visibility == View.VISIBLE) {
                playPause.requestFocus()
                return
            }
        }
        val slideshow = controllerRootView.findViewById<ImageButton>(R.id.image_slideshow)
        if (!isVideo && slideshow?.visibility == View.VISIBLE) {
            slideshow.requestFocus()
            return
        }
        for (i in 0 until row.childCount) {
            val child = row.getChildAt(i)
            if (child is ImageButton && child.isVisible) {
                child.requestFocus()
                return
            }
        }
    }

    fun updateMuteIcon(button: ImageButton, soundEnabled: Boolean) {
        button.setImageResource(if (soundEnabled) R.drawable.unmute_icon else R.drawable.mute_icon)
    }

    fun updatePlayPauseIcon(button: ImageButton, isPlaying: Boolean) {
        button.setImageResource(
            if (isPlaying) android.R.drawable.ic_media_pause
            else android.R.drawable.ic_media_play
        )
    }

    fun defaultSeekBarChangeListener(): SeekBar.OnSeekBarChangeListener {
        return object : SeekBar.OnSeekBarChangeListener {
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
        }
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

    private fun placeMediaControllerButton(button: ImageButton, placement: ControllerButtonPlacement, anchorViewId: Int?) {
        val row = controllerRootView.findViewById<LinearLayout>(R.id.media_controller_button_row) ?: return
        if (button.parent !== row) {
            (button.parent as? LinearLayout)?.removeView(button)
            row.addView(button)
        }
        val currentIndex = row.indexOfChild(button)
        if (currentIndex >= 0) {
            row.removeViewAt(currentIndex)
        }
        val targetIndex = when (placement) {
            ControllerButtonPlacement.START -> 0
            ControllerButtonPlacement.END -> row.childCount
            ControllerButtonPlacement.LEFT_OF -> {
                val anchorIndex = anchorViewId?.let { findChildIndexById(row, it) } ?: -1
                if (anchorIndex >= 0) anchorIndex else row.childCount
            }
            ControllerButtonPlacement.RIGHT_OF -> {
                val anchorIndex = anchorViewId?.let { findChildIndexById(row, it) } ?: -1
                if (anchorIndex >= 0) (anchorIndex + 1).coerceAtMost(row.childCount) else row.childCount
            }
        }
        row.addView(button, targetIndex)
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
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
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
        const val TRANSPORT_AUTO_HIDE_MS = 4_000L
        const val REMOTE_SEEK_STEP_MS = 10_000L
        const val HOLD_SEEK_STEP_MS = 2_000L
        const val HOLD_SEEK_START_DELAY_MS = 400L
        const val HOLD_SEEK_TICK_MS = 200L
        const val SEEK_EDGE_EPSILON_MS = 500L
    }
}
