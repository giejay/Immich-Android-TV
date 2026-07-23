package nl.giejay.android.tv.immich.shared.fragment

import android.annotation.SuppressLint
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.leanback.app.BackgroundManager
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.FocusHighlight
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.OnItemViewSelectedListener
import androidx.leanback.widget.VerticalGridPresenter
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import arrow.core.Either
import arrow.core.getOrElse
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.SimpleTarget
import com.bumptech.glide.request.transition.Transition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.giejay.android.tv.immich.ImmichApplication
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.api.ApiClient
import nl.giejay.android.tv.immich.api.ApiClientConfig
import nl.giejay.android.tv.immich.api.model.TimeBucketSummary
import nl.giejay.android.tv.immich.card.Card
import nl.giejay.android.tv.immich.card.CardPresenterSelector
import nl.giejay.android.tv.immich.home.HomeFragmentDirections
import nl.giejay.android.tv.immich.shared.prefs.API_KEY
import nl.giejay.android.tv.immich.shared.prefs.DEBUG_MODE
import nl.giejay.android.tv.immich.shared.prefs.DISABLE_SSL_VERIFICATION
import nl.giejay.android.tv.immich.shared.prefs.HOST_NAME
import nl.giejay.android.tv.immich.shared.prefs.LOAD_BACKGROUND_IMAGE
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import nl.giejay.android.tv.immich.shared.util.Debouncer
import nl.giejay.android.tv.immich.shared.util.Utils
import nl.giejay.android.tv.immich.shared.viewmodel.KeyEventsViewModel
import nl.giejay.android.tv.immich.timeline.TimelineScrubberModel
import nl.giejay.android.tv.immich.timeline.TimelineScrubberView
import nl.giejay.android.tv.immich.timeline.TimelineViewModel
import timber.log.Timber
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit


abstract class VerticalCardGridFragment<ITEM> : GridFragment() {
    protected var assets: List<ITEM> = emptyList()
    protected val startPage = 1

    private lateinit var mMetrics: DisplayMetrics
    private var mBackgroundManager: BackgroundManager? = null

    protected lateinit var apiClient: ApiClient
    protected lateinit var keyEvents: KeyEventsViewModel
    private val ZOOM_FACTOR = FocusHighlight.ZOOM_FACTOR_SMALL
    protected val ioScope = CoroutineScope(Job() + Dispatchers.IO)
    private val mainScope = CoroutineScope(Job() + Dispatchers.Main)
    protected val assetsStillToRender: MutableList<ITEM> = mutableListOf()
    protected var currentPage: Int = startPage
    protected var allPagesLoaded: Boolean = false
    private var currentLoadingJob: Job? = null
    protected val selectionMode: Boolean
        get() = arguments?.getBoolean("selectionMode", false) ?: false
    protected var currentSelectedIndex: Int = 0

    protected var railContainer: LinearLayout? = null
    protected var settingsButton: ImageButton? = null

    abstract fun sortItems(items: List<ITEM>): List<ITEM>
    abstract suspend fun loadItems(
        apiClient: ApiClient,
        page: Int,
        pageCount: Int
    ): Either<String, List<ITEM>>

    abstract fun createCard(a: ITEM): Card
    abstract fun getBackgroundPicture(it: ITEM): String?
    open fun setTitle(response: List<ITEM>) {
        // default no title
    }

    abstract fun onItemSelected(card: Card, indexOf: Int)
    abstract fun onItemClicked(card: Card)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (PreferenceManager.isLoggedId()) {
            apiClient =
                ApiClient.getClient(
                    ApiClientConfig(
                        PreferenceManager.hostName,
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

        keyEvents = ViewModelProvider(requireActivity())[KeyEventsViewModel::class.java]
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                keyEvents.state.collect { event ->
                    if (event?.action == KeyEvent.ACTION_DOWN) {
                        when (event.keyCode) {
                            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                if (currentSelectedIndex >= 0 && (currentSelectedIndex % COLUMNS == 3 || currentSelectedIndex + 1 == adapter.size())) {
                                    onExitRightFromGrid()
                                }
                            }
                            KeyEvent.KEYCODE_FORWARD, KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                                updateManualPositionHandler(adapter.size() - 1)
                            }
                            KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD -> {
                                updateManualPositionHandler((currentSelectedIndex - FETCH_PAGE_COUNT).coerceAtLeast(0))
                            }
                        }
                    }
                }
            }
        }

        setupAdapter()
        setupBackgroundManager()

        onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
            val card: Card = item as Card
            if (selectionMode) {
                card.selected = !card.selected
                adapter.notifyArrayItemRangeChanged(currentSelectedIndex, 1)
                onItemSelected(card, currentSelectedIndex)
            } else {
                onItemClicked(card)
            }
        }
        setOnItemViewSelectedListener { _, item, _, _ ->
            currentSelectedIndex = adapter.indexOf(item)
            item?.let {
                loadBackgroundDebounced((it as Card).backgroundUrl) {
                    loadBackgroundDebounced(it.thumbnailUrl) {
                        Timber.tag(javaClass.name)
                            .e("Could not load background url")
                    }
                }
            }
            with(this@VerticalCardGridFragment) {
                val position = adapter.indexOf(item)
                loadNextItemsIfNeeded(position)
                updateOnSelected(position)
            }
        }
        // fetch initial items
        fetchInitialItems()
    }

    protected open fun updateOnSelected(position: Int) {
        // to implement by children
    }

    protected open fun onExitRightFromGrid() {
        if (currentSelectedIndex < COLUMNS) {
            settingsButton?.post { settingsButton?.requestFocus() }
        } else if (settingsButton?.visibility == View.VISIBLE) {
            settingsButton?.post { settingsButton?.requestFocus() }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        injectRightRail(view)
    }

    private fun injectRightRail(view: View) {
        val gridDock: ViewGroup = view.findViewById(R.id.browse_grid_dock)
        val rail = layoutInflater.inflate(R.layout.right_rail, gridDock, false) as LinearLayout
        gridDock.addView(rail)
        rail.bringToFront()
        
        railContainer = rail
        settingsButton = rail.findViewById(R.id.rail_settings_button)
        settingsButton?.apply {
            nextFocusDownId = R.id.rail_scrubber
            setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    v.animate().scaleX(2.0f).scaleY(2.0f).setDuration(200).start()
                    v.elevation = Utils.convertDpToPixel(requireContext(), 32).toFloat()
                } else {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start()
                    v.elevation = 0f
                }
            }
        }
    }

    protected fun fetchInitialItems() {
        ioScope.launch {
            loadData().fold(
                { itLeft -> showErrorMessage(itLeft) },
                { itRight ->
                    val assets = filterItems(itRight)
                    setupViews(assets)
                    if (assets.size < FETCH_COUNT) {
                        // immediately load next assets
                        currentLoadingJob = fetchNextItems()
                    }
                }
            )
        }
    }

    private fun loadNextItemsIfNeeded(selectedIndex: Int) {
        if (selectedIndex != -1 && (adapter.size() - selectedIndex < FETCH_NEXT_THRESHOLD)) {
            if (currentLoadingJob?.isActive != true && assetsStillToRender.isEmpty() && !allPagesLoaded) {
                // try a next page if its available
                currentLoadingJob = fetchNextItems()
            } else {
                mainScope.launch {
                    addAssetsPaginated()
                }
            }
        }
    }

    protected open fun resortItems() {
        assets = sortItems(assets)
        adapter.clear()
        adapter.addAll(0, assets.map { createCard(it) })
    }

    protected open fun clearState() {
        currentPage = startPage
        assets = emptyList()
        assetsStillToRender.clear()
        adapter.clear()
    }

    protected open fun openPopUpMenu() {
        // to implement by children
    }

    private fun fetchNextItems(): Job {
        return ioScope.launch {
            val nextAssets = loadMoreAssets()
            setDataOnMain(nextAssets)
        }
    }

    protected open fun filterItems(items: List<ITEM>): List<ITEM> = items

    protected open suspend fun loadMoreAssets(): List<ITEM> {
        if (allPagesLoaded) {
            return emptyList()
        }
        currentPage += 1
        return loadData().fold(
            { errorMessage ->
                showErrorMessage(errorMessage)
                emptyList()
            },
            { items ->
                Timber.i("Loading next items, ${items.size}")
                val filteredItems = filterItems(items)
                allPagesLoaded = allPagesLoaded(items)
                if (filteredItems.size < FETCH_COUNT) {
                    return filteredItems + loadMoreAssets()
                } else  {
                    return filteredItems
                }
            }
        )
    }

    protected open fun allPagesLoaded(items: List<ITEM>): Boolean =
        items.size < FETCH_PAGE_COUNT

    override fun onResume() {
        super.onResume()
        if (assets.isNotEmpty()) {
            progressBar?.visibility = View.GONE
        }
    }

    private fun setupAdapter() {
        val presenter = VerticalGridPresenter(ZOOM_FACTOR)
        presenter.numberOfColumns = COLUMNS
        gridPresenter = presenter
        val cardPresenter = CardPresenterSelector(requireContext())
        adapter = ArrayObjectAdapter(cardPresenter)
    }

    private fun setupBackgroundManager() {
        mBackgroundManager = BackgroundManager.getInstance(activity)
        if (!mBackgroundManager!!.isAttached) {
            mBackgroundManager!!.attach(requireActivity().window)
        }
        mMetrics = DisplayMetrics()
        requireActivity().windowManager.defaultDisplay.getMetrics(mMetrics)
    }

    override fun onPause() {
        // Cancel pending hover-background loads so they don't paint onto Timeline later.
        Debouncer.cancel(BACKGROUND_DEBOUNCE_KEY)
        super.onPause()
    }

    override fun onDestroyView() {
        Debouncer.cancel(BACKGROUND_DEBOUNCE_KEY)
        super.onDestroyView()
        if (mBackgroundManager?.isAttached == true) {
            mBackgroundManager?.drawable = null
        }
    }

    private suspend fun setupViews(assets: List<ITEM>) =
        withContext(Dispatchers.Main) {
            progressBar?.visibility = View.GONE
            setTitle(assets)
            assets.firstOrNull()?.let { loadBackground(getBackgroundPicture(it)) {} }
            setDataOnMain(assets)
        }

    protected suspend fun setDataOnMain(assets: List<ITEM>) = withContext(Dispatchers.Main) {
        setData(assets)
    }

    protected open fun setData(assets: List<ITEM>) {
        val sortedItems = sortItems(assets.filter { !this@VerticalCardGridFragment.assets.contains(it) })
        this@VerticalCardGridFragment.assets += sortedItems
        assetsStillToRender.addAll(sortedItems)
        addAssetsPaginated()
    }

    protected open suspend fun loadData(): Either<String, List<ITEM>> {
        return loadItems(apiClient, currentPage, FETCH_PAGE_COUNT)
    }

    private fun loadBackgroundDebounced(backgroundUrl: String?, onLoadFailed: () -> Unit) {
        if (PreferenceManager.get(LOAD_BACKGROUND_IMAGE)) {
            Debouncer.debounce(
                BACKGROUND_DEBOUNCE_KEY,
                { loadBackground(backgroundUrl, onLoadFailed) },
                1,
                TimeUnit.SECONDS
            )
        }
    }

    private fun loadBackground(backgroundUrl: String?, onLoadFailed: () -> Unit) {
        if (!isAdded || !isResumed || !PreferenceManager.get(LOAD_BACKGROUND_IMAGE)) {
            return
        }
        if (backgroundUrl.isNullOrEmpty()) {
            Timber.i("Could not load background because background url is null")
            return
        }
        val width: Int = mMetrics.widthPixels
        val height: Int = mMetrics.heightPixels
        try {
            Glide.with(requireActivity())
                .load(backgroundUrl)
                .centerCrop()
                .into(object : SimpleTarget<Drawable?>(width, height) {
                    override fun onResourceReady(
                        resource: Drawable,
                        transition: Transition<in Drawable?>?
                    ) {
                        // Fragment may already be gone (e.g. user switched to Timeline).
                        if (!isAdded || !isResumed) return
                        try {
                            mBackgroundManager?.drawable = resource
                        } catch (e: Exception) {
                            Timber.e(e, "Could not set background")
                        }
                    }

                    override fun onLoadFailed(errorDrawable: Drawable?) {
                        if (!isAdded || !isResumed) return
                        onLoadFailed()
                    }
                })
        } catch (e: Exception) {
            Timber.e(e, "Could not fetch background")
        }
    }

    private fun addAssetsPaginated() {
        val assetsPaginated = assetsStillToRender.take(FETCH_COUNT)
        val cards = assetsPaginated.map { createCard(it) }
        adapter.addAll(adapter.size(), cards)
        assetsStillToRender.removeAll(assetsPaginated)
    }

    private suspend fun showErrorMessage(message: String) = withContext(Dispatchers.Main) {
        if (isAdded) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG)
                .show()
        }
        Timber.e(message)
    }

    companion object {
        const val COLUMNS = 4
        private const val FETCH_NEXT_THRESHOLD = COLUMNS * 6
        const val FETCH_COUNT = 50
        const val FETCH_PAGE_COUNT = FETCH_COUNT
        /** Shared with Timeline so it can cancel a pending Photos background paint. */
        const val BACKGROUND_DEBOUNCE_KEY = "background"
    }
}