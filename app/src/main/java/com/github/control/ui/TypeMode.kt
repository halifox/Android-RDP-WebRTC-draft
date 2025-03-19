package com.github.control.ui

import androidx.appcompat.app.AppCompatActivity
import com.github.control.PullActivityNetty
import com.github.control.PullActivityNettyImage
import com.github.control.PullActivityWebRTC
import com.github.control.ScreenCaptureService0
import com.github.control.ScreenCaptureServiceNetty
import com.github.control.ScreenCaptureServiceNettyImage
import com.github.control.ScreenCaptureServiceWebRTC

enum class TypeMode {
    WEBRTC,
    TCP,
    TCP_IMAGE
}

val typeMode = TypeMode.TCP

fun getPullActivity(): Class<out AppCompatActivity> {
    return when (typeMode) {
        TypeMode.WEBRTC -> PullActivityWebRTC::class.java
        TypeMode.TCP -> PullActivityNetty::class.java
        TypeMode.TCP_IMAGE -> PullActivityNettyImage::class.java
    }
}

fun getScreenCaptureService(): Class<out ScreenCaptureService0> {
    return when (typeMode) {
        TypeMode.WEBRTC -> ScreenCaptureServiceWebRTC::class.java
        TypeMode.TCP -> ScreenCaptureServiceNetty::class.java
        TypeMode.TCP_IMAGE -> ScreenCaptureServiceNettyImage::class.java
    }
}