package nl.giejay.android.tv.immich.auth

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.github.alexzhirkevich.customqrgenerator.QrData
import com.github.alexzhirkevich.customqrgenerator.vector.QrCodeDrawable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.giejay.android.tv.immich.ImmichApplication
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.api.AuthenticationClient
import nl.giejay.android.tv.immich.api.service.DeviceConfigResponse
import nl.giejay.android.tv.immich.api.service.ImmichAuthenticationService
import nl.giejay.android.tv.immich.databinding.FragmentAuthByPhoneBinding
import nl.giejay.android.tv.immich.shared.prefs.API_KEY
import nl.giejay.android.tv.immich.shared.prefs.HOST_NAME
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import timber.log.Timber

class AuthByPhoneFragment : Fragment() {
    private var job: Job? = null
    private val ioScope = CoroutineScope(Job() + Dispatchers.IO)
    private val authClient = AuthenticationClient()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = FragmentAuthByPhoneBinding.inflate(inflater, container, false)
        ioScope.launch {
            fetchDeviceCode { code ->
                val authUrl = requireContext().resources.getString(R.string.authentication_url)
                val data = QrData.Url("$authUrl?code=$code")
                val drawable: Drawable = QrCodeDrawable(data)
                binding.qr.setImageDrawable(drawable)
                binding.qr.visibility = View.VISIBLE
                binding.qrProgressBar.visibility = View.GONE
                binding.qrText.text = getString(R.string.or_enter_code_on_auth_url, code, authUrl)
                job = ioScope.launch {
                    pollConfig(code)
                }
            }
        }
        return binding.root
    }

    private suspend fun pollConfig(code: String) {
        val timer = (0..60)
            .asSequence()
            .asFlow()
            .onEach { delay(3_000) } // specify delay
            .onCompletion { cause ->
                if (cause == null && isAdded) {
                    showErrorMessage(getString(R.string.auth_timeout_recreating_qr))
                    rebootQRFlow(findNavController())
                }
            }
        timer.collect {
            val config = authClient.getConfig(code)
            if (config?.status == "SUCCESS") {
                validateConfig(config)
                coroutineScope { cancel() }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        job?.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        ioScope.cancel()
    }

    private suspend fun validateConfig(config: DeviceConfigResponse) {
        withContext(Dispatchers.Main) {
            if (!isAdded) return@withContext
            val findNavController = findNavController()
            if (PreferenceManager.isValid(
                    config.configuration.host,
                    config.configuration.apiKey
                )
            ) {
                PreferenceManager.save(API_KEY, config.configuration.apiKey)
                PreferenceManager.save(HOST_NAME, config.configuration.host)
                findNavController.navigate(
                    AuthByPhoneFragmentDirections.actionGlobalHomeFragment(),
                    NavOptions.Builder().setPopUpTo(R.id.authFragment, true)
                        .build()
                )
            } else {
                showErrorMessage(getString(R.string.invalid_server_url_or_api_key))
                rebootQRFlow(findNavController)
            }
        }
    }

    private suspend fun rebootQRFlow(findNavController: NavController) =
        withContext(Dispatchers.Main) {
            findNavController.navigate(
                AuthByPhoneFragmentDirections.actionGlobalSignInByPhoneFragment(),
                NavOptions.Builder()
                    .setPopUpTo(R.id.authByPhoneFragment, true).build()
            )
        }

    private suspend fun fetchDeviceCode(callback: (String) -> Unit) = withContext(Dispatchers.IO) {
        val deviceCode = authClient.registerDevice()
        withContext(Dispatchers.Main) {
            if (!isAdded) return@withContext
            if (deviceCode != null) {
                callback(deviceCode.code)
            } else {
                Timber.e("Could not fetch qr: registerDevice returned null")
                showErrorMessage(getString(R.string.could_not_fetch_qr, "Network error"))
            }
        }
    }

    private suspend fun showErrorMessage(message: String) {
        withContext(Dispatchers.Main) {
            context?.let {
                Toast.makeText(it, message, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
