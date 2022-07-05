package com.example.test_webrtc

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity


class MainActivity : AppCompatActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<Button>(R.id.btn_push).setOnClickListener {
            startActivity(Intent(this, PushActivity::class.java))
        }
        findViewById<Button>(R.id.btn_pull).setOnClickListener {
            startActivity(Intent(this, PullActivity::class.java))
        }
    }
}