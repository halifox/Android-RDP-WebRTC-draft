package com.github.control.ui

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
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
import com.blankj.utilcode.util.ServiceUtils
import com.github.control.MyAccessibilityService
import com.github.control.ScreenCaptureService
import org.koin.compose.koinInject


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SlaveScreen() {
    val nsdManager = koinInject<NsdManager>()
    val mediaProjectionManager = koinInject<MediaProjectionManager>()
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    var accessibilityEnabled by remember { mutableStateOf(isAccessibilityEnabled(context)) }
    var screenCaptureServiceEnabled by remember { mutableStateOf(isServiceRunning(context)) }
    var running by remember { mutableStateOf(false) }
    val screenCaptureLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == AppCompatActivity.RESULT_OK && it.data != null) {
            ScreenCaptureService.start(context, it.data)
        }
    }
    val registrationListener = remember {
        object : NsdManager.RegistrationListener {
            private val TAG = "NsdManager"
            override fun onServiceRegistered(nsdServiceInfo: NsdServiceInfo) {
                Log.d(TAG, "onServiceRegistered:注册成功 ")
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.d(TAG, "onRegistrationFailed:注册失败 ")
            }

            override fun onServiceUnregistered(arg0: NsdServiceInfo) {
                Log.d(TAG, "onServiceUnregistered:取消注册 ")
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.d(TAG, "onUnregistrationFailed:取消注册失败 ")
            }
        }
    }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            accessibilityEnabled = isAccessibilityEnabled(context)
            screenCaptureServiceEnabled = isServiceRunning(context)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(title = {
                Text(text = "受控端")
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
                            openAccessibilitySettings(context)
                        },
                        enabled = !accessibilityEnabled,
                    ) {
                        Text(text = "授权")
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("屏幕录制权限", modifier = Modifier.weight(1f))
                    Button(
                        onClick = {
                            screenCaptureLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
                        },
                        enabled = !screenCaptureServiceEnabled,
                    ) {
                        Text(text = "授权")
                    }
                }

                Button(
                    onClick = {
                        running = true
                        val serviceInfo = NsdServiceInfo().apply {
                            serviceName = "control"
                            serviceType = "_control._tcp."
                            port = 40000
                        }
                        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
                    },
                    enabled = screenCaptureServiceEnabled && accessibilityEnabled && !running
                ) {
                    Text(text = "开始")
                }
                Button(
                    onClick = {
                        running = false
                        nsdManager.unregisterService(registrationListener)

                        ScreenCaptureService.stop(context)
                    },
                    enabled = screenCaptureServiceEnabled && accessibilityEnabled && running
                ) {
                    Text(text = "停止")
                }
            }
        }
    )
}

private fun isServiceRunning(context: Context): Boolean {
    return ServiceUtils.isServiceRunning(ScreenCaptureService::class.java)
}

private fun isAccessibilityEnabled(context: Context): Boolean {
    val enabledServices = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
    return enabledServices != null && enabledServices.contains(MyAccessibilityService::class.java.name)
}

private fun openAccessibilitySettings(context: Context) {
    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    context.startActivity(intent)
}