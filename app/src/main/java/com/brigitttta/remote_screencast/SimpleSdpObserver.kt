package com.brigitttta.remote_screencast

import android.util.Log
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription


open class SimpleSdpObserver constructor(private val TAG: String) : SdpObserver {

    override fun onSetFailure(str: String) {
    }

    override fun onSetSuccess() {
    }

    override fun onCreateSuccess(description: SessionDescription) {
    }

    override fun onCreateFailure(str: String) {
    }
}