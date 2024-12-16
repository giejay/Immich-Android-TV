package nl.giejay.android.tv.immich.slider

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.navigation.fragment.findNavController
import com.zeuskartik.mediaslider.MediaSliderFragment
import nl.giejay.android.tv.immich.shared.db.LocalStorage
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import timber.log.Timber

class ImmichMediaSlider : MediaSliderFragment() {
    @SuppressLint("UnsafeOptInUsageError")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Timber.i("Loading ${this.javaClass.simpleName}")

        val bundle = ImmichMediaSliderArgs.fromBundle(requireArguments())

        if(LocalStorage.mediaSliderItems.isNullOrEmpty()){
            Timber.i("No items to play for photoslider")
            Toast.makeText(requireContext(),"No items to play", Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
            return
        }

        setDefaultExoFactory(
            DefaultHttpDataSource.Factory()
                .setDefaultRequestProperties(mapOf("x-api-key" to PreferenceManager.apiKey()))
        )

        loadMediaSliderView(bundle.config, LocalStorage.mediaSliderItems)
    }
}