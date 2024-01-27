package nl.giejay.android.tv.immich.settings

import android.content.Intent
import android.widget.Toast
import androidx.preference.Preference
import nl.giejay.android.tv.immich.R

class ScreenSaverSettingsFragment : SettingsScreenFragment() {
    private val SCREENSAVER_SETTINGS = "android.settings.DREAM_SETTINGS"
    private val SETTINGS = "android.settings.SETTINGS"

    override fun getFragmentLayout(): Int {
        return R.xml.preferences_screensaver
    }

    override fun handlePreferenceClick(preference: Preference?): Boolean {
        when(preference?.key){
            "screensaver_set" ->{
                startScreenSaverIntent()
                return true
            }
            "screensaver_include_videos", "screensaver_play_sound" -> {
                Toast.makeText(requireContext(), "Work in progress", Toast.LENGTH_SHORT).show()
            }

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
            if (!intentAvailable(intent)) {
                // If all else fails, open the normal settings screen
                intent = Intent(SETTINGS);
            }
        }
        startActivity(intent);
    }

}