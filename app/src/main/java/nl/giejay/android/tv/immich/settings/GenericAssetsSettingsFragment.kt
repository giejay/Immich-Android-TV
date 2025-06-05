package nl.giejay.android.tv.immich.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.NavController
import androidx.navigation.fragment.FragmentNavigator
import androidx.navigation.fragment.findNavController
import androidx.preference.ListPreference
import androidx.preference.Preference
import arrow.core.Either
import nl.giejay.android.tv.immich.shared.prefs.ALL_ASSETS_SORTING
import nl.giejay.android.tv.immich.shared.prefs.AlbumDetailsSettingsScreen
import nl.giejay.android.tv.immich.shared.prefs.FILTER_CONTENT_TYPE
import nl.giejay.android.tv.immich.shared.prefs.GenericAssetsSettingsScreen
import nl.giejay.android.tv.immich.shared.prefs.PrefScreen
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager

class GenericAssetsSettingsFragment : SettingsScreenFragment(){
    override fun getFragment(): SettingsInnerFragment {
        return GenericAssetsInnerSettingsFragment()
    }
}
class GenericAssetsInnerSettingsFragment : SettingsScreenFragment.SettingsInnerFragment() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        findPreference<ListPreference>(ALL_ASSETS_SORTING.key())!!.summary = ALL_ASSETS_SORTING.getValue(PreferenceManager.sharedPreference).getTitle()
        findPreference<ListPreference>(FILTER_CONTENT_TYPE.key())!!.summary = FILTER_CONTENT_TYPE.getValue(PreferenceManager.sharedPreference).getTitle()
    }

    override fun getLayout(): Either<Int, PrefScreen> {
        return Either.Right(GenericAssetsSettingsScreen)
    }

    override fun handlePreferenceClick(preference: Preference?): Boolean {
        return false
    }

}