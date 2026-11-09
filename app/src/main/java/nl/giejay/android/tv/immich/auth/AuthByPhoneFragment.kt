package nl.giejay.android.tv.immich.auth

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.github.alexzhirkevich.customqrgenerator.QrData
import com.github.alexzhirkevich.customqrgenerator.vector.QrCodeDrawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.databinding.FragmentAuthByPhoneBinding
import nl.giejay.android.tv.immich.shared.prefs.API_KEY
import nl.giejay.android.tv.immich.shared.prefs.HOST_NAME
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import nl.giejay.android.tv.immich.shared.prefs.SCREENSAVER_ALBUMS
import timber.log.Timber
import java.net.BindException

/**
 * Sign in with a phone: the TV serves a small form in the home network ([LocalAuthServer]) and shows its
 * address as a QR code. No third party is involved.
 */
class AuthByPhoneFragment : Fragment() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var server: LocalAuthServer? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = FragmentAuthByPhoneBinding.inflate(inflater, container, false)
        val texts = LocalAuthTexts(
            title = getString(R.string.app_name),
            hostLabel = getString(R.string.server_url_hint),
            keyLabel = getString(R.string.api_key_text),
            submit = getString(R.string.submit),
            success = getString(R.string.phone_sign_in_done),
            invalid = getString(R.string.enter_valid_server_url),
            language = resources.configuration.locales[0].language,
        )
        // The server object is owned by the fragment from the start (it opens no socket yet), so
        // onDestroyView always stops it, even when the start below is cancelled half way.
        val localServer = LocalAuthServer(texts, ::acceptCredentials, onAccepted = ::leaveAfterSignIn)
        server = localServer
        viewLifecycleOwner.lifecycleScope.launch {
            val address = withContext(Dispatchers.IO) { LocalAuthServer.findLanAddress() }
            if (address == null) {
                binding.qrProgressBar.visibility = View.GONE
                binding.qrText.text = getString(R.string.phone_sign_in_no_network)
                return@launch
            }
            withContext(Dispatchers.IO + NonCancellable) {
                try {
                    localServer.start(PORT)
                } catch (e: BindException) {
                    localServer.start(0)
                }
            }
            if (!isAdded) return@launch
            val url = "http://${address.hostAddress}:${localServer.port}/?t=${localServer.token}"
            val drawable: Drawable = QrCodeDrawable(QrData.Url(url))
            binding.qr.setImageDrawable(drawable)
            binding.qr.visibility = View.VISIBLE
            binding.qrProgressBar.visibility = View.GONE
            binding.qrText.text = getString(R.string.phone_sign_in_open_address, url)
            mainHandler.postDelayed({ onTimeout() }, TIMEOUT_MS)
        }
        return binding.root
    }

    /** Called on a server thread; true when the credentials were saved and the app moves on. */
    private fun acceptCredentials(host: String, apiKey: String): Boolean {
        if (!PreferenceManager.isValid(host, apiKey)) return false
        PreferenceManager.save(SCREENSAVER_ALBUMS, emptySet())
        PreferenceManager.save(API_KEY, apiKey)
        PreferenceManager.save(HOST_NAME, host)
        return true
    }

    /** Called on a server thread once the phone got its success page. */
    private fun leaveAfterSignIn() {
        mainHandler.post {
            if (!isAdded) return@post
            val navController = findNavController()
            // Clear the whole back stack so every page (and the timeline) starts with the new server settings.
            navController.navigate(
                AuthByPhoneFragmentDirections.actionGlobalHomeFragment(),
                NavOptions.Builder().setPopUpTo(navController.graph.id, true).build(),
            )
        }
    }

    private fun onTimeout() {
        // The old token must die even when navigation is not possible right now (e.g. the app is in the background).
        server?.stop()
        if (!isAdded) return
        Toast.makeText(requireContext(), R.string.auth_timeout_recreating_qr, Toast.LENGTH_SHORT).show()
        try {
            findNavController().navigate(
                AuthByPhoneFragmentDirections.actionGlobalSignInByPhoneFragment(),
                NavOptions.Builder().setPopUpTo(R.id.authByPhoneFragment, true).build(),
            )
        } catch (e: IllegalStateException) {
            Timber.w(e, "Could not navigate: fragment likely detached")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mainHandler.removeCallbacksAndMessages(null)
        server?.stop()
        server = null
    }

    private companion object {
        const val PORT = 8765
        const val TIMEOUT_MS = 3 * 60 * 1000L
    }
}
