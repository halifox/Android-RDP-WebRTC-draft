package com.github.control

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationChannelGroupCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.LifecycleService
import com.blankj.utilcode.util.ScreenUtils
import com.github.control.scrcpy.Controller
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelInitializer
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.LengthFieldBasedFrameDecoder
import io.netty.handler.codec.LengthFieldPrepender
import io.netty.handler.codec.bytes.ByteArrayDecoder
import io.netty.handler.codec.bytes.ByteArrayEncoder
import org.koin.android.ext.android.inject
import org.webrtc.EglBase
import org.webrtc.HardwareVideoDecoderFactory
import org.webrtc.HardwareVideoEncoderFactory
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.ScreenCapturerAndroid
import org.webrtc.SurfaceTextureHelper


class ScreenCaptureServiceWebRTC : LifecycleService() {
    private val context = this

    private val bossGroup = NioEventLoopGroup()
    private val workerGroup = NioEventLoopGroup()
    private val screenHeight = ScreenUtils.getScreenHeight()
    private val screenWidth = ScreenUtils.getScreenWidth()

    private val eglBase = EglBase.create()
    private val eglBaseContext = eglBase.getEglBaseContext()
    private val peerConnectionFactory = PeerConnectionFactory.builder()
        .setVideoEncoderFactory(HardwareVideoEncoderFactory(eglBaseContext, true, true))
        .setVideoDecoderFactory(HardwareVideoDecoderFactory(eglBaseContext))
        .createPeerConnectionFactory()
    private val surfaceTextureHelper = SurfaceTextureHelper.create("surface_texture_thread", eglBaseContext, true)
    private val videoSource = peerConnectionFactory.createVideoSource(true, true)
    private val videoTrack = peerConnectionFactory.createVideoTrack("local_video_track", videoSource)
    private val rtcConfig = PeerConnection.RTCConfiguration(emptyList())


    private val nsdManager by inject<NsdManager>()

    private val registrationListener = object : NsdManager.RegistrationListener {
        private val TAG = "NsdManager"
        override fun onServiceRegistered(nsdServiceInfo: NsdServiceInfo) {
            Log.d(TAG, "onServiceRegistered:注册成功 ")
        }

        override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            Log.d(TAG, "onRegistrationFailed:注册失败 ")
        }

        override fun onServiceUnregistered(arg0: NsdServiceInfo) {
            Log.d(TAG, "onServiceUnregistered:取消注册 ")
        }

        override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            Log.d(TAG, "onUnregistrationFailed:取消注册失败 ")
        }
    }


    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate: ")
        startForeground()
        startServer()

        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "control"
            serviceType = "_control._tcp."
            port = 40000
        }
        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
    }


    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy: ")

        stopServer()
        stopForeground(STOP_FOREGROUND_REMOVE)

        nsdManager.unregisterService(registrationListener)
        screenCapturerAndroid?.stopCapture()
        screenCapturerAndroid?.dispose()
    }

    private var screenCapturerAndroid: ScreenCapturerAndroid? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: ")
        val screenCaptureIntent = intent?.getParcelableExtra<Intent?>(SCREEN_CAPTURE_INTENT)
        if (screenCaptureIntent != null) {
            screenCapturerAndroid = ScreenCapturerAndroid(screenCaptureIntent, object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.d(TAG, "onStop: ")
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
        val controller by inject<Controller>()

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
                        .addLast(ControlInboundHandler(controller = controller))
                        .addLast(PeerConnectionInboundHandler(peerConnectionFactory, videoTrack, isOffer = true))
                }
            })
            .bind(40000)
            .apply {
                addListener { future ->
                    if (future.isSuccess) {
                        println("Server started on port 40000");
                    } else {
                        println("Failed to start server");
                        future.cause()
                            .printStackTrace();
                    }
                }
            }//
            .channel()//
            .closeFuture()
            .apply {
                addListener {
                    bossGroup.shutdownGracefully()
                    workerGroup.shutdownGracefully()
                }
            }
    }

    private fun stopServer() {
        workerGroup.shutdownGracefully()
        bossGroup.shutdownGracefully()
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
        private const val TAG = "ScreenCaptureService"
        private const val SCREEN_CAPTURE_INTENT = "SCREEN_CAPTURE_INTENT"

        @JvmStatic
        fun start(context: Context, screenCaptureIntent: Intent?) {
            val intent = Intent(context, ScreenCaptureServiceWebRTC::class.java)
                .putExtra(SCREEN_CAPTURE_INTENT, screenCaptureIntent)
            context.startService(intent)
        }

        @JvmStatic
        fun stop(context: Context) {
            val intent = Intent(context, ScreenCaptureServiceWebRTC::class.java)
            context.stopService(intent)
        }

    }
}