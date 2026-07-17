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
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.children
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.viewpager.widget.ViewPager
import com.google.common.collect.Iterables
import com.google.common.collect.Lists
import com.zeuskartik.mediaslider.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.giejay.mediaslider.adapter.ScreenSlidePagerAdapter
import nl.giejay.mediaslider.config.MediaSliderConfiguration
import nl.giejay.mediaslider.model.SliderItemType
import nl.giejay.mediaslider.model.SliderItemViewHolder
import nl.giejay.mediaslider.plugin.MetadataViewPlugin
import nl.giejay.mediaslider.plugin.SliderKeyEventPlugin
import nl.giejay.mediaslider.plugin.SliderKeyEventResult
import nl.giejay.mediaslider.plugin.SliderKeyEventState
import nl.giejay.mediaslider.plugin.SliderViewPlugin
import nl.giejay.mediaslider.plugin.SliderViewPluginContext
import nl.giejay.mediaslider.util.FixedSpeedScroller
import nl.giejay.mediaslider.util.MediaSliderListener
import timber.log.Timber
import java.lang.reflect.Field


/**
 * Pager + metadata shell for the media slider. Timeline/memories chrome is provided
 * via pluggable view/controller/key plugins.
 */
open class MediaSliderView(context: Context) : ConstraintLayout(context) {
    // view elements
    protected val mainHandler = Handler(Looper.getMainLooper())
    protected val mPager: ViewPager
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
    private var defaultExoFactory = DefaultHttpDataSource.Factory()
    private var pagerAdapter: ScreenSlidePagerAdapter? = null
    private var loading = false
    private val ioScope = CoroutineScope(Job() + Dispatchers.IO)
    private data class ViewPluginEntry(val plugin: SliderViewPlugin<Any?>, val state: Any?)
    private val viewPlugins = mutableListOf<ViewPluginEntry>()
    private val keyEventPlugins = mutableListOf<SliderKeyEventPlugin>()
    private val viewPluginContext by lazy { SliderViewPluginContext(context, this, controller, ioScope) { currentItem() } }

    init {
        inflate(getContext(), R.layout.slider, this)

        val playButton: View = findViewById(R.id.playPause)
        mPager = findViewById(R.id.pager)
        controller = MediaSliderController(context, mainHandler, mPager, playButton, this)
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

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
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
        if (mPager.adapter == null) {
            return super.dispatchKeyEvent(event)
        }
        val itemType = currentItemType()
        if (event.action == KeyEvent.ACTION_DOWN) {
            val pluginState = SliderKeyEventState(
                isControllerVisible = controller.isControllerVisible,
                isSlideshowPlaying = controller.slideShowPlaying,
                currentItemType = itemType,
                controller = controller
            )
            var pluginHandled = false
            keyEventPlugins.forEach { plugin ->
                when (plugin.onKeyDown(event, pluginState)) {
                    SliderKeyEventResult.UNHANDLED -> Unit
                    SliderKeyEventResult.HANDLED_CONTINUE -> pluginHandled = true
                    SliderKeyEventResult.HANDLED_CONSUME -> return true
                    SliderKeyEventResult.DISPATCH_TO_SUPER -> return super.dispatchKeyEvent(event)
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
                if (controller.isControllerVisible) {
                    return super.dispatchKeyEvent(event)
                }
                if (controller.toggleController()) {
                    return true
                }
                return false
            } else if (event.keyCode == KeyEvent.KEYCODE_BACK && controller.isControllerVisible) {
                controller.hideController()
                return true
            } else if (event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN && !controller.isControllerVisible) {
                // Down-arrow on a video opens the unified controller.
                if (controller.toggleController()) return true
                return super.dispatchKeyEvent(event)
            } else if (controller.slideShowPlaying && itemType == SliderItemType.IMAGE) {
                if (event.keyCode != KeyEvent.KEYCODE_DPAD_RIGHT) {
                    controller.toggleSlideshow(true)
                } else {
                    controller.skipToNextAndRestartTimer()
                    return false
                }
                return super.dispatchKeyEvent(event)
            } else if (event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (controller.isControllerVisible) {
                    return super.dispatchKeyEvent(event)
                }
                controller.goToNextAsset()
                return false
            } else if (event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (controller.isControllerVisible) {
                    return super.dispatchKeyEvent(event)
                }
                controller.goToPreviousAsset()
                return false
            }
        }
        return if (controller.isControllerVisible) super.dispatchKeyEvent(event) else false
    }

    open fun loadMediaSliderView(config: MediaSliderConfiguration) {
        this.config = config

        viewPlugins.clear()
        val allViewPlugins = mutableListOf<SliderViewPlugin<*>>()
        allViewPlugins.add(MetadataViewPlugin())
        allViewPlugins.addAll(config.viewPlugins)
        allViewPlugins.forEach { plugin ->
            @Suppress("UNCHECKED_CAST")
            val typedPlugin = plugin as SliderViewPlugin<Any?>
            viewPlugins.add(ViewPluginEntry(typedPlugin, typedPlugin.createState(viewPluginContext, config)))
        }

        keyEventPlugins.clear()
        keyEventPlugins.addAll(config.keyEventPlugins)

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
        viewPlugins.forEach { it.plugin.attachView(this, it.state) }
        viewPlugins.forEach { it.plugin.onLoadConfig(viewPluginContext, config, it.state) }
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
            listener
        )

        try {
            if (config.enableSlideAnimation && config.animationSpeedMillis > 0) {
                val mScroller: Field = ViewPager::class.java.getDeclaredField("mScroller")
                mScroller.isAccessible = true
                val scroller = FixedSpeedScroller(mPager.context, DecelerateInterpolator(0.75F), config.animationSpeedMillis)
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
                        loading = nextItems.isEmpty()
                    }
                }
                controller.stopPlayer()
                if (sliderItemIndex != mPager.currentItem) {
                    return
                }
                val sliderItem = config.items[sliderItemIndex]
                val mainItem = sliderItem.mainItem

                viewPlugins.forEach { entry ->
                    entry.plugin.onPageSettled(viewPluginContext, config, sliderItem, sliderItemIndex, mainHandler, entry.state)
                }

                config.onAssetSelected(sliderItem)

                val statusLayoutLeft = findViewById<LinearLayout>(R.id.meta_data_holder)
                if (sliderItem.type == SliderItemType.VIDEO) {
                    if (config.isGradiantOverlayVisible) {
                        statusLayoutLeft?.background = null
                    }
                    val viewTag = mPager.findViewWithTag<ExoPlayerView>("view$sliderItemIndex") ?: return
                    if (!viewTag.isReady()) {
                        Timber.e("Player is not initialized properly, cannot play video.")
                        Toast.makeText(context, "Player is not initialized properly, cannot play video.", Toast.LENGTH_LONG).show()
                        return
                    }
                    val player = viewTag.getPlayer()!!
                    controller.setCurrentPlayer(player)
                    player.seekTo(0, 0)
                    if (player.playbackState == Player.STATE_IDLE && sliderItem.url != null) {
                        prepareMedia(sliderItem.url!!, player, defaultExoFactory)
                    }
                    if (!config.isVideoSoundEnable) {
                        player.volume = 0f
                    }
                    player.playWhenReady = true
                    controller.configureController(sliderItem, sliderItemIndex)
                } else {
                    controller.setCurrentPlayer(null)
                    controller.configureController(sliderItem, sliderItemIndex)
                    if (config.isGradiantOverlayVisible) {
                        statusLayoutLeft?.setBackgroundResource(R.drawable.gradient_overlay)
                    }
                    if (controller.slideShowPlaying) {
                        controller.startTimerNextAsset()
                        val viewTag = mPager.findViewWithTag<ViewGroup>("view$sliderItemIndex") ?: return
                        val touchImageView = viewTag.children.first() as? TouchImageView
                        if (touchImageView != null && config.zoomAndScrollPanorama && config.interval >= 10 && mainItem.isPanorama) {
                            touchImageView.zoomAndScrollPanorama(config, sliderItem)
                        } else if (touchImageView != null && config.zoomAndScrollPanorama && !mainItem.isPanorama) {
                            touchImageView.zoomAndPanEffect(config, sliderItem)
                        }
                    }
                }
            }

            override fun onPageSelected(i: Int) {
                viewPlugins.forEach { it.plugin.onPageSelected(viewPluginContext, i, it.state) }
                if (!controller.isControllerVisible) {
                    controller.restorePagerFocus()
                }
            }

            override fun onPageScrollStateChanged(i: Int) {}
        })
    }

    fun onDestroy() {
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
        pagerAdapter!!.setItems(items)
        if (controller.slideShowPlaying && currentItemType() == SliderItemType.IMAGE) {
            controller.startTimerNextAsset()
        }
    }

    protected fun currentItem(): SliderItemViewHolder = config.items[mPager.currentItem]
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
