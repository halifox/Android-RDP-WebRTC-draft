package com.github.control

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.MotionEvent


class GestureTracker {
    var isTracking = false
    var pointerId = 0
    val path = Path()
    var stroke = null as GestureDescription.StrokeDescription?
    var previousX = 0f
    var previousY = 0f

    init {
        reset()
    }


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

class GestureTrackerManager {
    val trackers = List(16) { GestureTracker() }

    fun getAvailableTracker(): GestureTracker? {
        for (tracker in trackers) {
            if (!tracker.isTracking) {
                return tracker
            }
        }
        return null
    }

    fun getTrackerByPointerId(pointerId: Int): GestureTracker? {
        for (tracker in trackers) {
            if (tracker.isTracking && tracker.pointerId == pointerId) {
                return tracker
            }
        }
        return null
    }

    fun resetAllTrackers() {
        for (tracker in trackers) {
            tracker.reset()
        }
    }
}

class MotionEventHandler(
    private val accessibilityService: AccessibilityService,
    private val gestureTrackerManager: GestureTrackerManager = GestureTrackerManager(),
) {
    private var isGestureActive = false
    fun handleEvent(motionEvent: MotionEvent) {
        try {
            val actionMasked = motionEvent.actionMasked
            val actionIndex = motionEvent.actionIndex
            val pointerId = motionEvent.getPointerId(actionIndex)
            val x = motionEvent.getX(actionIndex)
            val y = motionEvent.getY(actionIndex)
            when (actionMasked) {
                MotionEvent.ACTION_DOWN           -> onActionDown(pointerId, x, y, motionEvent)
                MotionEvent.ACTION_UP             -> onActionUp(pointerId, x, y, motionEvent)
                MotionEvent.ACTION_MOVE           -> onActionMove(pointerId, x, y, motionEvent)
                MotionEvent.ACTION_CANCEL         -> onActionCancel(pointerId, x, y, motionEvent)
                MotionEvent.ACTION_OUTSIDE        -> {}
                MotionEvent.ACTION_POINTER_DOWN   -> onActionPointerDown(pointerId, x, y, motionEvent)
                MotionEvent.ACTION_POINTER_UP     -> onActionPointerUp(pointerId, x, y, motionEvent)
                MotionEvent.ACTION_HOVER_MOVE     -> {}
                MotionEvent.ACTION_SCROLL         -> {}
                MotionEvent.ACTION_HOVER_ENTER    -> {}
                MotionEvent.ACTION_HOVER_EXIT     -> {}
                MotionEvent.ACTION_BUTTON_PRESS   -> {}
                MotionEvent.ACTION_BUTTON_RELEASE -> {}
            }
            motionEvent.recycle()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    private fun onActionDown(pointerId: Int, x: Float, y: Float, motionEvent: MotionEvent) {
        gestureTrackerManager.resetAllTrackers()
        val availableTracker = gestureTrackerManager.getAvailableTracker()
        if (availableTracker != null) {
            isGestureActive = true
            availableTracker.startTracking(pointerId, x, y)
            val builder = GestureDescription.Builder()
            builder.addStroke(availableTracker.stroke!!)
            accessibilityService.dispatchGesture(builder.build(), null, null)
        }
    }

    private fun onActionMove(pointerId: Int, x: Float, y: Float, motionEvent: MotionEvent) {
        if (isGestureActive) {
            val builder = GestureDescription.Builder()
            for (index in 0 until motionEvent.pointerCount) {
                val tracker = gestureTrackerManager.getTrackerByPointerId(motionEvent.getPointerId(index))
                if (tracker != null) {
                    tracker.updateStroke(motionEvent.getX(index), motionEvent.getY(index), true)
                    builder.addStroke(tracker.stroke!!)
                }
            }
            accessibilityService.dispatchGesture(builder.build(), null, null)
        }
    }

    private fun onActionUp(pointerId: Int, x: Float, y: Float, motionEvent: MotionEvent) {
        val tracker = gestureTrackerManager.getTrackerByPointerId(pointerId)
        if (isGestureActive && tracker != null) {
            tracker.updateStroke(x, y, false)
            val builder = GestureDescription.Builder()
            builder.addStroke(tracker.stroke!!)
            isGestureActive = false
            gestureTrackerManager.resetAllTrackers()
            accessibilityService.dispatchGesture(builder.build(), null, null)
        }
    }

    private fun onActionCancel(pointerId: Int, x: Float, y: Float, motionEvent: MotionEvent) {
        isGestureActive = false
        gestureTrackerManager.resetAllTrackers()
    }

    private fun onActionPointerUp(pointerId: Int, x: Float, y: Float, motionEvent: MotionEvent) {
        val tracker = gestureTrackerManager.getTrackerByPointerId(pointerId)
        if (isGestureActive && tracker != null) {
            tracker.updateStroke(x, y, false)
            val builder = GestureDescription.Builder()
            builder.addStroke(tracker.stroke!!)
            for (otherTracker in gestureTrackerManager.trackers) {
                if (otherTracker.isTracking && otherTracker != tracker) {
                    otherTracker.updateStroke(otherTracker.previousX, otherTracker.previousY, true)
                    builder.addStroke(otherTracker.stroke!!)
                }
            }
            tracker.reset()
            accessibilityService.dispatchGesture(builder.build(), null, null)
        }
    }

    private fun onActionPointerDown(pointerId: Int, x: Float, y: Float, motionEvent: MotionEvent) {
        val availableTracker = gestureTrackerManager.getAvailableTracker()
        if (isGestureActive && availableTracker != null) {
            val builder = GestureDescription.Builder()
            for (tracker in gestureTrackerManager.trackers) {
                if (tracker.isTracking) {
                    tracker.startTracking(tracker.pointerId, tracker.previousX, tracker.previousY)
                    builder.addStroke(tracker.stroke!!)
                }
            }
            availableTracker.startTracking(pointerId, x, y)
            builder.addStroke(availableTracker.stroke!!)
            accessibilityService.dispatchGesture(builder.build(), null, null)
        }
    }

}
