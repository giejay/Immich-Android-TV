package nl.giejay.android.tv.immich.timeline

import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import nl.giejay.android.tv.immich.ImmichApplication
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.api.ApiClient
import nl.giejay.android.tv.immich.api.ApiClientConfig
import nl.giejay.android.tv.immich.home.HomeFragmentDirections
import nl.giejay.android.tv.immich.home.HomeNavHost
import nl.giejay.android.tv.immich.home.NavTransitionListener
import nl.giejay.android.tv.immich.shared.prefs.API_KEY
import nl.giejay.android.tv.immich.shared.prefs.DEBUG_MODE
import nl.giejay.android.tv.immich.shared.prefs.DISABLE_SSL_VERIFICATION
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import nl.giejay.android.tv.immich.ui.immichComposeView
import nl.giejay.android.tv.immich.ui.timeline.TimelineScreen
import nl.giejay.mediaslider.viewmodel.MediaSliderViewModel
import kotlin.math.roundToInt

/**
 * Compose version of the timeline (step K), shown instead of [TimelineFragment] while the
 * experimental switch in the debug settings is on. It shares [TimelineViewModel] with the View
 * timeline; focus handling, scrubber, video preview and opening items are still to come.
 */
class TimelineComposeFragment : Fragment() {
    private lateinit var viewModel: TimelineViewModel
    private val sliderViewModel: MediaSliderViewModel by activityViewModels()
    private lateinit var sliderLauncher: TimelineSliderLauncher
    private var restoreRequest by mutableIntStateOf(0)
    private var loadingSlider by mutableStateOf(false)
    private var hostPaused by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!PreferenceManager.isLoggedId()) {
            Toast.makeText(
                ImmichApplication.appContext,
                "Invalid Immich server settings, redirecting to login screen.",
                Toast.LENGTH_SHORT
            ).show()
            findNavController().navigate(HomeFragmentDirections.actionGlobalSignInFragment())
            return
        }
        val apiClient = ApiClient.getClient(
            ApiClientConfig(
                PreferenceManager.hostName,
                PreferenceManager.get(API_KEY),
                PreferenceManager.get(DISABLE_SSL_VERIFICATION),
                PreferenceManager.get(DEBUG_MODE)
            )
        )
        // same key as the View timeline, so both share the loaded data and a server change resets it
        val clientKey = ApiClientConfig.fromPrefs().hashCode()
        viewModel = ViewModelProvider(
            requireActivity(),
            TimelineViewModelFactory(apiClient)
        )["timeline-$clientKey", TimelineViewModel::class.java]
        sliderLauncher = TimelineSliderLauncher(
            viewModel = viewModel,
            sliderViewModel = sliderViewModel,
            scope = lifecycleScope,
            isActive = { isAdded },
            navigate = { findNavController().navigate(it) },
            setLoading = { loadingSlider = it },
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Back from the side navigation: put focus back on the cell the user left.
        (parentFragment as? HomeNavHost)?.setNavTransitionListener(
            object : NavTransitionListener() {
                override fun onHeadersTransitionStart(withHeaders: Boolean) {
                    // Mark the restore pending as soon as the navigation opens, so the entry cell that
                    // grabs focus when it closes cannot overwrite the leave-off before the restore runs.
                    if (isAdded && withHeaders) restoreRequest++
                }

                override fun onHeadersTransitionStop(withHeaders: Boolean) {
                    if (isAdded && !withHeaders) restoreRequest++
                }
            }
        )
    }

    override fun onResume() {
        super.onResume()
        // Back from the slider (or the app): land on the cell that was last viewed.
        hostPaused = false
        restoreRequest++
    }

    override fun onPause() {
        hostPaused = true
        super.onPause()
    }

    override fun onDestroyView() {
        (parentFragment as? HomeNavHost)?.setNavTransitionListener(null)
        super.onDestroyView()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        if (!::viewModel.isInitialized) return null
        return immichComposeView(requireContext()) {
            val error by viewModel.error.collectAsState()
            LaunchedEffect(error) {
                error?.let {
                    Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
                    viewModel.clearError()
                }
            }
            TimelineScreen(
                viewModel = viewModel,
                rowHeightPx = dp(180),
                gapPx = dp(6),
                todayLabel = getString(R.string.today),
                yesterdayLabel = getString(R.string.yesterday),
                onOpenAsset = { sliderLauncher.openAsset(it) },
                onOpenMemory = { sliderLauncher.openMemory(it) },
                restoreRequest = restoreRequest,
                isNavFocused = { (parentFragment as? HomeNavHost)?.isNavFocused == true },
                hostPaused = hostPaused,
            )
        }
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).roundToInt()
}
