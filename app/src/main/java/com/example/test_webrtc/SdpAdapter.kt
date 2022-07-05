package com.example.test_webrtc

import android.util.Log
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription


open class SdpAdapter constructor(private val tag: String) : SdpObserver {

    override fun onSetFailure(str: String?) {
        Log.d("TAG", "onSetFailure: ${str}")
    }

    override fun onSetSuccess() {
    }

    override fun onCreateSuccess(description: SessionDescription?) {
    }

    override fun onCreateFailure(s: String?) {
        Log.d("TAG", "onCreateFailure:${tag} ${s}")

    }
}