package nl.giejay.android.tv.immich.settings

import android.os.Bundle
import android.view.View
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import arrow.core.Either
import arrow.core.left
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.shared.prefs.PrefScreen
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager

class AlbumDetailsSettingsFragment : SettingsScreenFragment(){
    override fun getFragment(): SettingsInnerFragment {
        return AlbumDetailsInnerSettingsFragment()
    }
}
class AlbumDetailsInnerSettingsFragment : SettingsScreenFragment.SettingsInnerFragment() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val bundle = requireArguments()
        val albumId = bundle.getString("albumId")!!
        val albumName = bundle.getString("albumName")
        setTitle("$albumName settings")
        val sortPref = findPreference<ListPreference>("photos_sorting_specific_album")
        val sortingForAlbum = PreferenceManager.getSortingForAlbum(albumId)
        sortPref!!.value = sortingForAlbum.toString()
        sortPref.title = "Sort photos in $albumName by"
        sortPref.onPreferenceChangeListener = Preference.OnPreferenceChangeListener{_, newValue ->
            PreferenceManager.saveSortingForAlbum(albumId, newValue as String)
            true
        }
    }

    override fun getLayout(): Either<Int, PrefScreen> {
        return Either.Left(R.xml.preferences_album_details)
    }

    override fun handlePreferenceClick(preference: Preference?): Boolean {
        return false
    }
}