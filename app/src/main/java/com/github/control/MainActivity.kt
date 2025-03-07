package com.github.control

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.github.control.scrcpy.Controller
import com.github.control.ui.App


@SuppressLint("MissingInflatedId", "ClickableViewAccessibility")
class MainActivity : ComponentActivity() {
    private val context = this
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            App()
        }
    }
}
