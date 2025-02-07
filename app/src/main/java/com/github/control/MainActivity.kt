package com.github.control

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.provider.Settings
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.genymobile.scrcpy.compat.CoreControllerCompat
import com.genymobile.scrcpy.control.ControlMessage
import com.genymobile.scrcpy.util.Binary
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket


@SuppressLint("MissingInflatedId", "ClickableViewAccessibility")
class MainActivity : AppCompatActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)!!) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        CoreControllerCompat.updateDisplayMetrics(this)

        findViewById<View>(R.id.btna)!!.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }
        findViewById<View>(R.id.btnb)!!.setOnClickListener {
            val host = findViewById<EditText>(R.id.etb)!!.text.toString()
            closeConnection()
            connectToHost(host)
        }

        findViewById<View>(R.id.main)?.setOnTouchListener { v, event ->
            sendTouchEvent(event)
            true
        }

    }


    private var socket: Socket? = null
    private var outputStream: DataOutputStream? = null
    private val handlerThread = HandlerThread("").apply {
        start()
    }
    private val handler = Handler(handlerThread.looper)
    private fun connectToHost(host: String) {
        Thread {
            try {
                socket = Socket().also { socket ->
                    socket.connect(InetSocketAddress(host, 40000))
                    Log.d("TAG", "connect:${socket} ")
                    outputStream = DataOutputStream(socket.getOutputStream())
                }
            } catch (e: Exception) {
                closeConnection()
            }
        }.start()
    }

    private fun sendTouchEvent(event: MotionEvent) {
        handler.post {
            try {
                outputStream?.also { outputStream ->
                    outputStream.writeByte(ControlMessage.TYPE_INJECT_TOUCH_EVENT)
                    outputStream.writeByte(event.action)
                    outputStream.writeLong(event.getPointerId(event.actionIndex).toLong())
                    outputStream.writeInt(event.getX(event.actionIndex).toInt())
                    outputStream.writeInt(event.getY(event.actionIndex).toInt())
                    outputStream.writeShort(CoreControllerCompat.displayMetrics.widthPixels)
                    outputStream.writeShort(CoreControllerCompat.displayMetrics.heightPixels)
                    outputStream.writeShort(Binary.floatToU16FixedPoint(event.pressure).toInt())
                    outputStream.writeInt(event.actionButton)
                    outputStream.writeInt(event.buttonState)
                    outputStream.flush()
                }
            } catch (e: Exception) {
                closeConnection()
            }
        }
    }

    private fun closeConnection() {
        outputStream?.close()
        outputStream = null
        socket?.close()
        socket = null
    }
}