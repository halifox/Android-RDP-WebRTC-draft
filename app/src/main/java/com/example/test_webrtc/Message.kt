package com.example.test_webrtc;

import com.google.gson.Gson
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription

class Message {
    var description: SessionDescription? = null
    var iceCandidates: List<IceCandidate> = emptyList()

    constructor(json: String) {
        val message = Gson().fromJson(json, Message::class.java)
        this.description = message.description
        this.iceCandidates = message.iceCandidates
    }

    constructor(description: SessionDescription?, iceCandidates: List<IceCandidate>) {
        this.description = description
        this.iceCandidates = iceCandidates
    }

    override fun toString(): String {
        return Gson().toJson(this)
    }

}
