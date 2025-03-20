package com.github.control.gesture

import android.graphics.Point
import android.hardware.input.InputManager
import android.os.Build
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import com.blankj.utilcode.util.ScreenUtils
import java.lang.reflect.Method

/**
 * 处理手势输入并将其转化为适当的事件进行注入。
 */
class GestureInputController(
    val controller: GestureEventController,
) {
    private var lastTouchDown: Long = 0
    private val pointersState = PointersState()
    private val pointerProperties = Array(PointersState.MAX_POINTERS) {
        MotionEvent.PointerProperties()
            .apply { toolType = MotionEvent.TOOL_TYPE_FINGER }
    }
    private val pointerCoords = Array(PointersState.MAX_POINTERS) {
        MotionEvent.PointerCoords()
            .apply {
                orientation = 0f
                size = 0f
            }
    }

    companion object {
        private const val DEFAULT_DEVICE_ID = 0
        private const val POINTER_ID_MOUSE = -1
        private var setActionButtonMethod: Method? = null

        private fun getSetActionButtonMethod(): Method {
            return setActionButtonMethod ?: MotionEvent::class.java.getMethod("setActionButton", Int::class.java)
                .also {
                    setActionButtonMethod = it
                }
        }

        fun setActionButton(event: MotionEvent, actionButton: Int): Boolean {
            return try {
                getSetActionButtonMethod().invoke(event, actionButton)
                true
            } catch (e: ReflectiveOperationException) {
                false
            }
        }
    }


    fun injectInputEvent(event: MotionEvent, displayId: Int, injectMode: Int): Boolean {
        return controller.performInputEvent(event)
    }

    fun injectGlobalAction(action: Int): Boolean {
        return controller.performGlobalAction(action)
    }

    fun injectTouch(action: Int, pointerId: Int, position: Position, pressure: Float, actionButton: Int, buttons: Int): Boolean {
        val now = SystemClock.uptimeMillis()
        val point = mapToScreen(position)
        val pointerIndex = pointersState.getPointerIndex(pointerId)
        if (pointerIndex == -1) return false

        val pointer = pointersState[pointerIndex].apply {
            this.point = point
            this.pressure = pressure
        }

        val source = if (isMouseEvent(pointerId, action, buttons)) {
            pointerProperties[pointerIndex].toolType = MotionEvent.TOOL_TYPE_MOUSE
            InputDevice.SOURCE_MOUSE.also { pointer.up = buttons == 0 }
        } else {
            pointerProperties[pointerIndex].toolType = MotionEvent.TOOL_TYPE_FINGER
            InputDevice.SOURCE_TOUCHSCREEN.also { pointer.up = action == MotionEvent.ACTION_UP }
        }

        val pointerCount = pointersState.update(pointerProperties, pointerCoords)
        val finalAction = adjustAction(pointerCount, action, pointerIndex)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && source == InputDevice.SOURCE_MOUSE) {
            handleMouseEvents(now, finalAction, pointerCount, actionButton, buttons, source)
        } else {
            injectMotionEvent(now, finalAction, pointerCount, buttons, source)
        }
    }

    private fun isMouseEvent(pointerId: Int, action: Int, buttons: Int) =
        pointerId == POINTER_ID_MOUSE && (action == MotionEvent.ACTION_HOVER_MOVE || buttons and MotionEvent.BUTTON_PRIMARY.inv() != 0)

    private fun adjustAction(pointerCount: Int, action: Int, pointerIndex: Int): Int {
        return when {
            pointerCount == 1 && action == MotionEvent.ACTION_DOWN -> {
                lastTouchDown = SystemClock.uptimeMillis()
                action
            }

            action == MotionEvent.ACTION_UP -> MotionEvent.ACTION_POINTER_UP or (pointerIndex shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
            action == MotionEvent.ACTION_DOWN -> MotionEvent.ACTION_POINTER_DOWN or (pointerIndex shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
            else -> action
        }
    }

    private fun handleMouseEvents(now: Long, action: Int, pointerCount: Int, actionButton: Int, buttons: Int, source: Int): Boolean {
        if (action == MotionEvent.ACTION_DOWN && actionButton == buttons) {
            val downEvent = createMotionEvent(now, MotionEvent.ACTION_DOWN, pointerCount, buttons, source)
            if (!injectInputEvent(downEvent, 0, InputManager.INJECT_INPUT_EVENT_MODE_ASYNC)) return false
        }

        val pressEvent = createMotionEvent(now, MotionEvent.ACTION_BUTTON_PRESS, pointerCount, buttons, source)
        if (!setActionButton(pressEvent, actionButton) || !injectInputEvent(pressEvent, 0, InputManager.INJECT_INPUT_EVENT_MODE_ASYNC)) return false

        if (action == MotionEvent.ACTION_UP) {
            val releaseEvent = createMotionEvent(now, MotionEvent.ACTION_BUTTON_RELEASE, pointerCount, buttons, source)
            if (!setActionButton(releaseEvent, actionButton) || !injectInputEvent(releaseEvent, 0, InputManager.INJECT_INPUT_EVENT_MODE_ASYNC)) return false
            if (buttons == 0) {
                val upEvent = createMotionEvent(now, MotionEvent.ACTION_UP, pointerCount, buttons, source)
                if (!injectInputEvent(upEvent, 0, InputManager.INJECT_INPUT_EVENT_MODE_ASYNC)) return false
            }
        }
        return true
    }

    private fun injectMotionEvent(now: Long, action: Int, pointerCount: Int, buttons: Int, source: Int): Boolean {
        val event = createMotionEvent(now, action, pointerCount, buttons, source)
        return injectInputEvent(event, 0, InputManager.INJECT_INPUT_EVENT_MODE_ASYNC)
    }

    private fun createMotionEvent(now: Long, action: Int, pointerCount: Int, buttons: Int, source: Int): MotionEvent {
        return MotionEvent.obtain(lastTouchDown, now, action, pointerCount, pointerProperties, pointerCoords, 0, buttons, 1f, 1f, DEFAULT_DEVICE_ID, 0, source, 0)
    }

    fun mapToScreen(position: Position): Point {
        val x = ScreenUtils.getScreenWidth() * position.point.x / position.screenSize.width
        val y = ScreenUtils.getScreenHeight() * position.point.y / position.screenSize.height
        return Point(x, y)
    }
}
