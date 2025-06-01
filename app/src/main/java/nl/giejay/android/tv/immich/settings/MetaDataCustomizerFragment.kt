package nl.giejay.android.tv.immich.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ImageButton
import android.widget.ListView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.NavController.OnDestinationChangedListener
import androidx.navigation.fragment.FragmentNavigator
import androidx.navigation.fragment.findNavController
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.home.HomeFragmentDirections
import nl.giejay.android.tv.immich.shared.prefs.MetaDataScreen
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import nl.giejay.mediaslider.adapter.AlignOption
import nl.giejay.mediaslider.adapter.MetaDataItem
import nl.giejay.mediaslider.model.MetaDataType
import nl.giejay.mediaslider.util.MetaDataConverter.metaDataListToJson
import nl.giejay.mediaslider.util.MetaDataConverter.metaDataToJson

class MetaDataCustomizerFragment : Fragment() {
    private lateinit var metaDataScreen: MetaDataScreen
    private lateinit var listener: OnDestinationChangedListener
    private var leftMetaData = mutableListOf<MetaDataItem>()
    private var rightMetaData = mutableListOf<MetaDataItem>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_metadata, container, false)
        this.metaDataScreen = requireArguments().getSerializable("screen") as MetaDataScreen
        this.listener = OnDestinationChangedListener { _, destination, _ ->
            if (destination is FragmentNavigator.Destination && this@MetaDataCustomizerFragment.javaClass.name == destination.className) {
                // when going back from meta data item dialog
                setUpViews(view)
            }
        }

        setUpViews(view)
        view.findViewById<ImageButton>(R.id.meta_data_add_right).setOnClickListener {
            addItem(AlignOption.RIGHT, rightMetaData)
        }

        view.findViewById<ImageButton>(R.id.meta_data_add_left).setOnClickListener {
            addItem(AlignOption.LEFT, leftMetaData)
        }
        findNavController().addOnDestinationChangedListener(listener)
        return view
    }

    private fun addItem(align: AlignOption, allMetaData: MutableList<MetaDataItem>) {
        val findFirstNotAddedMetaDataType = MetaDataType.entries.filterNot { e -> allMetaData.any { it.type == e } }
        if (findFirstNotAddedMetaDataType.isEmpty()) {
            Toast.makeText(requireContext(), "You already added every possible meta data", Toast.LENGTH_SHORT).show()
        } else {
            val newItem = MetaDataItem.create(findFirstNotAddedMetaDataType.first(), align, MetaDataItem.DEFAULT_PADDING, findFirstNotAddedMetaDataType.first().defaultFontSize)
            allMetaData.add(newItem)
            findNavController().navigate(HomeFragmentDirections.actionGlobalToSettingsDialog("meta_data_item",
                "",
                "",
                metaDataToJson(newItem),
                metaDataListToJson(allMetaData),
                metaDataScreen))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        listener.let {
            findNavController().removeOnDestinationChangedListener(it)
        }
    }

    private fun setUpViews(view: View) {
        val left = view.findViewById<ListView>(R.id.metadata_customizer_view_left)
        val right = view.findViewById<ListView>(R.id.metadata_customizer_view_right)

        this.leftMetaData = PreferenceManager.getMetaData(AlignOption.LEFT, metaDataScreen).toMutableList()
        this.rightMetaData = PreferenceManager.getMetaData(AlignOption.RIGHT, metaDataScreen).toMutableList()

        left.onItemClickListener = createItemClickedListener(leftMetaData)
        right.onItemClickListener = createItemClickedListener(rightMetaData)

        left.adapter = MetaDataCustomizerAdapter(requireContext(), leftMetaData)
        right.adapter = MetaDataCustomizerAdapter(requireContext(), rightMetaData)
    }

    private fun createItemClickedListener(metaData: List<MetaDataItem>) = AdapterView.OnItemClickListener { _, _, position, _ ->
        findNavController().navigate(
            HomeFragmentDirections.actionGlobalToSettingsDialog("meta_data_item",
                "",
                "",
                metaDataToJson(metaData[position]),
                metaDataListToJson(metaData),
                metaDataScreen)
        )
    }
}