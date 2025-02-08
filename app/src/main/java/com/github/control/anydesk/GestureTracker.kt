package com.github.control.anydesk;

import android.accessibilityservice.GestureDescription;
import android.graphics.Path;

/**
 * GestureTracker 类用于跟踪手势轨迹并生成可用于 Android 辅助功能服务的手势描述。
 *
 * 该类主要用于记录手势的路径，并生成 GestureDescription.StrokeDescription
 * 以便在无障碍服务中模拟手势操作。
 */
class GestureTracker {
    /** 是否正在跟踪手势 */
    var isTracking = false

    /** 触摸点的标识符 */
    var pointerId = 0

    /** 记录手势路径的 Path 对象 */
    val path = Path()

    /** 当前手势的笔画描述 */
    var stroke: GestureDescription.StrokeDescription? = null

    /** 记录上一个触摸点的 X 坐标 */
    var previousX = 0f

    /** 记录上一个触摸点的 Y 坐标 */
    var previousY = 0f

    /**
     * 开始跟踪手势
     *
     * @param pointerId 触摸点 ID
     * @param x 起始 X 坐标
     * @param y 起始 Y 坐标
     */
    fun startTracking(pointerId: Int, x: Float, y: Float) {
        this.isTracking = true
        this.pointerId = pointerId
        this.path.reset()
        this.path.moveTo(x, y)
        this.stroke = GestureDescription.StrokeDescription(path, 0L, 10L, true)
        this.previousX = x
        this.previousY = y
    }

    /**
     * 更新手势路径，并继续笔画
     *
     * @param x 新的 X 坐标
     * @param y 新的 Y 坐标
     * @param willContinue 是否继续跟踪手势
     */
    fun updateStroke(x: Float, y: Float, willContinue: Boolean) {
        this.path.reset()
        this.path.moveTo(previousX, previousY)
        this.path.lineTo(x, y)
        this.stroke = stroke?.continueStroke(path, 0L, 10L, willContinue)
        this.previousX = x
        this.previousY = y
    }

    /**
     * 复位手势跟踪器，清除所有存储的数据。
     */
    fun reset() {
        this.isTracking = false
        this.pointerId = 0
        this.path.reset()
        this.stroke = null
        this.previousX = 0f
        this.previousY = 0f
    }
}
