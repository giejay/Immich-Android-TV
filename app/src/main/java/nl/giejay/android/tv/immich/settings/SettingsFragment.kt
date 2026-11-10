package nl.giejay.android.tv.immich.settings

import androidx.navigation.fragment.findNavController
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.card.Card
import nl.giejay.android.tv.immich.home.HomeFragmentDirections
import nl.giejay.android.tv.immich.shared.fragment.GridFragment
import nl.giejay.android.tv.immich.shared.prefs.DebugPrefScreen
import nl.giejay.android.tv.immich.shared.prefs.HomeScreenChannelsPrefScreen
import nl.giejay.android.tv.immich.shared.prefs.ScreensaverPrefScreen
import nl.giejay.android.tv.immich.shared.prefs.ViewPrefScreen

/** Settings page: one card per settings screen, rendered by the Compose grid. */
class SettingsFragment : GridFragment(hideProgressBar = true) {
    override val columns: Int = 5

    override fun onViewCreated(view: android.view.View, savedInstanceState: android.os.Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (cards.isEmpty()) {
            cards.addAll(
                listOf(
                    Card(getString(R.string.server), null, "server", "server", null),
                    Card(getString(R.string.view_settings), null, "view_settings", "view_settings", null),
                    Card(getString(R.string.screensaver), null, "screensaver", "screensaver", null),
                    Card(getString(R.string.home_screen_channels), null, "home_screen_channels", "home_screen_channels", null),
                    Card(getString(R.string.debug), null, "debug", "debug", null),
                    Card(getString(R.string.about), null, "about", "about", null),
                )
            )
        }
    }

    override fun onCardClicked(index: Int, card: Card) {
        val directions = when (card.id) {
            "server" -> HomeFragmentDirections.actionGlobalSignInFragment()
            "view_settings" -> HomeFragmentDirections.actionGlobalToSettingsDialog(ViewPrefScreen.key)
            "screensaver" -> HomeFragmentDirections.actionGlobalToSettingsDialog(ScreensaverPrefScreen.key)
            "home_screen_channels" -> HomeFragmentDirections.actionGlobalToSettingsDialog(HomeScreenChannelsPrefScreen.key)
            "debug" -> HomeFragmentDirections.actionGlobalToSettingsDialog(DebugPrefScreen.key)
            "about" -> HomeFragmentDirections.actionGlobalToSettingsDialog("about")
            else -> return
        }
        findNavController().navigate(directions)
    }
}
