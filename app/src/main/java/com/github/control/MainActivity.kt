package com.github.control

import android.annotation.SuppressLint
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.github.control.databinding.ActivityMainBinding
import com.github.control.scrcpy.Controller
import com.google.android.material.dialog.MaterialAlertDialogBuilder


@SuppressLint("MissingInflatedId", "ClickableViewAccessibility")
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val context = this


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Controller.updateDisplayMetrics(context)


        binding.slave.setOnClickListener {
            if (!isAccessibilityEnabled()) {
                MaterialAlertDialogBuilder(context)
                    .setTitle("需要授权无障碍")
                    .setPositiveButton("去授权") { _, _ ->
                        openAccessibilitySettings()
                    }
                    .show()
                return@setOnClickListener
            }
            MaterialAlertDialogBuilder(context)
                .setTitle("需要授权录屏")
                .setPositiveButton("去授权") { _, _ ->
                    screenCaptureLauncher.launch(getSystemService(MediaProjectionManager::class.java)!!.createScreenCaptureIntent())
                }
                .show()
        }
        binding.master.setOnClickListener {
            val host = binding.host.text.toString()
            val intent = Intent(context, PullActivity::class.java)
            intent.putExtra("host", host)
            startActivity(intent)
        }

    }


    private val screenCaptureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK && it.data != null) {
            ScreenCaptureService.start(context, it.data)
        }
    }

    override fun onDestroy() {
        ScreenCaptureService.stop(context)
        super.onDestroy()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        Process.killProcess(Process.myPid())
    }

    private fun isAccessibilityEnabled(): Boolean {
        val enabledServices = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        return enabledServices?.contains(ControlService::class.java.name) == true
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
    }

}
