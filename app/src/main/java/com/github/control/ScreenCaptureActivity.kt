package com.github.control

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.blankj.utilcode.util.ThreadUtils
import com.blankj.utilcode.util.ToastUtils
import com.github.control.databinding.ActivityScreenCaptureBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.webrtc.DataChannel
import org.webrtc.EglBase
import org.webrtc.HardwareVideoDecoderFactory
import org.webrtc.HardwareVideoEncoderFactory
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.VideoTrack
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket

@SuppressLint("ClickableViewAccessibility")
class ScreenCaptureActivity : AppCompatActivity() {
    private val context = this
    private lateinit var binding: ActivityScreenCaptureBinding
    private val host by lazy { intent.getStringExtra("host") }
    private val port by lazy { intent.getIntExtra("port", 40000) }
    private lateinit var peerConnection: PeerConnection
    private var socket: Socket? = null
    private lateinit var inputStream: DataInputStream
    private lateinit var outputStream: DataOutputStream
    private val eglBase = EglBase.create()
    private val eglBaseContext = eglBase.getEglBaseContext()
    private val peerConnectionFactory = PeerConnectionFactory.builder()
        .setVideoEncoderFactory(HardwareVideoEncoderFactory(eglBaseContext, false, false))
        .setVideoDecoderFactory(HardwareVideoDecoderFactory(eglBaseContext))
        .createPeerConnectionFactory()
    private val rtcConfig = PeerConnection.RTCConfiguration(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScreenCaptureBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initView()
        lifecycleScope.launch(Dispatchers.IO) {
            socket = Socket(host, port)
            inputStream = DataInputStream(socket!!.inputStream)
            outputStream = DataOutputStream(socket!!.outputStream)
            startReadThread()
            createPeerConnection()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        socket?.close()
        eglBase.release()
        binding.renderer.clearImage()
        binding.renderer.release()
    }

    private fun createPeerConnection() {

        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onAddTrack(rtpReceiver: RtpReceiver, mediaStreams: Array<out MediaStream>) {
                val track = rtpReceiver.track()
                if (track is VideoTrack) {
                    track.addSink(binding.renderer)
                }
            }

            override fun onSignalingChange(p0: PeerConnection.SignalingState?) {

            }

            override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {

            }

            override fun onIceConnectionReceivingChange(p0: Boolean) {

            }

            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {

            }

            override fun onIceCandidate(iceCandidate: IceCandidate) {
                sendIceCandidate(outputStream, iceCandidate)
            }

            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate?>?) {

            }

            override fun onAddStream(p0: MediaStream?) {

            }

            override fun onRemoveStream(p0: MediaStream?) {

            }

            override fun onDataChannel(p0: DataChannel?) {

            }

            override fun onRenegotiationNeeded() {

            }
        })!!
    }

    private fun startReadThread() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                while (isActive) {
                    val type = inputStream.readInt()
                    when (type) {
                        ICE_CANDIDATE -> receiveIceCandidate(inputStream, peerConnection)
                        SESSION_DESCRIPTION -> {
                            receiveSessionDescription(inputStream, peerConnection)
                            createAnswer()
                        }

                        CONFIGURATION_CHANGED -> {
                            receiveConfigurationChanged(inputStream, binding.renderer)
                        }
                    }
                }
            } catch (e: Exception) {
                ToastUtils.showLong("对端关闭")
                withContext(Dispatchers.Main) {
                    finish()
                }
            }
        }
    }

    private fun createAnswer() {
        peerConnection.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(description: SessionDescription) {
                peerConnection.setLocalDescription(object : SdpObserver {
                    override fun onCreateSuccess(sdp: SessionDescription) {

                    }

                    override fun onSetSuccess() {

                    }

                    override fun onCreateFailure(error: String) {

                    }

                    override fun onSetFailure(error: String) {

                    }
                }, description)
                sendSessionDescription(outputStream, description)
            }

            override fun onSetSuccess() {

            }

            override fun onCreateFailure(error: String) {

            }

            override fun onSetFailure(error: String) {

            }
        }, MediaConstraints())
    }

    private val executorService = ThreadUtils.getSinglePool()
    private fun initView() {
        binding.renderer.init(eglBaseContext, null)
        binding.renderer.setOnTouchListener { _, event ->
            executorService.submit {
                sendTouchEvent(outputStream, event, binding.renderer)
            }
            true
        }
        binding.back.setOnClickListener {
            executorService.submit {
                sendGlobalActionEvent(outputStream, AccessibilityService.GLOBAL_ACTION_BACK)
            }
        }
        binding.home.setOnClickListener {
            executorService.submit {
                sendGlobalActionEvent(outputStream, AccessibilityService.GLOBAL_ACTION_HOME)
            }
        }
        binding.recents.setOnClickListener {
            executorService.submit {
                sendGlobalActionEvent(outputStream, AccessibilityService.GLOBAL_ACTION_RECENTS)
            }
        }
    }


    companion object {
        private const val TAG = "ScreenPullActivity"

        @JvmStatic
        fun start(context: Context, host: String, port: Int) {
            val starter = Intent(context, ScreenCaptureActivity::class.java)
                .putExtra("host", host)
                .putExtra("port", port)
            context.startActivity(starter)
        }
    }
}