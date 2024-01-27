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
            Toast.makeText(
                this,
                "Could not start screensaver for Immich because of invalid Hostname/API key",
                Toast.LENGTH_SHORT
            ).show()
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
            loadImages()
        }
    }

    override fun onDreamingStopped() {
        mediaSliderView.onDestroy()
        super.onDreamingStopped()
    }

    private suspend fun loadImages() {
        try {
            // TODO fetch selected albums from settings
            // first fetch one album, show the (first few) pictures, then fetch other albums and shuffle again
            val albums = apiClient!!.listAlbums().body()!!
                .sortedByDescending { it.endDate }
            if (albums.isNotEmpty()) {
                val album = apiClient!!.listAssetsFromAlbum(albums.random().id).body()
                setImages(album!!.assets.shuffled())
            } else {
                showErrorMessage("No albums found in Immich")
            }
        } catch (e: Exception) {
            Timber.e("Could not fetch assets from Immich for Screensaver", e)
            showErrorMessage("Could not load assets from Immich")
        }
    }

    private suspend fun showErrorMessage(errorMessage: String) {
        withContext(Dispatchers.Main) {
            Toast.makeText(
                this@ScreenSaverService,
                errorMessage,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private suspend fun setImages(assets: List<Asset>) =
        withContext(Dispatchers.Main) {
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
}