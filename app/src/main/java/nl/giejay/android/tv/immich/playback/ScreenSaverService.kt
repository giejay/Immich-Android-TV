package nl.giejay.android.tv.immich.playback

import android.service.dreams.DreamService
import android.widget.Toast
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.zeuskartik.mediaslider.MediaSliderConfiguration
import com.zeuskartik.mediaslider.MediaSliderView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.giejay.android.tv.immich.api.ApiClient
import nl.giejay.android.tv.immich.api.model.Asset
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import nl.giejay.android.tv.immich.shared.util.toSliderItems
import timber.log.Timber

class ScreenSaverService : DreamService() {
    private val ioScope = CoroutineScope(Job() + Dispatchers.IO)
    private var apiClient: ApiClient? = null
    private lateinit var mediaSliderView: MediaSliderView

    override fun onDreamingStarted() {
        Timber.i("Starting screensaver")
        if (!PreferenceManager.isLoggedId()) {
            showErrorMessage("Could not start screensaver for Immich because of invalid Hostname/API key")
            finish()
            return
        }
        val apiKey = PreferenceManager.apiKey()
        apiClient = ApiClient.getClient(PreferenceManager.hostName(), apiKey)
        mediaSliderView = MediaSliderView(this)
        mediaSliderView.setDefaultExoFactory(
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
        mediaSliderView.onDestroy()
        super.onDreamingStopped()
    }

    private suspend fun loadImages(albums: Set<String>) {
        try {
            // first fetch one album, show the (first few) pictures, then fetch other albums and shuffle again
            if (albums.isNotEmpty()) {
                val album = apiClient!!.listAssetsFromAlbum(albums.first()).body()
                setInitialAssets(album!!.assets.shuffled())
                if (albums.size > 1) {
                    // load next ones
                    val nextAlbums =
                        albums.drop(1).map { apiClient!!.listAssetsFromAlbum(it).body() }
                    val assets = nextAlbums.flatMap { it?.assets ?: emptyList() }
                    setAllAssets((album.assets + assets).shuffled())
                }
            } else {
                showErrorMessageMainScope("Set the Immich albums to show in the screensaver settings")
                finish()
            }
        } catch (e: Exception) {
            Timber.e("Could not fetch assets from Immich for Screensaver", e)
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
        mediaSliderView.loadMediaSliderView(
            MediaSliderConfiguration(
                PreferenceManager.screensaverShowDescription(),
                PreferenceManager.screensaverShowMediaCount(),
                false,
                "",
                "#000000",
                null,
                0,
                PreferenceManager.screensaverInterval()
            ), assets.toSliderItems()
        )
        mediaSliderView.toggleSlideshow(false)
    }

    private suspend fun setAllAssets(assets: List<Asset>) = withContext(Dispatchers.Main) {
        mediaSliderView.setItems(assets.toSliderItems())
    }

}