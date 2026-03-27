package nl.giejay.android.tv.immich.slider

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.launch
import nl.giejay.android.tv.immich.ImmichApplication
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.api.ApiClient
import nl.giejay.android.tv.immich.api.ApiClientConfig
import nl.giejay.android.tv.immich.shared.prefs.API_KEY
import nl.giejay.android.tv.immich.shared.prefs.DEBUG_MODE
import nl.giejay.android.tv.immich.shared.prefs.DISABLE_SSL_VERIFICATION
import nl.giejay.android.tv.immich.shared.prefs.HOST_NAME
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import nl.giejay.mediaslider.config.MediaSliderConfiguration
import nl.giejay.mediaslider.view.MediaSliderFragment
import timber.log.Timber

class ImmichMediaSlider : MediaSliderFragment() {
    @SuppressLint("UnsafeOptInUsageError")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Timber.i("Loading ${this.javaClass.simpleName}")

        val bundle = ImmichMediaSliderArgs.fromBundle(requireArguments())

        if(bundle.config.items.isEmpty()){
            Timber.i("No items to play for photoslider")
            Toast.makeText(requireContext(), getString(R.string.no_items_to_play), Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
            return
        }

        setDefaultExoFactory(
            DefaultHttpDataSource.Factory()
                .setDefaultRequestProperties(mapOf("x-api-key" to PreferenceManager.get(API_KEY)))
        )

        MediaSliderConfiguration.onFavoriteToggle = { assetId, isFavorite ->
            val apiClient = ApiClient.getClient(
                ApiClientConfig(
                    PreferenceManager.get(HOST_NAME),
                    PreferenceManager.get(API_KEY),
                    PreferenceManager.get(DISABLE_SSL_VERIFICATION),
                    PreferenceManager.get(DEBUG_MODE)
                )
            )
            lifecycleScope.launch {
                apiClient.updateFavorite(assetId, isFavorite).fold(
                    ifLeft = { error ->
                        Timber.e("Failed to toggle favorite for asset $assetId: $error")
                        Toast.makeText(
                            ImmichApplication.appContext,
                            if (isFavorite) R.string.favorite_add_failed else R.string.favorite_remove_failed,
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    ifRight = {
                        Timber.i("Successfully toggled favorite for asset $assetId to $isFavorite")
                        Toast.makeText(
                            ImmichApplication.appContext,
                            if (isFavorite) R.string.favorite_added else R.string.favorite_removed,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            }
        }

        loadMediaSliderView(bundle.config)
    }
}
