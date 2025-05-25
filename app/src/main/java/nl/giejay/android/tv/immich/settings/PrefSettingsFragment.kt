package nl.giejay.android.tv.immich.settings

import android.os.Bundle
import android.view.View
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import arrow.core.Either
import arrow.core.getOrElse
import nl.giejay.android.tv.immich.shared.prefs.ActionPref
import nl.giejay.android.tv.immich.shared.prefs.PrefScreen
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager

class PrefSettingsFragment : SettingsScreenFragment() {
    override fun getFragment(): SettingsInnerFragment {
        return PrefSettingsInnerFragment()
    }
}

class PrefSettingsInnerFragment : SettingsScreenFragment.SettingsInnerFragment() {

    override fun getLayout(): Either<Int, PrefScreen> {
        val bundle = requireArguments()
        val settingsType = bundle.getString("settings_type")
        val prefScreen = PreferenceManager.subclasses(PrefScreen::class).find { it.objectInstance?.key ==  settingsType}?.objectInstance!!
        return Either.Right(prefScreen)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        getLayout().map { it.onViewCreated.invoke(preferenceManager) }
    }

    override fun handlePreferenceClick(preference: Preference?): Boolean {
        return getLayout().map {
            // only handle PrefScreen implementations for now
            it.findByKey(preference!!.key)?.onClick(requireContext(), findNavController()) == true
        }.getOrElse { false }
    }
}
