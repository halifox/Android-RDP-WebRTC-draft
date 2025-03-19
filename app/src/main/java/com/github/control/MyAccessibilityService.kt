package com.github.control

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import com.github.control.anydesk.InputEventControllerDelegate
import com.github.control.scrcpy.Controller
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinComponent


class MyAccessibilityService : AccessibilityService(), KoinComponent {
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

    companion object {
        fun isAccessibilityEnabled(context: Context): Boolean {
            val enabledServices = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            return enabledServices != null && enabledServices.contains(MyAccessibilityService::class.java.name)
        }

        fun openAccessibilitySettings(context: Context) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }
}

