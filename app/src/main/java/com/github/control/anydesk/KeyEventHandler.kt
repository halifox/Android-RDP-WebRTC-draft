package com.github.control.anydesk

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK
import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME
import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS
import android.os.Parcel
import android.view.KeyEvent

class KeyEventHandler(
    private val accessibilityService: AccessibilityService,
) {
    fun handleEvent(parcel: Parcel) {
        val keyEvent = KeyEvent.CREATOR.createFromParcel(parcel)
        val unusedByte1 = parcel.readByte()
        val unusedByte2 = parcel.readByte()
        val action = keyEvent.action
        val keyCode = keyEvent.keyCode
        val isShiftPressed = keyEvent.isShiftPressed
        val isCtrlPressed = keyEvent.isCtrlPressed
        when (keyCode) {
            KeyEvent.KEYCODE_HOME        -> accessibilityService.performGlobalAction(GLOBAL_ACTION_HOME)
            KeyEvent.KEYCODE_BACK        -> accessibilityService.performGlobalAction(GLOBAL_ACTION_BACK)
            KeyEvent.KEYCODE_VOLUME_UP   -> {}
            KeyEvent.KEYCODE_VOLUME_DOWN -> {}
            KeyEvent.KEYCODE_POWER       -> {}
            KeyEvent.KEYCODE_SPACE       -> {}
            KeyEvent.KEYCODE_MENU        -> {}
            KeyEvent.KEYCODE_APP_SWITCH  -> accessibilityService.performGlobalAction(GLOBAL_ACTION_RECENTS)
        }
    }
}