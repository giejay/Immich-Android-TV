package nl.giejay.android.tv.immich.settings

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.leanback.preference.LeanbackPreferenceFragmentCompat
import androidx.leanback.preference.LeanbackSettingsFragmentCompat
import androidx.preference.Preference
import androidx.preference.PreferenceDialogFragmentCompat
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen


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
        abstract fun getFragmentLayout(): Int

        abstract fun handlePreferenceClick(preference: Preference?): Boolean
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            // Load the preferences from an XML resource
            setPreferencesFromResource(getFragmentLayout(), rootKey)
        }

        override fun onPreferenceTreeClick(preference: Preference?): Boolean {
            val handled = handlePreferenceClick(preference!!)
            return if (handled) true else super.onPreferenceTreeClick(preference)
        }
    }
}