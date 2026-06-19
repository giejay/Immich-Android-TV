package nl.giejay.mediaslider.view

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.media3.datasource.DefaultHttpDataSource
import nl.giejay.mediaslider.config.MediaSliderConfiguration

open class MediaSliderFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return MediaSliderView(requireContext())
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.requestFocus()
    }

    override fun onPause() {
        destroyMediaPlayer()
        super.onPause()
    }

    private fun destroyMediaPlayer() {
        view.onDestroy()
    }

    override fun getView(): MediaSliderView {
        return super.getView() as MediaSliderView
    }

    fun loadMediaSliderView(config: MediaSliderConfiguration) {
        view.loadMediaSliderView(config)
    }

    fun setDefaultExoFactory(defaultExoFactory: DefaultHttpDataSource.Factory) {
        view.setDefaultExoFactory(defaultExoFactory)
    }
}
