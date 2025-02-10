package com.github.control

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.ProgressDialog
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.github.control.databinding.ActivityMainBinding
import com.github.control.scrcpy.Binary
import com.github.control.scrcpy.Controller
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.core.toByteArray
import io.ktor.utils.io.readByteArray
import io.ktor.utils.io.readInt
import io.ktor.utils.io.writeByteArray
import io.ktor.utils.io.writeInt
import io.ktor.utils.io.writeShort
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.VideoTrack


@SuppressLint("MissingInflatedId", "ClickableViewAccessibility")
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val context = this
    private val eventChannel = Channel<MotionEvent>(Channel.BUFFERED)
    private val actionChannel = Channel<Int>(Channel.BUFFERED)
    private val selectorManager = SelectorManager(Dispatchers.IO)
    private val progressDialog by lazy {
        ProgressDialog(context).apply {
            setTitle("Connect")
            setMessage("loading...")
            isIndeterminate = true
            setCancelable(false)
        }
    }
    private val coroutineExceptionHandler =
        CoroutineExceptionHandler { coroutineContext, throwable ->
            lifecycleScope.launch {
                progressDialog.hide()
                AlertDialog.Builder(context)
                    .setTitle("error")
                    .setMessage("${throwable}")
                    .create()
                    .show()
            }
        }

    //EglBase
    private val eglBase = EglBase.create()
    private val eglBaseContext = eglBase.getEglBaseContext()
    private val peerConnectionFactory = PeerConnectionFactory.builder()
        .setOptions(PeerConnectionFactory.Options())
        .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBaseContext, true, true))
        .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBaseContext))
        .createPeerConnectionFactory()

    //rtc配置
    private val rtcConfig = PeerConnection.RTCConfiguration(listOf())
        .apply {
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            keyType = PeerConnection.KeyType.ECDSA
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Controller.updateDisplayMetrics(context)

        binding.SurfaceViewRenderer.init(eglBaseContext, null)

        binding.slave.setOnClickListener {
            openAccessibilitySettings()
        }
        binding.master.setOnClickListener {
            connectToHost()
        }
        binding.SurfaceViewRenderer.setOnTouchListener { _, event ->
            eventChannel.trySend(event)
            true
        }

        binding.back.setOnClickListener {
            actionChannel.trySend(AccessibilityService.GLOBAL_ACTION_BACK)
        }
        binding.home.setOnClickListener {
            actionChannel.trySend(AccessibilityService.GLOBAL_ACTION_HOME)
        }
        binding.recents.setOnClickListener {
            actionChannel.trySend(AccessibilityService.GLOBAL_ACTION_RECENTS)
        }

        val mediaProjectionManager = getSystemService(MediaProjectionManager::class.java)!!
        screenCaptureLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
    }


    private val screenCaptureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK && it.data != null) {
            ScreenCaptureService.start(context, it.data)
        }
    }

    override fun onDestroy() {
        ScreenCaptureService.stop(context)
        super.onDestroy()
        progressDialog.dismiss()
        selectorManager.close()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        Process.killProcess(Process.myPid())
    }


    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
    }

    private fun connectToHost() {
        val host = binding.host.text.toString()
        startClient(host)
        b(host)
    }

    private fun b(host: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val socket = aSocket(selectorManager).tcp()
                .connect(host, 40001)

            val byteWriteChannel = socket.openWriteChannel()
            val byteReadChannel = socket.openReadChannel()

            val peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
                override fun onAddTrack(rtpReceiver: RtpReceiver, mediaStreams: Array<out MediaStream>) {
                    super.onAddTrack(rtpReceiver, mediaStreams)
                    val track = rtpReceiver.track()
                    when (track) {
                        is VideoTrack -> {
                            track.addSink(binding.SurfaceViewRenderer)
                        }
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
                    lifecycleScope.launch {
                        println(
                            """
                                send iceCandidate
                                ${iceCandidate.sdpMid}
                                ${iceCandidate.sdpMLineIndex}
                                ${iceCandidate.sdp}
                            """.trimIndent()
                        )

                        byteWriteChannel.writeInt(1)
                        byteWriteChannel.writeInt(iceCandidate.sdpMid.length)
                        byteWriteChannel.writeByteArray(iceCandidate.sdpMid.toByteArray())
                        byteWriteChannel.writeInt(iceCandidate.sdpMLineIndex)
                        byteWriteChannel.writeInt(iceCandidate.sdp.length)
                        byteWriteChannel.writeByteArray(iceCandidate.sdp.toByteArray())
                        byteWriteChannel.flush()
                    }

                }

                override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {

                }

                override fun onAddStream(p0: MediaStream?) {

                }

                override fun onRemoveStream(p0: MediaStream?) {

                }

                override fun onDataChannel(p0: DataChannel?) {

                }

                override fun onRenegotiationNeeded() {

                }
            })


            launch {
                while (true) {
                    val type = byteReadChannel.readInt()
                    when (type) {
                        1 -> {//ICE
                            val sdpMid = String(byteReadChannel.readByteArray(byteReadChannel.readInt()))
                            val sdpMLineIndex = byteReadChannel.readInt()
                            val sdp = String(byteReadChannel.readByteArray(byteReadChannel.readInt()))
                            val iceCandidate = IceCandidate(sdpMid, sdpMLineIndex, sdp)
                            peerConnection?.addIceCandidate(iceCandidate)
                        }

                        2 -> {//SDP
                            val type = String(byteReadChannel.readByteArray(byteReadChannel.readInt()))
                            val description = String(byteReadChannel.readByteArray(byteReadChannel.readInt()))
                            val sdp = SessionDescription(SessionDescription.Type.valueOf(type), description)
                            peerConnection?.setRemoteDescription(object : SdpObserver {
                                override fun onCreateSuccess(p0: SessionDescription?) {
                                    
                                }

                                override fun onSetSuccess() {
                                    
                                }

                                override fun onCreateFailure(p0: String?) {
                                    
                                }

                                override fun onSetFailure(p0: String?) {
                                    
                                }
                            }, sdp)
                            peerConnection?.createAnswer(
                                object : SdpObserver {
                                    override fun onCreateSuccess(sdp: SessionDescription?) {
                                        peerConnection?.setLocalDescription(object : SdpObserver {
                                            override fun onCreateSuccess(description: SessionDescription) {
                                                lifecycleScope.launch {
                                                    println(
                                                        """
                                send description
                                ${description.type.name}
                                ${description.description}
                            """.trimIndent()
                                                    )

                                                    byteWriteChannel.writeInt(2)
                                                    byteWriteChannel.writeInt(description.type.name.length)
                                                    byteWriteChannel.writeByteArray(description.type.name.toByteArray())
                                                    byteWriteChannel.writeInt(description.description.length)
                                                    byteWriteChannel.writeByteArray(description.description.toByteArray())
                                                    byteWriteChannel.flush()
                                                }
                                            }

                                            override fun onSetSuccess() {

                                            }

                                            override fun onCreateFailure(p0: String?) {

                                            }

                                            override fun onSetFailure(p0: String?) {

                                            }
                                        }, sdp)
                                    }

                                    override fun onSetSuccess() {

                                    }

                                    override fun onCreateFailure(p0: String?) {

                                    }

                                    override fun onSetFailure(p0: String?) {

                                    }
                                }, MediaConstraints()
                            )
                        }
                    }
                }
            }
        }
    }

    private fun startClient(host: String) {
        lifecycleScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            withContext(Dispatchers.Main) { progressDialog.show() }
            val socket = aSocket(selectorManager).tcp()
                .connect(host, 40000)
            withContext(Dispatchers.Main) {
                progressDialog.hide()
                binding.slave.visibility = View.GONE
                binding.host.visibility = View.GONE
                binding.master.visibility = View.GONE
                binding.controlPenal.visibility = View.VISIBLE
            }

            val byteWriteChannel = socket.openWriteChannel()
            launch {
                eventChannel.consumeEach { event ->
                    byteWriteChannel.writeInt(ControlService.TYPE_MOTION_EVENT)
                    byteWriteChannel.writeInt(event.action)
                    byteWriteChannel.writeInt(event.getPointerId(event.actionIndex))
                    byteWriteChannel.writeInt(
                        event.getX(event.actionIndex)
                            .toInt()
                    )
                    byteWriteChannel.writeInt(
                        event.getY(event.actionIndex)
                            .toInt()
                    )
                    byteWriteChannel.writeInt(Controller.displayMetrics.widthPixels)
                    byteWriteChannel.writeInt(Controller.displayMetrics.heightPixels)
                    byteWriteChannel.writeShort(Binary.floatToI16FixedPoint(event.pressure))
                    byteWriteChannel.writeInt(event.actionButton)
                    byteWriteChannel.writeInt(event.buttonState)
                    byteWriteChannel.flush()
                }
            }
            launch {
                actionChannel.consumeEach {
                    val action = it
                    byteWriteChannel.writeInt(ControlService.TYPE_GLOBAL_ACTION)
                    byteWriteChannel.writeInt(action)
                    byteWriteChannel.flush()
                }
            }
        }
    }

}
