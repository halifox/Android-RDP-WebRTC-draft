package com.github.control

import android.app.Application
import android.hardware.usb.UsbManager
import android.media.projection.MediaProjectionManager
import android.net.nsd.NsdManager
import androidx.core.content.ContextCompat
import com.github.control.gesture.Controller
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import org.webrtc.PeerConnectionFactory


class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(applicationContext)
                .createInitializationOptions()
        )

        startKoin {
            androidLogger()
            androidContext(applicationContext)
            modules(module {
                single { ContextCompat.getSystemService(get(), NsdManager::class.java) }
                single { ContextCompat.getSystemService(get(), UsbManager::class.java) }
                single { ContextCompat.getSystemService(get(), MediaProjectionManager::class.java) }
                singleOf(::Controller)
            })
        }
    }
}