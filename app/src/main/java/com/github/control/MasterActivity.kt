package com.github.control

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import org.koin.compose.koinInject

class MasterActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MasterScreen()
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MasterScreen() {
    val nsdManager = koinInject<NsdManager>()
    val context = LocalContext.current
    val serviceList = remember { mutableStateListOf<NsdServiceInfo>() }
    DisposableEffect(Unit) {
        val discoveryListener = object : NsdManager.DiscoveryListener {
            private val TAG = "NsdManager"
            override fun onDiscoveryStarted(serviceType: String) {
                Log.d(TAG, "onDiscoveryStarted: ")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "onServiceFound: ${serviceInfo}")
                serviceList.add(serviceInfo)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "onServiceLost: ${serviceInfo}")
                serviceList.removeIf {
                    it.serviceName == serviceInfo.serviceName
                }
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(TAG, "onDiscoveryStopped: ")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.d(TAG, "onStartDiscoveryFailed: ${errorCode}")
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.d(TAG, "onStopDiscoveryFailed: ${errorCode}")
            }
        }

        nsdManager.discoverServices("_control._tcp.", NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        onDispose {
            nsdManager.stopServiceDiscovery(discoveryListener)
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(title = {
                Text(text = "设备列表")
            })
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            items(serviceList.size) {
                val serviceInfo = serviceList[it]
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = serviceInfo.serviceName, modifier = Modifier.weight(1f))
                    Button(onClick = {
                        resolveAndStartService(serviceInfo, context, nsdManager)
                    }) {
                        Text(text = "连接")
                    }
                }
            }
        }
    }
}

private val resolveServiceCache = mutableMapOf<String, NsdServiceInfo>()
private fun resolveAndStartService(
    serviceInfo: NsdServiceInfo,
    context: Context,
    nsdManager: NsdManager,
) {
    if (resolveServiceCache.contains(serviceInfo.serviceName)) {
        val resolveServiceInfo = resolveServiceCache[serviceInfo.serviceName]!!
        ScreenCaptureActivity.start(context, resolveServiceInfo.host.hostName, resolveServiceInfo.port)
    } else {
        nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {

            }

            override fun onServiceResolved(resolveServiceInfo: NsdServiceInfo) {
                resolveServiceCache[serviceInfo.serviceName] = resolveServiceInfo
                ScreenCaptureActivity.start(context, resolveServiceInfo.host.hostName, resolveServiceInfo.port)
            }
        })
    }
}