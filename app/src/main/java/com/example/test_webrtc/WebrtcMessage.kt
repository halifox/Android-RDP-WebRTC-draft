package com.example.test_webrtc;

import com.google.gson.Gson
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription

class WebrtcMessage {
    enum class Type {
        NULL,
        SDP,
        ICE,
        MOVE,
    }

    var type: Type = Type.NULL
    var description: SessionDescription? = null
    var iceCandidate: IceCandidate? = null
    var motionModel: MotionModel? = null

    constructor(json: String) {
        val webrtcMessage = Gson().fromJson(json, WebrtcMessage::class.java)
        this.type = webrtcMessage.type
        this.description = webrtcMessage.description
        this.iceCandidate = webrtcMessage.iceCandidate
        this.motionModel = webrtcMessage.motionModel
    }

    constructor(
            type: Type,
            description: SessionDescription? = null,
            iceCandidate: IceCandidate? = null,
            motionModel: MotionModel? = null,
    ) {
        this.type = type
        this.description = description
        this.iceCandidate = iceCandidate
        this.motionModel = motionModel
    }

    override fun toString(): String {
        return Gson().toJson(this)
    }

}
