package com.example.test_webrtc

import android.view.MotionEvent
import java.io.Serializable

class MotionModel : Serializable {

    private var action = 0
    private var pointerCount = 0
    private var buttonState = 0
    private var metaState = 0
    private var flags = 0
    private var edgeFlags = 0
    private var downTime = 0L
    private var eventTime = 0L
    private var deviceId = 0
    private var source = 0
    private var xPrecision = 0f
    private var yPrecision = 0f
    private var pointerProperties = mutableListOf<PointerProperties>()
    private var pointerCoords = mutableListOf<PointerCoords>()
    private var remoteHeight = 0
    private var remoteWidth = 0

    private fun getPointerProperties(): Array<MotionEvent.PointerProperties> {
        return pointerProperties.map {
            it.toMotionEventPointerProperties()
        }.toTypedArray()
    }

    private fun getPointerCoords(): Array<MotionEvent.PointerCoords> {
        return pointerCoords.map {
            it.toMotionEventPointerCoords()
        }.toTypedArray()
    }

    constructor()
    constructor(event: MotionEvent, width: Int, height: Int) : this() {
        fromMotionEvent(event)
        remoteWidth = width
        remoteHeight = height
    }

    fun fromMotionEvent(event: MotionEvent) {
        downTime = event.downTime
        eventTime = event.eventTime
        action = event.action
        pointerCount = event.pointerCount
        repeat(pointerCount) {
            val properties = MotionEvent.PointerProperties()
            event.getPointerProperties(it, properties)
            pointerProperties.add(PointerProperties(properties))

            val coords = MotionEvent.PointerCoords()
            event.getPointerCoords(it, coords)
            pointerCoords.add(PointerCoords(coords))
        }
        metaState = event.metaState
        buttonState = event.buttonState
        xPrecision = event.xPrecision
        yPrecision = event.yPrecision
        deviceId = event.deviceId
        edgeFlags = event.edgeFlags
        source = event.source
        flags = event.flags

    }


    fun toMotionEvent(): MotionEvent {
        return MotionEvent.obtain(
                downTime,
                eventTime,
                action,
                pointerCount,
                getPointerProperties(),
                getPointerCoords(),
                metaState,
                buttonState,
                xPrecision,
                yPrecision,
                deviceId,
                edgeFlags,
                source,
                flags,
        )

    }

    fun scaleByScreen(screenHeight: Int, screenWidth: Int) {
        val screenRatio = screenWidth * 1f / screenHeight
        val remoteRatio = remoteWidth * 1f / remoteHeight

        if (remoteRatio > screenRatio) {
            val scale = screenHeight * 1f / remoteHeight
            val scaleWidth = remoteWidth * scale
            val offset = (scaleWidth - screenWidth) * 1f / 2
            pointerCoords.forEach { coords ->
                coords.x = coords.x * scale
                coords.y = coords.y * scale
                if (coords.x < offset || coords.x > scaleWidth - offset) {
                    coords.x = 0f
                } else {
                    coords.x = coords.x - offset
                }
            }
        } else {
            val scale = screenWidth * 1f / remoteWidth
            val scaleHeight = remoteHeight * scale
            val offset = (scaleHeight - screenHeight) * 1f / 2
            pointerCoords.forEach { coords ->
                coords.x = coords.x * scale
                coords.y = coords.y * scale
                if (coords.y < offset || coords.y > scaleHeight - offset) {
                    coords.y = 0f
                } else {
                    coords.y = coords.y - offset
                }
            }
        }
    }
}

class PointerProperties : Serializable {
    var id = 0
    var toolType = 0

    constructor()
    constructor(it: MotionEvent.PointerProperties) : this() {
        fromMotionEventPointerProperties(it)
    }

    fun toMotionEventPointerProperties(): MotionEvent.PointerProperties {
        return MotionEvent.PointerProperties().also {
            it.id = id
            it.toolType = toolType
        }
    }

    fun fromMotionEventPointerProperties(it: MotionEvent.PointerProperties) {
        id = it.id
        toolType = it.toolType
    }
}

class PointerCoords : Serializable {
    var x = 0f
    var y = 0f
    var pressure = 0f
    var size = 0f
    var touchMajor = 0f
    var touchMinor = 0f
    var toolMajor = 0f
    var toolMinor = 0f
    var orientation = 0f

    constructor()
    constructor(it: MotionEvent.PointerCoords) : this() {
        formMotionEventPointerCoords(it)
    }

    fun toMotionEventPointerCoords(): MotionEvent.PointerCoords {
        return MotionEvent.PointerCoords().also {
            it.x = x
            it.y = y
            it.pressure = pressure
            it.size = size
            it.touchMajor = touchMajor
            it.touchMinor = touchMinor
            it.toolMajor = toolMajor
            it.toolMinor = toolMinor
            it.orientation = orientation
        }
    }

    fun formMotionEventPointerCoords(it: MotionEvent.PointerCoords) {
        x = it.x
        y = it.y
        pressure = it.pressure
        size = it.size
        touchMajor = it.touchMajor
        touchMinor = it.touchMinor
        toolMajor = it.toolMajor
        toolMinor = it.toolMinor
        orientation = it.orientation
    }

}