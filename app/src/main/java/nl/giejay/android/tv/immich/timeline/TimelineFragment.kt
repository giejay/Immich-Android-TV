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
import nl.giejay.android.tv.immich.api.model.TimeBucketSummary
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

/**
 * Default Timeline screen: one horizontal [ListRow] per month (newest first).
 * Sibling of [nl.giejay.android.tv.immich.shared.fragment.VerticalCardGridFragment], not a
 * GenericAssetFragment — month rows do not fit that class's single flat-list model.
 */
class TimelineFragment : RowsSupportFragment() {

    private lateinit var apiClient: ApiClient
    private lateinit var viewModel: TimelineViewModel

    private var rowsAdapter: ArrayObjectAdapter? = null
    private val rowAdapters = linkedMapOf<String, ArrayObjectAdapter>()
    private var bucketIndexByKey = emptyMap<String, Int>()
    private var rowsBuilt = false
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

        // Activity-scoped so month cache + selection survive slider navigation (Browse recreates this fragment).
        viewModel = ViewModelProvider(
            requireActivity(),
            TimelineViewModelFactory(apiClient)
        )[TimelineViewModel::class.java]

        onItemViewClickedListener = OnItemViewClickedListener { _, item, _, row ->
            val card = item as Card
            val listRow = row as? ListRow
            val timeBucket = listRow?.headerItem?.contentDescription?.toString()
                ?: bucketKeyForHeaderId(listRow?.headerItem?.id ?: -1)
            if (timeBucket != null) {
                viewModel.rememberSelection(timeBucket, card.id)
            }
            onItemClicked(card)
        }
        setOnItemViewSelectedListener { _, item, _, row ->
            val listRow = row as? ListRow ?: return@setOnItemViewSelectedListener
            val timeBucket = listRow.headerItem.contentDescription?.toString()
                ?: bucketKeyForHeaderId(listRow.headerItem.id)
                ?: return@setOnItemViewSelectedListener
            viewModel.prefetchAround(timeBucket)
            val card = item as? Card ?: return@setOnItemViewSelectedListener
            viewModel.rememberSelection(timeBucket, card.id)
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        @Suppress("DEPRECATION")
        super.onActivityCreated(savedInstanceState)
        clearBackground()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.buckets.collect { buckets ->
                        if (buckets.isNotEmpty() && !rowsBuilt) {
                            buildRows(buckets)
                            // Re-bind any already-cached months after a fresh adapter build
                            viewModel.bucketAssets.value.forEach { (timeBucket, assets) ->
                                populateRow(timeBucket, assets)
                            }
                            restoreSelectionIfNeeded()
                        }
                    }
                }
                launch {
                    viewModel.bucketAssets.collect { loaded ->
                        loaded.forEach { (timeBucket, assets) ->
                            populateRow(timeBucket, assets)
                        }
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
        // Other browse pages may have set a photo background; keep Timeline solid.
        clearBackground()
        restoreSelectionIfNeeded()
    }

    private fun clearBackground() {
        val backgroundManager = BackgroundManager.getInstance(activity)
        if (backgroundManager.isAttached) {
            backgroundManager.drawable = null
        }
    }

    private fun buildRows(buckets: List<TimeBucketSummary>) {
        if (!isAdded) return
        rowsBuilt = true
        selectionRestored = false
        rowAdapters.clear()
        bucketIndexByKey = buckets.mapIndexed { index, bucket -> bucket.timeBucket to index }.toMap()

        val adapter = ArrayObjectAdapter(ListRowPresenter())
        val thisMonth = getString(R.string.this_month)
        buckets.forEachIndexed { index, bucket ->
            val cardAdapter = ArrayObjectAdapter(CardPresenter(requireContext(), R.style.TimelineCardTheme))
            rowAdapters[bucket.timeBucket] = cardAdapter
            val header = HeaderItem(
                index.toLong(),
                TimelineDateFormatter.label(bucket.timeBucket, thisMonthLabel = thisMonth)
            )
            header.contentDescription = bucket.timeBucket
            adapter.add(ListRow(header, cardAdapter))
        }
        rowsAdapter = adapter
        this.adapter = adapter
        mainFragmentAdapter.fragmentHost?.notifyDataReady(mainFragmentAdapter)
    }

    private fun populateRow(timeBucket: String, assets: List<nl.giejay.android.tv.immich.api.model.TimelineAsset>) {
        val rowAdapter = rowAdapters[timeBucket] ?: return
        if (rowAdapter.size() > 0) return
        rowAdapter.addAll(0, assets.map { it.toCard() })
    }

    private fun restoreSelectionIfNeeded() {
        if (selectionRestored || !rowsBuilt || !isAdded) return
        val timeBucket = viewModel.lastSelectedTimeBucket ?: return
        val assetId = viewModel.lastSelectedAssetId ?: return
        val rowIndex = bucketIndexByKey[timeBucket] ?: return
        val rowAdapter = rowAdapters[timeBucket] ?: return
        if (rowAdapter.size() == 0) {
            // Ensure the remembered month is loaded before we can restore focus
            lifecycleScope.launch(Dispatchers.IO) {
                viewModel.loadBucket(timeBucket)
            }
            return
        }
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
        val flat = viewModel.flatAssetIndex()
        if (flat.isEmpty()) return
        val sliderItems = flat.map { it.second.toSliderItemViewHolder() }
        val startIndex = sliderItems.indexOfFirst { it.ids().contains(card.id) }.coerceAtLeast(0)

        val loadMore: LoadMore = suspend loadMore@{
            val next = viewModel.nextUnloadedBucket() ?: return@loadMore emptyList()
            val knownIds = viewModel.flatAssetIndex().map { it.second.id }.toSet()
            viewModel.loadBucket(next.timeBucket)
            withContext(Dispatchers.Main) {
                viewModel.bucketAssets.value[next.timeBucket]?.let { populateRow(next.timeBucket, it) }
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

    private fun bucketKeyForHeaderId(id: Long): String? =
        bucketIndexByKey.entries.firstOrNull { it.value.toLong() == id }?.key
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
