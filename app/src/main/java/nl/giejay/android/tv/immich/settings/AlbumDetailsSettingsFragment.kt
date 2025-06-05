package nl.giejay.android.tv.immich.settings

import android.os.Bundle
import android.view.View
import androidx.preference.ListPreference
import androidx.preference.Preference
import arrow.core.Either
import nl.giejay.android.tv.immich.shared.prefs.ALL_ASSETS_SORTING
import nl.giejay.android.tv.immich.shared.prefs.AlbumDetailsSettingsScreen
import nl.giejay.android.tv.immich.shared.prefs.EnumByTitlePref
import nl.giejay.android.tv.immich.shared.prefs.EnumWithTitle
import nl.giejay.android.tv.immich.shared.prefs.FILTER_CONTENT_TYPE
import nl.giejay.android.tv.immich.shared.prefs.PrefScreen
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager

class AlbumDetailsSettingsFragment : SettingsScreenFragment(){
    override fun getFragment(): SettingsInnerFragment {
        return AlbumDetailsInnerSettingsFragment()
    }
}
class AlbumDetailsInnerSettingsFragment : SettingsScreenFragment.SettingsInnerFragment() {

    override fun getLayout(): Either<Int, PrefScreen> {
        val bundle = requireArguments()
        val albumId = bundle.getString("albumId")!!
        val albumName = bundle.getString("albumName")!!
        return Either.Right(AlbumDetailsSettingsScreen(albumId, albumName))
    }

    override fun handlePreferenceClick(preference: Preference?): Boolean {
        return false
    }
}