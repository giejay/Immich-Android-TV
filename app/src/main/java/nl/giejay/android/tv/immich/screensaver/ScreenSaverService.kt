package nl.giejay.android.tv.immich.screensaver

import android.annotation.SuppressLint
import android.service.dreams.DreamService
import android.view.KeyEvent
import android.widget.Toast
import androidx.media3.datasource.DefaultHttpDataSource
import arrow.core.Either
import arrow.core.getOrElse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.api.ApiClient
import nl.giejay.android.tv.immich.api.ApiClientConfig
import nl.giejay.android.tv.immich.api.model.Asset
import nl.giejay.android.tv.immich.api.model.AssetResponse
import nl.giejay.android.tv.immich.shared.prefs.API_KEY
import nl.giejay.android.tv.immich.shared.prefs.ContentType
import nl.giejay.android.tv.immich.shared.prefs.DEBUG_MODE
import nl.giejay.android.tv.immich.shared.prefs.DISABLE_SSL_VERIFICATION
import nl.giejay.android.tv.immich.shared.prefs.EXCLUDE_ASSETS_IN_ALBUM
import nl.giejay.android.tv.immich.shared.prefs.MetaDataScreen
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import nl.giejay.android.tv.immich.shared.prefs.SCREENSAVER_ALBUMS
import nl.giejay.android.tv.immich.shared.prefs.SCREENSAVER_ANIMATE_ASSET_SLIDE
import nl.giejay.android.tv.immich.shared.prefs.SCREENSAVER_INCLUDE_VIDEOS
import nl.giejay.android.tv.immich.shared.prefs.SCREENSAVER_INTERVAL
import nl.giejay.android.tv.immich.shared.prefs.SCREENSAVER_PLAY_SOUND
import nl.giejay.android.tv.immich.shared.prefs.SCREENSAVER_TYPE
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_ANIMATION_SPEED
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_DPAD_SEEK_IN_VIDEO
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_FORCE_ORIGINAL_VIDEO
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_GLIDE_TRANSFORMATION
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_MAX_CUT_OFF_HEIGHT
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_MERGE_PORTRAIT_PHOTOS
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_ONLY_USE_THUMBNAILS
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_PAN_EFFECT
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_ZOOM_EFFECT
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_ZOOM_SCROLL_PANORAMAS
import nl.giejay.android.tv.immich.shared.util.Utils.pmap
import nl.giejay.android.tv.immich.shared.util.toSliderItems
import nl.giejay.android.tv.immich.slider.FavoriteService
import nl.giejay.mediaslider.config.MediaSliderConfiguration
import nl.giejay.mediaslider.util.LoadMore
import nl.giejay.mediaslider.util.LoadMoreResult
import nl.giejay.mediaslider.util.MediaSliderListener
import nl.giejay.mediaslider.view.MediaSliderView
import timber.log.Timber

// internal so app/src/test can call it directly without instantiating ScreenSaverService
internal fun <T> Either<String, T>.getOrElseLogged(logContext: String, default: T): T =
    this.onLeft { error -> Timber.w("Failed to load assets for %s: %s", logContext, error) }
        .getOrElse { default }

internal fun filterExcludedAssets(assets: List<Asset>, excludedAssetIds: Set<String>): List<Asset> =
    assets.filter { asset -> asset.tags?.none { it.name == "exclude_immich_tv" } ?: true }
        .filterNot { excludedAssetIds.contains(it.id) }

class ScreenSaverService : DreamService(), MediaSliderListener {
    private var ioScope = CoroutineScope(Job() + Dispatchers.IO)
    private lateinit var apiClient: ApiClient
    private val favoriteService = FavoriteService()
    private var mediaSliderView: MediaSliderView? = null
    private var currentPage = 0
    private var excludedAssetIds: Set<String> = emptySet()
    private val filteredAssetLoader = FilteredAssetLoader(
        filterAssets = { filterExcludedAssets(it, excludedAssetIds) }
    )

    @SuppressLint("UnsafeOptInUsageError")
    override fun onDreamingStarted() {
        ioScope = CoroutineScope(Job() + Dispatchers.IO)
        filteredAssetLoader.reset()
        Timber.i("Starting screensaver")
        if (!PreferenceManager.isLoggedId()) {
            showErrorMessage(getString(R.string.screensaver_not_possible))
            finish()
            return
        }
        val apiKey = PreferenceManager.get(API_KEY)
        val config = ApiClientConfig(
            PreferenceManager.hostName,
            apiKey,
            PreferenceManager.get(DISABLE_SSL_VERIFICATION),
            PreferenceManager.get(DEBUG_MODE)
        )
        apiClient = ApiClient.getClient(config)
        mediaSliderView = MediaSliderView(this)
        mediaSliderView!!.setDefaultExoFactory(
            DefaultHttpDataSource.Factory()
                .setDefaultRequestProperties(mapOf("x-api-key" to apiKey))
        )
        setContentView(mediaSliderView)
        isInteractive = true
        ioScope.launch {
            val excludedAlbums = PreferenceManager.get(EXCLUDE_ASSETS_IN_ALBUM)
            if (excludedAlbums.isNotEmpty()) {
                excludedAssetIds = apiClient.listAssetsFromAlbum(excludedAlbums.toList(), pageCount = 5000)
                    .getOrElse { AssetResponse(emptyList(), false) }
                    .assets
                    .map { it.id }
                    .toSet()
            }

            if (ScreenSaverType.ALBUMS == PreferenceManager.get(SCREENSAVER_TYPE)) {
                loadImagesFromAlbums(PreferenceManager.get(SCREENSAVER_ALBUMS).toList())
            } else {
                val screenSaverType = PreferenceManager.get(SCREENSAVER_TYPE)
                filteredAssetLoader.load { loadNextAssets(screenSaverType) }.map { response ->
                    setInitialAssets(
                        response.assets,
                        if (response.canLoadMore) createLoadMore { loadNextAssets(screenSaverType) } else null
                    )
                }
            }
        }
    }

    override fun onDreamingStopped() {
        ioScope.cancel()
        mediaSliderView?.onDestroy()
        super.onDreamingStopped()
    }

    private suspend fun loadNextAssets(screenSaverType: ScreenSaverType): Either<String, AssetResponse> {
        val contentType = if (PreferenceManager.get(SCREENSAVER_INCLUDE_VIDEOS)) ContentType.ALL else ContentType.IMAGE
        val page = currentPage
        currentPage += 1
        return when (screenSaverType) {
            ScreenSaverType.RECENT -> apiClient.recentAssets(page, PAGE_COUNT, contentType = contentType)
            ScreenSaverType.SIMILAR_TIME_PERIOD -> apiClient.similarAssets(page, PAGE_COUNT, contentType = contentType)
            else -> apiClient.listAssets(page, PAGE_COUNT, true, contentType = contentType)
        }
    }

    private fun createLoadMore(fetch: suspend () -> Either<String, AssetResponse>): LoadMore = suspend {
        filteredAssetLoader.load(fetch).fold(
            { error ->
                Timber.w("Failed to load more screensaver assets: %s", error)
                LoadMoreResult(emptyList(), false)
            },
            { response ->
                LoadMoreResult(
                    response.assets.toSliderItems(false, PreferenceManager.get(SLIDER_MERGE_PORTRAIT_PHOTOS)),
                    response.canLoadMore
                )
            }
        )
    }

    private suspend fun loadImagesFromAlbums(albums: List<String>) {
        try {
            if (albums.isNotEmpty()) {
                var loadRandomAssets = true
                val initialResponse = filteredAssetLoader.load {
                    if (loadRandomAssets) {
                        loadRandomAssets = false
                        loadNextAssetsFromAlbums(albums, random = true).fold(
                            {
                                Timber.w("Could not load random assets for albums $albums, falling back to all album assets")
                                loadNextAssetsFromAlbums(albums, random = false)
                            },
                            { Either.Right(it) }
                        )
                    } else {
                        Timber.w("Random album sample was empty after exclusions for albums $albums, checking their complete asset lists")
                        loadNextAssetsFromAlbums(albums, random = false)
                    }
                }.getOrElse { throw IllegalStateException(it) }

                setInitialAssets(
                    initialResponse.assets,
                    if (initialResponse.canLoadMore) {
                        createLoadMore { loadNextAssetsFromAlbums(albums, random = false) }
                    } else null
                )
            } else {
                showErrorMessageMainScope(getString(R.string.set_albums_screensaver_error))
                finish()
            }
        } catch (e: Exception) {
            Timber.e(e, "Could not fetch assets from Immich for Screensaver")
            showErrorMessageMainScope(getString(R.string.could_not_load_assets))
            finish()
        }
    }

    private suspend fun loadNextAssetsFromAlbums(albums: List<String>, random: Boolean = false): Either<String, AssetResponse> {
        val contentType = if (PreferenceManager.get(SCREENSAVER_INCLUDE_VIDEOS)) ContentType.ALL else ContentType.IMAGE
        if (random) {
            val pageCount = (50 / albums.size).coerceAtLeast(1)
            val responses = albums.pmap { albumId ->
                apiClient.listAssets(
                    page = 1,
                    pageCount = pageCount,
                    random = true,
                    contentType = contentType,
                    albumIds = listOf(albumId)
                )
            }
            responses.firstOrNull { it.isLeft() }?.let { return it }
            return Either.Right(AssetResponse(
                responses.flatMap { it.getOrElse { AssetResponse(emptyList(), true) }.assets }.shuffled(),
                true
            ))
        } else {
            return apiClient.listAssetsFromAlbum(albums, contentType, pageCount = 1000)
        }
    }

    private suspend fun showErrorMessageMainScope(errorMessage: String) {
        withContext(Dispatchers.Main) {
            showErrorMessage(errorMessage)
        }
    }

    private fun showErrorMessage(errorMessage: String) {
        Toast.makeText(
            this@ScreenSaverService,
            errorMessage,
            Toast.LENGTH_SHORT
        ).show()
    }

    private suspend fun setInitialAssets(assets: List<Asset>, loadMore: LoadMore?) = withContext(Dispatchers.Main) {
        if (assets.isEmpty()) {
            Toast.makeText(this@ScreenSaverService,
                getString(R.string.no_assets_for_screensaver),
                Toast.LENGTH_LONG).show()
        } else {
            val enabledPlugins = PreferenceManager.createEnabledSliderPlugins(ioScope, favoriteService)
            mediaSliderView?.loadMediaSliderView(
                MediaSliderConfiguration(
                    0,
                    PreferenceManager.get(SCREENSAVER_INTERVAL),
                    PreferenceManager.get(SLIDER_ONLY_USE_THUMBNAILS),
                    PreferenceManager.get(SCREENSAVER_PLAY_SOUND),
                    assets.toSliderItems(keepOrder = false, mergePortrait = PreferenceManager.get(SLIDER_MERGE_PORTRAIT_PHOTOS)),
                    loadMore,
                    animationSpeedMillis = PreferenceManager.get(SLIDER_ANIMATION_SPEED),
                    maxCutOffHeight = PreferenceManager.get(SLIDER_MAX_CUT_OFF_HEIGHT),
                    maxCutOffWidth = PreferenceManager.get(SLIDER_MAX_CUT_OFF_HEIGHT),
                    glideTransformation = PreferenceManager.get(SLIDER_GLIDE_TRANSFORMATION),
                    enableSlideAnimation = PreferenceManager.get(SCREENSAVER_ANIMATE_ASSET_SLIDE),
                    gradiantOverlay = true,
                    metaDataConfig = PreferenceManager.getAllMetaData(MetaDataScreen.SCREENSAVER),
                    zoomAndScrollPanorama = PreferenceManager.get(SLIDER_ZOOM_SCROLL_PANORAMAS),
                    zoomEffectPercent = PreferenceManager.get(SLIDER_ZOOM_EFFECT),
                    panEffectPercent = PreferenceManager.get(SLIDER_PAN_EFFECT),
                    useLargeVideoBuffer = PreferenceManager.get(SLIDER_FORCE_ORIGINAL_VIDEO),
                    dpadSeeksInVideo = PreferenceManager.get(SLIDER_DPAD_SEEK_IN_VIDEO),
                    controllerPlugins = enabledPlugins.controllerPlugins,
                    viewPlugins = enabledPlugins.viewPlugins,
                    keyEventPlugins = enabledPlugins.keyEventPlugins
                )
            )
            mediaSliderView?.toggleSlideshow(false)
        }
    }

    companion object ScreenSaverService {
        private const val PAGE_COUNT = 100
    }

    override fun onButtonPressed(keyEvent: KeyEvent): Boolean {
        if ((keyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyEvent.keyCode == KeyEvent.KEYCODE_ENTER) && mediaSliderView?.isControllerVisible() == false) {
            finish()
            return true
        }
        return false
    }
}
