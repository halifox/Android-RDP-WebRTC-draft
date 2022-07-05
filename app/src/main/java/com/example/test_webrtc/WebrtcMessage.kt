package com.example.test_webrtc;

import com.google.gson.Gson
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription

class WebrtcMessage {
    var description: SessionDescription? = null
    var iceCandidates: List<IceCandidate> = emptyList()

    constructor(json: String) {
        val webrtcMessage = Gson().fromJson(json, WebrtcMessage::class.java)
        this.description = webrtcMessage.description
        this.iceCandidates = webrtcMessage.iceCandidates
    }

    constructor(description: SessionDescription?, iceCandidates: List<IceCandidate>) {
        this.description = description
        this.iceCandidates = iceCandidates
    }

    override fun toString(): String {
        return Gson().toJson(this)
    }

}
