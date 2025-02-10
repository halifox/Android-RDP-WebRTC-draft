package com.github.control

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.os.IBinder
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationChannelGroupCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.blankj.utilcode.util.ScreenUtils
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.core.toByteArray
import io.ktor.utils.io.readByteArray
import io.ktor.utils.io.readInt
import io.ktor.utils.io.writeByteArray
import io.ktor.utils.io.writeInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.ScreenCapturerAndroid
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper

class ScreenCaptureService : Service() {
    private val context = this
    private val lifecycleScope = MainScope()


    private val screenHeight = ScreenUtils.getScreenHeight()
    private val screenWidth = ScreenUtils.getScreenWidth()

    // EglBase
    private val eglBase = EglBase.create()
    private val eglBaseContext = eglBase.getEglBaseContext()
    private val peerConnectionFactory = PeerConnectionFactory.builder()
        .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBaseContext, true, true))
        .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBaseContext))
        .createPeerConnectionFactory()
    private val surfaceTextureHelper = SurfaceTextureHelper.create("surface_texture_thread", eglBaseContext, true)
    private val videoSource = peerConnectionFactory.createVideoSource(true, true)
    private val videoTrack = peerConnectionFactory.createVideoTrack("local_video_track", videoSource)

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        startForeground()
        startServer()
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleScope.cancel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val screenCaptureIntent = intent?.getParcelableExtra<Intent>("screenCaptureIntent")
        if (screenCaptureIntent != null) {
            ScreenCapturerAndroid(screenCaptureIntent, object : MediaProjection.Callback() {
                override fun onStop() {
                    println("MediaProjection::onStop")
                    stopSelf()
                }
            }).apply {
                initialize(surfaceTextureHelper, applicationContext, videoSource.capturerObserver)
                startCapture(screenWidth, screenHeight, 0)
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }


    private fun startServer() {
        lifecycleScope.launch(Dispatchers.IO) {
            val selectorManager = SelectorManager(Dispatchers.IO)
            val serverSocket = aSocket(selectorManager).tcp()
                .bind("0.0.0.0", 40001)
            println("Server is listening at ${serverSocket.localAddress}")
            while (true) {
                val socket = serverSocket.accept()
                val socketAddress = socket.remoteAddress
                println("accept $socketAddress")

                val byteReadChannel = socket.openReadChannel()
                val byteWriteChannel = socket.openWriteChannel()

                //RTC配置
                val rtcConfig = PeerConnection.RTCConfiguration(emptyList())
                    .apply {
                        tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED
                        bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
                        rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
                        continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
                        keyType = PeerConnection.KeyType.ECDSA
                    }

                //创建对等连接
                val peerConnection: PeerConnection? = peerConnectionFactory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
                    override fun onSignalingChange(p0: PeerConnection.SignalingState?) {

                    }

                    override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {

                    }

                    override fun onIceConnectionReceivingChange(p0: Boolean) {

                    }

                    override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {

                    }

                    override fun onIceCandidate(iceCandidate: IceCandidate) {
                        //发送 连接两端的主机的网络地址
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

                peerConnection?.addTrack(videoTrack)
                peerConnection?.createOffer(object : SdpObserver {
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
                }, MediaConstraints())

                launch(Dispatchers.IO) {
                    try {
                        while (true) {
                            recv(peerConnection, byteReadChannel, byteWriteChannel)
                        }
                    } catch (e: Throwable) {
                        socket.close()
                    } finally {
                        peerConnection?.dispose()
                        println("close $socketAddress")
                    }
                }
            }
        }
    }

    private suspend fun recv(peerConnection: PeerConnection?, byteReadChannel: ByteReadChannel, byteWriteChannel: ByteWriteChannel) {
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

            else -> {}
        }
    }


    private fun startForeground() {
        val groupId = "screenRecordingGroup"
        val channelId = "screenRecordingChannel"
        val notificationManager = NotificationManagerCompat.from(context)
        val notificationChannelGroup = NotificationChannelGroupCompat.Builder(groupId)
            .setName("录屏")
            .setDescription("录屏通知组别")
            .build()
        notificationManager.createNotificationChannelGroup(notificationChannelGroup)
        val notificationChannel = NotificationChannelCompat.Builder(channelId, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName("录屏通知渠道")
            .setDescription("录屏通知渠道")
            .setGroup(groupId)
            .build()
        notificationManager.createNotificationChannelsCompat(mutableListOf(notificationChannel))
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("录屏服务正在运行")
            .setSilent(true)
            .setOngoing(true)
            .build()
        startForeground(1, notification)
    }

    companion object {
        @JvmStatic
        fun start(context: Context, screenCaptureIntent: Intent?) {
            val intent = Intent(context, ScreenCaptureService::class.java)
            intent.putExtra("screenCaptureIntent", screenCaptureIntent)
            context.startService(intent)
        }

        @JvmStatic
        fun stop(context: Context) {
            val intent = Intent(context, ScreenCaptureService::class.java)
            context.stopService(intent)
        }
    }
}
