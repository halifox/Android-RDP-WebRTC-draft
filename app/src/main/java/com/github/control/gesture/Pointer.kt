package com.github.control.gesture

import android.graphics.Point

data class Pointer(
    var id: Int,
    var localId: Int
) {
    var point: Point? = null
    var pressure: Float = 0f
    var up: Boolean = false
}
