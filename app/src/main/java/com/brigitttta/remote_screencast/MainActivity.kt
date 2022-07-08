package com.brigitttta.remote_screencast

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.brigitttta.remote_screencast.srs.SrsActivity


class MainActivity : AppCompatActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<Button>(R.id.btn_push).setOnClickListener {
            startActivity(Intent(this, PushActivity::class.java))
        }
        findViewById<Button>(R.id.btn_push_reflection).setOnClickListener {
            startActivity(Intent(this, PushByReflectionActivity::class.java))
        }
        findViewById<Button>(R.id.btn_pull).setOnClickListener {
            startActivity(Intent(this, PullActivity::class.java))
        }
        findViewById<Button>(R.id.btn_pull_more).setOnClickListener {
            startActivity(Intent(this, PullMoreActivity::class.java))
        }
        findViewById<Button>(R.id.btn_srs).setOnClickListener {
            startActivity(Intent(this, SrsActivity::class.java))
        }
    }
}