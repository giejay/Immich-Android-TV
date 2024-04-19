package nl.giejay.android.tv.immich.playback

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.service.dreams.DreamService
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.giejay.android.tv.immich.api.ApiClient
import nl.giejay.android.tv.immich.api.ApiClientConfig
import nl.giejay.android.tv.immich.api.model.Asset
import nl.giejay.android.tv.immich.api.util.ApiUtil
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import timber.log.Timber


class ScreenSaverService : DreamService() {
    private val ioScope = CoroutineScope(Job() + Dispatchers.IO)
    private var apiClient: ApiClient? = null
    private var screenSaverSliderView: ScreenSaverSliderView? = null
    private var _broadcastReceiver: BroadcastReceiver? = null

    @SuppressLint("UnsafeOptInUsageError")
    override fun onDreamingStarted() {
        Timber.i("Starting screensaver")
        _broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent) {
                if (intent.action!!.compareTo(Intent.ACTION_TIME_TICK) == 0) screenSaverSliderView?.onTimeChanged()
            }
        }
        registerReceiver(_broadcastReceiver, IntentFilter(Intent.ACTION_TIME_TICK))

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
        screenSaverSliderView = ScreenSaverSliderView(this)
        screenSaverSliderView!!.setPagingInterval(PreferenceManager.screensaverInterval())
        setContentView(screenSaverSliderView)
        isInteractive = true
        ioScope.launch {
            loadImages(PreferenceManager.getScreenSaverAlbums().shuffled())
        }
    }

    override fun onDreamingStopped() {
        screenSaverSliderView?.onDestroy()
        if (_broadcastReceiver != null) unregisterReceiver(_broadcastReceiver)
        super.onDreamingStopped()
    }

    private suspend fun loadImages(albums: List<String>) {
        try {
            // first fetch one album, show the (first few) pictures, then fetch other albums and shuffle again
            if (albums.isNotEmpty()) {
                apiClient!!.listAssetsFromAlbum(albums.first()).map { firstAlbum ->
                    if (albums.size == 1) {
                        addAssets(firstAlbum.assets)
                        return
                    }

                    // else more than one album
                    // set first landscape photos to allow time to load remaining albums
                    // limit to single photo instead of all photos in first album
                    // so first images are not all from first album
                    val firstLandscapePhoto = firstAlbum.assets.first {
                        it.exifInfo?.orientation != null && it.exifInfo.orientation != 6
                    }
                    val l = ArrayList<Asset>();
                    l.add(firstLandscapePhoto)
                    addAssets(l)

                    val remainingAssetsFirstAlbum = firstAlbum.assets.drop(1)

                    // load remaining albums
                    val nextAlbums = albums.drop(1).map { apiClient!!.listAssetsFromAlbum(it) }
                    val remainingAlbumAssets =
                        nextAlbums.flatMap { it.getOrNone().toList() }.flatMap { it.assets }

                    addAssets(remainingAlbumAssets + remainingAssetsFirstAlbum)
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

    private suspend fun addAssets(assets: List<Asset>) = withContext(Dispatchers.Main) {
        screenSaverSliderView!!.addItems(getItems(assets.shuffled()))
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

    private fun getItems(assets: List<Asset>): List<ScreenSaverItem> {
        val imageAssets = assets.filter {
            it.type.uppercase() != "VIDEO"
        }
        val portImages = imageAssets.filter { asset ->
            var height = asset.exifInfo?.exifImageHeight?.toInt()
            var width = asset.exifInfo?.exifImageWidth?.toInt()

            if (asset.exifInfo?.orientation == 6) {
                val tempHeight = height
                height = width
                width = tempHeight
            }

            height != null && width != null && height > width
        }.shuffled()

        val pairedItems = portImages.chunked(2)
            .filter { it.size == 2 } // Ensure each group has exactly two items
            .map {
                val left = it[0]
                val right = it[1]
                ScreenSaverItem(
                    ApiUtil.getFileUrl(left.id),
                    ApiUtil.getFileUrl(right.id),
                    "${left.exifInfo?.city} • ${right.exifInfo?.city}",
                    "${left.exifInfo?.state} • ${right.exifInfo?.state}",
                )
            }

        val singleItems = imageAssets.filter {
            val height = it.exifInfo?.exifImageHeight?.toInt()
            val width = it.exifInfo?.exifImageWidth?.toInt()
            height != null && width != null && height <= width
        }.map {
            ScreenSaverItem(
                ApiUtil.getFileUrl(it.id),
                null,
                it.exifInfo?.city,
                it.exifInfo?.state,
            )
        }

        return (pairedItems + singleItems).shuffled()
    }
}