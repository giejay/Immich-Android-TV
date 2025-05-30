package nl.giejay.android.tv.immich.settings

import android.os.Bundle
import android.view.View
import androidx.preference.ListPreference
import androidx.preference.Preference
import arrow.core.Either
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.shared.prefs.ActionPref
import nl.giejay.android.tv.immich.shared.prefs.PHOTOS_SORTING_FOR_SPECIFIC_ALBUM
import nl.giejay.android.tv.immich.shared.prefs.PhotosOrder
import nl.giejay.android.tv.immich.shared.prefs.Pref
import nl.giejay.android.tv.immich.shared.prefs.PrefCategory
import nl.giejay.android.tv.immich.shared.prefs.PrefScreen
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import nl.giejay.mediaslider.adapter.MetaDataItem
import nl.giejay.mediaslider.config.MediaSliderConfiguration.Companion.gson

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
        val index = bundle.getInt("index")
        val itemCount = bundle.getInt("item_count")
        val metaDataItem = gson.fromJson(bundle.getString("meta_data_item")!!, MetaDataItem::class.java)
        
        // set order
        val entries: List<String> = List(itemCount) { it + 1 }.map { it.toString() }
        val metaDataItemOrder = findPreference<ListPreference>("meta_data_item_order")!!
        metaDataItemOrder.entries = entries.toTypedArray()
        metaDataItemOrder.entryValues = entries.toTypedArray()
        metaDataItemOrder.setDefaultValue((index + 1).toString())
        metaDataItemOrder.value = (index + 1).toString()
       
        // set current alignment
        findPreference<ListPreference>("meta_data_item_align")!!.value = metaDataItem.align.toString()
    
        // set types
        val type = findPreference<ListPreference>("meta_data_item_type")!!
        type.setEntries()
        
        findPreference<Preference>("meta_data_save")!!.setOnPreferenceClickListener { 
            
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