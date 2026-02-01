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
import nl.giejay.android.tv.immich.shared.prefs.API_KEY
import nl.giejay.android.tv.immich.shared.prefs.HOST_NAME
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import nl.giejay.android.tv.immich.shared.prefs.SCREENSAVER_ALBUMS
import timber.log.Timber



class AuthFragmentStep1 : GuidedStepSupportFragment() {
    private val ACTION_SIGN_IN = 0L
    private val ACTION_DEMO = 1L
    private val ACTION_PHONE_SIGN_IN = 2L
    private val ACTION_CONTINUE = 3L
    private var SELECTED_OPTION: Long = ACTION_SIGN_IN

    override fun onCreateGuidance(savedInstanceState: Bundle?): GuidanceStylist.Guidance {
        val icon: Drawable =
            requireContext().getDrawable(R.drawable.icon)!!
        return GuidanceStylist.Guidance(
            getString(R.string.app_name) + " (${BuildConfig.VERSION_NAME})",
            getString(R.string.login_immich_description),
            "",
            icon
        )
    }

    override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        addCheckedAction(
            actions,
            ACTION_SIGN_IN,
            getString(R.string.auth_sign_in_by_api_key),
            getString(R.string.auth_sign_in_by_api_key_desc),
            true,
            1
        )
        addCheckedAction(
            actions,
            ACTION_PHONE_SIGN_IN,
            getString(R.string.auth_sign_in_by_phone),
            getString(R.string.auth_sign_in_by_phone_desc),
            false,
            1
        )
        addCheckedAction(
            actions,
            ACTION_DEMO,
            getString(R.string.auth_demo_mode),
            getString(R.string.auth_demo_mode_desc),
            false,
            1
        )
    }

    override fun onCreateButtonActions(
        actions: MutableList<GuidedAction>,
        savedInstanceState: Bundle?
    ) {
        super.onCreateButtonActions(actions, savedInstanceState)
        addAction(actions, ACTION_CONTINUE, getString(R.string.continue_text), "")
    }

    override fun onGuidedActionClicked(action: GuidedAction) {
        super.onGuidedActionClicked(action)
        Timber.i("Clicked ${action.title} on step 1 of authentication, option: ${SELECTED_OPTION}")
        if (action.id == ACTION_CONTINUE) {
            val hostName = requireContext().resources.getString(R.string.host_name)
            val navController = findNavController()
            if (SELECTED_OPTION == ACTION_DEMO) {
                PreferenceManager.save(SCREENSAVER_ALBUMS, emptySet())
                PreferenceManager.save(API_KEY, requireContext().resources.getString(R.string.api_key))
                PreferenceManager.save(HOST_NAME, hostName)
                navController.navigate(AuthFragmentStep1Directions.actionGlobalHomeFragment(), NavOptions.Builder().setPopUpTo(R.id.authFragment, true).build())
            } else if (SELECTED_OPTION == ACTION_SIGN_IN) {
                if (PreferenceManager.get(HOST_NAME) == hostName) {
                    // remove demo instance api key
                    PreferenceManager.removeApiSettings()
                }
                if (navController.currentDestination?.id == R.id.authFragment) {
                    Timber.i("Navigating to step 2")
                    //https://stackoverflow.com/questions/51060762/illegalargumentexception-navigation-destination-xxx-is-unknown-to-this-navcontr
                    navController.navigate(AuthFragmentStep1Directions.actionAuthToAuth2())
                }
            } else if (SELECTED_OPTION == ACTION_PHONE_SIGN_IN) {
                findNavController().navigate(AuthFragmentStep1Directions.actionAuthToAuthByPhone())
            }
        } else {
            SELECTED_OPTION = action.id
        }
    }
}
