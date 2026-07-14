package nl.giejay.android.tv.immich.timeline

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.leanback.app.BackgroundManager
import androidx.leanback.app.RowsSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.giejay.android.tv.immich.ImmichApplication
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.album.AlbumDetailsFragmentDirections
import nl.giejay.android.tv.immich.api.ApiClient
import nl.giejay.android.tv.immich.api.ApiClientConfig
import nl.giejay.android.tv.immich.card.Card
import nl.giejay.android.tv.immich.card.CardPresenter
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

/**
 * Timeline screen: one horizontal [ListRow] per calendar day (newest first).
 * Month buckets are still fetched from the API; days are grouped client-side.
 */
class TimelineFragment : RowsSupportFragment() {

    private lateinit var apiClient: ApiClient
    private lateinit var viewModel: TimelineViewModel

    private var rowsAdapter: ArrayObjectAdapter? = null
    private val rowAdapters = linkedMapOf<String, ArrayObjectAdapter>()
    private var dayIndexByKey = emptyMap<String, Int>()
    private var selectionRestored = false

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

        onItemViewClickedListener = OnItemViewClickedListener { _, item, _, row ->
            val card = item as Card
            val dayKey = dayKeyFromRow(row as? ListRow)
            if (dayKey != null) {
                viewModel.rememberSelection(dayKey, card.id)
            }
            onItemClicked(card)
        }
        setOnItemViewSelectedListener { _, item, _, row ->
            val dayKey = dayKeyFromRow(row as? ListRow) ?: return@setOnItemViewSelectedListener
            viewModel.prefetchAroundDay(dayKey)
            val card = item as? Card ?: return@setOnItemViewSelectedListener
            viewModel.rememberSelection(dayKey, card.id)
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        @Suppress("DEPRECATION")
        super.onActivityCreated(savedInstanceState)
        clearBackground()
        ensureRowsAdapter()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.days.collect { days ->
                        syncDayRows(days)
                        restoreSelectionIfNeeded()
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

    private fun clearBackground() {
        val backgroundManager = BackgroundManager.getInstance(activity)
        if (backgroundManager.isAttached) {
            backgroundManager.drawable = null
        }
    }

    private fun ensureRowsAdapter() {
        if (rowsAdapter != null) return
        val adapter = ArrayObjectAdapter(ListRowPresenter())
        rowsAdapter = adapter
        this.adapter = adapter
        mainFragmentAdapter.fragmentHost?.notifyDataReady(mainFragmentAdapter)
    }

    /**
     * Keep ListRows in sync with [days]. Append-only when new older days arrive; rebuild if
     * order changed (parallel month loads).
     */
    private fun syncDayRows(days: List<TimelineDay>) {
        if (!isAdded) return
        ensureRowsAdapter()
        val adapter = rowsAdapter ?: return

        val oldKeys = rowAdapters.keys.toList()
        val newKeys = days.map { it.dayKey }

        if (oldKeys.isEmpty()) {
            rebuildAllDayRows(days)
            return
        }

        if (newKeys.take(oldKeys.size) == oldKeys) {
            // Refresh in case timezone boundaries merged more assets into an existing day.
            days.take(oldKeys.size).forEach { day ->
                refreshDayRow(day)
            }
            days.drop(oldKeys.size).forEach { day ->
                appendDayRow(day, adapter.size())
            }
            dayIndexByKey = days.mapIndexed { index, day -> day.dayKey to index }.toMap()
            return
        }

        // Reorder (e.g. newer month finished after an older one) — rebuild and restore focus.
        selectionRestored = false
        rebuildAllDayRows(days)
    }

    private fun rebuildAllDayRows(days: List<TimelineDay>) {
        val adapter = rowsAdapter ?: return
        adapter.clear()
        rowAdapters.clear()
        days.forEachIndexed { index, day ->
            appendDayRow(day, index)
        }
        dayIndexByKey = days.mapIndexed { index, day -> day.dayKey to index }.toMap()
        mainFragmentAdapter.fragmentHost?.notifyDataReady(mainFragmentAdapter)
    }

    private fun appendDayRow(day: TimelineDay, index: Int) {
        if (!isAdded) return
        val adapter = rowsAdapter ?: return
        if (rowAdapters.containsKey(day.dayKey)) return

        val cardAdapter = ArrayObjectAdapter(CardPresenter(requireContext(), R.style.TimelineCardTheme))
        cardAdapter.addAll(0, day.assets.map { it.toCard() })
        rowAdapters[day.dayKey] = cardAdapter

        val header = HeaderItem(
            index.toLong(),
            TimelineDateFormatter.dayLabel(
                date = day.date,
                todayLabel = getString(R.string.today),
                yesterdayLabel = getString(R.string.yesterday)
            )
        )
        header.contentDescription = day.dayKey
        adapter.add(ListRow(header, cardAdapter))
    }

    private fun refreshDayRow(day: TimelineDay) {
        val cardAdapter = rowAdapters[day.dayKey] ?: return
        val newIds = day.assets.map { it.id }
        val oldIds = (0 until cardAdapter.size()).mapNotNull { (cardAdapter[it] as? Card)?.id }
        if (oldIds == newIds) return
        cardAdapter.clear()
        cardAdapter.addAll(0, day.assets.map { it.toCard() })
    }

    private fun restoreSelectionIfNeeded() {
        if (selectionRestored || !isAdded) return
        val dayKey = viewModel.lastSelectedDayKey ?: return
        val assetId = viewModel.lastSelectedAssetId ?: return
        val rowIndex = dayIndexByKey[dayKey] ?: run {
            // Day not visible yet — load its month
            lifecycleScope.launch(Dispatchers.IO) {
                viewModel.loadBucket(TimelineViewModel.monthBucketKey(LocalDate.parse(dayKey)))
            }
            return
        }
        val rowAdapter = rowAdapters[dayKey] ?: return
        if (rowAdapter.size() == 0) return
        val itemIndex = (0 until rowAdapter.size()).indexOfFirst { (rowAdapter[it] as? Card)?.id == assetId }
        if (itemIndex < 0) return

        selectionRestored = true
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isAdded) return@postDelayed
            setSelectedPosition(
                rowIndex,
                false,
                ListRowPresenter.SelectItemViewHolderTask(itemIndex)
            )
        }, 100)
    }

    private fun onItemClicked(card: Card) {
        selectionRestored = false
        val flat = viewModel.flatAssetIndex()
        if (flat.isEmpty()) return
        val sliderItems = flat.map { it.second.toSliderItemViewHolder() }
        val startIndex = sliderItems.indexOfFirst { it.ids().contains(card.id) }.coerceAtLeast(0)

        val loadMore: LoadMore = suspend loadMore@{
            val next = viewModel.nextUnloadedBucket() ?: return@loadMore emptyList()
            val knownIds = viewModel.flatAssetIndex().map { it.second.id }.toSet()
            viewModel.loadBucket(next.timeBucket)
            withContext(Dispatchers.Main) {
                syncDayRows(viewModel.days.value)
            }
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

    private fun dayKeyFromRow(row: ListRow?): String? =
        row?.headerItem?.contentDescription?.toString()
            ?: dayIndexByKey.entries.firstOrNull { it.value.toLong() == row?.headerItem?.id }?.key
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
