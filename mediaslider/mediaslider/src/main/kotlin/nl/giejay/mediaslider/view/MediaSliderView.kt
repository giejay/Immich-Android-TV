package nl.giejay.mediaslider.view

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.children
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerControlView
import androidx.media3.ui.PlayerView
import androidx.viewpager.widget.ViewPager
import com.google.common.collect.Iterables
import com.google.common.collect.Lists
import com.zeuskartik.mediaslider.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.giejay.mediaslider.adapter.AlignOption
import nl.giejay.mediaslider.adapter.MetaDataAdapter
import nl.giejay.mediaslider.adapter.MetaDataClock
import nl.giejay.mediaslider.adapter.MetaDataMediaCount
import nl.giejay.mediaslider.adapter.ScreenSlidePagerAdapter
import nl.giejay.mediaslider.config.MediaSliderConfiguration
import nl.giejay.mediaslider.model.MetaDataType
import nl.giejay.mediaslider.model.SliderItem
import nl.giejay.mediaslider.model.SliderItemType
import nl.giejay.mediaslider.model.SliderItemViewHolder
import nl.giejay.mediaslider.util.FixedSpeedScroller
import nl.giejay.mediaslider.util.MediaSliderListener
import timber.log.Timber
import java.lang.reflect.Field


@OptIn(UnstableApi::class)
class MediaSliderView(context: Context) : ConstraintLayout(context) {
    // view elements
    private var playButton: View
    private var mainHandler: Handler
    private var mPager: ViewPager
    private lateinit var dateView: TextView
    private lateinit var metaDataHolder: FrameLayout
    private lateinit var metadataRows: LinearLayout
    private lateinit var videoControls: PlayerControlView
    private lateinit var muteButton: ImageButton
    private lateinit var storyProgress: StoryProgressView
    private lateinit var storyProgressRow: View
    private lateinit var storyProgressCount: TextView
    private var storyProgressEnabled = false
    private var storyProgressAnimator: ValueAnimator? = null
    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == "android.media.VOLUME_CHANGED_ACTION") {
                if (currentItemType() == SliderItemType.VIDEO && currentPlayerInScope?.isPlaying == true && currentPlayerInScope?.volume == 0f) {
                    Timber.i("Volume changed detected, unmuting video")
                    Toast.makeText(context, "Volume changed detected, unmuting video", Toast.LENGTH_SHORT).show()
                    muteButton.performClick()
                }
            }
        }
    }

    // config
    private lateinit var config: MediaSliderConfiguration
    private lateinit var metaDataLeftAdapter: MetaDataAdapter
    private lateinit var metaDataRightAdapter: MetaDataAdapter

    /// internal
    private var currentPlayerInScope: ExoPlayer? = null
    private var currentPlayerView: PlayerView? = null
    private var defaultExoFactory = DefaultHttpDataSource.Factory()
    private var slideShowPlaying = false
    private val goToNextAssetRunnable = Runnable { this.goToNextAsset() }
    private var pagerAdapter: ScreenSlidePagerAdapter? = null
    private var loading = false
    private val ioScope = CoroutineScope(Job() + Dispatchers.IO)
    private val transformResults = mutableMapOf<Int, String>()
    private var currentToast: Toast? = null
    private var detailsOverlayVisible = false
    private var pendingDateAssetId: String? = null
    private var hasBottomDetails = false
    private var videoControllerVisible = false
    /** Photo transport strip (slideshow button); independent of details so Back can dismiss it first. */
    private var imageTransportVisible = false
    /** Ignore Enter/Center ACTION_UP after opening transport so focus doesn't "click" the button. */
    private var suppressTransportEnterUp = false

    private val transportControlsVisible: Boolean
        get() = videoControllerVisible || imageTransportVisible

    /** YouTube-style hide of play/slideshow chrome after idle; EXIF/details stay up. */
    private val hideTransportRunnable = Runnable { hideTransportControls() }

    private val videoStoryProgressRunnable = object : Runnable {
        override fun run() {
            if (!storyProgressEnabled || !slideShowPlaying) return
            if (currentItemType() != SliderItemType.VIDEO) return
            val player = currentPlayerInScope ?: return
            val duration = player.duration
            if (duration > 0) {
                updateStoryProgressChrome(
                    mPager.currentItem,
                    player.currentPosition.toFloat() / duration.toFloat()
                )
            }
            mainHandler.postDelayed(this, 50)
        }
    }

    /**
     * Viewer: center opens a details overlay. Screensaver (MediaSliderListener) keeps
     * always-on metadata and uses center to exit the dream instead.
     */
    private val detailsOverlayToggleEnabled: Boolean
        get() = context !is MediaSliderListener

    init {
        inflate(getContext(), R.layout.slider, this)

        playButton = findViewById(R.id.playPause)
        playButton.setOnClickListener { toggleSlideshow(true) }
        mPager = findViewById(R.id.pager)
        dateView = findViewById(R.id.metadata_date)
        metaDataHolder = findViewById(R.id.meta_data_holder)
        metadataRows = findViewById(R.id.metadata_rows)
        videoControls = findViewById(R.id.slider_video_controls)
        muteButton = videoControls.findViewById(R.id.exo_mute)
        storyProgress = findViewById(R.id.story_progress)
        storyProgressRow = findViewById(R.id.story_progress_row)
        storyProgressCount = findViewById(R.id.story_progress_count)
        mainHandler = Handler(Looper.getMainLooper())
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
        context.registerReceiver(volumeReceiver, filter)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cancelTransportAutoHide()
        stopStoryProgressAnimation()
        context.unregisterReceiver(volumeReceiver)
    }

    /**
     * Immich Memories: always-on segmented progress over the media (above the date overlay).
     * Call before [toggleSlideshow] when opening a memory autoplay session.
     */
    fun setStoryProgressEnabled(enabled: Boolean) {
        storyProgressEnabled = enabled
        if (!enabled) {
            stopStoryProgressAnimation()
            storyProgressRow.visibility = GONE
            return
        }
        if (::config.isInitialized) {
            updateStoryProgressChrome(mPager.currentItem.coerceAtLeast(0), progress = 0f)
        }
        storyProgressRow.visibility = VISIBLE
    }

    private fun updateStoryProgressChrome(index: Int, progress: Float) {
        if (!storyProgressEnabled || !::config.isInitialized) return
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

    @OptIn(UnstableApi::class)
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (mPager.adapter == null) {
            return super.dispatchKeyEvent(event)
        }
        val itemType = currentItemType()
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (transportControlsVisible) {
                scheduleTransportAutoHide()
            }
            if (context is MediaSliderListener && (context as MediaSliderListener).onButtonPressed(event)) {
                return false
            } else if (event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || event.keyCode == KeyEvent.KEYCODE_ENTER) {
                if (detailsOverlayToggleEnabled) {
                    // Focused transport control (video or image slideshow): activate it.
                    if (videoControls.findFocus() != null) {
                        return super.dispatchKeyEvent(event)
                    }
                    // Details already open: bring transport back (same layered model as video).
                    if (detailsOverlayVisible && itemType == SliderItemType.IMAGE && !imageTransportVisible) {
                        showImageTransportControls()
                        suppressTransportEnterUp = true
                        return true
                    }
                    if (detailsOverlayVisible && itemType == SliderItemType.VIDEO &&
                        currentPlayerInScope != null && !videoControllerVisible
                    ) {
                        showVideoController()
                        suppressTransportEnterUp = true
                        return true
                    }
                    showDetailsOverlay()
                    if (itemType == SliderItemType.VIDEO && currentPlayerInScope != null) {
                        showVideoController()
                        suppressTransportEnterUp = true
                    } else if (itemType == SliderItemType.IMAGE) {
                        showImageTransportControls()
                        suppressTransportEnterUp = true
                    }
                    return true
                }
                if (itemType == SliderItemType.IMAGE) {
                    toggleSlideshow(true)
                    return false
                }
                if (currentPlayerView != null) {
                    return super.dispatchKeyEvent(event)
                }
                return false
            } else if (event.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY || event.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
                if (itemType == SliderItemType.IMAGE) {
                    toggleSlideshow(true)
                } else if (currentPlayerView != null) {
                    return super.dispatchKeyEvent(event)
                }
                return false
            } else if (event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN && itemType == SliderItemType.VIDEO && currentPlayerInScope != null) {
                if (!videoControllerVisible) {
                    showVideoController()
                }
                return super.dispatchKeyEvent(event)
            } else if (event.keyCode == KeyEvent.KEYCODE_BACK && videoControllerVisible) {
                hideVideoController()
                return true
            } else if (event.keyCode == KeyEvent.KEYCODE_BACK && imageTransportVisible) {
                hideImageTransportControls()
                return true
            } else if (event.keyCode == KeyEvent.KEYCODE_BACK && detailsOverlayToggleEnabled && detailsOverlayVisible) {
                detailsOverlayVisible = false
                hideImageTransportControls()
                hideVideoController()
                applyDetailsOverlayVisibility()
                return true
            } else if (slideShowPlaying && itemType == SliderItemType.IMAGE) {
                // Keep autoplay running for Left/Right so memories (and regular slideshow)
                // can step either direction without an accidental pause.
                when (event.keyCode) {
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        mainHandler.removeCallbacks(goToNextAssetRunnable)
                        stopStoryProgressAnimation()
                        goToNextAsset()
                        return false
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        mainHandler.removeCallbacks(goToNextAssetRunnable)
                        stopStoryProgressAnimation()
                        goToPreviousAsset()
                        return false
                    }
                    else -> {
                        // Back exits the viewer — don't flash the center pause glyph first.
                        if (event.keyCode != KeyEvent.KEYCODE_BACK) {
                            toggleSlideshow(true)
                        }
                        return super.dispatchKeyEvent(event)
                    }
                }
            } else if (event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (videoControllerVisible) {
                    return super.dispatchKeyEvent(event)
                }
                if (imageTransportVisible) {
                    // Soft-trap on photo slideshow control; Left/Right change assets only after Back.
                    return true
                }
                goToNextAsset()
                return false
            } else if (event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (videoControllerVisible) {
                    return super.dispatchKeyEvent(event)
                }
                if (imageTransportVisible) {
                    return true
                }
                goToPreviousAsset()
                return false
            }
        } else if (event.action == KeyEvent.ACTION_UP &&
            (event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || event.keyCode == KeyEvent.KEYCODE_ENTER)
        ) {
            if (suppressTransportEnterUp) {
                suppressTransportEnterUp = false
                return true
            }
        }
        // Forward remaining events to transport controls when they have focus.
        if (videoControls.findFocus() != null || videoControllerVisible) {
            return super.dispatchKeyEvent(event)
        }
        return if (itemType == SliderItemType.IMAGE) false else super.dispatchKeyEvent(event)
    }

    private fun showDetailsOverlay() {
        if (detailsOverlayVisible) return
        // Keep slideshow running (memories autoplay included). Metadata is batched per asset
        // so opening details no longer needs to pause to avoid overlay flicker.
        detailsOverlayVisible = true
        applyDetailsOverlayVisibility()
        refreshOverlayMetadata()
        metaDataLeftAdapter.bind()
        metaDataRightAdapter.bind()
        syncSlideshowButton()
    }

    @OptIn(UnstableApi::class)
    private fun showVideoController() {
        val player = currentPlayerInScope ?: return
        videoControllerVisible = true
        imageTransportVisible = false
        // Open the shared details strip so controls and metadata share one scrim.
        if (detailsOverlayToggleEnabled && !detailsOverlayVisible) {
            detailsOverlayVisible = true
            updateDateOverlay(currentItem().mainItem)
        }
        videoControls.player = player
        // Auto-hide is handled by [scheduleTransportAutoHide]; keep PlayerControlView sticky.
        videoControls.showTimeoutMs = 0
        videoControls.show()
        // PlayerControlView rebinds standard controls when player is set — re-apply ours after.
        wireSharedVideoControls()
        applyTransportChrome(isVideo = true)
        syncMuteButton()
        syncSlideshowButton()
        applyDetailsOverlayVisibility()
        scheduleTransportAutoHide()
        videoControls.post {
            val pause = videoControls.findViewById<View>(R.id.exo_pause)
            val progress = videoControls.findViewById<View>(R.id.exo_progress)
            val focusTarget = pause ?: progress
            focusTarget?.requestFocus()
            focusTarget?.invalidate()
        }
    }

    /** Photos: same overlay strip, only the slideshow control (no mute/seek chrome). */
    private fun showImageTransportControls() {
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
        videoControls.post {
            videoControls.findViewById<View>(R.id.exo_slideshow)?.requestFocus()
        }
    }

    private fun hideImageTransportControls() {
        if (!imageTransportVisible && videoControls.visibility != VISIBLE) return
        cancelTransportAutoHide()
        imageTransportVisible = false
        if (!videoControllerVisible) {
            videoControls.hide()
            videoControls.player = null
        }
        applyDetailsOverlayVisibility()
    }

    private fun hideTransportControls() {
        when {
            videoControllerVisible -> hideVideoController()
            imageTransportVisible -> hideImageTransportControls()
        }
    }

    private fun scheduleTransportAutoHide() {
        mainHandler.removeCallbacks(hideTransportRunnable)
        mainHandler.postDelayed(hideTransportRunnable, TRANSPORT_AUTO_HIDE_MS)
    }

    private fun cancelTransportAutoHide() {
        mainHandler.removeCallbacks(hideTransportRunnable)
    }

    private fun applyTransportChrome(isVideo: Boolean) {
        val videoOnly = if (isVideo) VISIBLE else GONE
        muteButton.visibility = videoOnly
        videoControls.findViewById<View>(R.id.exo_rewind)?.visibility = videoOnly
        videoControls.findViewById<View>(R.id.exo_pause)?.visibility = videoOnly
        videoControls.findViewById<View>(R.id.exo_forward)?.visibility = videoOnly
        videoControls.findViewById<View>(R.id.exo_progress_layout)?.visibility = videoOnly
        val slideshow = videoControls.findViewById<View>(R.id.exo_slideshow) ?: return
        slideshow.visibility = VISIBLE
        if (isVideo) {
            slideshow.nextFocusLeftId = R.id.exo_forward
            slideshow.nextFocusDownId = R.id.exo_progress
        } else {
            slideshow.nextFocusLeftId = View.NO_ID
            slideshow.nextFocusDownId = View.NO_ID
        }
    }

    @OptIn(UnstableApi::class)
    private fun hideVideoController() {
        cancelTransportAutoHide()
        videoControllerVisible = false
        if (!imageTransportVisible) {
            videoControls.hide()
            videoControls.player = null
        }
        applyDetailsOverlayVisibility()
    }

    private fun applyDetailsOverlayVisibility() {
        val detailsOn = !detailsOverlayToggleEnabled || detailsOverlayVisible
        val showTransport = transportControlsVisible
        val showHolder = detailsOn || showTransport
        metaDataHolder.visibility =
            if (showHolder && (hasBottomDetails || showTransport)) VISIBLE else GONE
        videoControls.visibility = if (showTransport) VISIBLE else GONE
        if (showTransport) {
            applyTransportChrome(isVideo = videoControllerVisible)
        }
        metadataRows.visibility = if (detailsOn && hasBottomDetails) VISIBLE else GONE
        // Soften EXIF while transport is up so times / scrubber stay readable over it.
        metadataRows.alpha = if (showTransport) METADATA_DIMMED_WHILE_TRANSPORT_ALPHA else 1f
        // Keep D-pad on transport controls; metadata rows would otherwise steal focus.
        metadataRows.descendantFocusability =
            if (showTransport) ViewGroup.FOCUS_BLOCK_DESCENDANTS
            else ViewGroup.FOCUS_AFTER_DESCENDANTS
        findViewById<LinearLayout>(R.id.metadata_view_left)?.apply {
            isFocusable = false
            isFocusableInTouchMode = false
        }
        findViewById<LinearLayout>(R.id.metadata_view_right)?.apply {
            isFocusable = false
            isFocusableInTouchMode = false
        }

        if (!detailsOn) {
            dateView.visibility = GONE
        } else if (dateView.text.isNotBlank()) {
            dateView.visibility = VISIBLE
        }
        if (showHolder && (hasBottomDetails || showTransport)) {
            if (!detailsOverlayToggleEnabled && config.isGradiantOverlayVisible && !showTransport) {
                metaDataHolder.setBackgroundResource(R.drawable.gradient_overlay)
            } else {
                metaDataHolder.setBackgroundResource(R.drawable.metadata_details_scrim)
            }
        }
    }

    private fun wireSharedVideoControls() {
        videoControls.findViewById<ImageButton>(R.id.exo_pause)?.setOnClickListener {
            val player = currentPlayerInScope ?: return@setOnClickListener
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
            val player = currentPlayerInScope ?: return@setOnClickListener
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
            toggleSlideshow(togglePlayButton = false)
            syncSlideshowButton()
        }
        val timeBar = videoControls.findViewById<View>(R.id.exo_progress)
        val positionView = videoControls.findViewById<TextView>(R.id.exo_position)
        val durationView = videoControls.findViewById<TextView>(R.id.exo_duration)
        timeBar?.setOnFocusChangeListener { _, hasFocus ->
            // Match transport-button focus: brighten times so scrub mode is obvious.
            val color = if (hasFocus) 0xFFFFFFFF.toInt() else 0xFFBEBEBE.toInt()
            val sizeSp = if (hasFocus) 18f else 14f
            positionView?.setTextColor(color)
            durationView?.setTextColor(color)
            positionView?.textSize = sizeSp
            durationView?.textSize = sizeSp
        }
    }

    private fun syncMuteButton() {
        muteButton.setImageResource(
            if (config.isVideoSoundEnable) R.drawable.unmute_icon else R.drawable.mute_icon
        )
    }

    private fun goToPreviousAsset() {
        mPager.setCurrentItem((if (0 == mPager.currentItem) mPager.adapter!!.count else mPager.currentItem) - 1,
            config.enableSlideAnimation)
    }

    fun loadMediaSliderView(config: MediaSliderConfiguration) {
        this.config = config
        detailsOverlayVisible = false

        // Date is reserved for the top-left chrome; exclude it from the bottom lists.
        val detailsConfig = config.metaDataConfig.filterNot { it.type == MetaDataType.DATE }
        hasBottomDetails = detailsConfig.isNotEmpty()

        val columnRight = findViewById<LinearLayout>(R.id.metadata_view_right)
        metaDataRightAdapter = MetaDataAdapter(context,
            detailsConfig.filter { it.align == AlignOption.RIGHT },
            detailsConfig.map { it.withAlign(align = AlignOption.RIGHT) }.distinct(),
            { if (currentItem().hasSecondaryItem()) currentItem().secondaryItem!! else currentItem().mainItem },
            { currentItem().hasSecondaryItem() })
        metaDataRightAdapter.attach(columnRight)

        val columnLeft = findViewById<LinearLayout>(R.id.metadata_view_left)
        metaDataLeftAdapter = MetaDataAdapter(context,
            detailsConfig.filter { it.align == AlignOption.LEFT },
            // don't show the clock/media count twice in portrait mode and force everything to be left aligned
            detailsConfig.filterNot { it is MetaDataClock || it is MetaDataMediaCount }
                .map { it.withAlign(align = AlignOption.LEFT) }.distinct(),
            { currentItem().mainItem },
            { currentItem().hasSecondaryItem() })
        metaDataLeftAdapter.attach(columnLeft)

        wireSharedVideoControls()
        applyDetailsOverlayVisibility()

        val listener: ExoPlayerListener = object : ExoPlayerListener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED && slideShowPlaying) {
                    goToNextAsset()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                val playPauseButton = videoControls.findViewById<ImageButton>(R.id.exo_pause)
                playPauseButton?.setImageResource(if (isPlaying) R.drawable.exo_legacy_controls_pause else R.drawable.exo_legacy_controls_play)

                // Handle screen wake lock for video playback
                if (currentItemType() == SliderItemType.VIDEO) {
                    if (isPlaying) {
                        setKeepScreenOnFlags()
                    } else {
                        clearKeepScreenOnFlags()
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (slideShowPlaying) {
                    goToNextAsset()
                }
                // Clear screen wake lock on error
                if (currentItemType() == SliderItemType.VIDEO) {
                    clearKeepScreenOnFlags()
                }
            }
        }
        initViewsAndSetAdapter(listener)
    }

    private fun setStartPosition() {
        if (config.startPosition >= 0) {
            if (config.startPosition > config.items.size) {
                mPager.currentItem = (config.items.size - 1)
                return
            }
            mPager.currentItem = config.startPosition
        } else {
            mPager.currentItem = 0
        }
        mPager.offscreenPageLimit = 1
    }

    fun toggleSlideshow(togglePlayButton: Boolean) {
        slideShowPlaying = !slideShowPlaying
        updateVideoRepeatMode()
        if (slideShowPlaying) {
            // do not start timers for videos, they will continue in the player listener
            if (currentItemType() == SliderItemType.IMAGE) {
                startTimerNextAsset()
            } else if (currentItemType() == SliderItemType.VIDEO) {
                // Resuming slideshow on a finished video: start playback / advance on end.
                val player = currentPlayerInScope
                if (player?.playbackState == Player.STATE_ENDED) {
                    player.seekTo(0)
                }
                player?.playWhenReady = true
                startVideoStoryProgress()
            }
            setKeepScreenOnFlags()
        } else {
            clearKeepScreenOnFlags()
            mainHandler.removeCallbacks(goToNextAssetRunnable)
            pauseStoryProgress()
            // Resuming manual viewing: restart if the video had already finished
            if (currentItemType() == SliderItemType.VIDEO &&
                currentPlayerInScope?.playbackState == Player.STATE_ENDED
            ) {
                currentPlayerInScope?.seekTo(0)
                currentPlayerInScope?.play()
            }
        }
        syncSlideshowButton()
        if (togglePlayButton) {
            togglePlayButton()
        }
    }

    /** Play = slideshow stopped (tap to start); Pause = slideshow running (tap to stop). */
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

    private fun updateVideoRepeatMode() {
        currentPlayerInScope?.repeatMode =
            if (slideShowPlaying) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ONE
    }

    private fun togglePlayButton() {
        playButton.visibility = VISIBLE
        playButton.setBackgroundResource(if (slideShowPlaying) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause)
        mainHandler.postDelayed({
            playButton.visibility = GONE
        }, 2000)
    }

    private fun startTimerNextAsset() {
        mainHandler.removeCallbacks(goToNextAssetRunnable)
        val durationMs = (config.interval * 1000).toLong()
        startImageStoryProgress(durationMs)
        mainHandler.postDelayed(goToNextAssetRunnable, durationMs)
    }

    private fun startImageStoryProgress(durationMs: Long) {
        if (!storyProgressEnabled) return
        stopStoryProgressAnimation()
        val index = mPager.currentItem
        updateStoryProgressChrome(index, progress = 0f)
        storyProgressAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                updateStoryProgressChrome(index, anim.animatedValue as Float)
            }
            start()
        }
    }

    private fun startVideoStoryProgress() {
        if (!storyProgressEnabled || !slideShowPlaying) return
        mainHandler.removeCallbacks(videoStoryProgressRunnable)
        updateStoryProgressChrome(mPager.currentItem, progress = 0f)
        mainHandler.post(videoStoryProgressRunnable)
    }

    private fun pauseStoryProgress() {
        storyProgressAnimator?.pause()
        mainHandler.removeCallbacks(videoStoryProgressRunnable)
    }

    private fun stopStoryProgressAnimation() {
        storyProgressAnimator?.cancel()
        storyProgressAnimator = null
        mainHandler.removeCallbacks(videoStoryProgressRunnable)
    }

    private fun goToNextAsset() {
        if (mPager.currentItem < mPager.adapter!!.count - 1) {
            mPager.setCurrentItem(mPager.currentItem + 1, config.enableSlideAnimation)
        } else {
            mPager.setCurrentItem(0, config.enableSlideAnimation)
        }
    }

    private fun initViewsAndSetAdapter(listener: ExoPlayerListener) {
        pagerAdapter = ScreenSlidePagerAdapter(
            context,
            config.items,
            config,
            { mPager.currentItem },
            { result, position -> transformResults[position] = result },
            listener,
            restorePagerIndex = { index ->
                val target = index.coerceIn(0, (config.items.size - 1).coerceAtLeast(0))
                if (mPager.currentItem != target) {
                    mPager.setCurrentItem(target, false)
                }
            }
        )

        try {
            if (config.enableSlideAnimation && config.animationSpeedMillis > 0) {
                val mScroller: Field = ViewPager::class.java.getDeclaredField("mScroller")
                mScroller.isAccessible = true
                val scroller = FixedSpeedScroller(mPager.context, DecelerateInterpolator(0.75F), config.animationSpeedMillis)
                // scroller.setFixedDuration(5000);
                mScroller.set(mPager, scroller)
            }
        } catch (e: Exception) {
            Timber.e(e, "Could not set scroller")
        }

        mPager.setAdapter(pagerAdapter)
        setStartPosition()
        mPager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrolled(sliderItemIndex: Int, v: Float, i1: Int) {
                if (config.loadMore != null && mPager.currentItem > config.items.size - 40 && !loading) {
                    loading = true

                    ioScope.launch {
                        val nextItems = config.loadMore!!.invoke()
                        addItemsMain(nextItems)
                        // keep loading until no more items are received, so set it to false if there are items
                        loading = nextItems.isEmpty()
                    }
                }
                stopPlayer()
                if (sliderItemIndex != mPager.currentItem) {
                    return
                }
                val sliderItem = config.items[sliderItemIndex]
                val mainItem = sliderItem.mainItem

                currentToast?.cancel()
                if (!sliderItem.hasSecondaryItem() && config.debugEnabled && transformResults.contains(sliderItemIndex)) {
                    currentToast = Toast.makeText(context, transformResults[sliderItemIndex], Toast.LENGTH_LONG)
                    currentToast!!.show()
                    transformResults.remove(sliderItemIndex)
                }

                if (sliderItem.type == SliderItemType.VIDEO) {
                    // Photos use the interval timer; videos play to the end then advance.
                    mainHandler.removeCallbacks(goToNextAssetRunnable)
                    if (!detailsOverlayToggleEnabled && config.isGradiantOverlayVisible && !videoControllerVisible) {
                        metaDataHolder.background = null
                    }
                    val viewTag = mPager.findViewWithTag<ExoPlayerView>("view$sliderItemIndex") ?: return
                    if (!viewTag.isReady()) {
                        Timber.e("Player is not initialized properly, cannot play video.")
                        Toast.makeText(context, "Player is not initialized properly, cannot play video.", Toast.LENGTH_LONG).show()
                        return
                    }
                    // Thumbnail over black surface until ExoPlayer paints the first frame.
                    viewTag.showLoadingPoster(mainItem.thumbnailUrl)
                    currentPlayerView = viewTag.getPlayerView()
                    currentPlayerInScope = viewTag.getPlayer()
                    currentPlayerInScope!!.seekTo(0, 0)
                    // Immich loops the current video while browsing; slideshow advances on end instead.
                    updateVideoRepeatMode()
                    if (currentPlayerInScope!!.playbackState == Player.STATE_IDLE && sliderItem.url != null) {
                        prepareMedia(sliderItem.url!!,
                            currentPlayerInScope!!, defaultExoFactory)
                    }
                    if (!config.isVideoSoundEnable) {
                        currentPlayerInScope!!.volume = 0f
                    }
                    syncMuteButton()
                    // Keep details if open, but never auto-surface transport when paging.
                    imageTransportVisible = false
                    if (videoControllerVisible) {
                        hideVideoController()
                    } else {
                        applyDetailsOverlayVisibility()
                    }
                    currentPlayerInScope!!.playWhenReady = true
                    if (slideShowPlaying) {
                        startVideoStoryProgress()
                    }
                } else {
                    // Keep details if open; dismiss any transport so Left/Right stay on assets.
                    hideVideoController()
                    hideImageTransportControls()
                    if (!detailsOverlayToggleEnabled && config.isGradiantOverlayVisible) {
                        metaDataHolder.setBackgroundResource(R.drawable.gradient_overlay)
                    } else if (detailsOverlayToggleEnabled) {
                        metaDataHolder.setBackgroundResource(R.drawable.metadata_details_scrim)
                    }
                    if (slideShowPlaying) {
                        startTimerNextAsset()
                        val viewTag = mPager.findViewWithTag<RelativeLayout>("view$sliderItemIndex") ?: return
                        val touchImageView = viewTag.children.first() as? TouchImageView
                        if (touchImageView != null && config.zoomAndScrollPanorama && config.interval >= 10 && mainItem.isPanorama) {
                            touchImageView.zoomAndScrollPanorama(config, sliderItem)
                        } else if (touchImageView != null && config.zoomAndScrollPanorama && !mainItem.isPanorama) {
                            touchImageView.zoomAndPanEffect(config, sliderItem)
                        }
                    }
                    stopPlayer()
                }
            }

            override fun onPageSelected(i: Int) {
                // Settled page only — onPageScrolled often skips while currentItem has already
                // advanced during animated Left/Right, which left timeline leave-off stuck on open.
                if (i in config.items.indices) {
                    config.onAssetSelected(config.items[i])
                }
                // Load/bind metadata here (once per page), not from onPageScrolled which fires
                // continuously and previously stamped null states after failed/aborted storms.
                refreshOverlayMetadata()
                metaDataLeftAdapter.bind()
                metaDataRightAdapter.bind()
                if (storyProgressEnabled) {
                    updateStoryProgressChrome(i, progress = 0f)
                }
            }

            override fun onPageScrollStateChanged(i: Int) {
            }
        })
        // Listener is registered after setCurrentItem, so the start page never receives
        // onPageSelected — load its metadata explicitly.
        refreshOverlayMetadata()
    }

    private fun refreshOverlayMetadata() {
        if (config.items.isEmpty() || mPager.adapter == null) return
        val index = mPager.currentItem.coerceIn(0, config.items.lastIndex)
        val sliderItem = config.items[index]
        val mainItem = sliderItem.mainItem
        updateMetaData(metaDataLeftAdapter, mainItem, index)
        updateMetaData(
            metaDataRightAdapter,
            if (sliderItem.hasSecondaryItem()) sliderItem.secondaryItem!! else mainItem,
            index
        )
        updateDateOverlay(mainItem)
    }

    private fun updateMetaData(adapter: MetaDataAdapter, sliderItem: SliderItem, sliderItemIndex: Int) {
        val items = adapter.getItemsToShow()
        if (items.isEmpty()) return
        // One batch per asset: parallel per-field fetches stamped blank DESCRIPTION before CITY
        // arrived, which collapsed the bottom strip (slideshow button dropped) then flickered.
        if (adapter.isFullyFetched(sliderItem.id)) return
        ioScope.launch {
            try {
                val values = items.map { item ->
                    item.getValue(context, sliderItem, sliderItemIndex, config.items.size)
                }
                withContext(Dispatchers.Main) {
                    if (!currentItem().ids().contains(sliderItem.id)) return@withContext
                    values.forEachIndexed { i, value ->
                        adapter.updateState(sliderItem.id, i, value)
                    }
                    adapter.bind()
                    if (detailsOverlayVisible) {
                        applyDetailsOverlayVisibility()
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to load metadata for %s", sliderItem.id)
            }
        }
    }

    fun onDestroy() {
        if (currentPlayerInScope != null) {
            currentPlayerInScope!!.release()
        }
        clearKeepScreenOnFlags()
        mainHandler.removeCallbacks(goToNextAssetRunnable)
    }

    private fun clearKeepScreenOnFlags() {
        if (context is Activity) {
            // view is being triggered from main app, remove the flags to keep screen on
            val window = (context as Activity).window
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            Log.d("MediaSliderView", "clear FLAG_KEEP_SCREEN_ON")
        }
    }

    private fun setKeepScreenOnFlags() {
        if (context is Activity) {
            // view is being triggered from main app, prevent app going to sleep
            val window = (context as Activity).window
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            Log.d("MediaSliderView", "set FLAG_KEEP_SCREEN_ON")
        }
    }

    private fun stopPlayer() {
        if (currentPlayerInScope != null && (currentPlayerInScope!!.isPlaying || currentPlayerInScope!!.isLoading)) {
            currentPlayerInScope!!.stop()
        }
        // Clear screen wake lock when stopping player
        if (currentItemType() == SliderItemType.VIDEO) {
            clearKeepScreenOnFlags()
        }
    }

    fun setDefaultExoFactory(defaultExoFactory: DefaultHttpDataSource.Factory) {
        this.defaultExoFactory = defaultExoFactory
    }

    suspend fun addItemsMain(items: List<SliderItemViewHolder>) = withContext(Dispatchers.Main) {
        addItems(items)
    }

    private fun addItems(items: List<SliderItemViewHolder>) {
        setItems(Lists.newArrayList(Iterables.concat(config.items, items)).distinct())
    }

    fun setItems(items: List<SliderItemViewHolder>) {
        if (items.isEmpty()) {
            Toast.makeText(context, "No items received to show in slideshow", Toast.LENGTH_SHORT).show()
            return
        }
        if (slideShowPlaying) {
            // to prevent timing issues when adding + sliding at the same time
            mainHandler.removeCallbacks(goToNextAssetRunnable)
        }
        // notifyDataSetChanged() + POSITION_NONE recreates pages and can reset the ViewPager
        // to index 0. That would fire onAssetSelected for a recent item and send the timeline
        // back to the wrong year when leaving the slider (e.g. after loadMore mid-playback).
        val current = mPager.currentItem
        val currentId = config.items.getOrNull(current)?.mainItem?.id
        config.items = items
        pagerAdapter!!.setItems(items)
        val restored = when {
            currentId != null -> items.indexOfFirst { it.mainItem.id == currentId }.takeIf { it >= 0 }
            else -> null
        } ?: current.coerceIn(0, (items.size - 1).coerceAtLeast(0))
        if (mPager.currentItem != restored) {
            mPager.setCurrentItem(restored, false)
        }
        if (slideShowPlaying && currentItemType() == SliderItemType.IMAGE) {
            startTimerNextAsset()
        }
    }

    private fun currentItem(): SliderItemViewHolder = config.items[mPager.currentItem]
    private fun currentItemType(): SliderItemType = config.items[mPager.currentItem].type

    private fun updateDateOverlay(sliderItem: SliderItem) {
        pendingDateAssetId = sliderItem.id
        ioScope.launch {
            val value = sliderItem.get(MetaDataType.DATE).orEmpty().trim()
            withContext(Dispatchers.Main) {
                if (pendingDateAssetId != sliderItem.id) return@withContext
                dateView.text = value
                val showOverlay = !detailsOverlayToggleEnabled || detailsOverlayVisible
                dateView.visibility = if (showOverlay && value.isNotEmpty()) VISIBLE else GONE
            }
        }
    }

    @OptIn(UnstableApi::class)
    fun isControllerVisible(): Boolean {
        return videoControllerVisible
    }

    companion object {
        private const val TRANSPORT_AUTO_HIDE_MS = 4_000L
        private const val METADATA_DIMMED_WHILE_TRANSPORT_ALPHA = 0.35f

        @SuppressLint("UnsafeOptInUsageError")
        fun prepareMedia(mediaUrl: String, player: ExoPlayer, factory: DefaultHttpDataSource.Factory) {
            val mediaUri = Uri.parse(mediaUrl)
            val mediaItem = MediaItem.fromUri(mediaUri)
            val mediaSource = ProgressiveMediaSource.Factory(factory).createMediaSource(mediaItem)
            player.setMediaSource(mediaSource, 0L)
            player.prepare()
        }
    }
}
