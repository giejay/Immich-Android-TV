package nl.giejay.mediaslider.util

import android.view.KeyEvent

interface MediaSliderListener {
    fun onButtonPressed(keyEvent: KeyEvent): Boolean
}