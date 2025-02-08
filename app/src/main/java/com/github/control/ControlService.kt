package com.github.control

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.MotionEvent
import android.view.accessibility.AccessibilityEvent
import com.github.control.anydesk.MotionEventHandler
import com.github.control.scrcpy.Binary
import com.github.control.scrcpy.Controller
import com.github.control.scrcpy.ControllerDelegate
import com.github.control.scrcpy.Position
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readInt
import io.ktor.utils.io.readShort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch


class ControlService : AccessibilityService() {
    companion object {
        const val TYPE_MOTION_EVENT = 2
    }

    private val context = this
    private val controller = Controller()
    private val motionEventHandler = MotionEventHandler(this)

    private val delegate = object : ControllerDelegate {
        override fun nothing() {
        }

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
        startServer()
    }


    override fun onDestroy() {
        super.onDestroy()
        controller.setInjectorDelegate(null)
        unregisterReceiver(receiver)
    }

    suspend fun recv(inputStream: ByteReadChannel) {
        val type = inputStream.readInt()
        when (type) {
            TYPE_MOTION_EVENT -> readMotionEvent(inputStream)
            else              -> {}
        }
    }

    private suspend fun readMotionEvent(inputStream: ByteReadChannel) {
        //val type = inputStream.readInt()//TYPE_MOTION_EVENT
        val action = inputStream.readInt()
        val pointerId = inputStream.readInt()
        val x = inputStream.readInt()
        val y = inputStream.readInt()
        val screenWidth = inputStream.readInt()
        val screenHeight = inputStream.readInt()
        val pressure = Binary.i16FixedPointToFloat(inputStream.readShort())
        val actionButton = inputStream.readInt()
        val buttons = inputStream.readInt()
        val position = Position(x, y, screenWidth, screenHeight)
        controller.injectTouch(action, pointerId, position, pressure, actionButton, buttons)
    }


    private fun startServer() {

        MainScope().launch(Dispatchers.IO) {
            val selectorManager = SelectorManager(Dispatchers.IO)
            val serverSocket = aSocket(selectorManager).tcp().bind("0.0.0.0", 40000)
            println("Server is listening at ${serverSocket.localAddress}")
            while (true) {
                val socket = serverSocket.accept()
                println("Accepted $socket")
                launch(Dispatchers.IO) {
                    val inputStream = socket.openReadChannel()
                    try {
                        while (true) {
                            recv(inputStream)
                        }
                    } catch (e: Throwable) {
                        socket.close()
                    }
                }
            }
        }

    }


    override fun onServiceConnected() {}
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

}

