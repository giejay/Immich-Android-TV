package nl.giejay.android.tv.immich.settings

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager


class ScreenSaverSettingsFragment : SettingsScreenFragment() {
    override fun getFragment(): SettingsInnerFragment {
        return ScreenSaverInnerSettingsFragment()
    }
}

class ScreenSaverInnerSettingsFragment : SettingsScreenFragment.SettingsInnerFragment() {
    private val SCREENSAVER_SETTINGS = "android.settings.DREAM_SETTINGS"

    override fun getFragmentLayout(): Int {
        return R.xml.preferences_screensaver
    }

    override fun handlePreferenceClick(preference: Preference?): Boolean {
        when (preference?.key) {
            "screensaver_set" -> {
                if (PreferenceManager.getScreenSaverAlbums().isEmpty()) {
                    Toast.makeText(
                        requireContext(),
                        "Please set your albums to show first!",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    startScreenSaverIntent()
                }
                return true
            }


            "screensaver_set_albums" ->
                findNavController().navigate(
                    ScreenSaverSettingsFragmentDirections.actionGlobalAlbumFragment(
                        true
                    )
                )

        }
        return false
    }

    private fun intentAvailable(intent: Intent): Boolean {
        val manager = requireContext().packageManager;
        val infos = manager.queryIntentActivities(intent, 0);
        return infos.isNotEmpty();
    }

    private fun startScreenSaverIntent() {
        // Check if the daydream intent is available - some devices (e.g. NVidia Shield) do not support it
        var intent = Intent(SCREENSAVER_SETTINGS);
        if (!intentAvailable(intent)) {
            // Try opening the daydream settings activity directly: https://gist.github.com/reines/bc798a2cb539f51877bb279125092104
            intent = Intent(Intent.ACTION_MAIN).setClassName(
                "com.android.tv.settings",
                "com.android.tv.settings.device.display.daydream.DaydreamActivity"
            );
            if (!intentAvailable(intent) || Build.MANUFACTURER == "Google") {
                val layoutInflater =
                    requireActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
                val inflate: View = layoutInflater.inflate(R.layout.screensaver_adb, null)
                val dialog = AlertDialog.Builder(requireContext())
                    .setTitle("Not possible to set screensaver")
                    .create()
                dialog.setView(inflate)
                dialog.setButton(AlertDialog.BUTTON_NEGATIVE, "Close") { d, _ ->
                    d.dismiss()
                }
                return dialog.show()
            }
            startActivity(intent);
        }

    }
}
