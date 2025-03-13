package com.github.control

import android.app.Activity
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Log
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
import io.netty.handler.codec.bytes.ByteArrayDecoder
import io.netty.handler.codec.bytes.ByteArrayEncoder
import org.koin.android.ext.android.inject


class ScreenCaptureServiceNettyImage : Service() {
    private val context = this

    private val bossGroup = NioEventLoopGroup()
    private val workerGroup = NioEventLoopGroup()
    private val handlerThread = HandlerThread("screenshot", 10).apply {
        start()
    }
    private val screenshotHandler = Handler(handlerThread.looper)
    lateinit var mediaProjection: MediaProjection
    lateinit var virtualDisplay: VirtualDisplay
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
        Log.d("TAG", "onCreate: ")
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
        Log.d("TAG", "onDestroy: ")
        workerGroup.shutdownGracefully()
        bossGroup.shutdownGracefully()
        handlerThread.quit()
        nsdManager.unregisterService(registrationListener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("TAG", "onStartCommand: ")
        val screenCaptureIntent = intent?.getParcelableExtra<Intent?>(SCREEN_CAPTURE_INTENT)
        if (screenCaptureIntent != null) {
            mediaProjection = getSystemService(MediaProjectionManager::class.java)!!.getMediaProjection(Activity.RESULT_OK, screenCaptureIntent)
            mediaProjection.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    stopSelf()
                }
            }, null)

            val imageReader = ImageReader.newInstance(ScreenUtils.getScreenWidth(), ScreenUtils.getScreenHeight(), PixelFormat.RGBA_8888, 2)
            imageReader.setOnImageAvailableListener({
                val image = it.acquireLatestImage()
                val planes = image.planes
                val buffer = planes[0].buffer
                val data = ByteArray(buffer.remaining())
                buffer.get(data)
                //todo
                image.close()
            }, screenshotHandler)


            virtualDisplay = mediaProjection.createVirtualDisplay(
                "VirtualScreen", ScreenUtils.getScreenWidth(), ScreenUtils.getScreenHeight(), ScreenUtils.getScreenDensityDpi(),
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, imageReader.surface, null, null
            )

        }
        return super.onStartCommand(intent, flags, startId)
    }

    var ctx: ChannelHandlerContext? = null

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
                            override fun channelActive(ctx: ChannelHandlerContext) {
                                this@ScreenCaptureServiceNettyImage.ctx = ctx
                            }

                            override fun channelInactive(ctx: ChannelHandlerContext) {
                                this@ScreenCaptureServiceNettyImage.ctx = null
                                this@ScreenCaptureServiceNettyImage.stopSelf()
                            }

                            override fun channelRead0(ctx: ChannelHandlerContext, msg: ByteArray) {

                            }
                        })
                }
            })
            .bind(40003)
            .apply {
                addListener { future ->
                    if (future.isSuccess) {
                        println("Server started on port 8888");
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
            val intent = Intent(context, ScreenCaptureServiceNettyImage::class.java)
                .putExtra(SCREEN_CAPTURE_INTENT, screenCaptureIntent)
            context.startService(intent)
        }

        @JvmStatic
        fun stop(context: Context) {
            val intent = Intent(context, ScreenCaptureServiceNettyImage::class.java)
            context.stopService(intent)
        }

    }
}