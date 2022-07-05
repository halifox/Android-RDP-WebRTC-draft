package com.example.test_webrtc;

import com.squareup.moshi.JsonClass
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription

@JsonClass(generateAdapter = true)
class Message {
    var d: SessionDescription? = null
    var i: MutableList<IceCandidate> = mutableListOf<IceCandidate>()

    constructor(d: SessionDescription?, i: MutableList<IceCandidate>) {
        this.d = d
        this.i = i
    }
}
