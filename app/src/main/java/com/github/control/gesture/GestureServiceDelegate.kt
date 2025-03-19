package com.github.control.gesture

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.os.Handler

class GestureServiceDelegate {

    var accessibilityService: AccessibilityService? = null

    fun performGlobalAction(action: Int): Boolean {
        return accessibilityService?.performGlobalAction(action) ?: false
    }

    fun dispatchGesture(gesture: GestureDescription, callback: AccessibilityService.GestureResultCallback?, handler: Handler?): Boolean {
        return accessibilityService?.dispatchGesture(gesture, callback, handler) ?: false
    }
}
