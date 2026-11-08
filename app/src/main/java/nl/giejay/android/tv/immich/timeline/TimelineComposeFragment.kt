package nl.giejay.android.tv.immich.timeline

import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import nl.giejay.android.tv.immich.ImmichApplication
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.api.ApiClient
import nl.giejay.android.tv.immich.api.ApiClientConfig
import nl.giejay.android.tv.immich.home.HomeFragmentDirections
import nl.giejay.android.tv.immich.shared.prefs.API_KEY
import nl.giejay.android.tv.immich.shared.prefs.DEBUG_MODE
import nl.giejay.android.tv.immich.shared.prefs.DISABLE_SSL_VERIFICATION
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import nl.giejay.android.tv.immich.ui.immichComposeView
import nl.giejay.android.tv.immich.ui.timeline.TimelineScreen
import kotlin.math.roundToInt

/**
 * Compose version of the timeline (step K), shown instead of [TimelineFragment] while the
 * experimental switch in the debug settings is on. It shares [TimelineViewModel] with the View
 * timeline; focus handling, scrubber, video preview and opening items are still to come.
 */
class TimelineComposeFragment : Fragment() {
    private lateinit var viewModel: TimelineViewModel

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
            )
        }
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).roundToInt()
}
