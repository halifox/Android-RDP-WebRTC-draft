package com.github.webrtc

import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.getSystemService


class MainActivity : AppCompatActivity() {
    val registerMediaProjectionPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            ScreenCaptureService.start(this, it.data)
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.push).setOnClickListener {
            registerMediaProjectionPermission.launch(getSystemService<MediaProjectionManager>()?.createScreenCaptureIntent())
        }

        findViewById<Button>(R.id.pull).setOnClickListener {
            startActivity(Intent(this, PullActivity::class.java))
        }
    }
}