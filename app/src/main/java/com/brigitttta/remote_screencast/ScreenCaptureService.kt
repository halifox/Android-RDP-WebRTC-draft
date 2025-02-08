package com.brigitttta.remote_screencast

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.os.IBinder
import android.util.Size
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationChannelGroupCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.blankj.utilcode.util.ScreenUtils
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.LengthFieldBasedFrameDecoder
import io.netty.handler.codec.LengthFieldPrepender
import io.netty.handler.codec.string.StringDecoder
import io.netty.handler.codec.string.StringEncoder
import io.netty.util.CharsetUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
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

class ScreenCaptureService : Service() {
    private val context = this
    private val lifecycleScope = MainScope()

    private val bossGroup = NioEventLoopGroup()
    private val workerGroup = NioEventLoopGroup()
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

    override fun onCreate() {
        super.onCreate()
        startForeground()
        startServer()
    }

    override fun onDestroy() {
        super.onDestroy()
        workerGroup.shutdownGracefully()
        bossGroup.shutdownGracefully()
        lifecycleScope.cancel()
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val screenCaptureIntent = intent?.getParcelableExtra<Intent?>(ScreenCaptureIntent)
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
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                ServerBootstrap()
                    .group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel::class.java)
                    .childHandler(object : ChannelInitializer<SocketChannel>() {
                        override fun initChannel(channel: SocketChannel) {
                            channel.pipeline()
                                .addLast(LengthFieldBasedFrameDecoder(Int.MAX_VALUE, 0, 4, 0, 4))
                                .addLast(LengthFieldPrepender(4))
                                .addLast(StringDecoder(CharsetUtil.UTF_8))
                                .addLast(StringEncoder(CharsetUtil.UTF_8))
                                .addLast(object : SimpleChannelInboundHandler<String>() {
                                    private var peerConnection: PeerConnection? = null

                                    private var downTime = 0L

                                    override fun channelActive(ctx: ChannelHandlerContext) {
                                        createOffer(ctx)
                                    }

                                    override fun channelInactive(ctx: ChannelHandlerContext) {
                                        disposeOffer(ctx)
                                    }

                                    override fun channelRead0(ctx: ChannelHandlerContext, msg: String) {
                                        handlerMsg(ctx, msg)
                                    }

                                    private fun createOffer(ctx: ChannelHandlerContext) {
                                        //发送设备尺寸
                                        ctx.writeAndFlush(WebrtcMessage(type = WebrtcMessage.Type.SIZE, size = Size(screenWidth, screenHeight)).toString())

                                        //RTC配置
                                        val rtcConfig = PeerConnection.RTCConfiguration(emptyList()).apply {
                                            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED
                                            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
                                            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
                                            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
                                            keyType = PeerConnection.KeyType.ECDSA
                                        }

                                        //创建对等连接
                                        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, object : SimplePeerConnectionObserver() {
                                            override fun onIceCandidate(iceCandidate: IceCandidate) {
                                                //发送 连接两端的主机的网络地址
                                                ctx.writeAndFlush(WebrtcMessage(type = WebrtcMessage.Type.ICE, iceCandidate = iceCandidate).toString())
                                            }
                                        })

                                        peerConnection?.addTrack(videoTrack)
                                        peerConnection?.createOffer(object : SimpleSdpObserver() {
                                            override fun onCreateSuccess(description: SessionDescription) {
                                                peerConnection?.setLocalDescription(SimpleSdpObserver(), description)
                                                ctx.writeAndFlush(WebrtcMessage(type = WebrtcMessage.Type.SDP, description = description).toString())
                                            }
                                        }, MediaConstraints())
                                    }

                                    private fun disposeOffer(ctx: ChannelHandlerContext) {
                                        peerConnection?.dispose()
                                        peerConnection = null
                                    }

                                    private fun handlerMsg(ctx: ChannelHandlerContext, msg: String) {
                                        val webrtcMessage = WebrtcMessage(msg)

                                        when (webrtcMessage.type) {
                                            WebrtcMessage.Type.SDP -> {
                                                // 接收远端sdp并将自身sdp发送给远端完成sdp交换
                                                peerConnection?.setRemoteDescription(SimpleSdpObserver(), webrtcMessage.description)
                                                // 只有设置了远端sdp才能createAnswer
                                                peerConnection?.createAnswer(object : SimpleSdpObserver() {
                                                    override fun onCreateSuccess(description: SessionDescription) {
                                                        peerConnection?.setLocalDescription(SimpleSdpObserver(), description)
                                                        ctx.writeAndFlush(WebrtcMessage(type = WebrtcMessage.Type.SDP, description = description).toString())
                                                    }
                                                }, MediaConstraints())
                                            }

                                            WebrtcMessage.Type.ICE -> {
                                                // 接收 连接两端的主机的网络地址
                                                // 发送在PeerConnectionFactory.createPeerConnection.onIceCandidate(iceCandidate: IceCandidate)
                                                peerConnection?.addIceCandidate(webrtcMessage.iceCandidate)
                                            }

                                            else -> {}
                                        }
                                    }

                                })
                        }
                    }).bind(8888).sync().channel().closeFuture().sync()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
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
        const val ScreenCaptureIntent = "screenCaptureIntent"

        @JvmStatic
        fun start(context: Context, screenCaptureIntent: Intent?) {
            val intent = Intent(context, ScreenCaptureService::class.java)
                .putExtra(ScreenCaptureIntent, screenCaptureIntent)
            context.startService(intent)
        }

        @JvmStatic
        fun stop(context: Context) {
            val intent = Intent(context, ScreenCaptureService::class.java)
            context.stopService(intent)
        }

    }
}