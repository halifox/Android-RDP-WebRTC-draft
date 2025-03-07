package com.github.control.anydesk

import android.view.KeyEvent
import android.view.MotionEvent

interface IControllerDelegate {
    fun injectInputEvent(inputEvent: MotionEvent, displayId: Int, injectMode: Int): Boolean

    fun injectKeyEvent(keyEvent: KeyEvent)

    fun injectGlobalAction(action: Int): Boolean
}
