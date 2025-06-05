package nl.giejay.android.tv.immich.settings

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.leanback.preference.LeanbackPreferenceFragmentCompat
import androidx.leanback.preference.LeanbackSettingsFragmentCompat
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceDialogFragmentCompat
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import arrow.core.Either
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.shared.prefs.EnumByTitlePref
import nl.giejay.android.tv.immich.shared.prefs.EnumWithTitle
import nl.giejay.android.tv.immich.shared.prefs.PrefScreen
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager


abstract class SettingsScreenFragment : LeanbackSettingsFragmentCompat() {

    abstract fun getFragment(): SettingsInnerFragment

    override fun onPreferenceStartInitialScreen() {
        val fragment = getFragment()
        fragment.arguments = arguments
        startPreferenceFragment(fragment)
    }

    override fun onPreferenceStartFragment(
        caller: PreferenceFragmentCompat,
        pref: Preference
    ): Boolean {
        val args = pref.extras
        val f = childFragmentManager.fragmentFactory.instantiate(
            requireActivity().classLoader, pref.fragment
        )
        f.arguments = args
        f.setTargetFragment(caller, 0)
        if (f is PreferenceFragmentCompat
            || f is PreferenceDialogFragmentCompat
        ) {
            startPreferenceFragment(f)
        } else {
            startImmersiveFragment(f)
        }
        return true
    }

    override fun onPreferenceStartScreen(
        caller: PreferenceFragmentCompat,
        pref: PreferenceScreen
    ): Boolean {
        val fragment: Fragment = getFragment()
        val args = Bundle(1)
        args.putString(PreferenceFragmentCompat.ARG_PREFERENCE_ROOT, pref.key)
        fragment.arguments = args
        startPreferenceFragment(fragment)
        return true
    }

    abstract class SettingsInnerFragment : LeanbackPreferenceFragmentCompat() {
        abstract fun getLayout(): Either<Int, PrefScreen>

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)

            // after changing a pref, set the summary again for all listprefs with enumWithTitle
            getLayout().map { prefScreen ->
                val prefs = prefScreen.children.flatMap { it.children }
                prefs.filterIsInstance(EnumByTitlePref::class.java).forEach{
                    val value: EnumWithTitle = it.getValue(PreferenceManager.sharedPreference) as EnumWithTitle
                    findPreference<Preference>(it.key())!!.summary = value.getTitle()
                }
            }
        }

        abstract fun handlePreferenceClick(preference: Preference?): Boolean
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            // Load the preferences from an XML resource
            setPreferencesFromResource(R.xml.preferences_empty, rootKey)
            val layout = getLayout()
            layout.fold(
                { layoutId -> setPreferencesFromResource(layoutId, rootKey) },
                { prefScreen ->
                    preferenceScreen.title = prefScreen.title
                    prefScreen.children.forEach {
                        val preferenceCategory = PreferenceCategory(requireContext())
                        preferenceScreen.addPreference(preferenceCategory)
                        if(it.title.isNotBlank()){
                            preferenceCategory.title = it.title
                        }
                        it.children.forEach { child ->
                            preferenceCategory.addPreference(child.createPreference(requireContext()))
                        }
                    }
                })
        }


        override fun onPreferenceTreeClick(preference: Preference?): Boolean {
            val handled = handlePreferenceClick(preference!!)
            return if (handled) true else super.onPreferenceTreeClick(preference)
        }
    }
}