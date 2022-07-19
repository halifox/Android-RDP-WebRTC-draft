package com.brigitttta.remote_screencast

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.blankj.utilcode.util.ScreenUtils
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule

@Deprecated("")
class TestActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test)
        //EglBase
        val eglBase = EglBase.create()
        val eglBaseContext = eglBase.getEglBaseContext()

        val surfaceViewRenderer = findViewById<SurfaceViewRenderer>(R.id.srv)
        surfaceViewRenderer.init(eglBaseContext, null)
        val surfaceViewRenderer2 = findViewById<SurfaceViewRenderer>(R.id.srv2)
        surfaceViewRenderer2.init(eglBaseContext, null)
        //初始化
//        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(applicationContext).createInitializationOptions())
        //视频编码器
        val encoderFactory = DefaultVideoEncoderFactory(eglBaseContext, true, true)
        //视频解码器
        val decoderFactory = DefaultVideoDecoderFactory(eglBaseContext)
        //音频设备模块
        val audioDeviceModule = JavaAudioDeviceModule.builder(this).createAudioDeviceModule()
        //对等连接Factory
        val peerConnectionFactory = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory)
                .setAudioDeviceModule(audioDeviceModule)
                .createPeerConnectionFactory()

        //音频处理
        val audioConstraints = MediaConstraints()
        //回声消除
        audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
        //自动增益
        audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
        //高音过滤
        audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
        //噪音处理
        audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
        //创建音频源
        val audioSource = peerConnectionFactory.createAudioSource(audioConstraints)
        //创建音轨
        val audioTrack = peerConnectionFactory.createAudioTrack("local_audio_track", audioSource)

        val videoCapturer = FileVideoCapturer("/sdcard/test.y4m")
        //屏幕捕获
//        val videoCapturer = ScreenCapturerAndroid(mediaProjectionPermissionResultData, object : MediaProjection.Callback() {})
        //创建视频源
        val videoSource = peerConnectionFactory.createVideoSource(videoCapturer.isScreencast)
        val surfaceTextureHelper = SurfaceTextureHelper.create("surface_texture_thread", eglBaseContext)
        videoCapturer.initialize(surfaceTextureHelper, this, videoSource.capturerObserver)
        videoCapturer.startCapture(ScreenUtils.getScreenWidth(), ScreenUtils.getScreenHeight(), 30)
        //创建视频轨
        val videoTrack = peerConnectionFactory.createVideoTrack("local_video_track", videoSource)
//        val videoFileRenderer = VideoFileRenderer("/sdcard/test.y4m",ScreenUtils.getScreenWidth(), ScreenUtils.getScreenHeight(), eglBaseContext)
//        videoTrack.addSink(videoFileRenderer)
        videoTrack.addSink(surfaceViewRenderer)
        //创建媒体流
        val mediaStream = peerConnectionFactory.createLocalMediaStream("102")
//        mediaStream.addTrack(audioTrack)
        mediaStream.addTrack(videoTrack)


        //RTC配置
        val rtcConfig = PeerConnection.RTCConfiguration(listOf())
        rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED
        rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
        rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        rtcConfig.keyType = PeerConnection.KeyType.ECDSA


        var peerConnectionSend: PeerConnection? = null
        var peerConnectionRec: PeerConnection? = null
        //创建对等连接
        peerConnectionSend = peerConnectionFactory.createPeerConnection(rtcConfig, object : SimplePeerConnectionObserver("push") {
            override fun onIceCandidate(iceCandidate: IceCandidate) {
                peerConnectionRec?.addIceCandidate(iceCandidate)
            }
        })
        peerConnectionRec = peerConnectionFactory.createPeerConnection(rtcConfig, object : SimplePeerConnectionObserver("push") {
            override fun onIceCandidate(iceCandidate: IceCandidate) {
                peerConnectionSend?.addIceCandidate(iceCandidate)
            }

            override fun onAddStream(mediaStream: MediaStream) {
                mediaStream.videoTracks[0].addSink(surfaceViewRenderer2)
            }
        })


        peerConnectionSend?.addStream(mediaStream)
        peerConnectionSend?.createOffer(object : SimpleSdpObserver("push-createOffer") {
            override fun onCreateSuccess(description: SessionDescription) {
                peerConnectionSend?.setLocalDescription(SimpleSdpObserver("4"), description)
                peerConnectionRec?.setRemoteDescription(SimpleSdpObserver("3"), description)
                peerConnectionRec?.createAnswer(object : SimpleSdpObserver("5") {
                    override fun onCreateSuccess(description: SessionDescription) {
                        peerConnectionRec.setLocalDescription(SimpleSdpObserver("1"), description)
                        peerConnectionSend.setRemoteDescription(SimpleSdpObserver("2"), description)
                    }
                }, MediaConstraints())

            }
        }, MediaConstraints())

    }


}