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
    abstract fun getFragmentLayout(): Int

    abstract fun handlePreferenceClick(preference: Preference?): Boolean

    override fun onPreferenceStartInitialScreen() {
        startPreferenceFragment(SettingsInnerFragment(getFragmentLayout()) { pref ->
            handlePreferenceClick(
                pref
            )
        })
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
        val fragment: Fragment = SettingsInnerFragment(getFragmentLayout()){ preference ->
            handlePreferenceClick(
                preference
            )
        }
        val args = Bundle(1)
        args.putString(PreferenceFragmentCompat.ARG_PREFERENCE_ROOT, pref.key)
        fragment.arguments = args
        startPreferenceFragment(fragment)
        return true
    }

    class SettingsInnerFragment(private val pref: Int, private val handler: (Preference) -> Boolean) : LeanbackPreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            // Load the preferences from an XML resource
            setPreferencesFromResource(pref, rootKey)
        }

        override fun onPreferenceTreeClick(preference: Preference?): Boolean {
            val handled = handler(preference!!)
            return if(handled) true else super.onPreferenceTreeClick(preference)
        }
    }
}