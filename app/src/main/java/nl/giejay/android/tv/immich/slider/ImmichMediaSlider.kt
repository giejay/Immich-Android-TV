package nl.giejay.android.tv.immich.slider

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.launch
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.shared.prefs.API_KEY
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import nl.giejay.mediaslider.config.MediaSliderConfiguration
import nl.giejay.mediaslider.view.MediaSliderFragment
import nl.giejay.mediaslider.view.TimelineSliderView
import timber.log.Timber

class ImmichMediaSlider : MediaSliderFragment() {
    private val favoriteService = FavoriteService()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = TimelineSliderView(requireContext())

    @SuppressLint("UnsafeOptInUsageError")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Timber.i("Loading ${this.javaClass.simpleName}")

        val bundle = ImmichMediaSliderArgs.fromBundle(requireArguments())

        if (bundle.config.items.isEmpty()) {
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
            lifecycleScope.launch {
                favoriteService.toggleFavorite(assetId, isFavorite)
            }
        }

        loadMediaSliderView(bundle.config)

        if (bundle.autoPlay) {
            // Memories: story progress + auto-advance (ScreenSaver uses toggleSlideshow too).
            val slider = view as TimelineSliderView
            slider.setStoryProgressEnabled(true)
            slider.toggleSlideshow(false)
        }
    }
}
