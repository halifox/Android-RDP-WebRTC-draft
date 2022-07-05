package com.example.test_webrtc.srs

import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.getSystemService
import com.example.test_webrtc.MediaProjectionForegroundService
import com.example.test_webrtc.R
import com.example.test_webrtc.SimplePeerConnectionObserver
import com.example.test_webrtc.SimpleSdpObserver
import kotlinx.coroutines.*
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.create
import retrofit2.http.Body
import retrofit2.http.POST
import java.time.LocalTime

class SrsActivity : AppCompatActivity() {

    interface ApiService {

        @POST("/rtc/v1/play/")
        suspend fun play(@Body body: SrsRequestBean): SrsResponseBean

        @POST("/rtc/v1/publish/")
        suspend fun publish(@Body body: SrsRequestBean): SrsResponseBean
    }

    private var SRS_SERVER_IP = "192.168.8.100"
    private val baseUrl = "http://$SRS_SERVER_IP:${1985}/"
    private var webrtcUrl = "webrtc://${SRS_SERVER_IP}/live/livestream"

    private val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    private var apiService = retrofit.create<ApiService>()
    private var peerConnection: PeerConnection? = null
    private val mainScope = MainScope()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_srs)
        findViewById<Button>(R.id.btn_push).setOnClickListener { push() }
        findViewById<Button>(R.id.btn_pull).setOnClickListener { pull() }
        findViewById<Button>(R.id.btn_stop).setOnClickListener { stop() }
        val textView = findViewById<TextView>(R.id.tv_time)
        val handler = Handler()
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    textView.text = LocalTime.now().toString()
                }
                handler.postDelayed(this, 16)
            }
        }, 16)

    }

    override fun onDestroy() {
        stop()
        super.onDestroy()
    }

    private val registerMediaProjectionPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        pushCore(it.data)
    }

    private fun push() {
        MediaProjectionForegroundService.start(this)
        val mediaProjectionManager = getSystemService<MediaProjectionManager>()
        registerMediaProjectionPermission.launch(mediaProjectionManager?.createScreenCaptureIntent())
    }

    private fun pull() {
        pullCore()
    }

    private fun stop() {
        val surfaceViewRenderer = findViewById<SurfaceViewRenderer>(R.id.srv)
        surfaceViewRenderer.release()
        peerConnection?.dispose()
        mainScope.cancel()
        MediaProjectionForegroundService.stop(this)
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

        val surfaceViewRenderer = findViewById<SurfaceViewRenderer>(R.id.srv)
        surfaceViewRenderer.init(eglBaseContext, null)

        val rtcConfig = PeerConnection.RTCConfiguration(emptyList())
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        val peerConnectionObserver = object : SimplePeerConnectionObserver("2") {
            override fun onAddStream(mediaStream: MediaStream) {
                super.onAddStream(mediaStream)
                if (mediaStream.videoTracks.isNotEmpty()) {
                    //显示
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
                    peerConnection?.setLocalDescription(SimpleSdpObserver("setLocalDescription"), description)
                    val srsBean = SrsRequestBean(description.description, webrtcUrl)
                    mainScope.launch(Dispatchers.IO) {
                        try {
                            val result = apiService.play(srsBean)
                            if (result.code == 0) {
                                val remoteSdp = SessionDescription(SessionDescription.Type.ANSWER, convertAnswerSdp(offerSdp, result.sdp))
                                peerConnection?.setRemoteDescription(SimpleSdpObserver("setRemoteDescription"), remoteSdp)
                            } else {
                                launch(Dispatchers.Main) {
                                    Toast.makeText(this@SrsActivity, "网络请求失败，code：${result.code}", Toast.LENGTH_LONG).show()
                                }
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
        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, SimplePeerConnectionObserver("1"))
        peerConnection?.addTransceiver(videoTrack, RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_ONLY))
        peerConnection?.addTransceiver(audioTrack, RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_ONLY))
        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(description: SessionDescription) {
                if (description.type == SessionDescription.Type.OFFER) {
                    peerConnection?.setLocalDescription(SimpleSdpObserver("setLocalDescription"), description)
                    //这个offerSdp将用于向SRS服务进行网络请求
                    val offerSdp = description.description
                    val srsBean = SrsRequestBean(offerSdp, webrtcUrl)
                    mainScope.launch(Dispatchers.IO) {
                        try {
                            val result = apiService.publish(srsBean)
                            if (result.code == 0) {
                                val remoteSdp = SessionDescription(SessionDescription.Type.ANSWER, convertAnswerSdp(offerSdp, result.sdp))
                                peerConnection?.setRemoteDescription(SimpleSdpObserver("setRemoteDescription"), remoteSdp)
                            } else {
                                launch(Dispatchers.Main) {
                                    Toast.makeText(this@SrsActivity, "网络请求失败，code：${result.code}", Toast.LENGTH_LONG).show()
                                }
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