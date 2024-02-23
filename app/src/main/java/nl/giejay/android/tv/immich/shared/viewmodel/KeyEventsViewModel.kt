package nl.giejay.android.tv.immich.shared.viewmodel

import android.view.KeyEvent
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class KeyEventsViewModel : ViewModel() {

    private val keyEventState = MutableStateFlow<KeyEvent?>(null)
    val state: StateFlow<KeyEvent?>
        get() = keyEventState

    fun postKeyEvent(keyEvent: KeyEvent?){
        keyEventState.value = keyEvent
    }
}