package com.github.control

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Parcel
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.EditText
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket

@SuppressLint("MissingInflatedId", "ClickableViewAccessibility")
class MainActivity : AppCompatActivity() {
    var outputStream: DataOutputStream? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)!!) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        findViewById<View>(R.id.btna)!!.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }
        findViewById<View>(R.id.btnb)!!.setOnClickListener {
            val host = findViewById<EditText>(R.id.etb)!!.text.toString()
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val socket = Socket()
                    socket.connect(InetSocketAddress(host, 40000), 3000)
                    Log.d("TAG", "socket:${socket} ")
                    outputStream = DataOutputStream(socket.getOutputStream())
                } catch (e: Exception) {
                    e.printStackTrace()
                    outputStream = null
                }
            }
        }
        val th = HandlerThread("")
        th.start()
        val handler = Handler(th.looper)
        findViewById<View>(R.id.main)?.setOnTouchListener { v, event ->
            handler.post {
                val parcel = Parcel.obtain()
                event.writeToParcel(parcel, 0)
                val bytes = parcel.marshall()
                parcel.recycle()


                outputStream?.let { outputStream ->
                    outputStream.writeInt(bytes.size)
                    outputStream.write(bytes)
                    outputStream.flush()
                }
            }

            true
        }

    }
}