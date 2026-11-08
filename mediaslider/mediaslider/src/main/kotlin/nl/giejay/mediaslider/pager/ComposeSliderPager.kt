package nl.giejay.mediaslider.pager

import android.content.Context
import android.view.View
import android.widget.Toast
import androidx.annotation.OptIn as AndroidOptIn
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import androidx.compose.runtime.snapshotFlow
import nl.giejay.mediaslider.config.MediaSliderConfiguration
import nl.giejay.mediaslider.model.SliderItemType
import nl.giejay.mediaslider.overlay.createOverlayView
import nl.giejay.mediaslider.transitions.SliderTransition
import nl.giejay.mediaslider.transitions.pageTransform
import nl.giejay.mediaslider.transitions.randomTransitionChoices
import nl.giejay.mediaslider.view.ExoPlayerListener
import nl.giejay.mediaslider.player.AmlogicSafeRenderersFactory
import nl.giejay.mediaslider.player.createVideoLoadControl
import timber.log.Timber
import kotlin.random.Random

/**
 * Compose replacement of the ViewPager: a [HorizontalPager] with image, double-image and video pages.
 * [MediaSliderView] owns it, feeds the items and reacts to the callbacks below; the playback logic
 * stays in the controller so both pagers behave the same.
 */
@OptIn(ExperimentalFoundationApi::class)
class ComposeSliderPager(
    private val context: Context,
    internal val config: MediaSliderConfiguration,
    internal val exoListener: ExoPlayerListener,
    startIndex: Int,
) : SliderPager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    var items by mutableStateOf(config.items)
    internal val state = PagerState(currentPage = startIndex.coerceIn(0, (config.items.size - 1).coerceAtLeast(0))) { items.size }

    /** Videos whose SurfaceView failed and play on a TextureView instead. */
    internal val failedVideoUrls = mutableSetOf<String>()

    /** The one player of all video pages; it is created with the first video and bound to the current page only. */
    var videoPlayer: ExoPlayer? = null
        private set

    /** False from the moment a page change starts until the current video drew its first frame; the poster covers the wait. */
    var firstVideoFrameRendered by mutableStateOf(false)

    /** Bumped when a failed SurfaceView page has to be rebuilt with a TextureView. */
    internal var videoRebuild by mutableIntStateOf(0)

    /** Bumped whenever the shared player is released, so a page view bound to the old player is rebuilt on any recomposition. */
    internal var playerGeneration by mutableIntStateOf(0)

    /** True from the slider's pause until its resume: no player view (and no player) is created meanwhile. */
    internal var paused by mutableStateOf(false)

    /** Page whose video is retried once the rebuilt player view is attached to the player; null when nothing waits. */
    private var pendingRetryPage: Int? = null

    /** Asks the slider to prepare the current video again (after the page was rebuilt for a TextureView). */
    var onVideoRetry: (Int) -> Unit = {}

    /** Transition in use; RANDOM resolves to a concrete one that changes whenever the pager starts moving. */
    internal var activeTransition by mutableStateOf(initialTransition())

    var onScrollStarted: () -> Unit = {}
    var onPageSelected: (Int) -> Unit = {}
    var onPageSettled: (Int) -> Unit = {}

    /** Page [onPageSettled] last ran for since the pager last started to move; -1 after a scroll start. */
    private var settledNotified = -1

    /** Scope of the composition: animations need its frame clock, which a plain scope does not have. */
    internal var compositionScope: CoroutineScope? = null

    val view: View = createOverlayView(context) { SliderPagerContent(this) }.apply {
        isFocusable = true
    }

    /** True while [page] is the current page and nothing is scrolling; delayed work (a retry) checks it before acting. */
    fun isSettledOn(page: Int): Boolean = state.settledPage == page && !state.isScrollInProgress

    override val currentIndex: Int get() = state.currentPage
    override val itemCount: Int get() = items.size

    override fun scrollTo(index: Int, animate: Boolean) {
        val animationScope = compositionScope
        (animationScope ?: scope).launch {
            if (animationScope != null && animate && config.enableSlideAnimation) {
                state.animateScrollToPage(index, animationSpec = scrollSpec())
            } else {
                settledNotified = -1
                firstVideoFrameRendered = false
                onScrollStarted()
                state.scrollToPage(index)
            }
        }
    }

    override fun restoreFocus() {
        view.post {
            if (!view.hasFocus()) view.requestFocus()
        }
    }

    /** Starts observing the pager; call once after the callbacks are set. */
    fun start() {
        scope.launch {
            snapshotFlow { state.currentPage }.collect { onPageSelected(it) }
        }
        scope.launch {
            snapshotFlow { state.isScrollInProgress }.collect { scrolling ->
                if (scrolling) {
                    if (config.transition == SliderTransition.RANDOM) pickNextTransition()
                    firstVideoFrameRendered = false
                    settledNotified = -1
                    pendingRetryPage = null
                    onScrollStarted()
                } else {
                    settle()
                }
            }
        }
        scope.launch {
            // immediate jumps (no animation) may never report a scroll in progress; the settled page still changes
            snapshotFlow { state.settledPage }.collect { settle() }
        }
    }

    private fun settle() {
        val page = state.settledPage
        if (items.getOrNull(page) == null) return
        if (state.isScrollInProgress || page == settledNotified) return
        settledNotified = page
        onPageSettled(page)
    }

    /** The shared video player; built on first use with the same renderers and buffering as the ViewPager pages. */
    @AndroidOptIn(UnstableApi::class)
    fun requirePlayer(): ExoPlayer = videoPlayer ?: ExoPlayer.Builder(context)
        .setRenderersFactory(AmlogicSafeRenderersFactory(context))
        .setLoadControl(createVideoLoadControl(context, config))
        .build()
        .also { player ->
            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) = exoListener.onPlaybackStateChanged(playbackState)
                override fun onIsPlayingChanged(isPlaying: Boolean) = exoListener.onIsPlayingChanged(isPlaying)
                override fun onRenderedFirstFrame() {
                    firstVideoFrameRendered = true
                }

                override fun onPlayerError(error: PlaybackException) = handleVideoError(error)
            })
            if (!config.isVideoSoundEnable) player.volume = 0f
            videoPlayer = player
        }

    /** The page's player view attached to the shared player; a pending retry can start now. */
    internal fun onPlayerViewBound() {
        val page = pendingRetryPage ?: return
        pendingRetryPage = null
        view.post { onVideoRetry(page) }
    }

    /** A SurfaceView that failed is retried once on a TextureView, like the per-page players did. */
    private fun handleVideoError(error: PlaybackException) {
        val page = state.settledPage
        val item = items.getOrNull(page) ?: return
        val useTextureView = item.mainItem.orientation != 1 || failedVideoUrls.contains(item.url)
        Timber.e(error, "Player error at page $page for ${item.url}. Retry with TextureView: ${!useTextureView}. ${error.message}")
        if (!useTextureView) {
            item.url?.let { failedVideoUrls.add(it) }
            videoPlayer?.stop()
            firstVideoFrameRendered = false
            pendingRetryPage = page
            videoRebuild++
        } else {
            Toast.makeText(context, "Cannot play video: ${error.message}", Toast.LENGTH_LONG).show()
            exoListener.onPlayerError(error)
        }
    }

    /** Frees the shared player (the slider's pause); the observers stay so the pager keeps working on resume. */
    fun release() {
        paused = true
        pendingRetryPage = null
        videoPlayer?.release()
        videoPlayer = null
        playerGeneration++
        firstVideoFrameRendered = false
    }

    /** Back in the foreground: settle the current page again so a released video player is built and started anew. */
    fun resume() {
        if (!paused) return
        paused = false
        settledNotified = -1
        settle()
    }

    /** Ends the observers for good; the slider calls it when its view goes away. */
    fun dispose() {
        scope.cancel()
        videoPlayer?.release()
        videoPlayer = null
    }

    private fun scrollSpec() = if (config.transition == SliderTransition.SLIDE) {
        tween<Float>(config.animationSpeedMillis.coerceAtLeast(1), easing = FastOutSlowInEasing)
    } else {
        tween<Float>(config.transitionDurationMillis, easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f))
    }

    private fun initialTransition(): SliderTransition =
        if (config.transition == SliderTransition.RANDOM) randomTransitionChoices.first() else config.transition

    private fun pickNextTransition() {
        val others = randomTransitionChoices.filterNot { it == activeTransition }
        activeTransition = others[Random.nextInt(others.size)]
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SliderPagerContent(pager: ComposeSliderPager) = CompositionLocalProvider(LocalSliderConfig provides pager.config) {
    val items = pager.items
    val composeScope = rememberCoroutineScope()
    SideEffect { pager.compositionScope = composeScope }
    HorizontalPager(
        state = pager.state,
        beyondViewportPageCount = 1,
        key = { index -> items.getOrNull(index)?.ids()?.joinToString("+") ?: index },
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) { page ->
        val item = items.getOrNull(page) ?: return@HorizontalPager
        Box(
            Modifier
                .fillMaxSize()
                .zIndex(page.toFloat())
                .pageTransition(pager, page),
        ) {
            when {
                item.type == SliderItemType.VIDEO -> SliderVideoPage(pager, page, item)
                item.hasSecondaryItem() -> Row(Modifier.fillMaxSize()) {
                    SliderImagePage(item.mainItem, Modifier.weight(1f).fillMaxSize())
                    SliderImagePage(item.secondaryItem!!, Modifier.weight(1f).fillMaxSize())
                }
                else -> SliderImagePage(item.mainItem, Modifier.fillMaxSize())
            }
        }
    }
}

/** Applies the active transition to a page; the plain slide needs no layer at all. */
private fun Modifier.pageTransition(pager: ComposeSliderPager, page: Int): Modifier {
    if (pager.activeTransition == SliderTransition.SLIDE) return this
    return graphicsLayer {
        val position = page - pager.state.currentPage - pager.state.currentPageOffsetFraction
        val t = pager.activeTransition.pageTransform(position)
        alpha = t.alpha
        translationX = t.translationXFraction * size.width
        scaleX = t.scale
        scaleY = t.scale
        rotationY = t.rotationY
        transformOrigin = TransformOrigin(t.pivotXFraction, 0.5f)
        if (t.perspective) cameraDistance = 12f * density
    }
}

internal val LocalSliderConfig = staticCompositionLocalOf<MediaSliderConfiguration> { error("No slider configuration") }
