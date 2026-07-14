package nl.giejay.android.tv.immich.timeline

import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.leanback.app.BackgroundManager
import androidx.leanback.app.BrandedSupportFragment
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.BrowseFrameLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.giejay.android.tv.immich.ImmichApplication
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.album.AlbumDetailsFragmentDirections
import nl.giejay.android.tv.immich.api.ApiClient
import nl.giejay.android.tv.immich.api.ApiClientConfig
import nl.giejay.android.tv.immich.api.model.Memory
import nl.giejay.android.tv.immich.home.HomeFragmentDirections
import nl.giejay.android.tv.immich.shared.prefs.API_KEY
import nl.giejay.android.tv.immich.shared.prefs.DEBUG_MODE
import nl.giejay.android.tv.immich.shared.prefs.DISABLE_SSL_VERIFICATION
import nl.giejay.android.tv.immich.shared.prefs.HOST_NAME
import nl.giejay.android.tv.immich.shared.prefs.MetaDataScreen
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import nl.giejay.android.tv.immich.shared.prefs.SCREENSAVER_ANIMATE_ASSET_SLIDE
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_ANIMATION_SPEED
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_FORCE_ORIGINAL_VIDEO
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_GLIDE_TRANSFORMATION
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_INTERVAL
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_MAX_CUT_OFF_HEIGHT
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_MAX_CUT_OFF_WIDTH
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_MERGE_PORTRAIT_PHOTOS
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_ONLY_USE_THUMBNAILS
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_PAN_EFFECT
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_ZOOM_EFFECT
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_ZOOM_SCROLL_PANORAMAS
import nl.giejay.android.tv.immich.shared.util.toSliderItems
import nl.giejay.mediaslider.config.MediaSliderConfiguration
import nl.giejay.mediaslider.model.MetaDataType
import nl.giejay.mediaslider.util.LoadMore
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Vertical Immich-style mosaic timeline: day headers + justified asset rows,
 * with a right-edge year/month scrubber. Up/Down previews a month; Enter jumps while staying
 * on the scrubber; Left jumps and returns focus to the mosaic.
 */
class TimelineFragment : BrandedSupportFragment(), BrowseSupportFragment.MainFragmentAdapterProvider {

    private lateinit var apiClient: ApiClient
    private lateinit var viewModel: TimelineViewModel

    private var recyclerView: RecyclerView? = null
    private var progressBar: ProgressBar? = null
    private var scrubberView: TimelineScrubberView? = null
    private var mosaicAdapter: TimelineMosaicAdapter? = null
    private var focusNavigator: TimelineFocusNavigator? = null
    private var focusVideoPreview: TimelineFocusVideoPreview? = null

    private var selectionRestored = false
    private var dataReadyNotified = false
    private var loadingNextBucket = false
    private var contentWidthPx = 0
    private var rowHeightPx = 0
    private var gapPx = 0

    /** Month key waiting for assets after a scrubber jump (`YYYY-MM-01`). */
    private var pendingJumpMonth: String? = null
    /** After a committed scrubber jump, focus a mosaic cell once that month is scrolled into view. */
    private var pendingJumpFocus = false
    /**
     * Keep re-applying the jump scroll while the scrubber stays focused after Enter.
     * Neighbor bucket loads insert newer months above the target (newest-first), which would
     * otherwise leave February visible after an Enter jump to January.
     */
    private var stickyJumpBucketKey: String? = null
    /** True after Up/Down on the scrubber while focused — Left should commit a jump. */
    private var scrubberPreviewMoved = false

    private val mainFragmentAdapter: BrowseSupportFragment.MainFragmentAdapter<Fragment> =
        object : BrowseSupportFragment.MainFragmentAdapter<Fragment>(this) {}

    override fun getMainFragmentAdapter(): BrowseSupportFragment.MainFragmentAdapter<*> =
        mainFragmentAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (PreferenceManager.isLoggedId()) {
            apiClient = ApiClient.getClient(
                ApiClientConfig(
                    PreferenceManager.get(HOST_NAME),
                    PreferenceManager.get(API_KEY),
                    PreferenceManager.get(DISABLE_SSL_VERIFICATION),
                    PreferenceManager.get(DEBUG_MODE)
                )
            )
        } else {
            Toast.makeText(
                ImmichApplication.appContext,
                "Invalid Immich server settings, redirecting to login screen.",
                Toast.LENGTH_SHORT
            ).show()
            findNavController().navigate(HomeFragmentDirections.actionGlobalSignInFragment())
            return
        }

        viewModel = ViewModelProvider(
            requireActivity(),
            TimelineViewModelFactory(apiClient)
        )[TimelineViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.timeline_fragment, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!::viewModel.isInitialized) return
        clearBackground()
        showTitle(false)
        mainFragmentAdapter.fragmentHost?.showTitleView(false)

        progressBar = view.findViewById(R.id.browse_progressbar)
        recyclerView = view.findViewById(R.id.timeline_recycler)
        scrubberView = view.findViewById(R.id.timeline_scrubber)

        gapPx = dp(6)
        rowHeightPx = dp(180)

        scrubberView?.onCommit = { monthKey, exitToMosaic ->
            commitJumpFromScrubber(monthKey, exitToMosaic = exitToMosaic)
        }
        scrubberView?.onPreviewMoved = {
            stickyJumpBucketKey = null
            scrubberPreviewMoved = true
        }

        focusVideoPreview = TimelineFocusVideoPreview(requireContext())

        val adapter = TimelineMosaicAdapter(
            gapPx = gapPx,
            onCellClick = { cell ->
                viewModel.rememberSelection(cell.dayKey, cell.asset.id)
                onItemClicked(cell.asset.id)
            },
            onCellFocus = { cell, cellView ->
                focusNavigator?.onCellFocused(cell.dayKey, cell.asset.id)
                viewModel.prefetchAroundDay(cell.dayKey)
                viewModel.rememberSelection(cell.dayKey, cell.asset.id)
                syncScrubberToAsset(cell.asset.id, cell.dayKey)
                focusVideoPreview?.onCellFocus(cell, cellView)
            },
            onCellBlur = { cell, _ ->
                focusVideoPreview?.onCellBlur(cell)
            },
            onCellDetached = { cellView ->
                focusVideoPreview?.onHostDetached(cellView)
            },
            onCellKey = { v, keyCode, event ->
                focusNavigator?.onCellKey(v, keyCode, event) == true
            },
            onMemoryClicked = { memory -> onMemoryClicked(memory) }
        )
        mosaicAdapter = adapter

        val rv = recyclerView!!
        rv.layoutManager = TimelineMosaicLayoutManager(requireContext())
        rv.adapter = adapter
        rv.setHasFixedSize(false)
        rv.itemAnimator = null

        focusNavigator = TimelineFocusNavigator(
            rv,
            adapter,
            onFocused = { dayKey, assetId ->
                viewModel.prefetchAroundDay(dayKey)
                viewModel.rememberSelection(dayKey, assetId)
            },
            onExitRightToScrubber = { focusScrubberFromMosaic() }
        )

        rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                maybeLoadMoreNearEnd()
                updateScrubberFromVisibleMonth()
            }
        })

        val frame = view.findViewById<BrowseFrameLayout>(R.id.timeline_frame)
        frame.onFocusSearchListener = BrowseFrameLayout.OnFocusSearchListener { focused, direction ->
            if (focused is TimelineScrubberView) {
                return@OnFocusSearchListener if (direction == View.FOCUS_LEFT) {
                    focused.selectedMonthKey()?.let {
                        commitJumpFromScrubber(it, exitToMosaic = true)
                    }
                    focused
                } else {
                    focused
                }
            }
            if (focused?.getTag(R.id.timeline_mosaic_cell_asset_id) == null) {
                return@OnFocusSearchListener null
            }
            val assetId = focused.getTag(R.id.timeline_mosaic_cell_asset_id) as? String
                ?: return@OnFocusSearchListener null
            val n = focusNavigator?.neighbors?.get(assetId) ?: return@OnFocusSearchListener null
            val targetId = when (direction) {
                View.FOCUS_LEFT -> n.leftAssetId
                View.FOCUS_RIGHT -> n.rightAssetId
                View.FOCUS_UP -> n.upAssetId
                View.FOCUS_DOWN -> n.downAssetId
                else -> null
            }
            when {
                targetId != null -> {
                    focusNavigator?.focusAsset(targetId)
                    focused
                }
                direction == View.FOCUS_RIGHT -> {
                    focusScrubberFromMosaic()
                    focused
                }
                direction == View.FOCUS_DOWN -> focused
                else -> null
            }
        }

        mainFragmentAdapter.fragmentHost?.notifyViewCreated(mainFragmentAdapter)

        rv.post {
            contentWidthPx = (rv.width - rv.paddingStart - rv.paddingEnd).coerceAtLeast(1)
            bindDays(viewModel.days.value)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.days.collect { days ->
                        bindDays(days)
                    }
                }
                launch {
                    viewModel.buckets.collect { buckets ->
                        scrubberView?.setBuckets(buckets)
                    }
                }
                launch {
                    viewModel.memories.collect {
                        bindDays(viewModel.days.value)
                    }
                }
                launch {
                    viewModel.error.collect { message ->
                        if (message != null && isAdded) {
                            progressBar?.visibility = View.GONE
                            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                            viewModel.clearError()
                        }
                    }
                }
            }
        }

        lifecycleScope.launch(Dispatchers.IO) {
            viewModel.loadBucketList()
        }
        lifecycleScope.launch(Dispatchers.IO) {
            viewModel.loadMemories()
        }
    }

    override fun onResume() {
        super.onResume()
        clearBackground()
        restoreSelectionIfNeeded()
    }

    override fun onPause() {
        focusVideoPreview?.pause()
        super.onPause()
    }

    override fun onDestroyView() {
        focusVideoPreview?.release()
        focusVideoPreview = null
        super.onDestroyView()
        recyclerView = null
        progressBar = null
        scrubberView = null
        mosaicAdapter = null
        focusNavigator = null
        pendingJumpMonth = null
        pendingJumpFocus = false
        stickyJumpBucketKey = null
        scrubberPreviewMoved = false
    }

    private fun clearBackground() {
        val backgroundManager = BackgroundManager.getInstance(activity)
        if (!backgroundManager.isAttached) {
            backgroundManager.attach(requireActivity().window)
        }
        backgroundManager.drawable = ColorDrawable(
            ContextCompat.getColor(requireContext(), R.color.gray_dark)
        )
    }

    /** Enter jumps and keeps scrubber focus; Left jumps then moves focus into the mosaic. */
    private fun commitJumpFromScrubber(monthKey: String, exitToMosaic: Boolean) {
        if (!isAdded) return
        val bucketKey = viewModel.resolveBucketKey(monthKey) ?: monthKey

        // Accidental scrubber focus: Left returns to the current mosaic cell without jumping.
        if (exitToMosaic && !scrubberPreviewMoved && stickyJumpBucketKey == null) {
            scrubberPreviewMoved = false
            pendingJumpMonth = null
            pendingJumpFocus = false
            returnFocusToLastMosaicCell()
            return
        }

        // Enter already scrolled the mosaic; Left should only hand off focus — no second scroll.
        if (exitToMosaic && stickyJumpBucketKey == bucketKey) {
            stickyJumpBucketKey = null
            scrubberPreviewMoved = false
            pendingJumpMonth = null
            pendingJumpFocus = false
            returnFocusFromScrubber()
            return
        }

        scrubberPreviewMoved = false
        pendingJumpMonth = bucketKey
        pendingJumpFocus = exitToMosaic
        // Enter: keep re-anchoring after neighbor loads insert content above the target.
        stickyJumpBucketKey = if (exitToMosaic) null else bucketKey
        scrubberView?.setIndicatorMonthKey(bucketKey)
        lifecycleScope.launch(Dispatchers.IO) {
            viewModel.loadBucket(bucketKey)
            val list = viewModel.buckets.value
            val index = list.indexOfFirst { it.timeBucket == bucketKey }
            if (index >= 0) {
                list.getOrNull(index - 1)?.let { viewModel.loadBucket(it.timeBucket) }
                list.getOrNull(index + 1)?.let { viewModel.loadBucket(it.timeBucket) }
            }
            withContext(Dispatchers.Main) {
                if (!isAdded) return@withContext
                // Adapter may lag StateFlow by a frame; try now and again after layout.
                if (!attemptPendingJumpScroll()) {
                    recyclerView?.post { attemptPendingJumpScroll() }
                }
            }
        }
    }

    /** @return true if the pending scrubber jump scrolled successfully. */
    private fun attemptPendingJumpScroll(): Boolean {
        val month = pendingJumpMonth ?: return false
        if (!scrollToMonth(month)) return false
        finishJumpFocus()
        return true
    }

    /** Re-apply Enter jump after adapter updates so neighbor inserts don't shift the viewport. */
    private fun reanchorStickyJumpIfNeeded() {
        val key = stickyJumpBucketKey ?: return
        if (scrubberView?.hasFocus() != true) {
            stickyJumpBucketKey = null
            return
        }
        scrollToMonth(key)
    }

    private fun scrollToMonth(monthKey: String): Boolean {
        val adapter = mosaicAdapter ?: return false
        val rv = recyclerView ?: return false
        // Prefer a local day that actually belongs to this Immich UTC month bucket.
        val preferredDay = viewModel.newestDayKeyForBucket(monthKey)
        val position = adapter.positionForScrubberMonth(monthKey, preferredDay)
        if (position == RecyclerView.NO_POSITION) return false
        pendingJumpMonth = null
        val lm = rv.layoutManager as? LinearLayoutManager ?: return false
        lm.scrollToPositionWithOffset(position, dp(24))
        scrubberView?.setIndicatorMonthKey(monthKey)
        return true
    }

    private fun finishJumpFocus() {
        if (!pendingJumpFocus) return
        pendingJumpFocus = false
        stickyJumpBucketKey = null
        scrubberPreviewMoved = false
        returnFocusFromScrubber()
    }

    private fun focusScrubberFromMosaic() {
        scrubberPreviewMoved = false
        val focused = recyclerView?.findFocus()
        val assetId = focused?.getTag(R.id.timeline_mosaic_cell_asset_id) as? String
            ?: viewModel.lastSelectedAssetId
        val dayKey = focused?.getTag(R.id.timeline_mosaic_cell_day_key) as? String
            ?: viewModel.lastSelectedDayKey
        val monthKey = when {
            assetId != null -> viewModel.bucketKeyForAsset(assetId)
            dayKey != null -> runCatching {
                TimelineViewModel.monthBucketKey(LocalDate.parse(dayKey))
            }.getOrNull()
            else -> null
        }
        scrubberView?.prepareFocusForMonth(monthKey)
        scrubberView?.requestFocus()
    }

    /** Restore the cell that had focus before the scrubber — no scroll. */
    private fun returnFocusToLastMosaicCell() {
        val navigator = focusNavigator ?: return
        val assetId = viewModel.lastSelectedAssetId ?: run {
            returnFocusFromScrubber()
            return
        }
        val position = mosaicAdapter?.positionOfAsset(assetId) ?: RecyclerView.NO_POSITION
        if (position >= 0) {
            navigator.focusAsset(assetId, smooth = false, adjustScroll = false)
            return
        }
        returnFocusFromScrubber()
    }

    /**
     * Hand focus to the right-most mosaic cell of the jump target day (closest to the scrubber),
     * without changing scroll.
     */
    private fun returnFocusFromScrubber() {
        val navigator = focusNavigator ?: return
        val monthKey = scrubberView?.selectedMonthKey()
        val adapter = mosaicAdapter ?: return

        if (monthKey != null) {
            val bucketKey = viewModel.resolveBucketKey(monthKey) ?: monthKey
            val preferredDay = viewModel.newestDayKeyForBucket(bucketKey)
                ?: viewModel.flatAssetIndex()
                    .firstOrNull { it.first.startsWith(monthKey.take(7)) }
                    ?.first
            val assetId = preferredDay?.let { adapter.rightmostAssetIdForDay(it) }
            if (assetId != null) {
                navigator.focusAsset(assetId, smooth = false, adjustScroll = false)
                return
            }
        }
        adapter.firstRowRightmostAssetId()?.let {
            navigator.focusAsset(it, smooth = false, adjustScroll = false)
        }
    }

    private fun syncScrubberToAsset(assetId: String, dayKey: String) {
        if (scrubberView?.hasFocus() == true) return
        val bucketKey = viewModel.bucketKeyForAsset(assetId)
            ?: runCatching {
                TimelineViewModel.monthBucketKey(LocalDate.parse(dayKey))
            }.getOrNull()
        scrubberView?.setIndicatorMonthKey(bucketKey)
    }

    private fun updateScrubberFromVisibleMonth() {
        if (scrubberView?.hasFocus() == true) return
        val rv = recyclerView ?: return
        val adapter = mosaicAdapter ?: return
        val lm = rv.layoutManager as? LinearLayoutManager ?: return
        val first = lm.findFirstVisibleItemPosition()
        if (first == RecyclerView.NO_POSITION) return
        val dayKey = adapter.dayKeyAtAdapterPosition(first) ?: return
        val assetId = adapter.firstAssetIdAtAdapterPosition(first)
        if (assetId != null) {
            syncScrubberToAsset(assetId, dayKey)
        } else {
            scrubberView?.setIndicatorMonthKey(
                TimelineViewModel.monthBucketKey(LocalDate.parse(dayKey))
            )
        }
    }

    private fun bindDays(days: List<TimelineDay>) {
        if (!isAdded) return
        val adapter = mosaicAdapter ?: return
        val width = contentWidthPx
        if (width <= 0) {
            recyclerView?.post {
                contentWidthPx = ((recyclerView?.width ?: 0) -
                    (recyclerView?.paddingStart ?: 0) -
                    (recyclerView?.paddingEnd ?: 0)).coerceAtLeast(1)
                bindDays(viewModel.days.value)
            }
            return
        }

        val items = TimelineMosaicLayoutEngine.layout(
            days = days,
            contentWidthPx = width,
            rowHeightPx = rowHeightPx,
            gapPx = gapPx,
            dayLabel = { day ->
                TimelineDateFormatter.dayLabel(
                    date = day.date,
                    todayLabel = getString(R.string.today),
                    yesterdayLabel = getString(R.string.yesterday)
                )
            }
        )
        val neighbors = TimelineMosaicLayoutEngine.buildFocusNeighbors(items)
        focusNavigator?.updateNeighbors(neighbors)

        val memories = viewModel.memories.value
        val itemsWithMemories = if (memories.isNotEmpty()) {
            listOf(TimelineMosaicItem.MemoriesRow(memories)) + items
        } else {
            items
        }

        adapter.submitList(itemsWithMemories) {
            if (!isAdded) return@submitList
            if (days.isNotEmpty()) {
                progressBar?.visibility = View.GONE
                if (!dataReadyNotified) {
                    mainFragmentAdapter.fragmentHost?.notifyDataReady(mainFragmentAdapter)
                    dataReadyNotified = true
                }
            }
            val hasCellFocus =
                recyclerView?.findFocus()?.getTag(R.id.timeline_mosaic_cell_asset_id) != null
            if (!hasCellFocus && scrubberView?.hasFocus() != true) {
                selectionRestored = false
            }
            restoreSelectionIfNeeded()
            if (!attemptPendingJumpScroll()) {
                reanchorStickyJumpIfNeeded()
            }
            loadingNextBucket = false
        }
    }

    private fun restoreSelectionIfNeeded() {
        if (selectionRestored || !isAdded) return
        val navigator = focusNavigator ?: return
        val adapter = mosaicAdapter ?: return

        val assetId = viewModel.lastSelectedAssetId
        if (assetId == null) {
            selectionRestored = true
            return
        }

        val position = adapter.positionOfAsset(assetId)
        if (position < 0) {
            val dayKey = viewModel.lastSelectedDayKey
            if (dayKey != null) {
                lifecycleScope.launch(Dispatchers.IO) {
                    viewModel.loadBucket(TimelineViewModel.monthBucketKey(LocalDate.parse(dayKey)))
                }
            }
            return
        }

        selectionRestored = true
        Handler(Looper.getMainLooper()).postDelayed({
            if (isAdded) navigator.focusAsset(assetId, smooth = false)
        }, 100)
    }

    private fun maybeLoadMoreNearEnd() {
        val rv = recyclerView ?: return
        val adapter = mosaicAdapter ?: return
        if (loadingNextBucket) return
        val lm = rv.layoutManager as? LinearLayoutManager ?: return
        val lastVisible = lm.findLastVisibleItemPosition()
        if (lastVisible == RecyclerView.NO_POSITION) return
        val remainingDays = adapter.remainingDayHeadersFrom(lastVisible)
        val nearEnd = remainingDays <= 2 ||
            lastVisible >= (adapter.itemCount - 4).coerceAtLeast(0)
        if (!nearEnd) return

        val next = viewModel.nextUnloadedBucket() ?: return
        loadingNextBucket = true
        lifecycleScope.launch(Dispatchers.IO) {
            viewModel.loadBucket(next.timeBucket)
            withContext(Dispatchers.Main) {
                loadingNextBucket = false
            }
        }
    }

    private fun onItemClicked(assetId: String) {
        selectionRestored = false
        val flat = viewModel.flatAssetIndex()
        if (flat.isEmpty()) return
        val sliderItems = flat.map { it.second.toSliderItemViewHolder() }
        val startIndex = sliderItems.indexOfFirst { it.ids().contains(assetId) }.coerceAtLeast(0)

        // Continue strictly forward in time from the oldest asset already in the slider.
        // [TimelineViewModel.nextUnloadedBucket] picks the first *gap* in the whole bucket
        // list — unrelated to where the slider currently is — which was jumping the slider
        // to an arbitrary month once the locally-loaded assets ran out.
        var lastBucketKey: String? = flat.lastOrNull()?.let { (_, asset) ->
            viewModel.bucketKeyForAsset(asset.id)
        }

        val loadMore: LoadMore = suspend loadMore@{
            val afterKey = lastBucketKey ?: return@loadMore emptyList()
            val buckets = viewModel.buckets.value
            val afterIndex = buckets.indexOfFirst { it.timeBucket == afterKey }
            if (afterIndex < 0) return@loadMore emptyList()
            val next = buckets.getOrNull(afterIndex + 1) ?: return@loadMore emptyList()
            val knownIds = viewModel.flatAssetIndex().map { it.second.id }.toSet()
            viewModel.loadBucket(next.timeBucket)
            lastBucketKey = next.timeBucket
            viewModel.flatAssetIndex()
                .map { it.second }
                .filter { it.id !in knownIds }
                .map { it.toSliderItemViewHolder() }
        }

        findNavController().navigate(
            AlbumDetailsFragmentDirections.actionToPhotoSlider(
                MediaSliderConfiguration(
                    startIndex,
                    PreferenceManager.get(SLIDER_INTERVAL),
                    PreferenceManager.get(SLIDER_ONLY_USE_THUMBNAILS),
                    isVideoSoundEnable = true,
                    sliderItems,
                    loadMore,
                    { item -> viewModel.rememberSelectionByAssetId(item.mainItem.id) },
                    animationSpeedMillis = PreferenceManager.get(SLIDER_ANIMATION_SPEED),
                    maxCutOffHeight = PreferenceManager.get(SLIDER_MAX_CUT_OFF_HEIGHT),
                    maxCutOffWidth = PreferenceManager.get(SLIDER_MAX_CUT_OFF_WIDTH),
                    transformation = PreferenceManager.get(SLIDER_GLIDE_TRANSFORMATION),
                    debugEnabled = PreferenceManager.get(DEBUG_MODE),
                    enableSlideAnimation = PreferenceManager.get(SCREENSAVER_ANIMATE_ASSET_SLIDE),
                    gradiantOverlay = false,
                    metaDataConfig = PreferenceManager.getAllMetaData(MetaDataScreen.VIEWER)
                        .filter { it.type != MetaDataType.MEDIA_COUNT },
                    zoomAndScrollPanorama = PreferenceManager.get(SLIDER_ZOOM_SCROLL_PANORAMAS),
                    zoomEffectPercent = PreferenceManager.get(SLIDER_ZOOM_EFFECT),
                    panEffectPercent = PreferenceManager.get(SLIDER_PAN_EFFECT),
                    useLargeVideoBuffer = PreferenceManager.get(SLIDER_FORCE_ORIGINAL_VIDEO)
                )
            )
        )
    }

    /** Opens a memory's assets in the slider, auto-playing — bounded set, no [LoadMore]. */
    private fun onMemoryClicked(memory: Memory) {
        if (memory.assets.isEmpty()) return
        val sliderItems = memory.assets.toSliderItems(
            keepOrder = true,
            mergePortrait = PreferenceManager.get(SLIDER_MERGE_PORTRAIT_PHOTOS)
        )
        findNavController().navigate(
            AlbumDetailsFragmentDirections.actionToPhotoSlider(
                MediaSliderConfiguration(
                    0,
                    PreferenceManager.get(SLIDER_INTERVAL),
                    PreferenceManager.get(SLIDER_ONLY_USE_THUMBNAILS),
                    isVideoSoundEnable = true,
                    sliderItems,
                    null,
                    animationSpeedMillis = PreferenceManager.get(SLIDER_ANIMATION_SPEED),
                    maxCutOffHeight = PreferenceManager.get(SLIDER_MAX_CUT_OFF_HEIGHT),
                    maxCutOffWidth = PreferenceManager.get(SLIDER_MAX_CUT_OFF_WIDTH),
                    transformation = PreferenceManager.get(SLIDER_GLIDE_TRANSFORMATION),
                    debugEnabled = PreferenceManager.get(DEBUG_MODE),
                    enableSlideAnimation = PreferenceManager.get(SCREENSAVER_ANIMATE_ASSET_SLIDE),
                    gradiantOverlay = false,
                    metaDataConfig = PreferenceManager.getAllMetaData(MetaDataScreen.VIEWER)
                        .filter { it.type != MetaDataType.MEDIA_COUNT },
                    zoomAndScrollPanorama = PreferenceManager.get(SLIDER_ZOOM_SCROLL_PANORAMAS),
                    zoomEffectPercent = PreferenceManager.get(SLIDER_ZOOM_EFFECT),
                    panEffectPercent = PreferenceManager.get(SLIDER_PAN_EFFECT),
                    useLargeVideoBuffer = PreferenceManager.get(SLIDER_FORCE_ORIGINAL_VIDEO)
                ),
                autoPlay = true
            )
        )
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).roundToInt()

}

class TimelineViewModelFactory(
    private val apiClient: ApiClient
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TimelineViewModel::class.java)) {
            return TimelineViewModel(
                fetchBuckets = { apiClient.getTimeBuckets() },
                fetchBucket = { apiClient.getTimeBucket(it) },
                fetchMemories = { apiClient.getMemories() }
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
