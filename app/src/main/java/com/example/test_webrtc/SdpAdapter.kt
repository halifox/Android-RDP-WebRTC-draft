package com.example.test_webrtc

import org.webrtc.SdpObserver
import org.webrtc.SessionDescription


open class SdpAdapter constructor(private val tag: String) : SdpObserver {

    override fun onSetFailure(str: String?) {
    }

    override fun onSetSuccess() {
    }

    override fun onCreateSuccess(description: SessionDescription?) {
    }

    override fun onCreateFailure(s: String?) {
    }
}