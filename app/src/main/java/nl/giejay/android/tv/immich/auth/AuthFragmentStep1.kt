package nl.giejay.android.tv.immich.auth

import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.leanback.app.GuidedStepSupportFragment
import androidx.leanback.widget.GuidanceStylist
import androidx.leanback.widget.GuidedAction
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import nl.giejay.android.tv.immich.BuildConfig
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.shared.guidedstep.GuidedStepUtil.addAction
import nl.giejay.android.tv.immich.shared.guidedstep.GuidedStepUtil.addCheckedAction
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import timber.log.Timber


/**
 * Created by kurt on 2016/02/29.
 */

class AuthFragmentStep1 : GuidedStepSupportFragment() {
    private val ACTION_CONTINUE = 0L
    private val ACTION_DEMO = 1L
    private var demoMode: Boolean = false

    override fun onCreateGuidance(savedInstanceState: Bundle?): GuidanceStylist.Guidance {
        val icon: Drawable = requireContext().getDrawable(nl.giejay.android.tv.immich.R.drawable.icon)!!
        return GuidanceStylist.Guidance("Immich TV (${BuildConfig.VERSION_NAME})", "Login to your Immich server or try a demo.", "", icon)
    }

    override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        addCheckedAction(actions, ACTION_DEMO, "Demo mode", "Login to a demo Immich instance.", demoMode)
    }

    override fun onCreateButtonActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        super.onCreateButtonActions(actions, savedInstanceState)
        addAction(actions, ACTION_CONTINUE, "Continue", "")
    }

    override fun onGuidedActionClicked(action: GuidedAction) {
        super.onGuidedActionClicked(action)
        Timber.i("Clicked ${action.title} on step 1 of authentication, demoMode: $demoMode")
        if (action.id == ACTION_CONTINUE) {
            val hostName = requireContext().resources.getString(R.string.host_name)
            val navController = findNavController()
            if (demoMode) {
                PreferenceManager.saveApiKey(requireContext().resources.getString(R.string.api_key))
                PreferenceManager.saveHostName(hostName)
                navController.navigate(AuthFragmentStep1Directions.actionAuthToHomeFragment())
            } else {
                if(PreferenceManager.hostName() == hostName){
                    PreferenceManager.removeApiSettings()
                }
                if (navController.currentDestination?.id == R.id.authFragment) {
                    Timber.i("Navigating to step 2")
                    //https://stackoverflow.com/questions/51060762/illegalargumentexception-navigation-destination-xxx-is-unknown-to-this-navcontr
                    navController.navigate(AuthFragmentStep1Directions.actionAuthToAuth2())
                }
            }
        } else if (action.id == ACTION_DEMO) {
            demoMode = !demoMode
        }
    }
}