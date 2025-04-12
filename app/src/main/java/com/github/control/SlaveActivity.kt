package com.github.control

import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.github.control.gesture.GestureControlAccessibilityService
import org.koin.compose.koinInject

class SlaveActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SlaveScreen()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SlaveScreen() {
    val mediaProjectionManager = koinInject<MediaProjectionManager>()
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    var accessibilityEnabled by remember { mutableStateOf(GestureControlAccessibilityService.isAccessibilityEnabled(context)) }
    var screenCaptureServiceEnabled by remember { mutableStateOf(ScreenCaptureService.isServiceRunning(context)) }
    fun checkState() {
        accessibilityEnabled = GestureControlAccessibilityService.isAccessibilityEnabled(context)
        screenCaptureServiceEnabled = ScreenCaptureService.isServiceRunning(context)
    }


    val screenCaptureLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.StartActivityForResult()) {
        val screenCaptureIntent = it.data
        if (it.resultCode == AppCompatActivity.RESULT_OK && screenCaptureIntent != null) {
            ScreenCaptureService.start(context, screenCaptureIntent)
            checkState()
        }
    }
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            checkState()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(title = {
                Text(text = "受控端配置")
            })
        },
        content = { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("无障碍权限", modifier = Modifier.weight(1f))
                    Button(
                        onClick = {
                            GestureControlAccessibilityService.openAccessibilitySettings(context)
                        },
                        enabled = !accessibilityEnabled,
                    ) {
                        Text(text = "授权")
                    }
                }
                Button(
                    onClick = {
                        screenCaptureLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
                    },
                    enabled = !screenCaptureServiceEnabled && accessibilityEnabled
                ) {
                    Text(text = "开启服务")
                }
                Button(
                    onClick = {
                        ScreenCaptureService.stop(context)
                        checkState()
                    },
                    enabled = screenCaptureServiceEnabled
                ) {
                    Text(text = "停止服务")
                }
            }
        }
    )
}