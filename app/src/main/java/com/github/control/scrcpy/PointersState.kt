package com.github.control.scrcpy

import android.view.MotionEvent

class PointersState {

    companion object {
        const val MAX_POINTERS = 10
    }

    private val pointers = mutableListOf<Pointer>()

    private fun indexOf(id: Int) = pointers.indexOfFirst { it.id == id }

    private fun nextUnusedLocalId() = (0 until MAX_POINTERS).firstOrNull { id -> pointers.none { it.localId == id } } ?: -1

    operator fun get(index: Int) = pointers[index]

    fun getPointerIndex(id: Int): Int {
        indexOf(id).takeIf { it != -1 }
            ?.let { return it }
        if (pointers.size >= MAX_POINTERS) return -1

        val localId = nextUnusedLocalId().takeIf { it != -1 } ?: error("No available local ID")
        pointers.add(Pointer(id, localId))
        return pointers.lastIndex
    }

    fun update(props: Array<MotionEvent.PointerProperties>, coords: Array<MotionEvent.PointerCoords>): Int {
        pointers.forEachIndexed { i, pointer ->
            props[i].id = pointer.localId
            coords[i].apply {
                x = pointer.point!!.x.toFloat()
                y = pointer.point!!.y.toFloat()
                pressure = pointer.pressure
            }
        }
        pointers.removeAll { it.up }
        return pointers.size
    }
}
