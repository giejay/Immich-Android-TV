package nl.giejay.android.tv.immich.playback

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.media.session.PlaybackState.STATE_STOPPED
import android.service.dreams.DreamService
import android.util.Log
import android.widget.Toast
import androidx.core.content.getSystemService
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


class ScreenSaverService : DreamService(), MediaSessionManager.OnActiveSessionsChangedListener {
    private var mediaController: MediaController? = null
    private val ioScope = CoroutineScope(Job() + Dispatchers.IO)
    private var apiClient: ApiClient? = null
    private var screenSaverSliderView: ScreenSaverSliderView? = null
    private var _broadcastReceiver: BroadcastReceiver? = null

    private var callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            super.onPlaybackStateChanged(state)
            Timber.d("onPlaybackStateChanged state: " + state.toString())
            if (state?.state == STATE_STOPPED) {
                updateMediaInfo(null)
            }
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            super.onMetadataChanged(metadata)
            updateMediaInfo(metadata)
        }
    }

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
            loadImages(PreferenceManager.getScreenSaverAlbums().toList().shuffled())
        }

        initMedia();
    }

    @SuppressLint("LogNotTimber", "BinaryOperationInTimber", "WrongConstant")
    private fun initMedia() {
        try {
            val m = getSystemService<MediaSessionManager>()!!
            val component = ComponentName(
                "nl.giejay.android.tv.immich",
                MediaService::class.qualifiedName.toString()
            )

            val sessions = m.getActiveSessions(component)
            mediaController = sessions.first { it.metadata?.keySet()?.size!! > 0 && it.metadata?.getString("com.google.android.apps.mediashell.CAST_APP_NAME") != "YouTube"}
            mediaController?.registerCallback(callback)
            val metadata = mediaController?.metadata ?: return
            updateMediaInfo(metadata)

            m.addOnActiveSessionsChangedListener(this, component)

        } catch (e: Exception) {
            Log.e(ScreenSaverService::class.simpleName, "Unable to get media info", e)
        }
    }

    private fun updateMediaInfo(metadata: MediaMetadata?) {
        val artistName = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
        val songTitle = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
        val albumArtUrl = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
        screenSaverSliderView?.updateMediaInfo(artistName, songTitle, albumArtUrl)
    }

    override fun onDreamingStopped() {
        screenSaverSliderView?.onDestroy()
        if (_broadcastReceiver != null) unregisterReceiver(_broadcastReceiver)

        val m = getSystemService<MediaSessionManager>()!!
        m.removeOnActiveSessionsChangedListener(this)
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

                    val firstAlbumAssets = firstAlbum.assets.shuffled()

                    // else more than one album
                    // set first landscape photos to allow time to load remaining albums
                    // limit to single photo instead of all photos in first album
                    // so first images are not all from first album
                    val firstLandscapePhotoIndex = firstAlbumAssets.indexOfFirst {
                        it.exifInfo?.orientation != null && it.exifInfo.orientation != 6
                    }
                    val l = ArrayList<Asset>();
                    l.add(firstAlbumAssets[firstLandscapePhotoIndex])
                    addAssets(l)

                    val remainingAssetsFirstAlbum =
                        firstAlbumAssets.filterIndexed { index, _ -> index != firstLandscapePhotoIndex }

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

        val landscapeItems = imageAssets.filter {
            it.exifInfo?.orientation != 6
        }.map {
            ScreenSaverItem(
                ApiUtil.getFileUrl(it.id),
                it.exifInfo?.city,
                it.exifInfo?.state,
                it.exifInfo?.country,
                it.exifInfo?.orientation,
            )
        }

        val portImages = imageAssets.filter { asset ->
            asset.exifInfo?.orientation == 6
        }.shuffled() // shuffle before pairing

        val pairedItems = portImages.chunked(2)
            .filter { it.size == 2 } // Ensure each group has exactly two items
            .map {
                val left = it[0]
                val right = it[1]
                ScreenSaverItem(
                    ApiUtil.getFileUrl(left.id),
                    left.exifInfo?.city,
                    left.exifInfo?.state,
                    left.exifInfo?.country,
                    null,
                    ApiUtil.getFileUrl(right.id),
                    right.exifInfo?.city,
                    right.exifInfo?.state,
                    right.exifInfo?.country,
                )
            }

        return (pairedItems + landscapeItems).shuffled()
    }

    override fun onActiveSessionsChanged(sessions: MutableList<MediaController>?) {
        Timber.i("active media sessions changed");
        mediaController = sessions?.first { it.metadata?.keySet()?.size!! > 0 }
        mediaController?.registerCallback(callback)
        val metadata = mediaController?.metadata
        updateMediaInfo(metadata)
    }
}