package com.github.control.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController


val LocalNavController = compositionLocalOf<NavHostController> { error("No NavController provided.") }

@Composable
fun App() {
    MaterialTheme {
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

