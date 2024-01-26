package nl.giejay.android.tv.immich.auth

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.InputType
import android.webkit.URLUtil
import android.widget.Toast
import androidx.leanback.app.GuidedStepSupportFragment
import androidx.leanback.widget.GuidanceStylist
import androidx.leanback.widget.GuidedAction
import androidx.navigation.fragment.findNavController
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.shared.guidedstep.GuidedStepUtil.addAction
import nl.giejay.android.tv.immich.shared.guidedstep.GuidedStepUtil.addEditableAction
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import timber.log.Timber


/**
 * Created by kurt on 2016/02/29.
 */
data class AuthSettings(var hostName: String?, var apiKey: String?) {
    fun isValid(): Boolean {
        return !(hostName.isNullOrEmpty() || apiKey.isNullOrEmpty()) && URLUtil.isValidUrl(hostName)
    }
}

class AuthFragmentStep2 : GuidedStepSupportFragment() {
    private val ACTION_NAME = 0L
    private val ACTION_API_KEY = 1L
    private val ACTION_CONTINUE = 2L
    private val entry: AuthSettings = AuthSettings(PreferenceManager.hostName(), PreferenceManager.apiKey())

    override fun onCreateGuidance(savedInstanceState: Bundle?): GuidanceStylist.Guidance {
        val icon: Drawable = requireActivity().getDrawable(R.drawable.icon)!!
        return GuidanceStylist.Guidance("Immich TV", "Login to your Immich server or try a demo.", "", icon)
    }

    override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        addEditableAction(actions, ACTION_NAME, "Hostname", entry.hostName, InputType.TYPE_CLASS_TEXT)
        addEditableAction(actions, ACTION_API_KEY, "API Key", entry.apiKey, InputType.TYPE_CLASS_TEXT)
    }

//    override fun onCreateActionsStylist(): GuidedActionsStylist {
//        return object : GuidedActionsStylist() {
//            override fun onProvideItemLayoutId(): Int {
//                // return your custom layout for each GuidedAction item
//                return R.layout.guided_step_text_view
//            }
//
//            override fun onCreateViewHolder(parent: ViewGroup?): ViewHolder {
//                val viewHolder = super.onCreateViewHolder(parent)
////                viewHolder.itemView.setOnKeyListener(OnKeyListener { v, keyCode, event ->
////                    if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP) {
////                        val mgr = v.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
////                        v.isFocusable = true
////                        v.requestFocus()
////                        mgr.showSoftInput(v, 0)
////                        return@OnKeyListener true
////                    }
////                    false
////                })
//                return viewHolder
//            }
//        }
//    }

    override fun onCreateButtonActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        super.onCreateButtonActions(actions, savedInstanceState)
        addAction(actions, ACTION_CONTINUE, "Submit", "")
    }

    override fun onGuidedActionClicked(action: GuidedAction) {
        super.onGuidedActionClicked(action)
        Timber.i("Clicked on ${action.title} in step 2, entry valid: ${entry.isValid()}")
        if (action.id == ACTION_CONTINUE) {
            if(entry.isValid()){
                PreferenceManager.saveApiKey(entry.apiKey!!)
                PreferenceManager.saveHostName(entry.hostName!!)
                findNavController().navigate(AuthFragmentStep2Directions.actionAuth2ToHomeFragment())
            } else {
                Toast.makeText(activity, "Please enter a valid hostname and API key.", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    override fun onGuidedActionEditedAndProceed(action: GuidedAction): Long {
        saveState(action)
        return super.onGuidedActionEditedAndProceed(action)
    }

    override fun onGuidedActionEditCanceled(action: GuidedAction) {
        saveState(action)
        super.onGuidedActionEditCanceled(action)
    }

    private fun saveState(action: GuidedAction) {
        if (action.id == ACTION_NAME) {
            entry.hostName = action.description.toString()
        } else if (action.id == ACTION_API_KEY) {
            entry.apiKey = action.description.toString()
        }
    }
}