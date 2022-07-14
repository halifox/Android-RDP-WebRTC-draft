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
//        StrictMode.setThreadPolicy(ThreadPolicy.Builder()
//                .detectDiskReads()
//                .detectDiskWrites()
//                .detectNetwork() // or .detectAll() for all detectable problems
//                .penaltyLog()
//                .build())
//        StrictMode.setVmPolicy(VmPolicy.Builder()
//                .detectLeakedSqlLiteObjects()
//                .detectLeakedClosableObjects()
//                .penaltyLog()
//                .penaltyDeath()
//                .build())
        findViewById<Button>(R.id.btn_push_mediaProjectionManager).setOnClickListener {
            startActivity(Intent(this, PushByMediaProjectionManagerActivity::class.java))
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
        findViewById<Button>(R.id.btn_test).setOnClickListener {
            startActivity(Intent(this, TestActivity::class.java))
        }
    }
}