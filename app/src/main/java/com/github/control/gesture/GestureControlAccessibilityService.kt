package com.github.control.gesture

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinComponent


class GestureControlAccessibilityService : AccessibilityService(), KoinComponent {
    private val gestureServiceDelegate by inject<GestureServiceDelegate>()

    override fun onCreate() {
        super.onCreate()
        gestureServiceDelegate.accessibilityService = this
    }


    override fun onDestroy() {
        super.onDestroy()
        gestureServiceDelegate.accessibilityService = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    companion object {
        fun isAccessibilityEnabled(context: Context): Boolean {
            val enabledServices = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            return enabledServices != null && enabledServices.contains(GestureControlAccessibilityService::class.java.name)
        }

        fun openAccessibilitySettings(context: Context) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }
}

