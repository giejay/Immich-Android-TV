package nl.giejay.android.tv.immich.settings

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.RelativeLayout
import androidx.fragment.app.DialogFragment
import nl.giejay.android.tv.immich.R


class SettingsDialogFragment : DialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val settingsType = SettingsDialogFragmentArgs.fromBundle(requireArguments()).settingsType
        val fragment = when(settingsType) {
            "view" -> ViewSettingsFragment()
            "album_details" -> AlbumDetailsSettingsFragment()
            "debug" -> DebugSettingsFragment()
            else -> ScreenSaverSettingsFragment()
        }
        val v: View = inflater.inflate(R.layout.fragment_dialog, container, false)
        childFragmentManager.beginTransaction().add(R.id.fragment_settings_holder, fragment)
            .commit()
        return v
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        // the content
        val root = RelativeLayout(activity)
//        root.layoutParams = ViewGroup.LayoutParams(
//            200,
//            ViewGroup.LayoutParams.MATCH_PARENT
//        )

        // creating the fullscreen dialog
        val dialog = Dialog(requireContext())
        val dialogWindow: Window? = dialog.window
        dialog.setContentView(root)
        dialogWindow!!.setBackgroundDrawable(ColorDrawable(Color.BLACK))
//        val params: WindowManager.LayoutParams = dialogWindow.attributes
//        params.height = ViewGroup.LayoutParams.MATCH_PARENT
//        dialogWindow.attributes = params
        dialogWindow.setGravity(Gravity.END)
        return dialog
    }

}