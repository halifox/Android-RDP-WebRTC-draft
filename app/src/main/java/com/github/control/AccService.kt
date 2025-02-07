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
            Log.d("TAG", "Starting server on port 40000...")
            val serverSocket = ServerSocket(40000, 1)
            Log.d("TAG", "Server started successfully, waiting for connections...")

            while (true) {
                Log.d("TAG", "Waiting for client connection...")
                val socket = serverSocket.accept()
                Log.d("TAG", "Connection accepted from: ${socket.inetAddress.hostAddress}, port: ${socket.port}")

                val handler = EventSocketHandler(socket, injector = injector)
                Log.d("TAG", "Handler created for connection: ${socket.inetAddress.hostAddress}:${socket.port}")

                executor.execute {
                    Log.d("TAG", "Starting handler loop for socket: ${socket.inetAddress.hostAddress}:${socket.port}")
                    handler.loopRecv()
                }
            }
        } catch (e: Exception) {
            Log.e("TAG", "Error occurred while starting server", e)
            e.printStackTrace()
        }
    }


    override fun onServiceConnected() {}
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

}

