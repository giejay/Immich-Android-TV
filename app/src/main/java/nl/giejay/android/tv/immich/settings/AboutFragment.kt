package nl.giejay.android.tv.immich.settings

import androidx.compose.runtime.Composable
import nl.giejay.android.tv.immich.ui.settings.AboutScreen

/** The About page inside the settings dialog (no choice lists, so Back always closes the dialog). */
class AboutFragment : ComposeSettingsFragment() {
    @Composable
    override fun Content(closeChoiceSignal: Int, onChoosingChanged: (Boolean) -> Unit) {
        AboutScreen()
    }
}
