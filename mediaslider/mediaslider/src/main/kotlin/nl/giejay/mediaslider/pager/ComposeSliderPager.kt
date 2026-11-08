package nl.giejay.mediaslider.pager

import android.content.Context
import android.view.View
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
import androidx.compose.runtime.mutableStateMapOf
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
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import androidx.compose.runtime.snapshotFlow
import nl.giejay.mediaslider.config.MediaSliderConfiguration
import nl.giejay.mediaslider.model.SliderItemType
import nl.giejay.mediaslider.overlay.createOverlayView
import nl.giejay.mediaslider.transitions.SliderTransition
import nl.giejay.mediaslider.transitions.pageTransform
import nl.giejay.mediaslider.transitions.randomTransitionChoices
import nl.giejay.mediaslider.view.ExoPlayerListener
import nl.giejay.mediaslider.view.ExoPlayerView
import kotlin.random.Random

/**
 * Compose replacement of the ViewPager: a [HorizontalPager] with image, double-image and video pages.
 * [MediaSliderView] owns it, feeds the items and reacts to the callbacks below; the playback logic
 * stays in the controller so both pagers behave the same.
 */
@OptIn(ExperimentalFoundationApi::class)
class ComposeSliderPager(
    context: Context,
    internal val config: MediaSliderConfiguration,
    internal val exoListener: ExoPlayerListener,
    startIndex: Int,
) : SliderPager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    var items by mutableStateOf(config.items)
    internal val state = PagerState(currentPage = startIndex.coerceIn(0, (config.items.size - 1).coerceAtLeast(0))) { items.size }

    /** Video views of the pages that are composed, by page index; [MediaSliderView] starts playback through them. */
    val videoViews = mutableStateMapOf<Int, ExoPlayerView>()
    internal val failedVideoUrls = mutableSetOf<String>()

    /** Transition in use; RANDOM resolves to a concrete one that changes whenever the pager starts moving. */
    internal var activeTransition by mutableStateOf(initialTransition())

    var onScrollStarted: () -> Unit = {}
    var onPageSelected: (Int) -> Unit = {}
    var onPageSettled: (Int) -> Unit = {}

    private var pendingSettle = false

    /** Scope of the composition: animations need its frame clock, which a plain scope does not have. */
    internal var compositionScope: CoroutineScope? = null

    val view: View = createOverlayView(context) { SliderPagerContent(this) }.apply {
        isFocusable = true
    }

    override val currentIndex: Int get() = state.currentPage
    override val itemCount: Int get() = items.size

    override fun scrollTo(index: Int, animate: Boolean) {
        val animationScope = compositionScope
        (animationScope ?: scope).launch {
            if (animationScope != null && animate && config.enableSlideAnimation) {
                state.animateScrollToPage(index, animationSpec = scrollSpec())
            } else {
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
                    onScrollStarted()
                } else {
                    settle()
                }
            }
        }
        scope.launch {
            // a video page composes after the settle that wanted it: start it as soon as its view exists
            snapshotFlow { videoViews[state.settledPage] }.filterNotNull().collect {
                if (pendingSettle) settle()
            }
        }
    }

    private fun settle() {
        val page = state.settledPage
        val item = items.getOrNull(page) ?: return
        if (item.type == SliderItemType.VIDEO && videoViews[page] == null) {
            pendingSettle = true
            return
        }
        pendingSettle = false
        onPageSettled(page)
    }

    fun release() {
        scope.cancel()
        videoViews.clear()
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
