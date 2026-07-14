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
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_ONLY_USE_THUMBNAILS
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_PAN_EFFECT
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_ZOOM_EFFECT
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_ZOOM_SCROLL_PANORAMAS
import nl.giejay.mediaslider.config.MediaSliderConfiguration
import nl.giejay.mediaslider.model.MetaDataType
import nl.giejay.mediaslider.util.LoadMore
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Vertical Immich-style mosaic timeline: day headers + justified asset rows.
 * Hosted inside Browse as a [BrowseSupportFragment.MainFragmentAdapterProvider].
 */
class TimelineFragment : BrandedSupportFragment(), BrowseSupportFragment.MainFragmentAdapterProvider {

    private lateinit var apiClient: ApiClient
    private lateinit var viewModel: TimelineViewModel

    private var recyclerView: RecyclerView? = null
    private var progressBar: ProgressBar? = null
    private var mosaicAdapter: TimelineMosaicAdapter? = null
    private var focusNavigator: TimelineFocusNavigator? = null

    private var selectionRestored = false
    private var dataReadyNotified = false
    private var loadingNextBucket = false
    private var contentWidthPx = 0
    private var rowHeightPx = 0
    private var gapPx = 0

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
        // Keep Browse's own title chrome off the mosaic; "Timeline" lives in the left headers.
        showTitle(false)
        mainFragmentAdapter.fragmentHost?.showTitleView(false)

        progressBar = view.findViewById(R.id.browse_progressbar)
        recyclerView = view.findViewById(R.id.timeline_recycler)

        gapPx = dp(6)
        rowHeightPx = dp(180)

        val adapter = TimelineMosaicAdapter(
            gapPx = gapPx,
            onCellClick = { cell ->
                viewModel.rememberSelection(cell.dayKey, cell.asset.id)
                onItemClicked(cell.asset.id)
            },
            onCellFocus = { cell ->
                focusNavigator?.onCellFocused(cell.dayKey, cell.asset.id)
                viewModel.prefetchAroundDay(cell.dayKey)
                viewModel.rememberSelection(cell.dayKey, cell.asset.id)
            },
            onCellKey = { v, keyCode, event ->
                focusNavigator?.onCellKey(v, keyCode, event) == true
            }
        )
        mosaicAdapter = adapter

        val rv = recyclerView!!
        rv.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
        rv.adapter = adapter
        rv.setHasFixedSize(false)
        rv.itemAnimator = null

        focusNavigator = TimelineFocusNavigator(rv, adapter) { dayKey, assetId ->
            viewModel.prefetchAroundDay(dayKey)
            viewModel.rememberSelection(dayKey, assetId)
        }

        rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                maybeLoadMoreNearEnd()
            }
        })

        val frame = view.findViewById<BrowseFrameLayout>(R.id.timeline_frame)
        frame.onFocusSearchListener = BrowseFrameLayout.OnFocusSearchListener { focused, direction ->
            if (focused?.getTag(R.id.timeline_mosaic_cell_asset_id) == null) return@OnFocusSearchListener null
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
            if (targetId != null) {
                focusNavigator?.focusAsset(targetId)
                focused // keep current until post-focus lands
            } else if (direction == View.FOCUS_RIGHT || direction == View.FOCUS_DOWN) {
                focused
            } else {
                null
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
                    viewModel.error.collect { message ->
                        if (message != null && isAdded) {
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
    }

    override fun onResume() {
        super.onResume()
        clearBackground()
        restoreSelectionIfNeeded()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        recyclerView = null
        progressBar = null
        mosaicAdapter = null
        focusNavigator = null
    }

    private fun clearBackground() {
        val backgroundManager = BackgroundManager.getInstance(activity)
        if (!backgroundManager.isAttached) {
            backgroundManager.attach(requireActivity().window)
        }
        // Solid theme background — never show hovered asset photos behind the mosaic.
        backgroundManager.drawable = ColorDrawable(
            ContextCompat.getColor(requireContext(), R.color.gray_dark)
        )
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

        adapter.submitList(items) {
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
            if (!hasCellFocus) {
                selectionRestored = false
            }
            restoreSelectionIfNeeded()
            loadingNextBucket = false
        }
    }

    private fun restoreSelectionIfNeeded() {
        if (selectionRestored || !isAdded) return
        val navigator = focusNavigator ?: return
        val adapter = mosaicAdapter ?: return

        val assetId = viewModel.lastSelectedAssetId
        if (assetId == null) {
            // Leave focus on Browse headers so the left menu stays visible on cold start.
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

        val loadMore: LoadMore = suspend loadMore@{
            val next = viewModel.nextUnloadedBucket() ?: return@loadMore emptyList()
            val knownIds = viewModel.flatAssetIndex().map { it.second.id }.toSet()
            viewModel.loadBucket(next.timeBucket)
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
                fetchBucket = { apiClient.getTimeBucket(it) }
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
