package nl.giejay.android.tv.immich

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavGraph
import androidx.navigation.fragment.NavHostFragment
import arrow.core.getOrElse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.giejay.android.tv.immich.api.ApiClient
import nl.giejay.android.tv.immich.api.ApiClientConfig
import nl.giejay.android.tv.immich.home.HomeFragmentDirections
import nl.giejay.android.tv.immich.homescreenchannels.ChannelDeepLinks
import nl.giejay.android.tv.immich.homescreenchannels.DeepLinkTarget
import nl.giejay.android.tv.immich.shared.prefs.MetaDataScreen
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import nl.giejay.android.tv.immich.shared.prefs.SCREENSAVER_ANIMATE_ASSET_SLIDE
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_ANIMATION_SPEED
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_DPAD_SEEK_IN_VIDEO
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_FORCE_ORIGINAL_VIDEO
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_GLIDE_TRANSFORMATION
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_INTERVAL
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_MAX_CUT_OFF_HEIGHT
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_MAX_CUT_OFF_WIDTH
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_ONLY_USE_THUMBNAILS
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_PAN_EFFECT
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_SHOW_DATE_TOP_LEFT
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_ZOOM_EFFECT
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_ZOOM_SCROLL_PANORAMAS
import nl.giejay.android.tv.immich.shared.util.toSliderItem
import nl.giejay.android.tv.immich.shared.viewmodel.KeyEventsViewModel
import nl.giejay.android.tv.immich.slider.ImmichMediaSliderArgs
import nl.giejay.mediaslider.adapter.AlignOption
import nl.giejay.mediaslider.config.MediaSliderConfiguration
import nl.giejay.mediaslider.model.SliderItemViewHolder
import nl.giejay.mediaslider.viewmodel.MediaSliderViewModel
import timber.log.Timber


/**
 * FragmentActivity that displays the various fragments
 */
class MainActivity : FragmentActivity() {
    private lateinit var keyEventsModel: KeyEventsViewModel
    private lateinit var navGraph: NavGraph
    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Timber.i("Booting main activity")

        setContentView(R.layout.activity_main)

        keyEventsModel = ViewModelProvider(this)[KeyEventsViewModel::class.java]

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController
        navGraph = navController.navInflater.inflate(R.navigation.nav_graph)

        if(!PreferenceManager.hasMetaDataForScreen(MetaDataScreen.VIEWER, AlignOption.RIGHT) || !PreferenceManager.hasMetaDataForScreen(MetaDataScreen.SCREENSAVER, AlignOption.RIGHT)){
            // first time using the customizer, do a migration
            val viewMetaDataFromOldPrefs = PreferenceManager.getViewMetaDataFromOldPrefs()
            val screenSaverMetaDataFromOldPrefs = PreferenceManager.getScreenSaverMetaDataFromOldPrefs()
            PreferenceManager.saveMetaData(AlignOption.RIGHT, MetaDataScreen.VIEWER, viewMetaDataFromOldPrefs)
            PreferenceManager.saveMetaData(AlignOption.RIGHT, MetaDataScreen.SCREENSAVER, screenSaverMetaDataFromOldPrefs)
        }

        // guarded so a process-restore recreation (savedInstanceState != null) doesn't
        // re-fetch and re-push a deep-linked asset into the slider a second time
        if (savedInstanceState == null) {
            loadDeepLinkOrStartingPage(intent.data)
        }
    }

    /**
     * MainActivity is singleTask (see the manifest), so a channel/card tap while the app is
     * already running arrives here instead of spinning up a second instance.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        loadDeepLinkOrStartingPage(intent.data)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        keyEventsModel.postKeyEvent(event)
        return super.onKeyDown(keyCode, event)
    }

    /**
     * Checks if the app was started with a deep link, loading it if it was
     *
     * If not (or the deep link is invalid), it triggers the normal starting process
     */
    private fun loadDeepLinkOrStartingPage(
        uri: Uri?
    ) {
        // deep links from home screen channel programs/channels, see homescreenchannels.ChannelDeepLinks
        val target = ChannelDeepLinks.parse(uri)
        // resuming a deep link after signing in isn't supported yet - it just falls through to auth
        if (target is DeepLinkTarget.None || !PreferenceManager.isLoggedId()) {
            loadStartingPage()
            return
        }

        navGraph.setStartDestination(R.id.homeFragment)
        navController.graph = navGraph

        when (target) {
            is DeepLinkTarget.Asset -> openAssetFromDeepLink(target.assetId)
            is DeepLinkTarget.Album -> navController.navigate(
                HomeFragmentDirections.actionHomeFragmentToAlbumDetailsFragment(target.albumId, target.albumName)
            )
            DeepLinkTarget.Home, DeepLinkTarget.None -> Unit
        }
    }

    private fun openAssetFromDeepLink(assetId: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val apiClient = ApiClient.getClient(ApiClientConfig.fromPrefs())
            val asset = apiClient.getAsset(assetId).getOrElse {
                Timber.w("Could not load deep-linked asset $assetId: $it")
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, getString(R.string.no_items_to_play), Toast.LENGTH_SHORT).show()
                }
                return@launch
            }
            val config = MediaSliderConfiguration(
                startPosition = 0,
                interval = PreferenceManager.get(SLIDER_INTERVAL),
                isOnlyUseThumbnails = PreferenceManager.get(SLIDER_ONLY_USE_THUMBNAILS),
                isVideoSoundEnable = true,
                items = listOf(SliderItemViewHolder(asset.toSliderItem())),
                loadMore = null,
                animationSpeedMillis = PreferenceManager.get(SLIDER_ANIMATION_SPEED),
                maxCutOffHeight = PreferenceManager.get(SLIDER_MAX_CUT_OFF_HEIGHT),
                maxCutOffWidth = PreferenceManager.get(SLIDER_MAX_CUT_OFF_WIDTH),
                glideTransformation = PreferenceManager.get(SLIDER_GLIDE_TRANSFORMATION),
                gradiantOverlay = false,
                enableSlideAnimation = PreferenceManager.get(SCREENSAVER_ANIMATE_ASSET_SLIDE),
                metaDataConfig = PreferenceManager.getAllMetaData(MetaDataScreen.VIEWER),
                zoomAndScrollPanorama = PreferenceManager.get(SLIDER_ZOOM_SCROLL_PANORAMAS),
                zoomEffectPercent = PreferenceManager.get(SLIDER_ZOOM_EFFECT),
                panEffectPercent = PreferenceManager.get(SLIDER_PAN_EFFECT),
                useLargeVideoBuffer = PreferenceManager.get(SLIDER_FORCE_ORIGINAL_VIDEO),
                dpadSeeksInVideo = PreferenceManager.get(SLIDER_DPAD_SEEK_IN_VIDEO),
                showDateTopLeft = PreferenceManager.get(SLIDER_SHOW_DATE_TOP_LEFT)
            )
            withContext(Dispatchers.Main) {
                ViewModelProvider(this@MainActivity)[MediaSliderViewModel::class.java].configuration = config
                try {
                    // the global action (not the homeFragment-scoped one) since the current
                    // destination may have moved on during the network round-trip above
                    navController.navigate(R.id.action_to_photo_slider, ImmichMediaSliderArgs(timelineView = false).toBundle())
                } catch (e: IllegalArgumentException) {
                    Timber.w(e, "Could not navigate to the photo slider for deep-linked asset $assetId")
                    Toast.makeText(this@MainActivity, getString(R.string.no_items_to_play), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * Chooses whether to show the browse screen or the "no Firebase" notice
     */
    private fun loadStartingPage() {
        if (!PreferenceManager.isLoggedId()) {
            Timber.i("Start page is authentication")
            navGraph.setStartDestination(R.id.authFragment)
        } else {
            Timber.i("Start page is home")
            navGraph.setStartDestination(R.id.homeFragment)
        }

        // Set the graph to trigger loading the start destination
        navController.graph = navGraph
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.i("Main activity got destroyed")
    }
}
