package nl.giejay.android.tv.immich.screensaver

import android.annotation.SuppressLint
import android.service.dreams.DreamService
import android.widget.Toast
import androidx.media3.datasource.DefaultHttpDataSource
import arrow.core.getOrElse
import com.zeuskartik.mediaslider.DisplayOptions
import com.zeuskartik.mediaslider.MediaSliderConfiguration
import com.zeuskartik.mediaslider.MediaSliderView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.giejay.android.tv.immich.api.ApiClient
import nl.giejay.android.tv.immich.api.ApiClientConfig
import nl.giejay.android.tv.immich.api.model.AlbumDetails
import nl.giejay.android.tv.immich.api.model.Asset
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import nl.giejay.android.tv.immich.shared.util.toSliderItems
import timber.log.Timber
import java.time.LocalDateTime
import java.util.EnumSet

class ScreenSaverService : DreamService() {
    private val ioScope = CoroutineScope(Job() + Dispatchers.IO)
    private var apiClient: ApiClient? = null
    private var mediaSliderView: MediaSliderView? = null

    @SuppressLint("UnsafeOptInUsageError")
    override fun onDreamingStarted() {
        Timber.i("Starting screensaver")
        if (!PreferenceManager.isLoggedId()) {
            showErrorMessage("Could not start screensaver for Immich because of invalid Hostname/API key")
            finish()
            return
        }
        val apiKey = PreferenceManager.apiKey()
        val config = ApiClientConfig(
            PreferenceManager.hostName(),
            apiKey,
            PreferenceManager.disableSslVerification(),
            PreferenceManager.debugEnabled()
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
            when (val screenSaverType = PreferenceManager.getScreenSaverType()) {
                ScreenSaverType.ALBUMS -> loadImagesFromAlbums(PreferenceManager.getScreenSaverAlbums())
                ScreenSaverType.RANDOM -> loadRandomImages(screenSaverType)
                ScreenSaverType.SIMILAR_TIME_PERIOD -> loadRandomImages(screenSaverType)
                ScreenSaverType.RECENT -> loadRandomImages(screenSaverType)
            }
        }
    }

    override fun onDreamingStopped() {
        mediaSliderView?.onDestroy()
        super.onDreamingStopped()
    }

    private suspend fun loadRandomImages(screenSaverType: ScreenSaverType) {
        when (screenSaverType) {
            ScreenSaverType.RECENT -> {
                // todo add pagination!
                apiClient!!.recentAssets(0, 1000, PreferenceManager.screensaverIncludeVideos()).map {
                    setInitialAssets(it.shuffled(), false)
                }
            }

            ScreenSaverType.SIMILAR_TIME_PERIOD -> {
                // todo add pagination!
                apiClient!!.similarAssets(0, 1000, PreferenceManager.screensaverIncludeVideos()).map {
                    setInitialAssets(it.shuffled(), false)
                }
            }

            else -> {
                // random
                // todo add pagination!
                apiClient!!.listAssets(0, 1000, true, includeVideos = PreferenceManager.screensaverIncludeVideos()).map {
                    setInitialAssets(it, false)
                }
            }
        }
    }

    private suspend fun loadImagesFromAlbums(albums: Set<String>) {
        try {
            // first fetch one album, show the (first few) pictures, then fetch other albums and shuffle again
            if (albums.isNotEmpty()) {
                val shuffledAlbums = albums.toList().shuffled()
                apiClient!!.listAssetsFromAlbum(shuffledAlbums.first()).map { album ->
                    val randomAssets = getAssets(listOf(album))
                    setInitialAssets(randomAssets, PreferenceManager.screensaverShowMediaCount())
                    if (shuffledAlbums.size > 1) {
                        // load next ones
                        val nextAlbums = shuffledAlbums.drop(1).map { apiClient!!.listAssetsFromAlbum(it) }
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

    private fun filterVideos(assets: List<Asset>) = if (PreferenceManager.screensaverIncludeVideos()) {
        assets.shuffled()
    } else {
        assets.filter { it.type != "VIDEO" }.shuffled()
    }

    private suspend fun setInitialAssets(assets: List<Asset>, showMediaCount: Boolean) = withContext(Dispatchers.Main) {
        val displayOptions: EnumSet<DisplayOptions> = EnumSet.of(DisplayOptions.GRADIENT_OVERLAY);
        if (PreferenceManager.screensaverShowClock()) {
            displayOptions += DisplayOptions.CLOCK
        }
        if (PreferenceManager.screensaverShowDescription()) {
            displayOptions += DisplayOptions.TITLE
        }
        if (PreferenceManager.screensaverShowAlbumName()) {
            displayOptions += DisplayOptions.SUBTITLE
        }
        if (PreferenceManager.screensaverShowDate()) {
            displayOptions += DisplayOptions.DATE
        }
        if (showMediaCount) {
            displayOptions += DisplayOptions.MEDIA_COUNT
        }
        if (PreferenceManager.screensaverAnimateAssetSlide()) {
            displayOptions += DisplayOptions.ANIMATE_ASST_SLIDE
        }

        mediaSliderView!!.loadMediaSliderView(
            MediaSliderConfiguration(
                displayOptions,
                "",
                "#000000",
                null,
                0,
                PreferenceManager.screensaverInterval(),
                PreferenceManager.sliderOnlyUseThumbnails(),
                PreferenceManager.screensaverVideoSound()
            ), assets.toSliderItems(keepOrder = false, mergePortrait = true)
        )
        mediaSliderView!!.toggleSlideshow(false)
    }

    private suspend fun setAllAssets(assets: List<Asset>) = withContext(Dispatchers.Main) {
        mediaSliderView!!.setItems(assets.toSliderItems(keepOrder = false, mergePortrait = true))
    }

}
