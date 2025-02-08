package com.github.control

import android.annotation.SuppressLint
import android.app.ProgressDialog
import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.github.control.databinding.ActivityMainBinding
import com.github.control.scrcpy.Binary
import com.github.control.scrcpy.Controller
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.writeInt
import io.ktor.utils.io.writeShort
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@SuppressLint("MissingInflatedId", "ClickableViewAccessibility")
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val context = this
    private val eventChannel = Channel<MotionEvent>(Channel.BUFFERED)
    private val selectorManager = SelectorManager(Dispatchers.IO)
    private val progressDialog by lazy {
        ProgressDialog(context).apply {
            setTitle("Connect")
            setMessage("loading...")
            isIndeterminate = true
            setCancelable(false)
        }
    }
    private val coroutineExceptionHandler =
        CoroutineExceptionHandler { coroutineContext, throwable ->
            lifecycleScope.launch {
                progressDialog.hide()
                AlertDialog.Builder(context)
                    .setTitle("error")
                    .setMessage("${throwable}")
                    .create()
                    .show()
            }
        }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Controller.updateDisplayMetrics(context)

        binding.slave.setOnClickListener {
            openAccessibilitySettings()
        }
        binding.master.setOnClickListener {
            connectToHost()
        }
        binding.main.setOnTouchListener { _, event ->
            eventChannel.trySend(event)
            true
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        progressDialog.dismiss()
        selectorManager.close()
        println("onDestroy")
    }

    override fun onBackPressed() {
        super.onBackPressed()
        Process.killProcess(Process.myPid())
    }


    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
    }

    private fun connectToHost() {
        val host = binding.host.text.toString()
        startClient(host)
    }


    private fun startClient(host: String) {
        lifecycleScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            withContext(Dispatchers.Main) { progressDialog.show() }
            val socket = aSocket(selectorManager).tcp().connect(host, 40000)
            withContext(Dispatchers.Main) {
                progressDialog.hide()
                binding.slave.visibility = View.GONE
                binding.host.visibility = View.GONE
                binding.master.visibility = View.GONE
            }

            val byteWriteChannel = socket.openWriteChannel()
            eventChannel.consumeEach { event ->
                byteWriteChannel.writeInt(ControlService.TYPE_MOTION_EVENT)
                byteWriteChannel.writeInt(event.action)
                byteWriteChannel.writeInt(event.getPointerId(event.actionIndex))
                byteWriteChannel.writeInt(event.getX(event.actionIndex).toInt())
                byteWriteChannel.writeInt(event.getY(event.actionIndex).toInt())
                byteWriteChannel.writeInt(Controller.displayMetrics.widthPixels)
                byteWriteChannel.writeInt(Controller.displayMetrics.heightPixels)
                byteWriteChannel.writeShort(Binary.floatToI16FixedPoint(event.pressure))
                byteWriteChannel.writeInt(event.actionButton)
                byteWriteChannel.writeInt(event.buttonState)
                byteWriteChannel.flush()
            }
        }
    }
}
