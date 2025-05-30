package nl.giejay.android.tv.immich.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ListView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.gson.reflect.TypeToken
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.home.HomeFragmentDirections
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import nl.giejay.mediaslider.adapter.AlignOption
import nl.giejay.mediaslider.adapter.MetaDataItem
import nl.giejay.mediaslider.config.MediaSliderConfiguration.Companion.gson

class MetaDataCustomizerFragment : Fragment() {
    private var leftMetaData = mutableListOf<MetaDataItem>()
    private var rightMetaData = mutableListOf<MetaDataItem>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_metadata, container, false)
        val left = view.findViewById<ListView>(R.id.metadata_customizer_view_left)
        val right = view.findViewById<ListView>(R.id.metadata_customizer_view_right)

        val viewMetaData = PreferenceManager.getViewMetaData()
        this.leftMetaData = viewMetaData.filter { it.align == AlignOption.LEFT }.toMutableList()
        this.rightMetaData = viewMetaData.filter { it.align == AlignOption.RIGHT }.toMutableList()

        left.onItemClickListener = createItemClickedListener(leftMetaData)
        right.onItemClickListener = createItemClickedListener(rightMetaData)

        left.adapter = MetaDataCustomizerAdapter(requireContext(), leftMetaData)
        right.adapter = MetaDataCustomizerAdapter(requireContext(), rightMetaData)

        return view
    }

    private fun createItemClickedListener(metaData: List<MetaDataItem>) = AdapterView.OnItemClickListener { _, _, position, _ ->
        findNavController().navigate(
            HomeFragmentDirections.actionGlobalToSettingsDialog("meta_data_item",
                "",
                "",
                gson.toJson(metaData[position], MetaDataItem::class.java), gson.toJson(metaData, object : TypeToken<ArrayList<MetaDataItem?>?>() {}.type))
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()

    }
}