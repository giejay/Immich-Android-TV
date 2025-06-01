package nl.giejay.android.tv.immich.settings

import android.os.Bundle
import androidx.navigation.fragment.findNavController
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.SeekBarPreference
import arrow.core.Either
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.shared.prefs.MetaDataScreen
import nl.giejay.android.tv.immich.shared.prefs.PrefScreen
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import nl.giejay.mediaslider.adapter.MetaDataClock
import nl.giejay.mediaslider.adapter.MetaDataItem
import nl.giejay.mediaslider.model.MetaDataType
import nl.giejay.mediaslider.util.MetaDataConverter

class MetaDataItemCustomizerFragment : SettingsScreenFragment() {
    override fun getFragment(): SettingsInnerFragment {
        return MetaDataItemInnerCustomizerFragment()
    }
}

class MetaDataItemInnerCustomizerFragment : SettingsScreenFragment.SettingsInnerFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        val bundle = requireArguments()
        val allItems = MetaDataConverter.metaDataListFromJson(bundle.getString("meta_data_items")!!)
        val metaDataItem = MetaDataConverter.metaDataFromJson(bundle.getString("meta_data_item")!!)
        val screen = bundle.getSerializable("screen") as MetaDataScreen

        // set order
        val entries: List<String> = List(allItems.size) { it + 1 }.map { it.toString() }
        val metaDataItemOrder = findPreference<ListPreference>("meta_data_item_order")!!
        metaDataItemOrder.entries = entries.toTypedArray()
        metaDataItemOrder.entryValues = entries.toTypedArray()
        val currentIndex = allItems.indexOf(metaDataItem)
        metaDataItemOrder.setDefaultValue((currentIndex + 1).toString())
        metaDataItemOrder.value = (currentIndex + 1).toString()

        val fontSize = findPreference<SeekBarPreference>("meta_data_item_font_size")
        fontSize!!.value = metaDataItem.fontSize

        // set types
        val type = findPreference<ListPreference>("meta_data_item_type")!!
        type.setOnPreferenceChangeListener {
            _, newVal -> fontSize.value = MetaDataType.valueOf(newVal as String).defaultFontSize
            true
        }
        val filteredTypes = MetaDataType.entries.filterNot { e -> e != metaDataItem.type && allItems.any { it.type == e } }
        type.entryValues = filteredTypes.map { it.toString() }.toTypedArray()
        type.entries = filteredTypes.map { it.title }.toTypedArray()
        type.value = metaDataItem.type.toString()

        val padding = findPreference<SeekBarPreference>("meta_data_item_padding")
        padding!!.value = metaDataItem.padding

        findPreference<Preference>("meta_data_save")!!.setOnPreferenceClickListener {
            val updatedList = allItems.toMutableList()
            updatedList.remove(metaDataItem)
            updatedList.add(metaDataItemOrder.value.toInt() - 1, MetaDataItem.create(MetaDataType.valueOf(type.value.toString()), metaDataItem.align, padding.value, fontSize.value))
            PreferenceManager.saveMetaData(metaDataItem.align, screen, updatedList.toList())
            findNavController().popBackStack()
            false
        }

        findPreference<Preference>("meta_data_delete")!!.setOnPreferenceClickListener {
            val updatedList = allItems.toMutableList()
            updatedList.remove(metaDataItem)
            PreferenceManager.saveMetaData(metaDataItem.align, screen, updatedList.toList())
            findNavController().popBackStack()
            false
        }
    }

    override fun getLayout(): Either<Int, PrefScreen> {
        return Either.Left(R.xml.preferences_meta_data_item)
    }

    override fun handlePreferenceClick(preference: Preference?): Boolean {
        return false
    }
}