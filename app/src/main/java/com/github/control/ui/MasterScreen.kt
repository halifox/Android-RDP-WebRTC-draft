package com.github.control.ui

import android.content.Intent
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.github.control.PullActivityNetty
import com.github.control.PullActivityNettyImage
import com.github.control.PullActivityWebRTC
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MasterScreen() {
    val nsdManager = koinInject<NsdManager>()
    val context = LocalContext.current
    var discovering by remember { mutableStateOf(false) }
    val serviceList = remember { mutableStateListOf<NsdServiceInfo>() }
    DisposableEffect(Unit) {
        val discoveryListener = object : NsdManager.DiscoveryListener {
            private val TAG = "NsdManager"
            override fun onDiscoveryStarted(serviceType: String) {
                Log.d(TAG, "onDiscoveryStarted: ")
                discovering = true
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "onServiceFound: ${serviceInfo}")
                serviceList.add(serviceInfo)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "onServiceLost: ${serviceInfo}")
                serviceList.remove(serviceInfo)
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(TAG, "onDiscoveryStopped: ")
                discovering = false
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
                    Text(text = serviceInfo.toString(), modifier = Modifier.weight(1f))
                    Button(onClick = {
                        val serviceHost = serviceInfo.host
                        if (serviceHost != null) {
                            val starter = Intent(context, PullActivityNetty::class.java)
                                .putExtra("host", serviceHost.hostName)
                            context.startActivity(starter)
                            return@Button
                        }
                        nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {

                            }

                            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                                serviceList[it] = serviceInfo
                                val serviceHost = serviceInfo.host
                                if (serviceHost != null) {
                                    val starter = Intent(context, PullActivityNetty::class.java)
                                        .putExtra("host", serviceHost.hostName)
                                    context.startActivity(starter)
                                }
                            }
                        })
                    }) {
                        Text(text = "连接")
                    }
                }
            }
        }
    }
}