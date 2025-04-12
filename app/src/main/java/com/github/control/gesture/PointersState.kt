package com.github.control.gesture

import android.view.MotionEvent

class PointersState {
    companion object {
        const val MAX_POINTERS = 10
    }

    private val pointers = ArrayList<Pointer>()

    private fun indexOf(id: Int): Int = pointers.indexOfFirst { it.id == id }

    private fun isLocalIdAvailable(localId: Int): Boolean =
        pointers.none { it.localId == localId }

    private fun nextUnusedLocalId(): Int =
        (0 until MAX_POINTERS).firstOrNull(::isLocalIdAvailable) ?: -1

    operator fun get(index: Int): Pointer = pointers[index]

    fun getPointerIndex(id: Int): Int {
        indexOf(id).takeIf { it != -1 }?.let { return it }

        if (pointers.size >= MAX_POINTERS) return -1

        val localId = nextUnusedLocalId()
        if (localId == -1) {
            throw AssertionError("pointers.size() < maxFingers implies that a local id is available")
        }

        val pointer = Pointer(id, localId)
        pointers.add(pointer)
        return pointers.lastIndex
    }

    fun update(props: Array<MotionEvent.PointerProperties>, coords: Array<MotionEvent.PointerCoords>): Int {
        return pointers.size.also { count ->
            pointers.forEachIndexed { index, pointer ->
                props[index].id = pointer.localId
                coords[index].apply {
                    x = pointer.point!!.x.toFloat()
                    y = pointer.point!!.y.toFloat()
                    pressure = pointer.pressure
                }
            }
            cleanUp()
        }
    }

    private fun cleanUp() {
        pointers.removeAll { it.isUp }
    }
}