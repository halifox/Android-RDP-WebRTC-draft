package com.github.control

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import android.view.MotionEvent
import android.view.accessibility.AccessibilityEvent
import com.github.control.anydesk.MotionEventHandler
import com.github.control.scrcpy.Injector
import com.github.control.scrcpy.InjectorDelegate
import java.net.ServerSocket
import java.util.concurrent.Executors


class AccService : AccessibilityService() {
    private val context = this
    private val injector = Injector()
    private val motionEventHandler = MotionEventHandler(this)

    private val executor = Executors.newCachedThreadPool()

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
        executor.execute(::startServer)
    }


    override fun onDestroy() {
        super.onDestroy()
        injector.setInjectorDelegate(null)
        unregisterReceiver(receiver)
        executor.shutdown()
    }

    private fun startServer() {
        try {
            val serverSocket = ServerSocket(40000, 1)
            while (true) {
                val socket = serverSocket.accept()
                Log.d("TAG", "accept:${socket} ")
                val handler = EventSocketHandler(socket, injector = injector)
                executor.execute(handler::loopRecv)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    override fun onServiceConnected() {}
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

}

