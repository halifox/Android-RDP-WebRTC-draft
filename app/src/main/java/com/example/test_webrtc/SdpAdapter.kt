package com.example.test_webrtc

import android.util.Log
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription


open class SdpAdapter constructor(private val tag: String) : SdpObserver {

    override fun onSetFailure(str: String) {
        Log.d(tag, "onSetFailure: ${str}")
    }

    override fun onSetSuccess() {
        Log.d(tag, "onSetSuccess: ${null}")

    }

    override fun onCreateSuccess(description: SessionDescription) {
        Log.d(tag, "onCreateSuccess: ${description}")

    }

    override fun onCreateFailure(str: String) {
        Log.d(tag, "onCreateFailure: ${str}")
    }
}