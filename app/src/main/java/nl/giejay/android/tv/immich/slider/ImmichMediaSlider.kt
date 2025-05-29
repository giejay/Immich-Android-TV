package nl.giejay.android.tv.immich.slider

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.navigation.fragment.findNavController
import nl.giejay.mediaslider.view.MediaSliderFragment
import nl.giejay.android.tv.immich.shared.prefs.API_KEY
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import timber.log.Timber

class ImmichMediaSlider : MediaSliderFragment() {
    @SuppressLint("UnsafeOptInUsageError")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Timber.i("Loading ${this.javaClass.simpleName}")

        val bundle = ImmichMediaSliderArgs.fromBundle(requireArguments())

        if(bundle.config.items.isEmpty()){
            Timber.i("No items to play for photoslider")
            Toast.makeText(requireContext(),"No items to play", Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
            return
        }

        setDefaultExoFactory(
            DefaultHttpDataSource.Factory()
                .setDefaultRequestProperties(mapOf("x-api-key" to PreferenceManager.get(API_KEY)))
        )

        loadMediaSliderView(bundle.config)
    }
}