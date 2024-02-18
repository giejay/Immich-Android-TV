package nl.giejay.android.tv.immich.settings

import android.widget.Toast
import androidx.preference.Preference
import nl.giejay.android.tv.immich.R

class ViewSettingsFragment : SettingsScreenFragment(){
    override fun getFragment(): SettingsInnerFragment {
        return ViewInnerSettingsFragment()
    }
}
class ViewInnerSettingsFragment : SettingsScreenFragment.SettingsInnerFragment() {

    override fun getFragmentLayout(): Int {
        return R.xml.preferences_view
    }

    override fun handlePreferenceClick(preference: Preference?): Boolean {
        when(preference?.key){
            "slider_play_sound" -> {
                Toast.makeText(requireContext(), "Work in progress", Toast.LENGTH_SHORT).show()
            }
        }
        return false
    }
}