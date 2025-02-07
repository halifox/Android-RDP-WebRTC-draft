package com.github.control

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.EditText
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.github.control.scrcpy.Injector
import java.net.InetSocketAddress
import java.net.Socket


@SuppressLint("MissingInflatedId", "ClickableViewAccessibility")
class MainActivity : AppCompatActivity() {
    private val context = this
    private var eventSocketHandler: EventSocketHandler? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)!!) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        Injector.updateDisplayMetrics(context)

        findViewById<View>(R.id.btna)!!.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }
        findViewById<View>(R.id.btnb)!!.setOnClickListener {
            val host = findViewById<EditText>(R.id.etb)!!.text.toString()
            connectToHost(host)
        }

        findViewById<View>(R.id.main)?.setOnTouchListener { v, event ->
            eventSocketHandler?.writeMotionEvent(event)
            true
        }
    }

    private var socket = Socket()

    private fun connectToHost(host: String) {
        Thread {
            try {
                if (!socket.isClosed) {
                    Log.d("TAG", "socket:close:${socket} ")
                    socket.close()
                }
                socket = Socket()
                socket.connect(InetSocketAddress(host, 40000))
                Log.d("TAG", "connect:${socket} ")
                eventSocketHandler = EventSocketHandler(socket)
            } catch (e: Exception) {
                Log.d("TAG", "socket:close:${socket} ${e}")
                if (!socket.isClosed) {
                    socket.close()
                }
            }
        }.start()
    }


}