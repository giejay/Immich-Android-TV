package nl.giejay.android.tv.immich.settings

import androidx.preference.Preference
import nl.giejay.android.tv.immich.R

class AlbumDetailsSettingsFragment : SettingsScreenFragment(){
    override fun getFragment(): SettingsInnerFragment {
        return AlbumDetailsInnerSettingsFragment()
    }
}
class AlbumDetailsInnerSettingsFragment : SettingsScreenFragment.SettingsInnerFragment() {

    override fun getFragmentLayout(): Int {
        return R.xml.preferences_album_details
    }

    override fun handlePreferenceClick(preference: Preference?): Boolean {
        return false
    }
}