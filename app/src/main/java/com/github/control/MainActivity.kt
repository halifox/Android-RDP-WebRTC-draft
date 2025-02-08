package com.github.control

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.github.control.scrcpy.Binary
import com.github.control.scrcpy.Controller
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.writeInt
import io.ktor.utils.io.writeShort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch

@SuppressLint("MissingInflatedId", "ClickableViewAccessibility")
class MainActivity : AppCompatActivity() {

    private val eventChannel = Channel<MotionEvent>(Channel.BUFFERED)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Controller.updateDisplayMetrics(this)

        findViewById<View>(R.id.btna)?.setOnClickListener { openAccessibilitySettings() }
        findViewById<View>(R.id.btnb)?.setOnClickListener { connectToHost() }
        findViewById<View>(R.id.panel)?.setOnTouchListener { _, event ->
            eventChannel.trySend(event)
            true
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
        startClient(host)
    }


    private fun startClient(host: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val selectorManager = SelectorManager(Dispatchers.IO)
            val socket = aSocket(selectorManager).tcp().connect(host, 40000)
            val outputStream = socket.openWriteChannel()
            eventChannel.consumeEach { event ->
                outputStream.writeInt(ControlService.TYPE_MOTION_EVENT)
                outputStream.writeInt(event.action)
                outputStream.writeInt(event.getPointerId(event.actionIndex))
                outputStream.writeInt(event.getX(event.actionIndex).toInt())
                outputStream.writeInt(event.getY(event.actionIndex).toInt())
                outputStream.writeInt(Controller.displayMetrics.widthPixels)
                outputStream.writeInt(Controller.displayMetrics.heightPixels)
                outputStream.writeShort(Binary.floatToI16FixedPoint(event.pressure))
                outputStream.writeInt(event.actionButton)
                outputStream.writeInt(event.buttonState)
                outputStream.flush()
            }
        }
    }
}
