package nl.giejay.android.tv.immich.shared.fragment

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.View
import android.widget.Toast
import androidx.leanback.app.BackgroundManager
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.FocusHighlight
import androidx.leanback.widget.VerticalGridPresenter
import androidx.navigation.fragment.findNavController
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
import nl.giejay.android.tv.immich.card.Card
import nl.giejay.android.tv.immich.card.CardPresenterSelector
import nl.giejay.android.tv.immich.home.HomeFragmentDirections
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import retrofit2.Response
import timber.log.Timber


abstract class VerticalCardGridFragment<ITEM, RESPONSE_TYPE> : GridFragment() {
    protected var response: RESPONSE_TYPE? = null
    protected var assets: List<ITEM> = emptyList()

    private lateinit var mMetrics: DisplayMetrics
    private var mBackgroundManager: BackgroundManager? = null

    private lateinit var apiClient: ApiClient;
    private val ZOOM_FACTOR = FocusHighlight.ZOOM_FACTOR_SMALL
    private val ioScope = CoroutineScope(Job() + Dispatchers.IO)
    private val mainScope = CoroutineScope(Job() + Dispatchers.Main)
    private val assetsStillToRender: MutableList<ITEM> = mutableListOf()
    private val selectedItems: MutableList<ITEM> = mutableListOf()
    private var selectionMode: Boolean = false

    abstract fun sortItems(items: List<ITEM>): List<ITEM>
    abstract fun loadItems(apiClient: ApiClient): Response<RESPONSE_TYPE>
    abstract fun mapResponseToItems(response: RESPONSE_TYPE): List<ITEM>
    abstract fun createCard(a: ITEM): Card
    abstract fun getPicture(it: ITEM): String?
    open fun setTitle(response: RESPONSE_TYPE) {
        // default no title
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (PreferenceManager.isLoggedId()) {
            apiClient =
                ApiClient.getClient(PreferenceManager.hostName(), PreferenceManager.apiKey())
        } else {
            Toast.makeText(
                ImmichApplication.appContext,
                "Invalid Immich server settings, redirecting to login screen.",
                Toast.LENGTH_SHORT
            ).show()
            findNavController().navigate(HomeFragmentDirections.actionGlobalSignInFragment())
            return
        }

        selectionMode = arguments?.getBoolean("selectionMode", false) ?: false

        setupAdapter()
        setupBackgroundManager()
        setOnItemViewSelectedListener { _, item, _, _ ->
//            item?.let {
//                loadBackground((it as Card).pictureUrl) {
//                    loadBackground(it.thumbnailUrl) {
//                        Timber.tag(javaClass.name)
//                            .e("Could not load background url")
//                    }
//                }
//            }
            val selectedIndex = adapter.indexOf(item);
            if (selectedIndex != -1 && (adapter.size() - selectedIndex < FETCH_NEXT_THRESHOLD)) {
                mainScope.launch {
                    addAssetsPaginated()
                }
            }
        }

        ioScope.launch {
            loadData()?.apply {
                setupViews(this, mapResponseToItems(this))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (response != null) {
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

    private suspend fun setupViews(response: RESPONSE_TYPE, assets: List<ITEM>) =
        withContext(Dispatchers.Main) {
            progressBar?.visibility = View.GONE
            this@VerticalCardGridFragment.response = response
            val sortedItems = sortItems(assets)
            this@VerticalCardGridFragment.assets = sortedItems
            setTitle(response)
            assets.firstOrNull()?.let { loadBackground(getPicture(it)) {} }
            assetsStillToRender.addAll(sortedItems)
            addAssetsPaginated()
        }

    private suspend fun loadData(): RESPONSE_TYPE? {
        return try {
            val res = loadItems(apiClient)
            when (val code = res.code()) {
                200 -> {
                    return res.body()
                }

                else -> {
                    withContext(Dispatchers.Main) {
                        Timber.e("Could not fetch items! Status code: $code")
                        // todo use either
                        Toast.makeText(
                            ImmichApplication.appContext,
                            "Could not fetch items! Status code: $code",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    return null
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Timber.e(e, "Could not fetch items")
                // todo use either
                Toast.makeText(
                    ImmichApplication.appContext,
                    "Could not fetch items! ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
            null
        }
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
        // tryout preloading
//        assetsPaginated.forEach {
//            Glide.with(requireContext())
//                .asBitmap()
//                .load(getPicture(it))
//                .submit()
//        }
        val cards = assetsPaginated.map { createCard(it) }
        adapter.addAll(adapter.size(), cards)
        assetsStillToRender.removeAll(assetsPaginated)
    }


    companion object {
        private const val COLUMNS = 4
        private const val FETCH_NEXT_THRESHOLD = COLUMNS * 3
        private const val FETCH_COUNT = COLUMNS * 3
    }
}