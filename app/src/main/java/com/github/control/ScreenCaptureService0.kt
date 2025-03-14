package com.github.control

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationChannelGroupCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.LifecycleService
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelPipeline
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.LengthFieldBasedFrameDecoder
import io.netty.handler.codec.LengthFieldPrepender
import io.netty.handler.codec.bytes.ByteArrayDecoder
import io.netty.handler.codec.bytes.ByteArrayEncoder
import org.koin.android.ext.android.inject


abstract class ScreenCaptureService0 : LifecycleService() {
    companion object {
        const val SCREEN_CAPTURE_INTENT = "SCREEN_CAPTURE_INTENT"
        private const val TAG = "ScreenCaptureService0"
        const val PORT = 40000
    }

    private val context = this
    protected var mediaProjection: MediaProjection? = null

    private val mediaProjectionManager by inject<MediaProjectionManager>()
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

    private fun startNsdService() {
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "control"
            serviceType = "_control._tcp."
            port = PORT
        }
        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
    }

    private fun stopNsdService() {
        nsdManager.unregisterService(registrationListener)
    }


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
        Log.d(TAG, "onStartCommand: ")
        val screenCaptureIntent = intent?.getParcelableExtra<Intent?>(SCREEN_CAPTURE_INTENT)
        if (screenCaptureIntent != null) {
            onInitScreenCaptureIntent(screenCaptureIntent)
        }
        return super.onStartCommand(intent, flags, startId)
    }

    val mediaProjectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            stopSelf()
        }
    }

    open fun onInitScreenCaptureIntent(screenCaptureIntent: Intent) {
        mediaProjection = mediaProjectionManager.getMediaProjection(Activity.RESULT_OK, screenCaptureIntent)
            .apply {
                registerCallback(mediaProjectionCallback, null)
                onInitMediaProjection(this)
            }
    }

    open fun onInitMediaProjection(mediaProjection: MediaProjection) {

    }


    abstract fun initChannel(pipeline: ChannelPipeline)

    private val bossGroup = NioEventLoopGroup()
    private val workerGroup = NioEventLoopGroup()
    private fun startNettyServer() {
        ServerBootstrap()
            .group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel::class.java)
            .childHandler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(channel: SocketChannel) {
                    val pipeline = channel.pipeline()
                        .addLast(LengthFieldBasedFrameDecoder(Int.MAX_VALUE, 0, 4, 0, 4))
                        .addLast(LengthFieldPrepender(4))
                        .addLast(ByteArrayDecoder())
                        .addLast(ByteArrayEncoder())
                    initChannel(pipeline)
                }
            })
            .bind(PORT)
            .apply {
                addListener { future ->
                    if (future.isSuccess) {
                        println("Success to start server : $PORT")
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

    fun stopForegroundService() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }
}