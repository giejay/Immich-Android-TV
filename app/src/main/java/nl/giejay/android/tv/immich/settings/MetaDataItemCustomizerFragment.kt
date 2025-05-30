package nl.giejay.android.tv.immich.settings

import android.os.Bundle
import android.view.View
import androidx.preference.ListPreference
import androidx.preference.Preference
import arrow.core.Either
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.shared.prefs.ActionPref
import nl.giejay.android.tv.immich.shared.prefs.PHOTOS_SORTING_FOR_SPECIFIC_ALBUM
import nl.giejay.android.tv.immich.shared.prefs.PhotosOrder
import nl.giejay.android.tv.immich.shared.prefs.Pref
import nl.giejay.android.tv.immich.shared.prefs.PrefCategory
import nl.giejay.android.tv.immich.shared.prefs.PrefScreen
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import nl.giejay.mediaslider.adapter.AlignOption
import nl.giejay.mediaslider.adapter.MetaDataItem
import nl.giejay.mediaslider.config.MediaSliderConfiguration.Companion.gson
import nl.giejay.mediaslider.model.MetaDataType
import java.lang.reflect.Type

class MetaDataItemCustomizerFragment : SettingsScreenFragment() {
    override fun getFragment(): SettingsInnerFragment {
        return MetaDataItemInnerCustomizerFragment()
    }
}

class MetaDataItemInnerCustomizerFragment : SettingsScreenFragment.SettingsInnerFragment() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        val bundle = requireArguments()
        val listType: Type = object : TypeToken<ArrayList<MetaDataItem?>?>() {}.type
        val allItems = gson.fromJson<List<MetaDataItem>>(bundle.getString("meta_data_items")!!, listType)
        val metaDataItem = gson.fromJson(bundle.getString("meta_data_item")!!, MetaDataItem::class.java)

        // set order
        val entries: List<String> = List(allItems.size) { it + 1 }.map { it.toString() }
        val metaDataItemOrder = findPreference<ListPreference>("meta_data_item_order")!!
        metaDataItemOrder.entries = entries.toTypedArray()
        metaDataItemOrder.entryValues = entries.toTypedArray()
        val currentIndex = allItems.indexOf(metaDataItem)
        metaDataItemOrder.setDefaultValue((currentIndex + 1).toString())
        metaDataItemOrder.value = (currentIndex + 1).toString()
       
        // set current alignment
//        findPreference<ListPreference>("meta_data_item_align")!!.value = metaDataItem.align.toString()
    
        // set types
        val type = findPreference<ListPreference>("meta_data_item_type")!!
        type.entries = MetaDataType.entries.map { it.toString().lowercase().capitalize() }.toTypedArray()
        type.entryValues = MetaDataType.entries.map { it.toString() }.toTypedArray()
        type.value = metaDataItem.type.toString()

        findPreference<Preference>("meta_data_save")!!.setOnPreferenceClickListener { 
            val updatedList = allItems.toMutableList()
            updatedList.remove(metaDataItem)
            updatedList.add(metaDataItemOrder.value.toInt() - 1, metaDataItem.createCopy(MetaDataType.valueOf(type.value.toString()), metaDataItem.align))

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