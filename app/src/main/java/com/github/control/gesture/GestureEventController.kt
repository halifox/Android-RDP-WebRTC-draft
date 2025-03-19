package com.github.control.gesture

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.view.KeyEvent
import android.view.MotionEvent
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject


/**
 * 处理触摸事件并将其转换为无障碍手势的事件处理类
 *
 */
class GestureEventController : KoinComponent {
    private var isGestureActive = false // 标识当前是否有手势在进行
    private val gestureServiceDelegate: GestureServiceDelegate by inject()

    //手势跟踪器列表，最多支持 16 个手势点
    private val trackers: List<GestureTracker> = List(16) { GestureTracker() }

    fun performInputEvent(motionEvent: MotionEvent): Boolean {
        val actionMasked = motionEvent.actionMasked
        val actionIndex = motionEvent.actionIndex
        val pointerId = motionEvent.getPointerId(actionIndex)
        val x = motionEvent.getX(actionIndex)
        val y = motionEvent.getY(actionIndex)
        when (actionMasked) {
            MotionEvent.ACTION_DOWN -> onActionDown(pointerId, x, y, motionEvent)
            MotionEvent.ACTION_UP -> onActionUp(pointerId, x, y, motionEvent)
            MotionEvent.ACTION_MOVE -> onActionMove(pointerId, x, y, motionEvent)
            MotionEvent.ACTION_CANCEL -> onActionCancel(pointerId, x, y, motionEvent)
            MotionEvent.ACTION_OUTSIDE -> {}
            MotionEvent.ACTION_POINTER_DOWN -> onActionPointerDown(pointerId, x, y, motionEvent)
            MotionEvent.ACTION_POINTER_UP -> onActionPointerUp(pointerId, x, y, motionEvent)
            MotionEvent.ACTION_HOVER_MOVE -> {}
            MotionEvent.ACTION_SCROLL -> {}
            MotionEvent.ACTION_HOVER_ENTER -> {}
            MotionEvent.ACTION_HOVER_EXIT -> {}
            MotionEvent.ACTION_BUTTON_PRESS -> {}
            MotionEvent.ACTION_BUTTON_RELEASE -> {}
        }
        motionEvent.recycle()
        return true
    }

    fun performKeyEvent(keyEvent: KeyEvent): Boolean {
        val action = keyEvent.action
        val keyCode = keyEvent.keyCode
        val isShiftPressed = keyEvent.isShiftPressed
        val isCtrlPressed = keyEvent.isCtrlPressed
        return when (keyCode) {
            KeyEvent.KEYCODE_HOME -> gestureServiceDelegate.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            KeyEvent.KEYCODE_BACK -> gestureServiceDelegate.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            KeyEvent.KEYCODE_VOLUME_UP -> false
            KeyEvent.KEYCODE_VOLUME_DOWN -> false
            KeyEvent.KEYCODE_POWER -> false
            KeyEvent.KEYCODE_SPACE -> false
            KeyEvent.KEYCODE_MENU -> false
            KeyEvent.KEYCODE_APP_SWITCH -> gestureServiceDelegate.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
            else -> false
        }
    }

    fun performGlobalAction(action: Int): Boolean {
        return gestureServiceDelegate.performGlobalAction(action)
    }

    /**
     * 处理单指触摸按下事件
     */
    private fun onActionDown(pointerId: Int, x: Float, y: Float, motionEvent: MotionEvent) {
        resetAllTrackers()
        val availableTracker = getAvailableTracker()
        if (availableTracker != null) {
            isGestureActive = true
            availableTracker.startTracking(pointerId, x, y)
            val builder = GestureDescription.Builder()
            builder.addStroke(availableTracker.stroke!!)
            gestureServiceDelegate.dispatchGesture(builder.build(), null, null)
        }
    }

    /**
     * 处理手指移动事件
     */
    private fun onActionMove(pointerId: Int, x: Float, y: Float, motionEvent: MotionEvent) {
        if (isGestureActive) {
            val builder = GestureDescription.Builder()
            for (index in 0 until motionEvent.pointerCount) {
                val tracker = getTrackerByPointerId(motionEvent.getPointerId(index))
                tracker?.let {
                    it.updateStroke(motionEvent.getX(index), motionEvent.getY(index), true)
                    builder.addStroke(it.stroke!!)
                }
            }
            gestureServiceDelegate.dispatchGesture(builder.build(), null, null)
        }
    }

    /**
     * 处理单指抬起事件
     */
    private fun onActionUp(pointerId: Int, x: Float, y: Float, motionEvent: MotionEvent) {
        val tracker = getTrackerByPointerId(pointerId)
        if (isGestureActive && tracker != null) {
            tracker.updateStroke(x, y, false)
            val builder = GestureDescription.Builder()
            builder.addStroke(tracker.stroke!!)
            isGestureActive = false
            resetAllTrackers()
            gestureServiceDelegate.dispatchGesture(builder.build(), null, null)
        }
    }

    /**
     * 处理手势取消事件
     */
    private fun onActionCancel(pointerId: Int, x: Float, y: Float, motionEvent: MotionEvent) {
        isGestureActive = false
        resetAllTrackers()
    }

    /**
     * 处理多指抬起事件
     */
    private fun onActionPointerUp(pointerId: Int, x: Float, y: Float, motionEvent: MotionEvent) {
        val tracker = getTrackerByPointerId(pointerId)
        if (isGestureActive && tracker != null) {
            tracker.updateStroke(x, y, false)
            val builder = GestureDescription.Builder()
            builder.addStroke(tracker.stroke!!)

            // 处理其他仍在追踪的手指
            for (otherTracker in trackers) {
                if (otherTracker.isTracking && otherTracker != tracker) {
                    otherTracker.updateStroke(otherTracker.previousX, otherTracker.previousY, true)
                    builder.addStroke(otherTracker.stroke!!)
                }
            }
            tracker.reset()
            gestureServiceDelegate.dispatchGesture(builder.build(), null, null)
        }
    }

    /**
     * 处理多指按下事件
     */
    private fun onActionPointerDown(pointerId: Int, x: Float, y: Float, motionEvent: MotionEvent) {
        val availableTracker = getAvailableTracker()
        if (isGestureActive && availableTracker != null) {
            val builder = GestureDescription.Builder()

            // 重新追踪已有的手势
            for (tracker in trackers) {
                if (tracker.isTracking) {
                    tracker.startTracking(tracker.pointerId, tracker.previousX, tracker.previousY)
                    builder.addStroke(tracker.stroke!!)
                }
            }
            availableTracker.startTracking(pointerId, x, y)
            builder.addStroke(availableTracker.stroke!!)
            gestureServiceDelegate.dispatchGesture(builder.build(), null, null)
        }
    }

    /**
     * 获取可用的手势跟踪器
     */
    private fun getAvailableTracker(): GestureTracker? {
        return trackers.firstOrNull { !it.isTracking }
    }

    /**
     * 根据 pointerId 获取对应的手势跟踪器
     */
    private fun getTrackerByPointerId(pointerId: Int): GestureTracker? {
        return trackers.firstOrNull { it.isTracking && it.pointerId == pointerId }
    }

    /**
     * 重置所有手势跟踪器
     */
    private fun resetAllTrackers() {
        trackers.forEach { it.reset() }
    }
}