package nl.giejay.android.tv.immich.playback

import android.annotation.SuppressLint
import android.service.dreams.DreamService
import android.widget.Toast
import androidx.media3.datasource.DefaultHttpDataSource
import com.zeuskartik.mediaslider.DisplayOptions
import com.zeuskartik.mediaslider.MediaSliderConfiguration
import com.zeuskartik.mediaslider.MediaSliderView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.giejay.android.tv.immich.api.ApiClient
import nl.giejay.android.tv.immich.api.ApiClientConfig
import nl.giejay.android.tv.immich.api.model.Asset
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import nl.giejay.android.tv.immich.shared.util.toSliderItems
import timber.log.Timber
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
            loadImages(PreferenceManager.getScreenSaverAlbums())
        }
    }

    override fun onDreamingStopped() {
        mediaSliderView?.onDestroy()
        super.onDreamingStopped()
    }

    private suspend fun loadImages(albums: Set<String>) {
        try {
            // first fetch one album, show the (first few) pictures, then fetch other albums and shuffle again
            if (albums.isNotEmpty()) {
                val shuffledAlbums = albums.toList().shuffled()
                apiClient!!.listAssetsFromAlbum(shuffledAlbums.first()).map { album ->

                    val randomAssets =
                        if (PreferenceManager.screensaverIncludeVideos()) {
                            album.assets.shuffled()
                        } else {
                            album.assets.filter {it.type != "VIDEO"}.shuffled()
                        }

                    setInitialAssets(randomAssets)
                    if (shuffledAlbums.size > 1) {
                        // load next ones
                        val nextAlbums = shuffledAlbums.drop(1).map { apiClient!!.listAssetsFromAlbum(it) }
                        val assets =
                            nextAlbums.flatMap { it.getOrNone().toList() }.flatMap { 
                                if (PreferenceManager.screensaverIncludeVideos()) {
                                    it.assets.shuffled()
                                } else {
                                    it.assets.filter {it.type != "VIDEO"}.shuffled()
                                }
                            }
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

    private suspend fun setInitialAssets(assets: List<Asset>) = withContext(Dispatchers.Main) {
        val displayOptions: EnumSet<DisplayOptions> = EnumSet.noneOf(DisplayOptions::class.java);
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
        if (PreferenceManager.screensaverShowMediaCount()) {
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
            ), assets.toSliderItems()
        )
        mediaSliderView!!.toggleSlideshow(false)
    }

    private suspend fun setAllAssets(assets: List<Asset>) = withContext(Dispatchers.Main) {
        mediaSliderView!!.setItems(assets.toSliderItems())
    }

}
