package com.github.control

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.blankj.utilcode.util.ToastUtils
import com.github.control.databinding.ActivityScreenCaptureBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.webrtc.EglBase
import org.webrtc.HardwareVideoDecoderFactory
import org.webrtc.HardwareVideoEncoderFactory
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
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
    private lateinit var socket: Socket
    private lateinit var inputStream: DataInputStream
    private lateinit var outputStream: DataOutputStream
    private val eglBase = EglBase.create()
    private val eglBaseContext = eglBase.getEglBaseContext()
    private val peerConnectionFactory = PeerConnectionFactory.builder()
        .setOptions(PeerConnectionFactory.Options())
        .setVideoEncoderFactory(HardwareVideoEncoderFactory(eglBaseContext, true, true))
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
            inputStream = DataInputStream(socket.inputStream)
            outputStream = DataOutputStream(socket.outputStream)
            startReadThread()
            createPeerConnection()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        inputStream.close()
        outputStream.close()
        socket.close()
        eglBase.release()
        binding.renderer.clearImage()
        binding.renderer.release()
    }

    private fun createPeerConnection() {

        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, object : EmptyPeerConnectionObserver() {
            override fun onAddTrack(rtpReceiver: RtpReceiver, mediaStreams: Array<out MediaStream>) {
                val track = rtpReceiver.track()
                if (track is VideoTrack) {
                    track.addSink(binding.renderer)
                }
            }

            override fun onIceCandidate(iceCandidate: IceCandidate) {
                sendIceCandidate(outputStream, iceCandidate)
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
                            peerConnection.createAnswer(object : EmptySdpObserver() {
                                override fun onCreateSuccess(description: SessionDescription) {
                                    peerConnection.setLocalDescription(EmptySdpObserver(), description)
                                    sendSessionDescription(outputStream, description)
                                }
                            }, MediaConstraints())
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

    private fun initView() {
        binding.renderer.init(eglBaseContext, null)
        binding.renderer.setOnTouchListener { _, event ->
            sendTouchEvent(outputStream, event, binding.renderer)
            true
        }
        binding.back.setOnClickListener {
            sendGlobalActionEvent(outputStream, AccessibilityService.GLOBAL_ACTION_BACK)
        }
        binding.home.setOnClickListener {
            sendGlobalActionEvent(outputStream, AccessibilityService.GLOBAL_ACTION_HOME)
        }
        binding.recents.setOnClickListener {
            sendGlobalActionEvent(outputStream, AccessibilityService.GLOBAL_ACTION_RECENTS)
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