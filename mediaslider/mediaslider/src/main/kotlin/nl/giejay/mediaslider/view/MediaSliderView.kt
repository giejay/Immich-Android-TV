package nl.giejay.mediaslider.view

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
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

/**
 * Pager + metadata shell for the media slider. Interactive control logic lives in
 * [MediaSliderController]; timeline/memories chrome subclasses [TimelineSliderView].
 */
@OptIn(UnstableApi::class)
open class MediaSliderView(context: Context) : ConstraintLayout(context), MediaSliderController.Host {

    protected val mainHandler = Handler(Looper.getMainLooper())

    init {
        inflate(getContext(), R.layout.slider, this)
    }

    protected val mPager: ViewPager = findViewById(R.id.pager)
    private val playButton: View = findViewById(R.id.playPause)
    private val dateView: TextView = findViewById(R.id.metadata_date)
    private val metaDataHolder: FrameLayout = findViewById(R.id.meta_data_holder)
    private val metadataRows: LinearLayout = findViewById(R.id.metadata_rows)
    private val videoControls: PlayerControlView = findViewById(R.id.slider_video_controls)
    private val muteButton: ImageButton = videoControls.findViewById(R.id.exo_mute)
    protected val controller = MediaSliderController(
        context,
        mainHandler,
        mPager,
        playButton,
        videoControls,
        muteButton,
        metaDataHolder,
        metadataRows,
        dateView,
        this
    )

    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == "android.media.VOLUME_CHANGED_ACTION") {
                if (currentItemType() == SliderItemType.VIDEO &&
                    controller.currentPlayer?.isPlaying == true &&
                    controller.currentPlayer?.volume == 0f
                ) {
                    Timber.i("Volume changed detected, unmuting video")
                    Toast.makeText(context, "Volume changed detected, unmuting video", Toast.LENGTH_SHORT).show()
                    controller.toggleMute()
                }
            }
        }
    }

    protected lateinit var config: MediaSliderConfiguration
        private set
    protected val isConfigReady: Boolean get() = this::config.isInitialized

    private lateinit var metaDataLeftAdapter: MetaDataAdapter
    private lateinit var metaDataRightAdapter: MetaDataAdapter

    private var currentPlayerView: PlayerView? = null
    private var defaultExoFactory = DefaultHttpDataSource.Factory()
    private var pagerAdapter: ScreenSlidePagerAdapter? = null
    private var loading = false
    private val ioScope = CoroutineScope(Job() + Dispatchers.IO)
    private val transformResults = mutableMapOf<Int, String>()
    private var currentToast: Toast? = null
    private var pendingDateAssetId: String? = null

    init {
        playButton.setOnClickListener { toggleSlideshow(true) }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        context.registerReceiver(volumeReceiver, IntentFilter("android.media.VOLUME_CHANGED_ACTION"))
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        controller.cancelTransportAutoHide()
        context.unregisterReceiver(volumeReceiver)
    }

    // -------------------------------------------------------------------------
    // MediaSliderController.Host
    // -------------------------------------------------------------------------

    override fun mediaConfig(): MediaSliderConfiguration = config
    override fun currentItem(): SliderItemViewHolder = currentSliderItem()
    override fun currentItemType(): SliderItemType = currentSliderItemType()

    override fun refreshOverlayMetadata() {
        if (!isConfigReady || config.items.isEmpty() || mPager.adapter == null) return
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

    override fun bindMetadataAdapters() {
        if (!this::metaDataLeftAdapter.isInitialized) return
        metaDataLeftAdapter.bind()
        metaDataRightAdapter.bind()
    }

    override fun updateDateOverlay(sliderItem: SliderItem) {
        pendingDateAssetId = sliderItem.id
        ioScope.launch {
            val value = sliderItem.get(MetaDataType.DATE).orEmpty().trim()
            withContext(Dispatchers.Main) {
                if (pendingDateAssetId != sliderItem.id) return@withContext
                dateView.text = value
                val showOverlay = !controller.detailsOverlayToggleEnabled || controller.detailsOverlayVisible
                dateView.visibility = if (showOverlay && value.isNotEmpty()) VISIBLE else GONE
            }
        }
    }

    override fun applyDetailsOverlayVisibilityIfDetailsOpen() {
        if (controller.detailsOverlayVisible) {
            controller.applyDetailsOverlayVisibility()
        }
    }

    override fun onVideoSlideshowStarted() {
        // TimelineSliderView overrides / hooks story progress via toggleSlideshow + callbacks.
    }

    override fun onSlideshowTimerCancelled() {
        // Subclasses (story progress) listen via controller callbacks / override.
    }

    // -------------------------------------------------------------------------
    // Key routing
    // -------------------------------------------------------------------------

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (mPager.adapter == null) {
            return super.dispatchKeyEvent(event)
        }
        val itemType = currentSliderItemType()
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (controller.transportControlsVisible) {
                controller.scheduleTransportAutoHide()
            }
            if (context is MediaSliderListener && (context as MediaSliderListener).onButtonPressed(event)) {
                return false
            } else if (event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || event.keyCode == KeyEvent.KEYCODE_ENTER) {
                if (controller.detailsOverlayToggleEnabled) {
                    if (videoControls.findFocus() != null) {
                        return super.dispatchKeyEvent(event)
                    }
                    if (controller.detailsOverlayVisible && itemType == SliderItemType.IMAGE &&
                        !controller.imageTransportVisible
                    ) {
                        controller.showImageTransportControls()
                        controller.suppressTransportEnterUp = true
                        return true
                    }
                    if (controller.detailsOverlayVisible && itemType == SliderItemType.VIDEO &&
                        controller.currentPlayer != null && !controller.videoControllerVisible
                    ) {
                        controller.showVideoController()
                        controller.suppressTransportEnterUp = true
                        return true
                    }
                    controller.showDetailsOverlay()
                    if (itemType == SliderItemType.VIDEO && controller.currentPlayer != null) {
                        controller.showVideoController()
                        controller.suppressTransportEnterUp = true
                    } else if (itemType == SliderItemType.IMAGE) {
                        controller.showImageTransportControls()
                        controller.suppressTransportEnterUp = true
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
            } else if (itemType == SliderItemType.VIDEO &&
                (event.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
                    event.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY ||
                    event.keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE)
            ) {
                return when (event.keyCode) {
                    KeyEvent.KEYCODE_MEDIA_PLAY -> controller.playVideo()
                    KeyEvent.KEYCODE_MEDIA_PAUSE -> controller.pauseVideo()
                    else -> controller.togglePlayPause()
                }
            } else if (itemType == SliderItemType.VIDEO && controller.isRemoteSeekForward(event.keyCode)) {
                return controller.seekBy(MediaSliderController.REMOTE_SEEK_STEP_MS)
            } else if (itemType == SliderItemType.VIDEO && controller.isRemoteSeekRewind(event.keyCode)) {
                return controller.seekBy(-MediaSliderController.REMOTE_SEEK_STEP_MS)
            } else if (event.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY ||
                event.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            ) {
                if (itemType == SliderItemType.IMAGE) {
                    if (controller.isImageControllerVisible) {
                        return super.dispatchKeyEvent(event)
                    }
                    if (controller.toggleImageController()) {
                        return true
                    }
                }
                return false
            } else if (event.keyCode == KeyEvent.KEYCODE_BACK &&
                itemType == SliderItemType.IMAGE &&
                controller.isImageControllerVisible
            ) {
                controller.hideImageController()
                return true
            } else if (event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN &&
                itemType == SliderItemType.VIDEO &&
                controller.currentPlayer != null
            ) {
                if (!controller.videoControllerVisible) {
                    controller.showVideoController()
                }
                return super.dispatchKeyEvent(event)
            } else if (event.keyCode == KeyEvent.KEYCODE_BACK && controller.videoControllerVisible) {
                controller.hideVideoController()
                return true
            } else if (event.keyCode == KeyEvent.KEYCODE_BACK && controller.imageTransportVisible) {
                controller.hideImageTransportControls()
                return true
            } else if (event.keyCode == KeyEvent.KEYCODE_BACK &&
                controller.detailsOverlayToggleEnabled &&
                controller.detailsOverlayVisible
            ) {
                controller.hideDetailsOverlay()
                return true
            } else if (controller.slideShowPlaying && itemType == SliderItemType.IMAGE) {
                if (handleSlideshowImageKey(event.keyCode)) {
                    return true
                }
                when (event.keyCode) {
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        controller.cancelNextAssetTimer()
                        controller.goToNextAsset()
                        return false
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        controller.cancelNextAssetTimer()
                        controller.goToPreviousAsset()
                        return false
                    }
                    else -> {
                        if (event.keyCode != KeyEvent.KEYCODE_BACK) {
                            toggleSlideshow(true)
                        }
                        return super.dispatchKeyEvent(event)
                    }
                }
            } else if (event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (itemType == SliderItemType.IMAGE && controller.isImageControllerVisible) {
                    return super.dispatchKeyEvent(event)
                }
                if (controller.videoControllerVisible) {
                    return super.dispatchKeyEvent(event)
                }
                if (controller.imageTransportVisible) {
                    return true
                }
                controller.goToNextAsset()
                return false
            } else if (event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (itemType == SliderItemType.IMAGE && controller.isImageControllerVisible) {
                    return super.dispatchKeyEvent(event)
                }
                if (controller.videoControllerVisible) {
                    return super.dispatchKeyEvent(event)
                }
                if (controller.imageTransportVisible) {
                    return true
                }
                controller.goToPreviousAsset()
                return false
            }
        } else if (event.action == KeyEvent.ACTION_UP &&
            (event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || event.keyCode == KeyEvent.KEYCODE_ENTER)
        ) {
            if (controller.suppressTransportEnterUp) {
                controller.suppressTransportEnterUp = false
                return true
            }
        }
        if (videoControls.findFocus() != null || controller.videoControllerVisible) {
            return super.dispatchKeyEvent(event)
        }
        return if (itemType == SliderItemType.IMAGE) {
            if (controller.isImageControllerVisible) super.dispatchKeyEvent(event) else false
        } else {
            super.dispatchKeyEvent(event)
        }
    }

    /**
     * When slideshow is running on an image, subclasses can consume Left/Right (return true)
     * without pausing autoplay — used by memories story progress.
     */
    protected open fun handleSlideshowImageKey(keyCode: Int): Boolean = false

    /** Subclasses use this for story chrome after the pager settles. */
    protected open fun onPageSettled(index: Int) {}

    open fun toggleSlideshow(showPlayIndicator: Boolean) =
        controller.toggleSlideshow(showPlayIndicator)

    open fun loadMediaSliderView(config: MediaSliderConfiguration) {
        this.config = config
        controller.initialize(config)

        val detailsConfig = config.metaDataConfig.filterNot { it.type == MetaDataType.DATE }
        controller.hasBottomDetails = detailsConfig.isNotEmpty()

        val columnRight = findViewById<LinearLayout>(R.id.metadata_view_right)
        metaDataRightAdapter = MetaDataAdapter(
            context,
            detailsConfig.filter { it.align == AlignOption.RIGHT },
            detailsConfig.map { it.withAlign(align = AlignOption.RIGHT) }.distinct(),
            { if (currentSliderItem().hasSecondaryItem()) currentSliderItem().secondaryItem!! else currentSliderItem().mainItem },
            { currentSliderItem().hasSecondaryItem() }
        )
        metaDataRightAdapter.attach(columnRight)

        val columnLeft = findViewById<LinearLayout>(R.id.metadata_view_left)
        metaDataLeftAdapter = MetaDataAdapter(
            context,
            detailsConfig.filter { it.align == AlignOption.LEFT },
            detailsConfig.filterNot { it is MetaDataClock || it is MetaDataMediaCount }
                .map { it.withAlign(align = AlignOption.LEFT) }.distinct(),
            { currentSliderItem().mainItem },
            { currentSliderItem().hasSecondaryItem() }
        )
        metaDataLeftAdapter.attach(columnLeft)

        controller.wireSharedVideoControls()
        controller.applyDetailsOverlayVisibility()

        val listener: ExoPlayerListener = object : ExoPlayerListener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                controller.onPlaybackStateChanged(playbackState)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                controller.onIsPlayingChanged(isPlaying)
            }

            override fun onPlayerError(error: PlaybackException) {
                controller.onPlayerError(error)
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
        controller.restorePagerFocus()
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
                val scroller = FixedSpeedScroller(
                    mPager.context,
                    DecelerateInterpolator(0.75F),
                    config.animationSpeedMillis
                )
                mScroller.set(mPager, scroller)
            }
        } catch (e: Exception) {
            Timber.e(e, "Could not set scroller")
        }

        mPager.adapter = pagerAdapter
        setStartPosition()
        mPager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrolled(sliderItemIndex: Int, v: Float, i1: Int) {
                if (config.loadMore != null && mPager.currentItem > config.items.size - 40 && !loading) {
                    loading = true
                    ioScope.launch {
                        val nextItems = config.loadMore!!.invoke()
                        addItemsMain(nextItems)
                        loading = nextItems.isEmpty()
                    }
                }
                controller.stopPlayer()
                if (sliderItemIndex != mPager.currentItem) {
                    return
                }
                val sliderItem = config.items[sliderItemIndex]
                val mainItem = sliderItem.mainItem

                currentToast?.cancel()
                if (!sliderItem.hasSecondaryItem() && config.debugEnabled &&
                    transformResults.contains(sliderItemIndex)
                ) {
                    currentToast = Toast.makeText(
                        context,
                        transformResults[sliderItemIndex],
                        Toast.LENGTH_LONG
                    )
                    currentToast!!.show()
                    transformResults.remove(sliderItemIndex)
                }

                if (sliderItem.type == SliderItemType.VIDEO) {
                    controller.removeNextAssetCallbacks()
                    if (!controller.detailsOverlayToggleEnabled &&
                        config.isGradiantOverlayVisible &&
                        !controller.videoControllerVisible
                    ) {
                        metaDataHolder.background = null
                    }
                    val viewTag = mPager.findViewWithTag<ExoPlayerView>("view$sliderItemIndex") ?: return
                    if (!viewTag.isReady()) {
                        Timber.e("Player is not initialized properly, cannot play video.")
                        Toast.makeText(
                            context,
                            "Player is not initialized properly, cannot play video.",
                            Toast.LENGTH_LONG
                        ).show()
                        return
                    }
                    viewTag.showLoadingPoster(mainItem.thumbnailUrl)
                    currentPlayerView = viewTag.getPlayerView()
                    val player = viewTag.getPlayer()!!
                    controller.bindCurrentPlayer(player)
                    player.seekTo(0, 0)
                    controller.updateVideoRepeatMode()
                    if (player.playbackState == Player.STATE_IDLE && sliderItem.url != null) {
                        prepareMedia(sliderItem.url!!, player, defaultExoFactory)
                    }
                    if (!config.isVideoSoundEnable) {
                        player.volume = 0f
                    }
                    controller.syncMuteButton()
                    // Keep details if open, but never auto-surface transport when paging.
                    controller.dismissTransportForPageChange()
                    player.playWhenReady = true
                    if (controller.slideShowPlaying) {
                        onVideoPageBoundWhileSlideshow()
                    }
                } else {
                    controller.hideVideoController()
                    controller.hideImageTransportControls()
                    controller.configureImageController(sliderItem, sliderItemIndex)
                    if (!controller.detailsOverlayToggleEnabled && config.isGradiantOverlayVisible) {
                        metaDataHolder.setBackgroundResource(R.drawable.gradient_overlay)
                    } else if (controller.detailsOverlayToggleEnabled) {
                        metaDataHolder.setBackgroundResource(R.drawable.metadata_details_scrim)
                    }
                    if (controller.slideShowPlaying) {
                        controller.startTimerNextAsset()
                        val viewTag =
                            mPager.findViewWithTag<RelativeLayout>("view$sliderItemIndex") ?: return
                        val touchImageView = viewTag.children.first() as? TouchImageView
                        if (touchImageView != null && config.zoomAndScrollPanorama &&
                            config.interval >= 10 && mainItem.isPanorama
                        ) {
                            touchImageView.zoomAndScrollPanorama(config, sliderItem)
                        } else if (touchImageView != null && config.zoomAndScrollPanorama &&
                            !mainItem.isPanorama
                        ) {
                            touchImageView.zoomAndPanEffect(config, sliderItem)
                        }
                    }
                    controller.stopPlayer()
                    controller.bindCurrentPlayer(null)
                }
            }

            override fun onPageSelected(i: Int) {
                if (i in config.items.indices) {
                    config.onAssetSelected(config.items[i])
                }
                refreshOverlayMetadata()
                bindMetadataAdapters()
                if (!controller.isImageControllerVisible) {
                    controller.restorePagerFocus()
                }
                onPageSettled(i)
            }

            override fun onPageScrollStateChanged(i: Int) {}
        })
        refreshOverlayMetadata()
        onPageSettled(mPager.currentItem)
    }

    /** Hook for [TimelineSliderView] to start video story progress when a video page binds. */
    protected open fun onVideoPageBoundWhileSlideshow() {
        onVideoSlideshowStarted()
    }

    private fun updateMetaData(adapter: MetaDataAdapter, sliderItem: SliderItem, sliderItemIndex: Int) {
        val items = adapter.getItemsToShow()
        if (items.isEmpty()) return
        if (adapter.isFullyFetched(sliderItem.id)) return
        ioScope.launch {
            try {
                val values = items.map { item ->
                    item.getValue(context, sliderItem, sliderItemIndex, config.items.size)
                }
                withContext(Dispatchers.Main) {
                    if (!currentSliderItem().ids().contains(sliderItem.id)) return@withContext
                    values.forEachIndexed { i, value ->
                        adapter.updateState(sliderItem.id, i, value)
                    }
                    adapter.bind()
                    applyDetailsOverlayVisibilityIfDetailsOpen()
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to load metadata for %s", sliderItem.id)
            }
        }
    }

    fun onDestroy() {
        controller.onDestroy()
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
        if (controller.slideShowPlaying) {
            controller.removeNextAssetCallbacks()
        }
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
        if (controller.slideShowPlaying && currentSliderItemType() == SliderItemType.IMAGE) {
            controller.startTimerNextAsset()
        }
    }

    protected fun currentSliderItem(): SliderItemViewHolder = config.items[mPager.currentItem]
    protected fun currentSliderItemType(): SliderItemType = config.items[mPager.currentItem].type

    fun isControllerVisible(): Boolean = controller.isControllerVisible

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
