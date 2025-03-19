package com.github.control

import android.content.Intent
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.blankj.utilcode.util.ScreenUtils
import com.github.control.scrcpy.Controller
import io.netty.channel.ChannelPipeline
import org.koin.android.ext.android.inject
import org.webrtc.EglBase
import org.webrtc.HardwareVideoDecoderFactory
import org.webrtc.HardwareVideoEncoderFactory
import org.webrtc.PeerConnectionFactory
import org.webrtc.ScreenCapturerAndroid
import org.webrtc.SurfaceTextureHelper


class ScreenCaptureServiceWebRTC : ScreenCaptureService0() {
    private val eglBase = EglBase.create()
    private val eglBaseContext = eglBase.getEglBaseContext()
    private val peerConnectionFactory = PeerConnectionFactory.builder()
        .setVideoEncoderFactory(HardwareVideoEncoderFactory(eglBaseContext, true, true))
        .setVideoDecoderFactory(HardwareVideoDecoderFactory(eglBaseContext))
        .createPeerConnectionFactory()
    private val surfaceTextureHelper = SurfaceTextureHelper.create("surface_texture_thread", eglBaseContext, true)
    private val videoSource = peerConnectionFactory.createVideoSource(true, true)
    private val videoTrack = peerConnectionFactory.createVideoTrack("local_video_track", videoSource)


    override fun onInitScreenCaptureIntent(screenCaptureIntent: Intent) {
        initScreenCapturerAndroid(screenCaptureIntent)
    }

    private fun initScreenCapturerAndroid(screenCaptureIntent: Intent) {
        ScreenCapturerAndroid(screenCaptureIntent, mediaProjectionCallback).apply {
            initialize(surfaceTextureHelper, applicationContext, videoSource.capturerObserver)
            startCapture(ScreenUtils.getScreenWidth(), ScreenUtils.getScreenHeight(), 0)
            lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) {
                    stopCapture()
                    dispose()
                }
            })
        }
    }

    private val controller by inject<Controller>()

    override fun initChannel(pipeline: ChannelPipeline) {
        pipeline
            .addLast(ControlInboundHandler(controller = controller))
            .addLast(PeerConnectionInboundHandler(peerConnectionFactory, videoTrack, isOffer = true))
    }


}