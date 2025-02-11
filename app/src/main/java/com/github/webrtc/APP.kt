package com.github.webrtc

import android.app.Application
import org.webrtc.PeerConnectionFactory

class APP : Application() {
    override fun onCreate() {
        super.onCreate()
        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(applicationContext).createInitializationOptions())
    }
}