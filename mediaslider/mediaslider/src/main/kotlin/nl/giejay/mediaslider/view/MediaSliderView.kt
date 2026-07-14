package nl.giejay.mediaslider.view

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
    private lateinit var metaDataHolder: LinearLayout
    private lateinit var metadataRows: LinearLayout
    private lateinit var videoControls: PlayerControlView
    private lateinit var muteButton: ImageButton
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
        mainHandler = Handler(Looper.getMainLooper())
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
        context.registerReceiver(volumeReceiver, filter)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        context.unregisterReceiver(volumeReceiver)
    }

    @OptIn(UnstableApi::class)
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (mPager.adapter == null) {
            return super.dispatchKeyEvent(event)
        }
        val itemType = currentItemType()
        if (event.action == KeyEvent.ACTION_DOWN) {
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
                if (event.keyCode != KeyEvent.KEYCODE_DPAD_RIGHT) {
                    toggleSlideshow(true)
                } else {
                    // remove all current callbacks to prevent multiple runnables
                    mainHandler.removeCallbacks(goToNextAssetRunnable)
                    goToNextAsset()
                    return false
                }
                return super.dispatchKeyEvent(event)
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
        // Memories (and any other autoplay entry) keep advancing while details are open,
        // which tears down/rebuilds the bottom strip before lazy EXIF can bind. Pause first
        // so metadata can settle — mosaic never hits this because it does not autoPlay.
        if (slideShowPlaying) {
            toggleSlideshow(false)
        }
        detailsOverlayVisible = true
        applyDetailsOverlayVisibility()
        refreshOverlayMetadata()
        metaDataLeftAdapter.bind()
        metaDataRightAdapter.bind()
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
        videoControls.showTimeoutMs = 0
        videoControls.show()
        // PlayerControlView rebinds standard controls when player is set — re-apply ours after.
        wireSharedVideoControls()
        applyTransportChrome(isVideo = true)
        syncMuteButton()
        applyDetailsOverlayVisibility()
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
        applyDetailsOverlayVisibility()
        videoControls.post {
            videoControls.findViewById<View>(R.id.exo_slideshow)?.requestFocus()
        }
    }

    private fun hideImageTransportControls() {
        if (!imageTransportVisible && videoControls.visibility != VISIBLE) return
        imageTransportVisible = false
        if (!videoControllerVisible) {
            videoControls.hide()
            videoControls.player = null
        }
        applyDetailsOverlayVisibility()
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
            toggleSlideshow(true)
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
            }
            setKeepScreenOnFlags();
        } else {
            clearKeepScreenOnFlags()
            mainHandler.removeCallbacks(goToNextAssetRunnable)
            // Resuming manual viewing: restart if the video had already finished
            if (currentItemType() == SliderItemType.VIDEO &&
                currentPlayerInScope?.playbackState == Player.STATE_ENDED
            ) {
                currentPlayerInScope?.seekTo(0)
                currentPlayerInScope?.play()
            }
        }
        if (togglePlayButton) {
            togglePlayButton()
        }
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
        mainHandler.postDelayed(goToNextAssetRunnable, (config.interval * 1000).toLong())
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
            listener
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

                config.onAssetSelected(sliderItem)
                currentToast?.cancel()
                if (!sliderItem.hasSecondaryItem() && config.debugEnabled && transformResults.contains(sliderItemIndex)) {
                    currentToast = Toast.makeText(context, transformResults[sliderItemIndex], Toast.LENGTH_LONG)
                    currentToast!!.show()
                    transformResults.remove(sliderItemIndex)
                }

                if (sliderItem.type == SliderItemType.VIDEO) {
                    if (!detailsOverlayToggleEnabled && config.isGradiantOverlayVisible && !videoControllerVisible) {
                        metaDataHolder.background = null
                    }
                    val viewTag = mPager.findViewWithTag<ExoPlayerView>("view$sliderItemIndex") ?: return
                    if (!viewTag.isReady()) {
                        Timber.e("Player is not initialized properly, cannot play video.")
                        Toast.makeText(context, "Player is not initialized properly, cannot play video.", Toast.LENGTH_LONG).show()
                        return
                    }
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
                // Load/bind metadata here (once per page), not from onPageScrolled which fires
                // continuously and previously stamped null states after failed/aborted storms.
                refreshOverlayMetadata()
                metaDataLeftAdapter.bind()
                metaDataRightAdapter.bind()
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
        adapter.getItemsToShow().forEachIndexed { metaDataIndex, item ->
            if (adapter.hasStateForItem(sliderItem.id, metaDataIndex)) {
                return@forEachIndexed
            }
            ioScope.launch {
                try {
                    val value = item.getValue(context, sliderItem, sliderItemIndex, config.items.size)
                    adapter.updateState(sliderItem.id, metaDataIndex, value)
                    withContext(Dispatchers.Main) {
                        adapter.bind()
                        if (detailsOverlayVisible) {
                            applyDetailsOverlayVisibility()
                        }
                    }
                } catch (e: Exception) {
                    // Do not mark as fetched — next open / page select can retry.
                    Timber.w(e, "Failed to load metadata %s for %s", item.type, sliderItem.id)
                }
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
        config.items = items
        pagerAdapter!!.setItems(items)
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
