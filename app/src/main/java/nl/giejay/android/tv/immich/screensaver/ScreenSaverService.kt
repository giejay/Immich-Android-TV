package nl.giejay.android.tv.immich.screensaver

import android.annotation.SuppressLint
import android.service.dreams.DreamService
import android.view.KeyEvent
import android.widget.Toast
import androidx.media3.datasource.DefaultHttpDataSource
import arrow.core.Either
import arrow.core.getOrElse
import nl.giejay.mediaslider.model.MetaDataType
import nl.giejay.mediaslider.config.MediaSliderConfiguration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.giejay.android.tv.immich.api.ApiClient
import nl.giejay.android.tv.immich.api.ApiClientConfig
import nl.giejay.android.tv.immich.api.model.AlbumDetails
import nl.giejay.android.tv.immich.api.model.Asset
import nl.giejay.android.tv.immich.shared.prefs.API_KEY
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
import nl.giejay.android.tv.immich.shared.prefs.SCREENSAVER_SHOW_ALBUM_NAME
import nl.giejay.android.tv.immich.shared.prefs.SCREENSAVER_SHOW_CLOCK
import nl.giejay.android.tv.immich.shared.prefs.SCREENSAVER_SHOW_DATE
import nl.giejay.android.tv.immich.shared.prefs.SCREENSAVER_SHOW_DESCRIPTION
import nl.giejay.android.tv.immich.shared.prefs.SCREENSAVER_SHOW_MEDIA_COUNT
import nl.giejay.android.tv.immich.shared.prefs.SCREENSAVER_TYPE
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_ANIMATION_SPEED
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_GLIDE_TRANSFORMATION
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_MAX_CUT_OFF_HEIGHT
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_MERGE_PORTRAIT_PHOTOS
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_ONLY_USE_THUMBNAILS
import nl.giejay.android.tv.immich.shared.util.toSliderItems
import nl.giejay.mediaslider.util.LoadMore
import nl.giejay.mediaslider.util.MediaSliderListener
import nl.giejay.mediaslider.view.MediaSliderView
import timber.log.Timber
import java.util.EnumSet

class ScreenSaverService : DreamService(), MediaSliderListener {
    private val ioScope = CoroutineScope(Job() + Dispatchers.IO)
    private lateinit var apiClient: ApiClient
    private lateinit var mediaSliderView: MediaSliderView
    private var currentPage = 0
    private var doneLoading: Boolean = false

    @SuppressLint("UnsafeOptInUsageError")
    override fun onDreamingStarted() {
        Timber.i("Starting screensaver")
        if (!PreferenceManager.isLoggedId()) {
            showErrorMessage("Could not start screensaver for Immich because of invalid Hostname/API key")
            finish()
            return
        }
        val apiKey = PreferenceManager.get(API_KEY)
        val config = ApiClientConfig(
            PreferenceManager.get(HOST_NAME),
            apiKey,
            PreferenceManager.get(DISABLE_SSL_VERIFICATION),
            PreferenceManager.get(DEBUG_MODE)
        )
        apiClient = ApiClient.getClient(config)
        mediaSliderView = MediaSliderView(this)
        mediaSliderView.setDefaultExoFactory(
            DefaultHttpDataSource.Factory()
                .setDefaultRequestProperties(mapOf("x-api-key" to apiKey))
        )
        setContentView(mediaSliderView)
        isInteractive = true
        ioScope.launch {
            if (ScreenSaverType.ALBUMS == PreferenceManager.get(SCREENSAVER_TYPE)) {
                loadImagesFromAlbums(PreferenceManager.get(SCREENSAVER_ALBUMS))
            } else {
                loadRandomImages(PreferenceManager.get(SCREENSAVER_TYPE)).invoke().map {
                    setInitialAssets(it, suspend {
                        if (doneLoading) {
                            emptyList()
                        } else {
                            currentPage += 1
                            val newAssets = loadRandomImages(PreferenceManager.get(SCREENSAVER_TYPE)).invoke().getOrElse { emptyList() }
                            doneLoading = newAssets.size < PAGE_COUNT
                            newAssets.toSliderItems(false, PreferenceManager.get(SLIDER_MERGE_PORTRAIT_PHOTOS))
                        }
                    })
                }
            }
        }
    }

    override fun onDreamingStopped() {
        mediaSliderView.onDestroy()
        super.onDreamingStopped()
    }

    private fun loadRandomImages(screenSaverType: ScreenSaverType): suspend () -> Either<String, List<Asset>> {
        when (screenSaverType) {
            ScreenSaverType.RECENT -> {
                return suspend {
                    apiClient.recentAssets(currentPage, PAGE_COUNT, PreferenceManager.get(SCREENSAVER_INCLUDE_VIDEOS))
                }
            }

            ScreenSaverType.SIMILAR_TIME_PERIOD -> {
                return suspend {
                    apiClient.similarAssets(currentPage, PAGE_COUNT, PreferenceManager.get(SCREENSAVER_INCLUDE_VIDEOS))
                }
            }

            else -> {
                // random
                return suspend {
                    apiClient.listAssets(currentPage, PAGE_COUNT, true, includeVideos = PreferenceManager.get(SCREENSAVER_INCLUDE_VIDEOS))
                }
            }
        }
    }

    private suspend fun loadImagesFromAlbums(albums: Set<String>) {
        try {
            // first fetch one album, show the (first few) pictures, then fetch other albums and shuffle again
            if (albums.isNotEmpty()) {
                val shuffledAlbums = albums.toList().shuffled()
                // todo use timeline buckets to speed up loading
                apiClient.listAssetsFromAlbum(shuffledAlbums.first()).map { album ->
                    val randomAssets = getAssets(listOf(album))
                    setInitialAssets(randomAssets, null)
                    if (shuffledAlbums.size > 1) {
                        // load next ones
                        val nextAlbums = shuffledAlbums.drop(1).map { apiClient.listAssetsFromAlbum(it) }
                        val assets = getAssets(nextAlbums.flatMap { it.getOrNone().toList() })
                        setAllAssets((randomAssets + assets).shuffled().distinct())
                    }
                }
            } else {
                showErrorMessageMainScope("Set the Immich albums to show in the screensaver settings")
                finish()
            }
        } catch (e: Exception) {
            Timber.e(e, "Could not fetch assets from Immich for Screensaver")
            showErrorMessageMainScope("Could not load assets from Immich")
            finish()
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

    private fun getAssets(albums: List<AlbumDetails>): List<Asset> {
        return albums.flatMap { filterVideos(it.assets) }
    }

    private fun filterVideos(assets: List<Asset>) = if (PreferenceManager.get(SCREENSAVER_INCLUDE_VIDEOS)) {
        assets.shuffled()
    } else {
        assets.filter { it.type != "VIDEO" }.shuffled()
    }

    private suspend fun setInitialAssets(assets: List<Asset>, loadMore: LoadMore?) = withContext(Dispatchers.Main) {
        if (assets.isEmpty()) {
            Toast.makeText(this@ScreenSaverService,
                "No assets to show for screensaver. Please configure a different screensaver type in the settings.",
                Toast.LENGTH_LONG).show()
        } else {
            mediaSliderView.loadMediaSliderView(
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
                    metaDataConfig = PreferenceManager.getAllMetaData(MetaDataScreen.SCREENSAVER)
                )
            )
            mediaSliderView.toggleSlideshow(false)
        }
    }

    private suspend fun setAllAssets(assets: List<Asset>) = withContext(Dispatchers.Main) {
        mediaSliderView.setItems(assets.toSliderItems(keepOrder = false, mergePortrait = PreferenceManager.get(SLIDER_MERGE_PORTRAIT_PHOTOS)))
    }

    companion object ScreenSaverService {
        private const val PAGE_COUNT = 100
    }

    override fun onButtonPressed(event: KeyEvent): Boolean {
        if((event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || event.keyCode == KeyEvent.KEYCODE_ENTER) && !mediaSliderView.isControllerVisible()){
            finish()
            return true
        }
        return false
    }
}

