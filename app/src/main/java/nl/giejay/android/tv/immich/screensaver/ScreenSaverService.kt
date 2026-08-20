package nl.giejay.android.tv.immich.screensaver

import android.annotation.SuppressLint
import android.service.dreams.DreamService
import android.view.KeyEvent
import android.widget.Toast
import androidx.media3.datasource.DefaultHttpDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.api.ApiClient
import nl.giejay.android.tv.immich.shared.prefs.API_KEY
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import nl.giejay.mediaslider.config.MediaSliderConfiguration
import nl.giejay.mediaslider.util.MediaSliderListener
import nl.giejay.mediaslider.view.MediaSliderView
import timber.log.Timber

class ScreenSaverService : DreamService(), MediaSliderListener, ScreenSaverAssetLoader.Host {
    private var ioScope = CoroutineScope(Job() + Dispatchers.IO)
    private var mediaSliderView: MediaSliderView? = null

    @SuppressLint("UnsafeOptInUsageError")
    override fun onDreamingStarted() {
        ioScope = CoroutineScope(Job() + Dispatchers.IO)
        Timber.i("Starting screensaver")
        if (!PreferenceManager.isLoggedId()) {
            showScreenSaverMessage(R.string.screensaver_not_possible)
            finish()
            return
        }
        val apiKey = PreferenceManager.get(API_KEY)
        mediaSliderView = MediaSliderView(this)
        mediaSliderView!!.setDefaultExoFactory(
            DefaultHttpDataSource.Factory()
                .setDefaultRequestProperties(mapOf("x-api-key" to apiKey))
        )
        setContentView(mediaSliderView)
        isInteractive = true
        ScreenSaverAssetLoader(
            ioScope,
            ApiClient.getClient(screenSaverApiClientConfig()),
            this
        ).start()
    }

    override fun onDreamingStopped() {
        ioScope.cancel()
        mediaSliderView?.onDestroy()
        super.onDreamingStopped()
    }

    override fun showScreenSaverMessage(messageRes: Int, longDuration: Boolean) {
        Toast.makeText(
            this,
            getString(messageRes),
            if (longDuration) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
        ).show()
    }

    override fun exitScreenSaver() {
        finish()
    }

    override fun onScreenSaverConfigurationReady(configuration: MediaSliderConfiguration) {
        mediaSliderView?.loadMediaSliderView(configuration)
        mediaSliderView?.toggleSlideshow(false)
    }

    override fun onButtonPressed(keyEvent: KeyEvent): Boolean {
        if ((keyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyEvent.keyCode == KeyEvent.KEYCODE_ENTER) && mediaSliderView?.isControllerVisible() == false) {
            finish()
            return true
        }
        return false
    }
}
