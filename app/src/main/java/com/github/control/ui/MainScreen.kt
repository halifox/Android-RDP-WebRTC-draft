package com.github.control.ui

import android.annotation.SuppressLint
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.github.control.ui.theme.ControlTheme


val LocalNavController = compositionLocalOf<NavHostController> { error("No NavController provided.") }

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun MainScreen() {
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
                composable("/SlaveScreen") {
                    SlaveScreen()
                }
            }

        }
    }
}

