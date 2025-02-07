package com.github.control.anydesk

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.view.MotionEvent


class MotionEventHandler(
    private val accessibilityService: AccessibilityService,
    private val trackers: List<GestureTracker> = List(16) { GestureTracker() },
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
        resetAllTrackers()
        val availableTracker = getAvailableTracker()
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
                val tracker = getTrackerByPointerId(motionEvent.getPointerId(index))
                if (tracker != null) {
                    tracker.updateStroke(motionEvent.getX(index), motionEvent.getY(index), true)
                    builder.addStroke(tracker.stroke!!)
                }
            }
            accessibilityService.dispatchGesture(builder.build(), null, null)
        }
    }

    private fun onActionUp(pointerId: Int, x: Float, y: Float, motionEvent: MotionEvent) {
        val tracker = getTrackerByPointerId(pointerId)
        if (isGestureActive && tracker != null) {
            tracker.updateStroke(x, y, false)
            val builder = GestureDescription.Builder()
            builder.addStroke(tracker.stroke!!)
            isGestureActive = false
            resetAllTrackers()
            accessibilityService.dispatchGesture(builder.build(), null, null)
        }
    }

    private fun onActionCancel(pointerId: Int, x: Float, y: Float, motionEvent: MotionEvent) {
        isGestureActive = false
        resetAllTrackers()
    }

    private fun onActionPointerUp(pointerId: Int, x: Float, y: Float, motionEvent: MotionEvent) {
        val tracker = getTrackerByPointerId(pointerId)
        if (isGestureActive && tracker != null) {
            tracker.updateStroke(x, y, false)
            val builder = GestureDescription.Builder()
            builder.addStroke(tracker.stroke!!)
            for (otherTracker in trackers) {
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
        val availableTracker = getAvailableTracker()
        if (isGestureActive && availableTracker != null) {
            val builder = GestureDescription.Builder()
            for (tracker in trackers) {
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


    private fun getAvailableTracker(): GestureTracker? {
        for (tracker in trackers) {
            if (!tracker.isTracking) {
                return tracker
            }
        }
        return null
    }

    private fun getTrackerByPointerId(pointerId: Int): GestureTracker? {
        for (tracker in trackers) {
            if (tracker.isTracking && tracker.pointerId == pointerId) {
                return tracker
            }
        }
        return null
    }

    private fun resetAllTrackers() {
        for (tracker in trackers) {
            tracker.reset()
        }
    }
}
