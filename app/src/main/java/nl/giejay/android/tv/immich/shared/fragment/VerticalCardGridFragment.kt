package nl.giejay.android.tv.immich.shared.fragment

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.View
import android.widget.Toast
import androidx.leanback.app.BackgroundManager
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.FocusHighlight
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.VerticalGridPresenter
import androidx.navigation.fragment.findNavController
import arrow.core.Either
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.SimpleTarget
import com.bumptech.glide.request.transition.Transition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.giejay.android.tv.immich.ImmichApplication
import nl.giejay.android.tv.immich.api.ApiClient
import nl.giejay.android.tv.immich.api.ApiClientConfig
import nl.giejay.android.tv.immich.card.Card
import nl.giejay.android.tv.immich.card.CardPresenterSelector
import nl.giejay.android.tv.immich.home.HomeFragmentDirections
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import nl.giejay.android.tv.immich.shared.util.Debouncer
import timber.log.Timber
import java.util.concurrent.TimeUnit


abstract class VerticalCardGridFragment<ITEM> : GridFragment() {
    protected var assets: List<ITEM> = emptyList()
    protected val startPage = 1

    private lateinit var mMetrics: DisplayMetrics
    private var mBackgroundManager: BackgroundManager? = null

    private lateinit var apiClient: ApiClient;
    private val ZOOM_FACTOR = FocusHighlight.ZOOM_FACTOR_SMALL
    private val ioScope = CoroutineScope(Job() + Dispatchers.IO)
    private val mainScope = CoroutineScope(Job() + Dispatchers.Main)
    private val assetsStillToRender: MutableList<ITEM> = mutableListOf()
    private var currentPage: Int = startPage
    private var allPagesLoaded: Boolean = false
    private var currentLoadingJob: Job? = null
    protected val selectionMode: Boolean
        get() = arguments?.getBoolean("selectionMode", false) ?: false

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

    abstract fun onItemSelected(card: Card)
    abstract fun onItemClicked(card: Card)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (PreferenceManager.isLoggedId()) {
            apiClient =
                ApiClient.getClient(
                    ApiClientConfig(
                        PreferenceManager.hostName(),
                        PreferenceManager.apiKey(),
                        PreferenceManager.disableSslVerification(),
                        PreferenceManager.debugEnabled()
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

        setupAdapter()
        setupBackgroundManager()
        onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
            val card: Card = item as Card
            if (selectionMode) {
                card.selected = !card.selected
                adapter.notifyArrayItemRangeChanged(adapter.indexOf(item), 1)
                onItemSelected(card)
            } else {
                onItemClicked(card)
            }
        }
        setOnItemViewSelectedListener { _, item, _, _ ->
            item?.let {
                loadBackgroundDebounced((it as Card).backgroundUrl) {
                    loadBackgroundDebounced(it.thumbnailUrl) {
                        Timber.tag(javaClass.name)
                            .e("Could not load background url")
                    }
                }
            }
            with(this@VerticalCardGridFragment) {
                val selectedIndex = adapter.indexOf(item);
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
        }
        // fetch initial items
        ioScope.launch {
            loadData().fold(
                { itLeft -> showErrorMessage(itLeft) },
                { itRight -> setupViews(itRight) }
            )
        }
    }

    private fun fetchNextItems(): Job {
        return ioScope.launch {
            loadData().fold(
                { errorMessage ->
                    showErrorMessage(errorMessage)
                },
                { items ->
                    Timber.i("Loading next items, ${items.size}")
                    if (items.isNotEmpty()) {
                        setData(items)
                    }
                    allPagesLoaded = items.size < FETCH_PAGE_COUNT
                }
            )
        }
    }

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

    override fun onDestroyView() {
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
            setData(assets)
        }

    private suspend fun setData(assets: List<ITEM>) = withContext(Dispatchers.Main) {
        val sortedItems = sortItems(assets)
        this@VerticalCardGridFragment.assets += sortedItems
        assetsStillToRender.addAll(sortedItems)
        addAssetsPaginated()
        currentPage += 1
    }

    private suspend fun loadData(): Either<String, List<ITEM>> {
        return loadItems(apiClient, currentPage, FETCH_PAGE_COUNT)
    }

    private fun loadBackgroundDebounced(backgroundUrl: String?, onLoadFailed: () -> Unit){
        Debouncer.debounce("background", { loadBackground(backgroundUrl, onLoadFailed)}, 1, TimeUnit.SECONDS)
    }

    private fun loadBackground(backgroundUrl: String?, onLoadFailed: () -> Unit) {
        if (!isAdded) {
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
                        mBackgroundManager?.drawable = resource
                    }

                    override fun onLoadFailed(errorDrawable: Drawable?) {
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
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT)
                .show()
        }
        Timber.e(message)
    }

    companion object {
        private const val COLUMNS = 4
        private const val FETCH_NEXT_THRESHOLD = COLUMNS * 6
        private const val FETCH_COUNT = COLUMNS * 3
        private const val FETCH_PAGE_COUNT = 100
    }
}