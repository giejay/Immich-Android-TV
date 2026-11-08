package nl.giejay.android.tv.immich.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.fragment.findNavController
import nl.giejay.android.tv.immich.shared.prefs.MetaDataScreen
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import nl.giejay.android.tv.immich.ui.settings.MetaDataItemEditor
import nl.giejay.mediaslider.adapter.MetaDataItem
import nl.giejay.mediaslider.util.MetaDataConverter

/** Edit one metadata item: type, position, font size and padding, or delete it. */
class MetaDataItemCustomizerFragment : ComposeSettingsFragment() {
    @Composable
    override fun Content(closeChoiceSignal: Int, onChoosingChanged: (Boolean) -> Unit) {
        val bundle = requireArguments()
        val allItems = remember { MetaDataConverter.metaDataListFromJson(bundle.getString("meta_data_items")!!) }
        val item = remember { MetaDataConverter.metaDataFromJson(bundle.getString("meta_data_item")!!) }
        val screen = remember { bundle.getSerializable("screen") as MetaDataScreen }

        MetaDataItemEditor(
            allItems = allItems,
            item = item,
            closeChoiceSignal = closeChoiceSignal,
            onChoosingChanged = onChoosingChanged,
            onSave = { edit ->
                val updated = allItems.toMutableList()
                updated.remove(item)
                updated.add(edit.order - 1, MetaDataItem.create(edit.type, item.align, edit.padding, edit.fontSize))
                PreferenceManager.saveMetaData(item.align, screen, updated.toList())
                findNavController().popBackStack()
            },
            onDelete = {
                val updated = allItems.toMutableList()
                updated.remove(item)
                PreferenceManager.saveMetaData(item.align, screen, updated.toList())
                findNavController().popBackStack()
            },
        )
    }
}
