package com.genymobile.scrcpy.compat

import android.content.Context
import android.util.DisplayMetrics
import android.util.Log
import android.view.InputEvent
import android.view.WindowManager
import androidx.core.content.ContextCompat
import com.genymobile.scrcpy.device.Point
import com.genymobile.scrcpy.device.Position

interface InputEventHandler {
    fun injectInputEvent(inputEvent: InputEvent?, injectMode: Int): Boolean = false
}

object CoreControllerCompat : InputEventHandler {
    val displayMetrics = DisplayMetrics()
    var inputEventHandlerInterface: InputEventHandler = object : InputEventHandler {}

    fun updateDisplayMetrics(context: Context) {
        val windowManager = ContextCompat.getSystemService(context, WindowManager::class.java) ?: return
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        Log.d("CoreControllerCompat", "displayMetrics:${displayMetrics.widthPixels} ${displayMetrics.heightPixels}")
    }


    override fun injectInputEvent(inputEvent: InputEvent?, injectMode: Int): Boolean {
        return inputEventHandlerInterface.injectInputEvent(inputEvent, injectMode) ?: false
    }

    fun mapToScreen(position: Position): Point {
        return Point(displayMetrics.widthPixels * position.point.x / position.screenSize.width, displayMetrics.heightPixels * position.point.y / position.screenSize.height)
    }
}
