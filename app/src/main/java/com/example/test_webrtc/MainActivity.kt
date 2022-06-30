package com.example.test_webrtc

import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.getSystemService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule

class MainActivity : AppCompatActivity() {
    var webrtcUrl = "webrtc://${SRS_SERVER_IP}/live/livestream"
    var peerConnection: PeerConnection? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        push()
        pull()
    }

    override fun onDestroy() {
        super.onDestroy()
        stop()
    }

    private fun pull() {
        pullCore()
    }

    private fun pullCore() {
        //EglBase
        val eglBase = EglBase.create()
        val eglBaseContext = eglBase.getEglBaseContext()
        //初始化
        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(applicationContext).createInitializationOptions())
        //视频编码器
        val encoderFactory = DefaultVideoEncoderFactory(eglBaseContext, true, true)
        //视频解码器
        val decoderFactory = DefaultVideoDecoderFactory(eglBaseContext)
        //音频设备模块
        val audioDeviceModule = JavaAudioDeviceModule.builder(this).createAudioDeviceModule()
        val options = PeerConnectionFactory.Options()
        //对等连接Factory
        val peerConnectionFactory = PeerConnectionFactory.builder()
                .setOptions(options)
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory)
                .setAudioDeviceModule(audioDeviceModule)
                .createPeerConnectionFactory()


        //显示
        val surfaceViewRenderer = findViewById<SurfaceViewRenderer>(R.id.srv)
        surfaceViewRenderer.init(eglBaseContext, null)


        val rtcConfig = PeerConnection.RTCConfiguration(emptyList())
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        val peerConnectionObserver = object : PeerConnectionObserver() {
            override fun onAddStream(mediaStream: MediaStream) {
                super.onAddStream(mediaStream)
                if (mediaStream.videoTracks.isNotEmpty()) {
                    mediaStream.videoTracks[0].addSink(surfaceViewRenderer)
                }
                if (mediaStream.audioTracks.isNotEmpty()) {

                }
            }
        }
        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, peerConnectionObserver)
        peerConnection?.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO, RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY))
        peerConnection?.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO, RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY))
        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(description: SessionDescription) {
                if (description.type == SessionDescription.Type.OFFER) {
                    val offerSdp = description.description
                    peerConnection?.setLocalDescription(SdpAdapter("setLocalDescription"), description)
                    val srsBean = SrsRequestBean(description.description, webrtcUrl)
                    MainScope().launch(Dispatchers.IO) {
                        try {
                            val result = apiService.play(srsBean)
                            if (result.code == 0) {
                                val remoteSdp = SessionDescription(SessionDescription.Type.ANSWER, convertAnswerSdp(offerSdp, result.sdp))
                                peerConnection?.setRemoteDescription(SdpAdapter("setRemoteDescription"), remoteSdp)
                            } else {
                                Toast.makeText(this@MainActivity, "网络请求失败，code：${result.code}", Toast.LENGTH_LONG).show()
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

            }

            override fun onSetSuccess() {

            }

            override fun onCreateFailure(p0: String?) {

            }

            override fun onSetFailure(p0: String?) {

            }
        }, MediaConstraints())
    }

    private val registerMediaProjectionPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        pushCore(it.data)
    }

    private fun push() {
        MediaProjectionForegroundService.start(this)
        val mediaProjectionManager = getSystemService<MediaProjectionManager>()
        registerMediaProjectionPermission.launch(mediaProjectionManager?.createScreenCaptureIntent())
    }

    private fun stop() {
        peerConnection?.dispose()
        MediaProjectionForegroundService.stop(this)
    }

    private fun pushCore(mediaProjectionPermissionResultData: Intent?) {
        //EglBase
        val eglBase = EglBase.create()
        val eglBaseContext = eglBase.getEglBaseContext()
        //初始化
        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(applicationContext).createInitializationOptions())
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

        //屏幕捕获
        val videoCapturer = ScreenCapturerAndroid(mediaProjectionPermissionResultData, object : MediaProjection.Callback() {})
        //创建视频源
        val videoSource = peerConnectionFactory.createVideoSource(videoCapturer.isScreencast)
        val surfaceTextureHelper = SurfaceTextureHelper.create("surface_texture_thread", eglBaseContext)
        videoCapturer.initialize(surfaceTextureHelper, this, videoSource.capturerObserver)
        videoCapturer.startCapture(2160, 1080, 60)
        val videoTrack = peerConnectionFactory.createVideoTrack("local_video_track", videoSource)

/*
        //显示
        val surfaceViewRenderer = findViewById<SurfaceViewRenderer>(R.id.srv)
        surfaceViewRenderer.init(eglBaseContext, null)
        videoTrack.addSink(surfaceViewRenderer)
*/

        //RTC配置
        val rtcConfig = PeerConnection.RTCConfiguration(listOf())
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        //创建对等连接
        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, PeerConnectionObserver())
        peerConnection?.addTransceiver(videoTrack, RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_ONLY))
        peerConnection?.addTransceiver(audioTrack, RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_ONLY))
        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(description: SessionDescription) {
                if (description.type == SessionDescription.Type.OFFER) {
                    peerConnection?.setLocalDescription(SdpAdapter("setLocalDescription"), description)
                    //这个offerSdp将用于向SRS服务进行网络请求
                    val offerSdp = description.description
                    val srsBean = SrsRequestBean(offerSdp, webrtcUrl)
                    MainScope().launch(Dispatchers.IO) {
                        try {
                            val result = apiService.publish(srsBean)
                            if (result.code == 0) {
                                val remoteSdp = SessionDescription(SessionDescription.Type.ANSWER, convertAnswerSdp(offerSdp, result.sdp))
                                peerConnection?.setRemoteDescription(SdpAdapter("setRemoteDescription"), remoteSdp)
                            } else {
                                Toast.makeText(this@MainActivity, "网络请求失败，code：${result.code}", Toast.LENGTH_LONG).show()
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }

            override fun onSetSuccess() {

            }

            override fun onCreateFailure(p0: String?) {

            }

            override fun onSetFailure(p0: String?) {

            }
        }, MediaConstraints())
    }

    //sdp
    private fun convertAnswerSdp(offerSdp: String, answerSdp: String?): String {
        if (answerSdp.isNullOrBlank()) {
            return ""
        }
        val indexOfOfferVideo = offerSdp.indexOf("m=video")
        val indexOfOfferAudio = offerSdp.indexOf("m=audio")
        if (indexOfOfferVideo == -1 || indexOfOfferAudio == -1) {
            return answerSdp
        }
        val indexOfAnswerVideo = answerSdp.indexOf("m=video")
        val indexOfAnswerAudio = answerSdp.indexOf("m=audio")
        if (indexOfAnswerVideo == -1 || indexOfAnswerAudio == -1) {
            return answerSdp
        }

        val isFirstOfferVideo = indexOfOfferVideo < indexOfOfferAudio
        val isFirstAnswerVideo = indexOfAnswerVideo < indexOfAnswerAudio
        return if (isFirstOfferVideo == isFirstAnswerVideo) {
            //顺序一致
            answerSdp
        } else {
            //需要调换顺序
            buildString {
                append(answerSdp.substring(0, indexOfAnswerVideo.coerceAtMost(indexOfAnswerAudio)))
                append(answerSdp.substring(indexOfAnswerVideo.coerceAtLeast(indexOfOfferVideo), answerSdp.length))
                append(answerSdp.substring(indexOfAnswerVideo.coerceAtMost(indexOfAnswerAudio), indexOfAnswerVideo.coerceAtLeast(indexOfOfferVideo)))
            }
        }
    }


}