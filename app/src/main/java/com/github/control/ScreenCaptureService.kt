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
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleService
import com.blankj.utilcode.util.ScreenUtils
import com.blankj.utilcode.util.ServiceUtils
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
import org.webrtc.PeerConnectionFactory
import org.webrtc.ScreenCapturerAndroid
import org.webrtc.SurfaceTextureHelper


class ScreenCaptureService : LifecycleService() {
    companion object {
        const val SCREEN_CAPTURE_INTENT = "SCREEN_CAPTURE_INTENT"
        private const val TAG = "ScreenCaptureService"

        @JvmStatic
        fun start(context: Context, screenCaptureIntent: Intent) {
            val starter = Intent(context, ScreenCaptureService::class.java)
                .putExtra(SCREEN_CAPTURE_INTENT, screenCaptureIntent)
            context.startService(starter)
        }

        @JvmStatic
        fun stop(context: Context) {
            val starter = Intent(context, ScreenCaptureService::class.java)
            context.stopService(starter)
        }

        @JvmStatic
        fun isServiceRunning(context: Context): Boolean {
            return ServiceUtils.isServiceRunning(ScreenCaptureService::class.java)
        }

    }

    private val context = this
    private val controller by inject<Controller>()

    private val eglBase = EglBase.create()
    private val eglBaseContext = eglBase.getEglBaseContext()
    private val peerConnectionFactory = PeerConnectionFactory.builder()
        .setVideoEncoderFactory(HardwareVideoEncoderFactory(eglBaseContext, true, true))
        .setVideoDecoderFactory(HardwareVideoDecoderFactory(eglBaseContext))
        .createPeerConnectionFactory()
    private val surfaceTextureHelper = SurfaceTextureHelper.create("surface_texture_thread", eglBaseContext, true)
    private val videoSource = peerConnectionFactory.createVideoSource(true, true)
    private val videoTrack = peerConnectionFactory.createVideoTrack("local_video_track", videoSource)


    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate: ")
        startForegroundService()
        startNettyServer()
        startNsdService()
    }


    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy: ")
        stopForegroundService()
        stopNettyServer()
        stopNsdService()
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand:")
        val screenCaptureIntent = intent?.getParcelableExtra<Intent?>(SCREEN_CAPTURE_INTENT)
        if (screenCaptureIntent != null) {
            initScreenCapturerAndroid(screenCaptureIntent)
        }
        return super.onStartCommand(intent, flags, startId)
    }


    private fun initScreenCapturerAndroid(screenCaptureIntent: Intent) {
        ScreenCapturerAndroid(screenCaptureIntent, object : MediaProjection.Callback() {
            override fun onStop() {
                stopSelf()
            }
        }).apply {
            initialize(surfaceTextureHelper, applicationContext, videoSource.capturerObserver)
            startCapture(ScreenUtils.getScreenWidth(), ScreenUtils.getScreenHeight(), 0)
            lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) {
                    stopCapture()
                    dispose()
                }
            })
        }
    }


    /**
     *  Netty
     */
    private val bossGroup = NioEventLoopGroup()
    private val workerGroup = NioEventLoopGroup()
    private fun startNettyServer() {
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
                        println("Success to start server : $40000")
                    } else {
                        println("Failed to start server")
                        future.cause()
                            .printStackTrace()
                    }
                }
            }//
            .channel()//
            .closeFuture()
            .apply {
                addListener {
                    stopNettyServer()
                }
            }
    }

    private fun stopNettyServer() {
        workerGroup.shutdownGracefully()
        bossGroup.shutdownGracefully()
    }

    /**
     * ForegroundService
     */
    private fun startForegroundService() {
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

    private fun stopForegroundService() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    /**
     *  Nsd
     */
    private val nsdManager by inject<NsdManager>()
    private val nsdServiceInfo = NsdServiceInfo().apply {
        serviceName = "control"
        serviceType = "_control._tcp."
        port = 40000
    }
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


    private fun startNsdService() {
        nsdManager.registerService(nsdServiceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
    }

    private fun stopNsdService() {
        nsdManager.unregisterService(registrationListener)
    }
}