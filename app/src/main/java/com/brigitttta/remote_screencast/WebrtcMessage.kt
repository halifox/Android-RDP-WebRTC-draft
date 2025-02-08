package com.brigitttta.remote_screencast;

import android.util.Size
import com.google.gson.Gson
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription

class WebrtcMessage {
    enum class Type {
        NULL,
        SDP,
        ICE,
        SIZE,
    }

    var type: Type = Type.NULL
    var description: SessionDescription? = null
    var iceCandidate: IceCandidate? = null
    var size: Size? = null

    constructor(json: String) {
        val webrtcMessage = Gson().fromJson(json, WebrtcMessage::class.java)
        this.type = webrtcMessage.type
        this.description = webrtcMessage.description
        this.iceCandidate = webrtcMessage.iceCandidate
        this.size = webrtcMessage.size
    }

    constructor(
            type: Type,
            description: SessionDescription? = null,
            iceCandidate: IceCandidate? = null,
            size: Size? = null,
    ) {
        this.type = type
        this.description = description
        this.iceCandidate = iceCandidate
        this.size = size
    }

    override fun toString(): String {
        return Gson().toJson(this)
    }

}
