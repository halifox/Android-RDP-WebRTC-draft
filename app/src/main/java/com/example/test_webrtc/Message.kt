package com.example.test_webrtc;

import org.webrtc.IceCandidate
import org.webrtc.SessionDescription

class Message {
    var description: SessionDescription? = null
    var iceCandidates: List<IceCandidate> = emptyList()

    constructor(description: SessionDescription?, iceCandidates: List<IceCandidate>) {
        this.description = description
        this.iceCandidates = iceCandidates
    }
}
