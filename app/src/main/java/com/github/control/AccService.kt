package com.github.control

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import android.view.MotionEvent
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.DataInputStream
import java.net.ServerSocket

class AccService : AccessibilityService() {
    private val context = this
    private val coroutineScope = MainScope()
    private val serverSocket = ServerSocket(40000, 1)
    private val touchEventInjector = TouchEventInjector()
    private val motionEventHandler = MotionEventHandler(this)

    private val inputEventHandlerInterface = object : InputEventInjector {
        override fun injectInputEvent(inputEvent: MotionEvent, displayId: Int, injectMode: Int): Boolean {
            motionEventHandler.handleEvent(inputEvent)
            return true
        }
    }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            TouchEventInjector.updateDisplayMetrics(context)
        }
    }


    override fun onCreate() {
        super.onCreate()
        registerReceiver(receiver, IntentFilter(Intent.ACTION_CONFIGURATION_CHANGED))
        TouchEventInjector.updateDisplayMetrics(context)
        touchEventInjector.setInputEventInjector(inputEventHandlerInterface)
        coroutineScope.launch(Dispatchers.IO) {
            while (true) {
                val socket = serverSocket.accept()
                Log.d("TAG", "accept:${socket} ")
                val inputStream = DataInputStream(socket.getInputStream())
                while (true) {
                    val type = inputStream.readByte().toInt()
                    when (type) {
                        2    -> {
                            val action = inputStream.readInt()
                            val pointerId = inputStream.readInt()
                            val x = inputStream.readInt()
                            val y = inputStream.readInt()
                            val screenWidth = inputStream.readInt()
                            val screenHeight = inputStream.readInt()
                            val pressure = inputStream.readFloat()
                            val actionButton = inputStream.readInt()
                            val buttons = inputStream.readInt()
                            val position = Position(x, y, screenWidth, screenHeight)
                            touchEventInjector.injectTouch(action, pointerId, position, pressure, actionButton, buttons)
                        }
                        else -> {}
                    }


                }

            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel()
        unregisterReceiver(receiver)
    }


    override fun onServiceConnected() {}
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

}

