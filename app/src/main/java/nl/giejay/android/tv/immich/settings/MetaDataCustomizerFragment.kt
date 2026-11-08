package nl.giejay.android.tv.immich.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.Fragment
import androidx.navigation.NavController.OnDestinationChangedListener
import androidx.navigation.fragment.findNavController
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.home.HomeFragmentDirections
import nl.giejay.android.tv.immich.shared.prefs.MetaDataScreen
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import nl.giejay.android.tv.immich.ui.immichComposeView
import nl.giejay.android.tv.immich.ui.settings.MetaDataCustomizerScreen
import nl.giejay.mediaslider.adapter.AlignOption
import nl.giejay.mediaslider.adapter.MetaDataItem
import nl.giejay.mediaslider.model.MetaDataType
import nl.giejay.mediaslider.util.MetaDataConverter.metaDataListToJson
import nl.giejay.mediaslider.util.MetaDataConverter.metaDataToJson

/** Full-screen preview of the slideshow metadata with add/edit actions. */
class MetaDataCustomizerFragment : Fragment() {
    private lateinit var metaDataScreen: MetaDataScreen
    private lateinit var listener: OnDestinationChangedListener
    private var leftMetaData by mutableStateOf<List<MetaDataItem>>(emptyList())
    private var rightMetaData by mutableStateOf<List<MetaDataItem>>(emptyList())

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        metaDataScreen = requireArguments().getSerializable("screen") as MetaDataScreen
        reload()
        // reload after coming back from the item editor dialog
        listener = OnDestinationChangedListener { _, destination, _ ->
            if (destination.id == R.id.settings_dialog_fragment) reload()
        }
        findNavController().addOnDestinationChangedListener(listener)
        return immichComposeView(requireContext()) {
            MetaDataCustomizerScreen(
                left = leftMetaData,
                right = rightMetaData,
                onItemClick = { item, side -> openEditor(item, side) },
                onAdd = { align -> addItem(align) },
            )
        }
    }

    private fun reload() {
        leftMetaData = PreferenceManager.getMetaData(AlignOption.LEFT, metaDataScreen)
        rightMetaData = PreferenceManager.getMetaData(AlignOption.RIGHT, metaDataScreen)
    }

    private fun addItem(align: AlignOption) {
        val side = if (align == AlignOption.LEFT) leftMetaData else rightMetaData
        val free = MetaDataType.entries.firstOrNull { e -> side.none { it.type == e } }
        if (free == null) {
            Toast.makeText(requireContext(), getString(R.string.already_added_metadata), Toast.LENGTH_SHORT).show()
        } else {
            val newItem = MetaDataItem.create(free, align, MetaDataItem.DEFAULT_PADDING, free.defaultFontSize)
            openEditor(newItem, side + newItem)
        }
    }

    private fun openEditor(item: MetaDataItem, side: List<MetaDataItem>) {
        findNavController().navigate(
            HomeFragmentDirections.actionGlobalToSettingsDialog(
                "meta_data_item",
                "",
                "",
                metaDataToJson(item),
                metaDataListToJson(side),
                metaDataScreen,
            )
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        findNavController().removeOnDestinationChangedListener(listener)
    }
}
