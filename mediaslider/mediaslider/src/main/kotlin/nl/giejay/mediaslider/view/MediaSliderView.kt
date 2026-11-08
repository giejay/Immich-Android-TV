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
import android.view.ViewGroup
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.google.common.collect.Iterables
import com.google.common.collect.Lists
import com.zeuskartik.mediaslider.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.giejay.mediaslider.config.MediaSliderConfiguration
import nl.giejay.mediaslider.controller.SliderControllerBar
import nl.giejay.mediaslider.controller.SliderControllerUiState
import nl.giejay.mediaslider.overlay.createOverlayView
import nl.giejay.mediaslider.pager.ComposeSliderPager
import nl.giejay.mediaslider.pager.DelegatingSliderPager
import nl.giejay.mediaslider.model.SliderItemType
import nl.giejay.mediaslider.model.SliderItemViewHolder
import nl.giejay.mediaslider.plugin.MetadataViewPlugin
import nl.giejay.mediaslider.plugin.SliderViewPlugin
import nl.giejay.mediaslider.plugin.SliderViewPluginContext
import nl.giejay.mediaslider.util.LoadMoreResult
import timber.log.Timber

/**
 * Pager + metadata shell for the media slider. Timeline/memories chrome is provided
 * via pluggable view/controller/key plugins.
 */
open class MediaSliderView(context: Context) : ConstraintLayout(context) {
    // view elements
    protected val mainHandler = Handler(Looper.getMainLooper())
    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == "android.media.VOLUME_CHANGED_ACTION" && isConfigReady) {
                if (currentItemType() == SliderItemType.VIDEO
                    && controller.currentPlayer?.isPlaying == true
                    && controller.currentPlayer?.volume == 0f
                ) {
                    Timber.i("Volume changed detected, unmuting video")
                    Toast.makeText(context, "Volume changed detected, unmuting video", Toast.LENGTH_SHORT).show()
                    controller.toggleMute()
                }
            }
        }
    }

    // config
    protected lateinit var config: MediaSliderConfiguration
        private set
    protected val isConfigReady: Boolean get() = this::config.isInitialized
    private var isVolumeReceiverRegistered = false

    // internal
    protected val controller: MediaSliderController
    private val controllerState = SliderControllerUiState()
    private val sliderPager = DelegatingSliderPager()
    private var composePager: ComposeSliderPager? = null
    private var defaultExoFactory = DefaultHttpDataSource.Factory()
    private var loading = false
    private val ioScope = CoroutineScope(Job() + Dispatchers.IO)
    private data class ViewPluginEntry(val plugin: SliderViewPlugin<Any?>, val state: Any?)
    private val viewPlugins = mutableListOf<ViewPluginEntry>()
    private val viewPluginContext by lazy {
        SliderViewPluginContext(
            context,
            findViewById<ConstraintLayout>(R.id.plugin_layer),
            controller,
            ioScope
        ) { currentItem() }
    }

    init {
        inflate(getContext(), R.layout.slider, this)

        val playButton: View = findViewById(R.id.playPause)
        val controllerHost = findViewById<android.widget.FrameLayout>(R.id.controller_host)
        controllerHost.addView(createOverlayView(context) { SliderControllerBar(controllerState) })
        controller = MediaSliderController(context, mainHandler, sliderPager, playButton, this, controllerState)
        playButton.setOnClickListener { controller.toggleSlideshow(true) }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!isVolumeReceiverRegistered) {
            val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
            context.registerReceiver(volumeReceiver, filter)
            isVolumeReceiverRegistered = true
        }
    }

    /** The slider came back after [onDestroy] (a pause): bring a released video player back. */
    fun onResume() {
        composePager?.resume()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        composePager?.dispose()
        if (isVolumeReceiverRegistered) {
            try {
                context.unregisterReceiver(volumeReceiver)
            } catch (e: IllegalArgumentException) {
                Timber.e(e, "volumeReceiver not registered")
            }
            isVolumeReceiverRegistered = false
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (composePager == null) {
            return super.dispatchKeyEvent(event)
        }
        return controller.dispatchKeyEvent(event) { super.dispatchKeyEvent(event) }
    }

    open fun loadMediaSliderView(config: MediaSliderConfiguration) {
        this.config = config

        config.viewPlugins.forEach { plugin ->
            @Suppress("UNCHECKED_CAST")
            val typedPlugin = plugin as SliderViewPlugin<Any?>
            viewPlugins.add(ViewPluginEntry(typedPlugin, typedPlugin.createState(viewPluginContext, config)))
        }

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
        controller.initialize(config)
        viewPlugins.forEach { it.plugin.attachView(viewPluginContext.rootView, it.state) }
        viewPlugins.forEach { it.plugin.onLoadConfig(viewPluginContext, config, it.state) }
        initComposePager(listener)
    }

    private fun maybeLoadMore() {
        if (config.loadMore != null && sliderPager.currentIndex > config.items.size - 40 && !loading) {
            loading = true
            ioScope.launch {
                val loadMore = config.loadMore ?: return@launch
                val result: LoadMoreResult = loadMore()
                addItemsMain(result.items)
                if (!result.canLoadMore) {
                    config.loadMore = null
                }
                loading = false
            }
        }
    }

    /** The asset at [sliderItemIndex] came to rest: update plugins and start what the page needs. */
    private fun onPageSettled(sliderItemIndex: Int) {
        val sliderItem = config.items.getOrNull(sliderItemIndex) ?: return
        val mainItem = sliderItem.mainItem

        viewPlugins.forEach { entry ->
            entry.plugin.onPageSettled(viewPluginContext, config, sliderItem, sliderItemIndex, mainHandler, entry.state)
        }

        config.onAssetSelected(sliderItem)

        if (sliderItem.type == SliderItemType.VIDEO) {
            val player: ExoPlayer = composePager?.requirePlayer() ?: return
            controller.setCurrentPlayer(player)
            player.seekTo(0, 0)
            // the shared player still holds the previous video: always load this page's media
            if (sliderItem.url != null) {
                prepareMedia(sliderItem.url!!, player, defaultExoFactory)
            }
            player.volume = if (config.isVideoSoundEnable) 1f else 0f
            player.playWhenReady = true
            controller.configureController(sliderItem, sliderItemIndex)
        } else {
            controller.setCurrentPlayer(null)
            controller.configureController(sliderItem, sliderItemIndex)
            if (controller.slideShowPlaying) {
                controller.startTimerNextAsset()
                composePager?.startSlideshowEffect(sliderItemIndex)
            }
        }
    }

    private fun initComposePager(listener: ExoPlayerListener) {
        val pager = ComposeSliderPager(context, config, listener, config.startPosition.coerceAtLeast(0))
        pager.onScrollStarted = { controller.stopPlayer() }
        pager.onPageSelected = { index ->
            viewPlugins.forEach { it.plugin.onPageSelected(viewPluginContext, index, it.state) }
            if (!controller.isControllerVisible) {
                controller.restorePagerFocus()
            }
        }
        // a retry waits a moment; if the user paged on meanwhile, the next normal settle starts the right video
        pager.onVideoRetry = { index -> if (pager.isSettledOn(index)) onPageSettled(index) }
        pager.onPageSettled = { index ->
            maybeLoadMore()
            onPageSettled(index)
        }
        composePager = pager
        sliderPager.delegate = pager
        findViewById<ViewGroup>(R.id.pager_host).addView(
            pager.view,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        pager.start()
        controller.restorePagerFocus()
    }

    fun onDestroy() {
        composePager?.release()
        ioScope.cancel()
        viewPlugins.forEach { it.plugin.onDestroy(viewPluginContext, it.state) }
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
            // Cancel any pending auto-advance to avoid timing races when new items arrive.
            controller.cancelNextAssetTimer()
        }
        config.items = items
        composePager?.items = items
        if (controller.slideShowPlaying && currentItemType() == SliderItemType.IMAGE) {
            controller.startTimerNextAsset()
        }
    }

    protected fun currentItem(): SliderItemViewHolder = config.items[sliderPager.currentIndex]
    protected fun currentItemType(): SliderItemType = currentItem().type

    fun isControllerVisible(): Boolean = controller.isControllerVisible

    /** Delegates to [MediaSliderController.toggleSlideshow]. Part of the public API for external callers. */
    open fun toggleSlideshow(showPlayIndicator: Boolean) = controller.toggleSlideshow(showPlayIndicator)


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
