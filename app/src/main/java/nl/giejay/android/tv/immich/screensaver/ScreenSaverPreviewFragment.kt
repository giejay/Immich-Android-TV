package nl.giejay.android.tv.immich.screensaver

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.api.ApiClient
import nl.giejay.android.tv.immich.shared.prefs.API_KEY
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import nl.giejay.mediaslider.config.MediaSliderConfiguration
import nl.giejay.mediaslider.view.MediaSliderFragment
import nl.giejay.mediaslider.view.MediaSliderView
import timber.log.Timber

/**
 * The screensaver has no interactive controls, so the preview must not have any either: BACK
 * leaves the preview and the volume keys reach the system, every other key is swallowed before
 * [nl.giejay.mediaslider.view.MediaSliderController] can act on it.
 */
internal class ScreenSaverPreviewSliderView(context: Context) : MediaSliderView(context) {
    override fun dispatchKeyEvent(event: KeyEvent): Boolean = when (event.keyCode) {
        KeyEvent.KEYCODE_BACK,
        KeyEvent.KEYCODE_VOLUME_UP,
        KeyEvent.KEYCODE_VOLUME_DOWN,
        KeyEvent.KEYCODE_VOLUME_MUTE -> false // let it bubble up to the activity / system

        else -> true // consumed here, never reaches the slider controller or the pager
    }
}

/**
 * Renders the configured screensaver full screen inside the app, so the settings can be checked
 * without registering the app as the system daydream and waiting for the TV to go idle.
 *
 * Everything shown here comes from [ScreenSaverAssetLoader], the same loader the real
 * [ScreenSaverService] uses, so the preview cannot drift away from the actual screensaver.
 */
class ScreenSaverPreviewFragment : MediaSliderFragment(), ScreenSaverAssetLoader.Host {
    private var ioScope = CoroutineScope(Job() + Dispatchers.IO)
    private var sliderView: ScreenSaverPreviewSliderView? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ScreenSaverPreviewSliderView(requireContext()).also {
            // The dream keeps the screen alive by itself. Without this the TV dims mid preview,
            // or the idle timeout starts the real screensaver on top of it.
            it.keepScreenOn = true
            sliderView = it
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Timber.i("Loading ${this.javaClass.simpleName}")

        if (!PreferenceManager.isLoggedId()) {
            showScreenSaverMessage(R.string.screensaver_not_possible)
            exitScreenSaver()
            return
        }

        ioScope = CoroutineScope(Job() + Dispatchers.IO)
        sliderView?.setDefaultExoFactory(
            DefaultHttpDataSource.Factory()
                .setDefaultRequestProperties(mapOf("x-api-key" to PreferenceManager.get(API_KEY)))
        )
        ScreenSaverAssetLoader(
            ioScope,
            ApiClient.getClient(screenSaverApiClientConfig()),
            this
        ).start()
    }

    override fun onPause() {
        // super tears the player down and never rebuilds it, so leave rather than come back to a
        // dead preview.
        super.onPause()
        if (isAdded) {
            findNavController().popBackStack()
        }
    }

    override fun onDestroyView() {
        ioScope.cancel()
        sliderView = null
        super.onDestroyView()
    }

    // --- ScreenSaverAssetLoader.Host ---

    override val exitWhenNothingToShow: Boolean = true

    override fun showScreenSaverMessage(messageRes: Int, longDuration: Boolean) {
        val context = context ?: return
        Toast.makeText(
            context,
            getString(messageRes),
            if (longDuration) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
        ).show()
    }

    override fun exitScreenSaver() {
        if (isAdded) {
            findNavController().popBackStack()
        }
    }

    override fun onScreenSaverConfigurationReady(configuration: MediaSliderConfiguration) {
        // null once the view is gone: the user left while the assets were still loading
        val slider = sliderView ?: return
        slider.loadMediaSliderView(configuration)
        slider.toggleSlideshow(false)
    }
}
