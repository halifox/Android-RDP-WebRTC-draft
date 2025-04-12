package com.github.control.gesture

import android.graphics.Point
import android.os.SystemClock
import android.util.Size
import android.view.InputDevice
import android.view.MotionEvent
import com.blankj.utilcode.util.ScreenUtils
import org.koin.core.component.KoinComponent

class Controller : KoinComponent {
    private val accessibilityService: GestureControlAccessibilityService?
        get() = getKoin().getOrNull<GestureControlAccessibilityService>()

    private var lastTouchDown: Long = 0

    private val pointersState = PointersState()
    private val pointerProperties = Array(PointersState.MAX_POINTERS) {
        MotionEvent.PointerProperties().apply {
            toolType = MotionEvent.TOOL_TYPE_FINGER
        }
    }
    private val pointerCoords = Array(PointersState.MAX_POINTERS) { MotionEvent.PointerCoords() }


    fun injectGlobalAction(action: Int) {
        accessibilityService?.performGlobalAction(action)
    }

    fun injectTouch(action: Int, pointerId: Int, inputPoint: Point, screenSize: Size, pressure: Float, actionButton: Int, buttons: Int) {
        val now = SystemClock.uptimeMillis()
        val point = mapToScreen(inputPoint, screenSize)

        val pointerIndex = pointersState.getPointerIndex(pointerId)
        if (pointerIndex == -1) return

        val pointer = pointersState[pointerIndex]
        pointer.point = point
        pointer.pressure = pressure
        pointerProperties[pointerIndex].toolType = MotionEvent.TOOL_TYPE_FINGER
        pointer.isUp = action == MotionEvent.ACTION_UP

        val pointerCount = pointersState.update(pointerProperties, pointerCoords)

        var finalAction = action
        if (pointerCount == 1 && action == MotionEvent.ACTION_DOWN) {
            lastTouchDown = now
        } else {
            finalAction = when (action) {
                MotionEvent.ACTION_UP -> MotionEvent.ACTION_POINTER_UP or (pointerIndex shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
                MotionEvent.ACTION_DOWN -> MotionEvent.ACTION_POINTER_DOWN or (pointerIndex shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
                else -> action
            }
        }
        val event = MotionEvent.obtain(lastTouchDown, now, finalAction, pointerCount, pointerProperties, pointerCoords, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
        accessibilityService?.dispatchGesture(event)
    }

    /**
     * 将一个点从一个屏幕尺寸映射到另一个屏幕尺寸。核心步骤是计算出源屏幕和目标屏幕的坐标系转换关系，最后返回映射后的新坐标点。
     */
    private fun mapToScreen(sourcePoint: Point, targetScreenSize: Size): Point {
        val sourceX = sourcePoint.x.toDouble()
        val sourceY = sourcePoint.y.toDouble()
        val targetWidth = targetScreenSize.width.toDouble()
        val targetHeight = targetScreenSize.height.toDouble()
        val actualWidth = ScreenUtils.getScreenWidth().toDouble()
        val actualHeight = ScreenUtils.getScreenHeight().toDouble()

        val maxTargetWidth = minOf(targetWidth, targetHeight * actualWidth / actualHeight)
        val maxTargetHeight = minOf(targetHeight, targetWidth * actualHeight / actualWidth)

        val offsetX = (targetWidth - maxTargetWidth) / 2
        val offsetY = (targetHeight - maxTargetHeight) / 2

        val mappedX = (sourceX - offsetX) / maxTargetWidth * actualWidth
        val mappedY = (sourceY - offsetY) / maxTargetHeight * actualHeight

        return Point(mappedX.toInt(), mappedY.toInt())
    }
}
