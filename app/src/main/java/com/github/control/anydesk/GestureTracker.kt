package com.github.control.anydesk

import android.accessibilityservice.GestureDescription
import android.graphics.Path


class GestureTracker {
    var isTracking = false
    var pointerId = 0
    val path = Path()
    var stroke: GestureDescription.StrokeDescription? = null
    var previousX = 0f
    var previousY = 0f
    
    fun startTracking(pointerId: Int, x: Float, y: Float) {
        this.isTracking = true
        this.pointerId = pointerId
        this.path.reset()
        this.path.moveTo(x, y)
        this.stroke = GestureDescription.StrokeDescription(path, 0L, 10L, true)
        this.previousX = x
        this.previousY = y
    }

    fun updateStroke(x: Float, y: Float, willContinue: Boolean) {
        this.path.reset()
        this.path.moveTo(previousX, previousY)
        this.path.lineTo(x, y)
        this.stroke = stroke?.continueStroke(path, 0L, 10L, willContinue)
        this.previousX = x
        this.previousY = y
    }

    fun reset() {
        this.isTracking = false
        this.pointerId = 0
        this.path.reset()
        this.stroke = null
        this.previousX = 0f
        this.previousY = 0f
    }

}
