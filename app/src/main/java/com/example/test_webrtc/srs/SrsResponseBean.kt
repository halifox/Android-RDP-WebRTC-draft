package com.example.test_webrtc.srs


data class SrsResponseBean(
        val code: Int,
        val sdp: String?,
        val server: String?,
        val sessionid: String?
)