package nl.giejay.android.tv.immich.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.fragment.app.Fragment
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.shared.prefs.API_KEY
import nl.giejay.android.tv.immich.shared.prefs.DEBUG_MODE
import nl.giejay.android.tv.immich.shared.prefs.DISABLE_SSL_VERIFICATION
import nl.giejay.android.tv.immich.shared.prefs.HOST_NAME
import nl.giejay.android.tv.immich.shared.prefs.PROXY_PASSWORD
import nl.giejay.android.tv.immich.shared.prefs.PROXY_USER
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import nl.giejay.android.tv.immich.shared.prefs.SCREENSAVER_ALBUMS
import nl.giejay.android.tv.immich.ui.auth.AuthButton
import nl.giejay.android.tv.immich.ui.auth.AuthScaffold
import nl.giejay.android.tv.immich.ui.auth.AuthTextField
import nl.giejay.android.tv.immich.ui.auth.AuthToggleRow
import nl.giejay.android.tv.immich.ui.immichComposeView
import timber.log.Timber

data class AuthSettings(val hostName: String, val apiKey: String) {
    fun isValid(): Boolean {
        return PreferenceManager.isValid(hostName, apiKey)
    }
}

/** Login step 2: server URL, API key and connection options. */
class AuthFragmentStep2 : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        immichComposeView(requireContext()) {
            var host by remember { mutableStateOf(PreferenceManager.hostName) }
            var apiKey by remember { mutableStateOf(PreferenceManager.get(API_KEY)) }
            var proxyUser by remember { mutableStateOf(PreferenceManager.get(PROXY_USER)) }
            var proxyPassword by remember { mutableStateOf(PreferenceManager.get(PROXY_PASSWORD)) }
            var disableSsl by remember { mutableStateOf(PreferenceManager.get(DISABLE_SSL_VERIFICATION)) }
            var debug by remember { mutableStateOf(PreferenceManager.get(DEBUG_MODE)) }
            val firstFocus = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                try {
                    firstFocus.requestFocus()
                } catch (_: IllegalStateException) {
                }
            }
            AuthScaffold(
                title = getString(R.string.app_name),
                description = getString(R.string.login_immich_description),
            ) {
                Column(Modifier.fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
                    AuthTextField(getString(R.string.server_url_hint), host, { host = it }, Modifier.focusRequester(firstFocus), KeyboardType.Uri)
                    AuthTextField(getString(R.string.api_key_text), apiKey, { apiKey = it })
                    AuthTextField(getString(R.string.proxy_user), proxyUser, { proxyUser = it })
                    AuthTextField(getString(R.string.proxy_password), proxyPassword, { proxyPassword = it }, masked = true)
                    AuthToggleRow(getString(R.string.disable_ssl_verification), getString(R.string.disable_ssl_verification_desc), disableSsl, { disableSsl = it })
                    AuthToggleRow(getString(R.string.debug_mode), getString(R.string.debug_mode_desc), debug, { debug = it })
                    AuthButton(getString(R.string.submit), { submit(host.trim(), apiKey.trim(), disableSsl, debug, proxyUser.trim(), proxyPassword) })
                }
            }
        }

    private fun submit(hostName: String, apiKey: String, disableSsl: Boolean, debug: Boolean, proxyUser: String, proxyPassword: String) {
        val entry = AuthSettings(hostName, apiKey)
        Timber.i("Submit in step 2, entry valid: ${entry.isValid()}")
        if (entry.isValid()) {
            PreferenceManager.save(SCREENSAVER_ALBUMS, emptySet())
            PreferenceManager.save(API_KEY, entry.apiKey)
            PreferenceManager.save(HOST_NAME, entry.hostName)
            PreferenceManager.save(PROXY_USER, proxyUser)
            PreferenceManager.save(PROXY_PASSWORD, proxyPassword)
            PreferenceManager.save(DISABLE_SSL_VERIFICATION, disableSsl)
            PreferenceManager.save(DEBUG_MODE, debug)
            val navControl = findNavController()
            // Clear the whole back stack so every page (and the timeline) starts with the new server settings.
            navControl.navigate(
                AuthFragmentStep2Directions.actionGlobalHomeFragment(),
                NavOptions.Builder().setPopUpTo(navControl.graph.id, true).build(),
            )
        } else if (entry.hostName.isEmpty()) {
            Toast.makeText(activity, getString(R.string.enter_server_url), Toast.LENGTH_SHORT).show()
        } else if (entry.apiKey.isEmpty()) {
            Toast.makeText(activity, getString(R.string.enter_api_key), Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(activity, getString(R.string.enter_valid_server_url), Toast.LENGTH_SHORT).show()
        }
    }
}
