package com.github.control.gesture

import android.graphics.Point


/**
 * @param id Pointer id as received from the client.
 * @param localId Local pointer id, using the lowest possible values to fill the [PointerProperties][android.view.MotionEvent.PointerProperties].
 */
data class Pointer @JvmOverloads constructor(
    var id: Int,
    var localId: Int,
    var point: Point? = null,
    var pressure: Float = 0f,
    var isUp: Boolean = false,
)
