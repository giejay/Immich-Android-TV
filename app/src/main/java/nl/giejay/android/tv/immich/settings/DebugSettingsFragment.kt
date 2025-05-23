package nl.giejay.android.tv.immich.settings

import android.os.Bundle
import android.view.View
import androidx.preference.Preference
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import nl.giejay.android.tv.immich.shared.prefs.USER_ID


class DebugSettingsFragment : SettingsScreenFragment() {
    override fun getFragment(): SettingsInnerFragment {
        return DebugInnerSettingsFragment()
    }
}
class DebugInnerSettingsFragment : SettingsScreenFragment.SettingsInnerFragment() {

    override fun getFragmentLayout(): Int {
        return R.xml.preferences_debug
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        findPreference<Preference>("user_id")?.summary = PreferenceManager.get(USER_ID)
    }

    override fun handlePreferenceClick(preference: Preference?): Boolean {
        return false
    }
}