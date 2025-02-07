package com.github.control

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import android.view.InputEvent
import android.view.MotionEvent
import android.view.accessibility.AccessibilityEvent
import com.genymobile.scrcpy.compat.CoreControllerCompat
import com.genymobile.scrcpy.compat.InputEventHandler
import com.genymobile.scrcpy.control.ControlMessage
import com.genymobile.scrcpy.device.Position
import com.genymobile.scrcpy.util.Binary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.DataInputStream
import java.net.ServerSocket

class AccService : AccessibilityService(), InputEventHandler {
    private val context = this
    private val coroutineScope = MainScope()
    private val serverSocket = ServerSocket(40000, 1)
    private val cc = CC()
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


    override fun onCreate() {
        super.onCreate()
        registerReceiver(receiver, IntentFilter(Intent.ACTION_CONFIGURATION_CHANGED))
        CoreControllerCompat.updateDisplayMetrics(context)
        CoreControllerCompat.inputEventHandlerInterface = inputEventHandlerInterface
        coroutineScope.launch(Dispatchers.IO) {
            while (true) {
                val socket = serverSocket.accept()
                Log.d("TAG", "accept:${socket} ")
                val inputStream = DataInputStream(socket.getInputStream())
                while (true) {
                    val type = inputStream.readByte().toInt()
                    when (type) {
                        ControlMessage.TYPE_INJECT_TOUCH_EVENT -> {
                            val action = inputStream.readByte().toInt()
                            val pointerId = inputStream.readLong()
                            val x = inputStream.readInt()
                            val y = inputStream.readInt()
                            val screenWidth = inputStream.readUnsignedShort()
                            val screenHeight = inputStream.readUnsignedShort()
                            val position = Position(x, y, screenWidth, screenHeight)
                            val pressure = Binary.u16FixedPointToFloat(inputStream.readShort())
                            val actionButton = inputStream.readInt()
                            val buttons = inputStream.readInt()
                            cc.injectTouch(action, pointerId, position, pressure, actionButton, buttons)
                        }
                        else                                   -> {}
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

