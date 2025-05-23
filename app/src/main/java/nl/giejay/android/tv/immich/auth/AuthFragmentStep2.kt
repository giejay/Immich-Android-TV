package nl.giejay.android.tv.immich.auth

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.InputType
import android.widget.Toast
import androidx.leanback.app.GuidedStepSupportFragment
import androidx.leanback.widget.GuidanceStylist
import androidx.leanback.widget.GuidedAction
import androidx.leanback.widget.GuidedActionEditText
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.shared.guidedstep.GuidedStepUtil.addAction
import nl.giejay.android.tv.immich.shared.guidedstep.GuidedStepUtil.addCheckedAction
import nl.giejay.android.tv.immich.shared.guidedstep.GuidedStepUtil.addEditableAction
import nl.giejay.android.tv.immich.shared.prefs.API_KEY
import nl.giejay.android.tv.immich.shared.prefs.DEBUG_MODE
import nl.giejay.android.tv.immich.shared.prefs.DISABLE_SSL_VERIFICATION
import nl.giejay.android.tv.immich.shared.prefs.HOST_NAME
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import nl.giejay.android.tv.immich.shared.prefs.SCREENSAVER_ALBUMS
import timber.log.Timber


data class AuthSettings(val hostName: String, val apiKey: String) {
    fun isValid(): Boolean {
        return PreferenceManager.isValid(hostName, apiKey)
    }
}

class AuthFragmentStep2 : GuidedStepSupportFragment() {
    private val ACTION_NAME = 0L
    private val ACTION_API_KEY = 1L
    private val ACTION_CHECK_CERTS = 2L
    private val ACTION_DEBUG_MODE = 3L
    private val ACTION_CONTINUE = 4L

    override fun onCreateGuidance(savedInstanceState: Bundle?): GuidanceStylist.Guidance {
        val icon: Drawable = requireContext().getDrawable(R.drawable.icon)!!
        return GuidanceStylist.Guidance(
            "Immich TV",
            "Login to your Immich server or try a demo.",
            "",
            icon
        )
    }

    override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        addEditableAction(
            actions,
            ACTION_NAME,
            "Hostname",
            PreferenceManager.get(HOST_NAME),
            InputType.TYPE_CLASS_TEXT
        )
        addEditableAction(
            actions,
            ACTION_API_KEY,
            "API Key",
            PreferenceManager.get(API_KEY),
            InputType.TYPE_CLASS_TEXT
        )
        addCheckedAction(
            actions,
            ACTION_CHECK_CERTS,
            "Disable SSL verification",
            "Only use this when you have issues with self signed certificates!",
            PreferenceManager.get(DISABLE_SSL_VERIFICATION)
        )
        addCheckedAction(
            actions,
            ACTION_DEBUG_MODE,
            "Debug mode",
            "Enable this if you are experiencing issues with getting the Immich data.",
            PreferenceManager.get(DEBUG_MODE)
        )
    }

    override fun onCreateButtonActions(
        actions: MutableList<GuidedAction>,
        savedInstanceState: Bundle?
    ) {
        super.onCreateButtonActions(actions, savedInstanceState)
        addAction(actions, ACTION_CONTINUE, "Submit", "")
    }

    override fun onGuidedActionClicked(action: GuidedAction) {
        super.onGuidedActionClicked(action)
        val entry = AuthSettings(getState(ACTION_NAME), getState(ACTION_API_KEY))
        Timber.i("Clicked on ${action.title} in step 2, entry valid: ${entry.isValid()}")
        if (action.id == ACTION_CONTINUE) {
            if (entry.isValid()) {
                PreferenceManager.save(SCREENSAVER_ALBUMS, emptySet())
                PreferenceManager.save(API_KEY, entry.apiKey)
                PreferenceManager.save(HOST_NAME, entry.hostName)
                PreferenceManager.save(DISABLE_SSL_VERIFICATION, findActionById(ACTION_CHECK_CERTS)?.isChecked == true)
                PreferenceManager.save(DEBUG_MODE, findActionById(ACTION_DEBUG_MODE)?.isChecked == true)
                val navControl = findNavController()
                navControl.navigate(AuthFragmentStep2Directions.actionGlobalHomeFragment(), NavOptions.Builder().setPopUpTo(R.id.authFragment, true).build())
            } else if (entry.hostName.isEmpty()) {
                Toast.makeText(activity, "Please enter a hostname", Toast.LENGTH_SHORT)
                    .show()
            } else if (entry.apiKey.isEmpty()) {
                Toast.makeText(
                    activity,
                    "Please enter an API key, if you did enter it, try to highlight it again and press enter/OK, not back (sorry).",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(activity, "Please enter a valid hostname", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    private fun getState(actionId: Long): String {
        // there really does not seem to be a better way to handle hardware keyboard input or for example: adb shell input text myText
        // https://github.com/giejay/Immich-Android-TV/issues/4
        val position = findActionPositionById(actionId)
        return getActionItemView(position)?.findViewById<GuidedActionEditText>(R.id.guidedactions_item_description)?.text.toString()
    }
}