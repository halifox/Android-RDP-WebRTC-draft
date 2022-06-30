package com.example.test_webrtc

import com.squareup.moshi.Json

data class SrsResponseBean(

        @Json(name = "code")
        val code: Int,

        @Json(name = "sdp")
        val sdp: String?,

        @Json(name = "server")
        val server: String?,

        @Json(name = "sessionid")
        val sessionId: String?
)