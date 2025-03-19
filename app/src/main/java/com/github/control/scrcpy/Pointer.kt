package com.github.control.scrcpy

data class Pointer(
    var id: Int,
    var localId: Int
) {
    var point: Point? = null
    var pressure: Float = 0f
    var up: Boolean = false
}
