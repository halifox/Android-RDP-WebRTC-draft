package com.github.control

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import android.view.MotionEvent
import android.view.accessibility.AccessibilityEvent
import com.github.control.anydesk.EventSocketHandler
import com.github.control.anydesk.MotionEventHandler
import com.github.control.scrcpy.Controller
import com.github.control.scrcpy.ControllerDelegate
import java.net.ServerSocket
import java.util.concurrent.Executors


class AccService : AccessibilityService() {
    private val context = this
    private val controller = Controller()
    private val motionEventHandler = MotionEventHandler(this)

    private val executor = Executors.newCachedThreadPool()

    private val delegate = object : ControllerDelegate {
        override fun injectInputEvent(inputEvent: MotionEvent, displayId: Int, injectMode: Int): Boolean {
            motionEventHandler.handleEvent(inputEvent)
            return true
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Controller.updateDisplayMetrics(context)
        }
    }

    override fun onCreate() {
        super.onCreate()
        registerReceiver(receiver, IntentFilter(Intent.ACTION_CONFIGURATION_CHANGED))
        Controller.updateDisplayMetrics(context)
        controller.setInjectorDelegate(delegate)
        executor.execute(::startServer)
    }


    override fun onDestroy() {
        super.onDestroy()
        controller.setInjectorDelegate(null)
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

                val handler = EventSocketHandler(socket, controller = controller)
                Log.d("TAG", "Handler created for connection: ${socket.inetAddress.hostAddress}:${socket.port}")

                executor.execute {
                    Log.d("TAG", "Starting handler loop for socket: ${socket.inetAddress.hostAddress}:${socket.port}")
                    handler.loopRecv()
                    Log.d("TAG", "Finish handler loop for socket: ${socket.inetAddress.hostAddress}:${socket.port}")
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

