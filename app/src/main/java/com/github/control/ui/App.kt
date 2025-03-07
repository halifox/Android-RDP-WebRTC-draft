package com.github.control.ui

import android.media.projection.MediaProjectionManager
import android.net.nsd.NsdManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.github.control.scrcpy.Controller
import com.github.control.ui.theme.ControlTheme
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.compose.KoinApplication
import org.koin.dsl.module


val LocalNavController = compositionLocalOf<NavHostController> { error("No NavController provided.") }

@Composable
fun App() {
    val context = LocalContext.current
    KoinApplication(application = {
        androidLogger()
        androidContext(context.applicationContext)
        modules(module {
            single { ContextCompat.getSystemService(get(), NsdManager::class.java) }
            single { ContextCompat.getSystemService(get(), MediaProjectionManager::class.java) }
            single { Controller() }
        })
    }) {
        ControlTheme {
            val navController = rememberNavController()
            CompositionLocalProvider(LocalNavController provides navController) {
                NavHost(
                    navController = navController,
                    startDestination = "/HomeScreen",
                ) {
                    composable("/HomeScreen") {
                        HomeScreen()
                    }
                    composable("/MasterScreen") {
                        MasterScreen()
                    }
                    composable("/SlaveScreen") {
                        SlaveScreen()
                    }
                }

            }
        }
    }
}

