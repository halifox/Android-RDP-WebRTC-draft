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

    private var eventSocketHandler: EventSocketHandler? = null
    private var socket = Socket()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        setupWindowInsets()
        Injector.updateDisplayMetrics(this)

        findViewById<View>(R.id.btna)?.setOnClickListener { openAccessibilitySettings() }
        findViewById<View>(R.id.btnb)?.setOnClickListener { connectToHost() }
        findViewById<View>(R.id.main)?.setOnTouchListener { _, event ->
            eventSocketHandler?.writeMotionEvent(event)
            true
        }
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)!!) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
    }

    private fun connectToHost() {
        val host = findViewById<EditText>(R.id.etb)?.text.toString()
        Thread { startClient(host) }.start()
    }

    private fun startClient(host: String) {
        try {
            closeSocketIfOpen()
            socket = Socket().apply {
                connect(InetSocketAddress(host, 40000))
            }
            Log.d("TAG", "Connected: $socket")
            eventSocketHandler = EventSocketHandler(socket)
        } catch (e: Exception) {
            Log.d("TAG", "Socket error: ${e.message}")
            closeSocketIfOpen()
        }
    }

    private fun closeSocketIfOpen() {
        if (!socket.isClosed) {
            socket.close()
            Log.d("TAG", "Socket closed: $socket")
        }
    }
}
