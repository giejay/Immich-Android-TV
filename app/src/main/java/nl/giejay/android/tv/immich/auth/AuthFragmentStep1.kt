package nl.giejay.android.tv.immich.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.fragment.app.Fragment
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import nl.giejay.android.tv.immich.BuildConfig
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.api.demo.DemoCatalog
import nl.giejay.android.tv.immich.shared.prefs.API_KEY
import nl.giejay.android.tv.immich.shared.prefs.HOST_NAME
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import nl.giejay.android.tv.immich.shared.prefs.SCREENSAVER_ALBUMS
import nl.giejay.android.tv.immich.ui.auth.AuthButton
import nl.giejay.android.tv.immich.ui.auth.AuthOptionRow
import nl.giejay.android.tv.immich.ui.auth.AuthScaffold
import nl.giejay.android.tv.immich.ui.immichComposeView
import timber.log.Timber

private enum class SignInOption { API_KEY, PHONE, DEMO }

/** Login step 1: choose how to sign in. */
class AuthFragmentStep1 : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        immichComposeView(requireContext()) {
            var selected by remember { mutableStateOf(SignInOption.API_KEY) }
            val firstFocus = remember { FocusRequester() }
            androidx.compose.runtime.LaunchedEffect(Unit) {
                try {
                    firstFocus.requestFocus()
                } catch (_: IllegalStateException) {
                }
            }
            AuthScaffold(
                title = getString(R.string.app_name) + " (${BuildConfig.VERSION_NAME})",
                description = getString(R.string.login_immich_description),
            ) {
                Column {
                    AuthOptionRow(
                        getString(R.string.auth_sign_in_by_api_key),
                        getString(R.string.auth_sign_in_by_api_key_desc),
                        selected == SignInOption.API_KEY,
                        { selected = SignInOption.API_KEY },
                        Modifier.focusRequester(firstFocus),
                    )
                    AuthOptionRow(
                        getString(R.string.auth_sign_in_by_phone),
                        getString(R.string.auth_sign_in_by_phone_desc),
                        selected == SignInOption.PHONE,
                        { selected = SignInOption.PHONE },
                    )
                    AuthOptionRow(
                        getString(R.string.auth_demo_mode),
                        getString(R.string.auth_demo_mode_desc),
                        selected == SignInOption.DEMO,
                        { selected = SignInOption.DEMO },
                    )
                    AuthButton(getString(R.string.continue_text), { onContinue(selected) })
                }
            }
        }

    private fun onContinue(option: SignInOption) {
        Timber.i("Continue on step 1 of authentication, option: $option")
        val navController = findNavController()
        when (option) {
            SignInOption.DEMO -> {
                PreferenceManager.save(SCREENSAVER_ALBUMS, emptySet())
                // offline demo: DemoInterceptor answers this reserved host with bundled sample photos
                PreferenceManager.save(API_KEY, DemoCatalog.API_KEY)
                PreferenceManager.save(HOST_NAME, DemoCatalog.HOST_URL)
                navController.navigate(
                    AuthFragmentStep1Directions.actionGlobalHomeFragment(),
                    NavOptions.Builder().setPopUpTo(navController.graph.id, true).build(),
                )
            }
            SignInOption.API_KEY -> {
                if (PreferenceManager.hostName == DemoCatalog.HOST_URL) {
                    // leave the demo profile
                    PreferenceManager.removeApiSettings()
                }
                if (navController.currentDestination?.id == R.id.authFragment) {
                    Timber.i("Navigating to step 2")
                    navController.navigate(AuthFragmentStep1Directions.actionAuthToAuth2())
                }
            }
            SignInOption.PHONE -> navController.navigate(AuthFragmentStep1Directions.actionAuthToAuthByPhone())
        }
    }
}
