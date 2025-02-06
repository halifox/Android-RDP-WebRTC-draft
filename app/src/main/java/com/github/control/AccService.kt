package com.github.control

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.InputEvent
import android.view.MotionEvent
import android.view.accessibility.AccessibilityEvent
import com.genymobile.scrcpy.BuildConfig
import com.genymobile.scrcpy.Server
import com.genymobile.scrcpy.compat.CoreControllerCompat
import com.genymobile.scrcpy.compat.InputEventHandler
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel

class AccService : AccessibilityService(), InputEventHandler {
    private val context = this
    private val coroutineScope = MainScope()

    private val motionEventHandler = MotionEventHandler(this)

    private val inputEventHandlerInterface = object : InputEventHandler {
        override fun injectInputEvent(inputEvent: InputEvent?, injectMode: Int): Boolean {
            if (inputEvent is MotionEvent) {
                motionEventHandler.handleEvent(inputEvent)
            }
            return true
        }
    }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            CoreControllerCompat.updateDisplayMetrics(context)
        }
    }
    private val scrcpyThread = Thread {
        Server.main(
            BuildConfig.VERSION_NAME,
            "tunnel_forward=true",
            "control=true",
            "video=false",
            "audio=false",
            "send_dummy_byte=false",
            "clipboard_autosync=false",
        )
    }


    override fun onCreate() {
        super.onCreate()
        registerReceiver(receiver, IntentFilter(Intent.ACTION_CONFIGURATION_CHANGED))
        CoreControllerCompat.updateDisplayMetrics(context)
        CoreControllerCompat.inputEventHandlerInterface = inputEventHandlerInterface
        scrcpyThread.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        scrcpyThread.destroy()
        coroutineScope.cancel()
        unregisterReceiver(receiver)
    }


    override fun onServiceConnected() {}
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
}

