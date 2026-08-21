package nl.giejay.android.tv.immich.screensaver

import androidx.annotation.StringRes
import arrow.core.Either
import arrow.core.getOrElse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
import nl.giejay.android.tv.immich.shared.prefs.SCREENSAVER_SHOW_DATE_TOP_LEFT
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
import timber.log.Timber

// internal so app/src/test can call it directly without instantiating ScreenSaverService
internal fun <T> Either<String, T>.getOrElseLogged(logContext: String, default: T): T =
    this.onLeft { error -> Timber.w("Failed to load assets for %s: %s", logContext, error) }
        .getOrElse { default }

internal fun filterExcludedAssets(assets: List<Asset>, excludedAssetIds: Set<String>): List<Asset> =
    assets.filter { asset -> asset.tags?.none { it.name == "exclude_immich_tv" } ?: true }
        .filterNot { excludedAssetIds.contains(it.id) }

/** Single construction site of the screensaver's ApiClientConfig, shared by both hosts. */
internal fun screenSaverApiClientConfig(): ApiClientConfig =
    ApiClientConfig(
        PreferenceManager.hostName,
        PreferenceManager.get(API_KEY),
        PreferenceManager.get(DISABLE_SSL_VERIFICATION),
        PreferenceManager.get(DEBUG_MODE)
    )

/**
 * THE single construction site of the screensaver MediaSliderConfiguration. Having exactly one
 * literal is what guarantees the in-app preview and the real DreamService stay identical.
 */
internal fun buildScreenSaverConfiguration(
    assets: List<Asset>,
    loadMore: LoadMore?,
    scope: CoroutineScope,
    favoriteService: FavoriteService
): MediaSliderConfiguration {
    val enabledPlugins = PreferenceManager.createEnabledSliderPlugins(scope, favoriteService)
    return MediaSliderConfiguration(
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
        showDateTopLeft = PreferenceManager.get(SCREENSAVER_SHOW_DATE_TOP_LEFT),
        controllerPlugins = enabledPlugins.controllerPlugins,
        viewPlugins = enabledPlugins.viewPlugins,
        keyEventPlugins = enabledPlugins.keyEventPlugins
    )
}

/**
 * Loads the assets for the Immich screensaver and builds its MediaSliderConfiguration.
 *
 * Context-agnostic on purpose: the host owns the view and all UI side effects, so the exact same
 * loading pipeline backs both [ScreenSaverService] and the in-app [ScreenSaverPreviewFragment].
 * Every [Host] callback is invoked on the main thread.
 */
class ScreenSaverAssetLoader(
    private val scope: CoroutineScope,
    private val apiClient: ApiClient,
    private val host: Host,
    private val favoriteService: FavoriteService = FavoriteService()
) {
    interface Host {
        /** Host resolves [messageRes] against its own Context and shows a toast. */
        fun showScreenSaverMessage(@StringRes messageRes: Int, longDuration: Boolean = false)

        /** DreamService.finish() for the dream, NavController.popBackStack() for the preview. */
        fun exitScreenSaver()

        /** Host calls loadMediaSliderView(configuration) and starts the slideshow. */
        fun onScreenSaverConfigurationReady(configuration: MediaSliderConfiguration)

        /**
         * True for the preview: leave the screen instead of sitting on black when there is
         * nothing to show. The real dream keeps its existing stay-on-black behaviour.
         */
        val exitWhenNothingToShow: Boolean
            get() = false
    }

    private var currentPage = 0
    private var excludedAssetIds: Set<String> = emptySet()
    private val filteredAssetLoader = FilteredAssetLoader(
        filterAssets = { filterExcludedAssets(it, excludedAssetIds) }
    )

    fun start(): Job = scope.launch(Dispatchers.IO) {
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
            filteredAssetLoader.load({ loadNextAssets(screenSaverType) }).fold(
                ifLeft = { error ->
                    Timber.w("Failed to load assets for screensaver type %s: %s", screenSaverType, error)
                    if (host.exitWhenNothingToShow) {
                        failAndExit(R.string.could_not_load_assets)
                    }
                },
                ifRight = { response ->
                    setInitialAssets(
                        response.assets,
                        if (response.canLoadMore) createLoadMore { loadNextAssets(screenSaverType) } else null
                    )
                }
            )
        }
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
                val response = filteredAssetLoader.load(fetch =
                    {
                        loadNextAssetsFromAlbums(albums, random = true).fold(
                            {
                                Timber.w("Could not load random assets for albums $albums, falling back to all album assets")
                                loadNextAssetsFromAlbums(albums, random = false)
                            },
                            {
                                Either.Right(it)
                            }
                        )
                    }, retry = {
                    // load assets not randomly. This can happen because the logged in User/API key does not have any own photo's, only shared access
                    // shared photos are not returned yet for the Random endpoint (ongoing feature request).
                    Timber.w("Random asset search for album was empty for albums $albums, now using the regular search assets in album endpoint")
                    loadNextAssetsFromAlbums(albums, random = false)
                }).getOrElse { AssetResponse(emptyList(), false) }

                setInitialAssets(
                    response.assets.shuffled(),
                    if (response.canLoadMore) {
                        createLoadMore { loadNextAssetsFromAlbums(albums, random = false) }
                    } else null
                )
            } else {
                failAndExit(R.string.set_albums_screensaver_error)
            }
        } catch (e: Exception) {
            Timber.e(e, "Could not fetch assets from Immich for Screensaver")
            failAndExit(R.string.could_not_load_assets)
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
            return apiClient.listAssetsFromAlbum(albums, contentType, pageCount = 1000).map { AssetResponse(it.assets.shuffled(), it.canLoadMore) }
        }
    }

    private suspend fun failAndExit(@StringRes messageRes: Int) = withContext(Dispatchers.Main) {
        host.showScreenSaverMessage(messageRes)
        host.exitScreenSaver()
    }

    private suspend fun setInitialAssets(assets: List<Asset>, loadMore: (suspend () -> LoadMoreResult)?) = withContext(Dispatchers.Main) {
        if (assets.isEmpty()) {
            host.showScreenSaverMessage(R.string.no_assets_for_screensaver, longDuration = true)
            if (host.exitWhenNothingToShow) {
                host.exitScreenSaver()
            }
        } else {
            host.onScreenSaverConfigurationReady(
                buildScreenSaverConfiguration(assets, loadMore, scope, favoriteService)
            )
        }
    }

    companion object {
        private const val PAGE_COUNT = 100
    }
}
