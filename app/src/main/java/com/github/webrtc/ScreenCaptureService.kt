package com.github.webrtc

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
import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.PooledByteBufAllocator
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.LengthFieldBasedFrameDecoder
import io.netty.handler.codec.LengthFieldPrepender
import io.netty.handler.codec.bytes.ByteArrayDecoder
import io.netty.handler.codec.bytes.ByteArrayEncoder
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.ScreenCapturerAndroid
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import java.nio.charset.Charset


class ScreenCaptureService : Service() {
    private val context = this

    private val bossGroup = NioEventLoopGroup()
    private val workerGroup = NioEventLoopGroup()
    private val screenHeight = ScreenUtils.getScreenHeight()
    private val screenWidth = ScreenUtils.getScreenWidth()

    private val eglBase = EglBase.create()
    private val eglBaseContext = eglBase.getEglBaseContext()
    private val peerConnectionFactory = PeerConnectionFactory.builder()
        .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBaseContext, true, true))
        .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBaseContext))
        .createPeerConnectionFactory()
    private val surfaceTextureHelper = SurfaceTextureHelper.create("surface_texture_thread", eglBaseContext, true)
    private val videoSource = peerConnectionFactory.createVideoSource(true, true)
    private val videoTrack = peerConnectionFactory.createVideoTrack("local_video_track", videoSource)
    private val rtcConfig = PeerConnection.RTCConfiguration(emptyList()).apply {
        tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED
        bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
        rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
        continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        keyType = PeerConnection.KeyType.ECDSA
    }

    override fun onCreate() {
        super.onCreate()
        startForeground()
        startServer()
    }

    override fun onDestroy() {
        super.onDestroy()
        workerGroup.shutdownGracefully()
        bossGroup.shutdownGracefully()
        eglBase.release()
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val screenCaptureIntent = intent?.getParcelableExtra<Intent?>(SCREEN_CAPTURE_INTENT)
        if (screenCaptureIntent != null) {
            ScreenCapturerAndroid(screenCaptureIntent, object : MediaProjection.Callback() {
                override fun onStop() {
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
        ServerBootstrap()
            .group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel::class.java)
            .childHandler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(channel: SocketChannel) {
                    channel.pipeline()
                        .addLast(LengthFieldBasedFrameDecoder(Int.MAX_VALUE, 0, 4, 0, 4))
                        .addLast(LengthFieldPrepender(4))
                        .addLast(ByteArrayDecoder())
                        .addLast(ByteArrayEncoder())
                        .addLast(object : SimpleChannelInboundHandler<ByteArray>() {
                            private var peerConnection: PeerConnection? = null
                            override fun channelActive(ctx: ChannelHandlerContext) {
                                peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, object : EmptyPeerConnectionObserver() {
                                    override fun onIceCandidate(iceCandidate: IceCandidate) {
                                        send(ctx, iceCandidate)
                                    }
                                })?.apply {
                                    addTrack(videoTrack)
                                    createOffer(object : EmptySdpObserver() {
                                        override fun onCreateSuccess(description: SessionDescription) {
                                            peerConnection?.setLocalDescription(EmptySdpObserver(), description)
                                            send(ctx, description)
                                        }
                                    }, MediaConstraints())
                                }
                            }

                            override fun channelInactive(ctx: ChannelHandlerContext) {
                                peerConnection?.dispose()
                                peerConnection = null
                            }

                            override fun channelRead0(ctx: ChannelHandlerContext, msg: ByteArray) {
                                val byteBuf = PooledByteBufAllocator.DEFAULT.buffer(msg.size)
                                byteBuf.writeBytes(msg)
                                val type = byteBuf.readInt()
                                when (type) {
                                    1 -> {
                                        val sdpMid = byteBuf.readCharSequence(byteBuf.readInt(), Charset.defaultCharset()).toString()
                                        val sdpMLineIndex = byteBuf.readInt()
                                        val sdp = byteBuf.readCharSequence(byteBuf.readInt(), Charset.defaultCharset()).toString()
                                        val iceCandidate = IceCandidate(sdpMid, sdpMLineIndex, sdp)
                                        peerConnection?.addIceCandidate(iceCandidate)
                                    }

                                    2 -> {
                                        val type = byteBuf.readCharSequence(byteBuf.readInt(), Charset.defaultCharset()).toString()
                                        val description = byteBuf.readCharSequence(byteBuf.readInt(), Charset.defaultCharset()).toString()
                                        val sdp = SessionDescription(SessionDescription.Type.valueOf(type), description)
                                        peerConnection?.setRemoteDescription(EmptySdpObserver(), sdp)
                                        peerConnection?.createAnswer(object : EmptySdpObserver() {}, MediaConstraints())
                                    }
                                }
                            }
                        })
                }
            })
            .bind(40000).apply {
                addListener { future ->
                    if (future.isSuccess) {
                        println("Server started on port 8888");
                    } else {
                        println("Failed to start server");
                        future.cause().printStackTrace();
                    }
                }
            }//
            .channel()//
            .closeFuture().apply {
                addListener {
                    bossGroup.shutdownGracefully()
                    workerGroup.shutdownGracefully()
                }
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


    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    companion object {
        private const val SCREEN_CAPTURE_INTENT = "SCREEN_CAPTURE_INTENT"

        @JvmStatic
        fun start(context: Context, screenCaptureIntent: Intent?) {
            val intent = Intent(context, ScreenCaptureService::class.java)
                .putExtra(SCREEN_CAPTURE_INTENT, screenCaptureIntent)
            context.startService(intent)
        }

        @JvmStatic
        fun stop(context: Context) {
            val intent = Intent(context, ScreenCaptureService::class.java)
            context.stopService(intent)
        }

    }
}