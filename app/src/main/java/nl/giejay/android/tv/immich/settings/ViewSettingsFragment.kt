package nl.giejay.android.tv.immich.settings

import android.widget.Toast
import androidx.preference.Preference
import arrow.core.Either
import nl.giejay.android.tv.immich.shared.prefs.PrefScreen
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_INTERVAL

class ViewSettingsFragment : SettingsScreenFragment() {
    override fun getFragment(): SettingsInnerFragment {
        return ViewInnerSettingsFragment()
    }
}

class ViewInnerSettingsFragment : SettingsScreenFragment.SettingsInnerFragment() {

    override fun getLayout(): Either<Int, PrefScreen> {
        return Either.Right(PreferenceManager.viewSettings)
    }

    override fun handlePreferenceClick(preference: Preference?): Boolean {
        when(preference?.key){
            SLIDER_INTERVAL.key() -> {
                Toast.makeText(requireContext(), "Testing", Toast.LENGTH_SHORT).show()
            }
        }
        return false
    }
}
