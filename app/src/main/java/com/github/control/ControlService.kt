package com.github.control

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.github.control.anydesk.InputEventControllerDelegate
import com.github.control.scrcpy.Controller
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinComponent


class ControlService : AccessibilityService(), KoinComponent {
    private val controller by inject<Controller>()
    private val inputEventControllerDelegate = InputEventControllerDelegate(this)

    override fun onCreate() {
        super.onCreate()
        controller.setControllerDelegate(inputEventControllerDelegate)
    }


    override fun onDestroy() {
        super.onDestroy()
        controller.setControllerDelegate(null)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
}

