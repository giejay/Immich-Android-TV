package nl.giejay.android.tv.immich.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentDialog
import androidx.activity.OnBackPressedCallback
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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

/**
 * Base for Compose content inside [SettingsDialogFragment]. While the content reports an open
 * choice list, Back closes only that list instead of the whole dialog.
 */
abstract class ComposeSettingsFragment : Fragment() {
    @Composable
    abstract fun Content(closeChoiceSignal: Int, onChoosingChanged: (Boolean) -> Unit)

    private var closeChoiceSignal by mutableIntStateOf(0)

    // While a choice list is open Back closes only that list instead of the whole dialog.
    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            closeChoiceSignal++
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        (requireParentFragment() as? androidx.fragment.app.DialogFragment)?.let { parent ->
            (parent.dialog as? ComponentDialog)?.onBackPressedDispatcher?.addCallback(viewLifecycleOwner, backCallback)
        }
        return immichComposeView(requireContext()) {
            Content(closeChoiceSignal = closeChoiceSignal, onChoosingChanged = { backCallback.isEnabled = it })
        }
    }
}

/** Hosts a [PrefScreen] as Compose panel. */
abstract class ComposePrefScreenFragment : ComposeSettingsFragment() {
    abstract fun getScreen(): PrefScreen

    @Composable
    override fun Content(closeChoiceSignal: Int, onChoosingChanged: (Boolean) -> Unit) {
        PrefScreenPanel(
            screen = remember { getScreen() },
            navController = findNavController(),
            closeChoiceSignal = closeChoiceSignal,
            onChoosingChanged = onChoosingChanged,
        )
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
