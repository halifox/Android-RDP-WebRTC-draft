package com.github.control

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.MotionEvent
import android.view.accessibility.AccessibilityEvent
import com.github.control.anydesk.MotionEventHandler
import com.github.control.scrcpy.Injector
import com.github.control.scrcpy.InjectorDelegate
import java.net.ServerSocket

class AccService : AccessibilityService() {
    private val context = this
    private val injector = Injector()
    private val motionEventHandler = MotionEventHandler(this)

    private val delegate = object : InjectorDelegate {
        override fun injectInputEvent(inputEvent: MotionEvent, displayId: Int, injectMode: Int): Boolean {
            motionEventHandler.handleEvent(inputEvent)
            return true
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Injector.updateDisplayMetrics(context)
        }
    }

    override fun onCreate() {
        super.onCreate()
        registerReceiver(receiver, IntentFilter(Intent.ACTION_CONFIGURATION_CHANGED))
        Injector.updateDisplayMetrics(context)
        injector.setInjectorDelegate(delegate)
        thread.start()
    }


    override fun onDestroy() {
        super.onDestroy()
        thread.interrupt()
        injector.setInjectorDelegate(null)
        unregisterReceiver(receiver)
    }

    private val serverSocket = ServerSocket(40000, 1)

    private val thread = Thread {
        while (true) {
            kotlin.runCatching {
                serverSocket.accept()
            }.onSuccess { socket ->
                EventSocketHandler(socket, injector)
            }
        }
    }


    override fun onServiceConnected() {}
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

}

