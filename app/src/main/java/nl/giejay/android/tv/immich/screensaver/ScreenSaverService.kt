package nl.giejay.android.tv.immich.screensaver

import android.annotation.SuppressLint
import android.service.dreams.DreamService
import android.view.KeyEvent
import android.widget.Toast
import androidx.media3.datasource.DefaultHttpDataSource
import arrow.core.Either
import arrow.core.flatten
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
import nl.giejay.android.tv.immich.shared.prefs.API_KEY
import nl.giejay.android.tv.immich.shared.prefs.ContentType
import nl.giejay.android.tv.immich.shared.prefs.DEBUG_MODE
import nl.giejay.android.tv.immich.shared.prefs.DISABLE_SSL_VERIFICATION
import nl.giejay.android.tv.immich.shared.prefs.HOST_NAME
import nl.giejay.android.tv.immich.shared.prefs.MetaDataScreen
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import nl.giejay.android.tv.immich.shared.prefs.SCREENSAVER_ALBUMS
import nl.giejay.android.tv.immich.shared.prefs.SCREENSAVER_ANIMATE_ASSET_SLIDE
import nl.giejay.android.tv.immich.shared.prefs.SCREENSAVER_INCLUDE_VIDEOS
import nl.giejay.android.tv.immich.shared.prefs.SCREENSAVER_INTERVAL
import nl.giejay.android.tv.immich.shared.prefs.SCREENSAVER_PLAY_SOUND
import nl.giejay.android.tv.immich.shared.prefs.SCREENSAVER_TYPE
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_ANIMATION_SPEED
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
import nl.giejay.mediaslider.config.MediaSliderConfiguration
import nl.giejay.mediaslider.model.MetaDataType
import nl.giejay.mediaslider.util.LoadMore
import nl.giejay.mediaslider.util.MediaSliderListener
import nl.giejay.mediaslider.view.MediaSliderView
import timber.log.Timber

// internal so app/src/test can call it directly without instantiating ScreenSaverService
internal fun <T> Either<String, T>.getOrElseLogged(logContext: String, default: T): T =
    this.onLeft { error -> Timber.w("Failed to load assets for %s: %s", logContext, error) }
        .getOrElse { default }

class ScreenSaverService : DreamService(), MediaSliderListener {
    private val ioScope = CoroutineScope(Job() + Dispatchers.IO)
    private lateinit var apiClient: ApiClient
    private var mediaSliderView: MediaSliderView? = null
    private var currentPage = 0
    private var doneLoading: Boolean = false

    @SuppressLint("UnsafeOptInUsageError")
    override fun onDreamingStarted() {
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
            if (ScreenSaverType.ALBUMS == PreferenceManager.get(SCREENSAVER_TYPE)) {
                loadImagesFromAlbums(PreferenceManager.get(SCREENSAVER_ALBUMS).toList())
            } else {
                loadRandomImages(PreferenceManager.get(SCREENSAVER_TYPE)).invoke().map {
                    setInitialAssets(it, suspend {
                        if (doneLoading) {
                            emptyList()
                        } else {
                            currentPage += 1
                            val newAssets = loadRandomImages(PreferenceManager.get(SCREENSAVER_TYPE)).invoke()
                                .getOrElseLogged("screensaver random/recent/similar loadMore", emptyList())
                            doneLoading = newAssets.size < PAGE_COUNT
                            newAssets.toSliderItems(false, PreferenceManager.get(SLIDER_MERGE_PORTRAIT_PHOTOS))
                        }
                    })
                }
            }
        }
    }

    override fun onDreamingStopped() {
        mediaSliderView?.onDestroy()
        super.onDreamingStopped()
    }

    private fun loadRandomImages(screenSaverType: ScreenSaverType): suspend () -> Either<String, List<Asset>> {
        val contentType = if (PreferenceManager.get(SCREENSAVER_INCLUDE_VIDEOS)) ContentType.ALL else ContentType.IMAGE
        when (screenSaverType) {
            ScreenSaverType.RECENT -> {
                return suspend {
                    apiClient.recentAssets(currentPage, PAGE_COUNT, contentType = contentType)
                }
            }

            ScreenSaverType.SIMILAR_TIME_PERIOD -> {
                return suspend {
                    apiClient.similarAssets(currentPage, PAGE_COUNT, contentType = contentType)
                }
            }

            else -> {
                // random
                return suspend {
                    apiClient.listAssets(currentPage, PAGE_COUNT, true, contentType = contentType)
                }
            }
        }
    }

    private suspend fun loadImagesFromAlbums(albums: List<String>) {
        try {
            if (albums.isNotEmpty()) {
                // first load x random assets from each album. Then load all assets from all albums.
                val initialAssets = loadNextAssetsFromAlbums(albums, random = true)
                setInitialAssets(initialAssets, suspend {
                    loadNextAssetsFromAlbums(albums, random = false).toSliderItems(false, PreferenceManager.get(SLIDER_MERGE_PORTRAIT_PHOTOS))
                })
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

    private fun loadNextAssetsFromAlbums(albums: List<String>, random: Boolean = false): List<Asset> {
        val contentType = if (PreferenceManager.get(SCREENSAVER_INCLUDE_VIDEOS)) ContentType.ALL else ContentType.IMAGE
        val assets = if (random) {
            val pageCount = (50 / albums.size).coerceAtLeast(1)
            albums.pmap { albumId ->
                apiClient.listAssets(
                    page = 1,
                    pageCount = pageCount,
                    random = true,
                    contentType = contentType,
                    albumIds = listOf(albumId)
                ).getOrElse { emptyList() }
            }.flatten()
        } else {
            apiClient.listAssetsFromAlbum(albums, contentType, pageCount = 1000).getOrElse { emptyList() }
        }

        return assets.shuffled()
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
                    transformation = PreferenceManager.get(SLIDER_GLIDE_TRANSFORMATION),
                    debugEnabled = PreferenceManager.get(DEBUG_MODE),
                    enableSlideAnimation = PreferenceManager.get(SCREENSAVER_ANIMATE_ASSET_SLIDE),
                    gradiantOverlay = true,
                    metaDataConfig = PreferenceManager.getAllMetaData(MetaDataScreen.SCREENSAVER),
                    zoomAndScrollPanorama = PreferenceManager.get(SLIDER_ZOOM_SCROLL_PANORAMAS),
                    zoomEffectPercent = PreferenceManager.get(SLIDER_ZOOM_EFFECT),
                    panEffectPercent = PreferenceManager.get(SLIDER_PAN_EFFECT),
                    useLargeVideoBuffer = PreferenceManager.get(SLIDER_FORCE_ORIGINAL_VIDEO)
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
