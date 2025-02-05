package com.github.control

import android.accessibilityservice.AccessibilityService
import android.os.Parcel
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.DataInputStream
import java.net.ServerSocket


class AccService : AccessibilityService() {
    private val coroutineScope = MainScope()
    private val serverSocket = ServerSocket(40000, 1)
    private val motionEventHandler = MotionEventHandler(this)
    private val keyEventHandler = KeyEventHandler(this)


    override fun onCreate() {
        super.onCreate()
        coroutineScope.launch(Dispatchers.IO) {
            while (true) {
                try {
                    val clientSocket = serverSocket.accept()
                    Log.d("TAG", "serverSocket.accept:${clientSocket} ")
                    val inputStream = DataInputStream(clientSocket.getInputStream())
                    while (true) {
                        val size = inputStream.readInt()
                        val data = ByteArray(size)
                        inputStream.readFully(data)
                        processEvent(data)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel()
        serverSocket.close()
    }


    private fun processEvent(bytes: ByteArray) {
        val parcel = Parcel.obtain()
        parcel.unmarshall(bytes, 0, bytes.size)
        parcel.setDataPosition(0)
        val eventType = parcel.readInt()
        parcel.setDataPosition(0)
        when (eventType) {
            1 -> motionEventHandler.handleEvent(parcel)
            2 -> keyEventHandler.handleEvent(parcel)
        }
        parcel.recycle()
    }


    override fun onServiceConnected() {}
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
}
