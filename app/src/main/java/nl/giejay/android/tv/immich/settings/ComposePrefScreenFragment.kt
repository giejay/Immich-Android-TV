package nl.giejay.android.tv.immich.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentDialog
import androidx.activity.OnBackPressedCallback
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import nl.giejay.android.tv.immich.shared.prefs.AlbumDetailsSettingsScreen
import nl.giejay.android.tv.immich.shared.prefs.GenericAssetsSettingsScreen
import nl.giejay.android.tv.immich.shared.prefs.PrefScreen
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import nl.giejay.android.tv.immich.ui.immichComposeView
import nl.giejay.android.tv.immich.ui.settings.PrefScreenPanel

/** Hosts a [PrefScreen] as Compose panel inside [SettingsDialogFragment]. */
abstract class ComposePrefScreenFragment : Fragment() {
    abstract fun getScreen(): PrefScreen

    private var closeChoiceSignal by mutableIntStateOf(0)

    // While a choice list is open Back closes only that list instead of the whole dialog.
    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            closeChoiceSignal++
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val screen = getScreen()
        (requireParentFragment() as? androidx.fragment.app.DialogFragment)?.let { parent ->
            (parent.dialog as? ComponentDialog)?.onBackPressedDispatcher?.addCallback(viewLifecycleOwner, backCallback)
        }
        return immichComposeView(requireContext()) {
            PrefScreenPanel(
                screen = screen,
                navController = findNavController(),
                closeChoiceSignal = closeChoiceSignal,
                onChoosingChanged = { backCallback.isEnabled = it },
            )
        }
    }
}

class PrefSettingsFragment : ComposePrefScreenFragment() {
    override fun getScreen(): PrefScreen {
        val settingsType = requireArguments().getString("settings_type")
        return PreferenceManager.subclasses(PrefScreen::class).find { it.objectInstance?.key == settingsType }?.objectInstance!!
    }
}

class AlbumDetailsSettingsFragment : ComposePrefScreenFragment() {
    override fun getScreen(): PrefScreen {
        val bundle = requireArguments()
        return AlbumDetailsSettingsScreen(bundle.getString("albumId")!!, bundle.getString("albumName")!!)
    }
}

class GenericAssetsSettingsFragment : ComposePrefScreenFragment() {
    override fun getScreen(): PrefScreen = GenericAssetsSettingsScreen
}
