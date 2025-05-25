package nl.giejay.android.tv.immich.settings

import android.os.Bundle
import android.view.View
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import arrow.core.Either
import arrow.core.left
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.shared.prefs.PHOTOS_SORTING_FOR_SPECIFIC_ALBUM
import nl.giejay.android.tv.immich.shared.prefs.PhotosOrder
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
        val sortPref = findPreference<ListPreference>("photos_sorting_specific_album")!!
        val photosSortingForSpecificAlbum = PHOTOS_SORTING_FOR_SPECIFIC_ALBUM(albumId)
        val sortingForAlbum = PreferenceManager.getString(photosSortingForSpecificAlbum.key(), photosSortingForSpecificAlbum.defaultValue.toString())
        sortPref.value = sortingForAlbum
        sortPref.title = "Sort photos in $albumName by"
        sortPref.onPreferenceChangeListener = Preference.OnPreferenceChangeListener{_, newValue ->
            PreferenceManager.save(photosSortingForSpecificAlbum, PhotosOrder.valueOf(newValue as String))
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