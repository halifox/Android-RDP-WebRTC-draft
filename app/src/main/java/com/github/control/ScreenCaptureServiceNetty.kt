package com.github.control

import android.app.Activity
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import android.util.Log
import android.widget.Toast
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


class ScreenCaptureServiceNetty : Service() {
    private val context = this

    private val bossGroup = NioEventLoopGroup()
    private val workerGroup = NioEventLoopGroup()
    private val screenHeight = ScreenUtils.getScreenHeight()
    private val screenWidth = ScreenUtils.getScreenWidth()
    private val screenDensity = ScreenUtils.getScreenDensityDpi();


    override fun onCreate() {
        super.onCreate()
        Log.d("TAG", "onCreate: ")
        startForeground()
        startServer()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("TAG", "onDestroy: ")
        workerGroup.shutdownGracefully()
        bossGroup.shutdownGracefully()
    }

    lateinit var mediaProjection: MediaProjection
    lateinit var virtualDisplay: VirtualDisplay
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("TAG", "onStartCommand: ")
        val screenCaptureIntent = intent?.getParcelableExtra<Intent?>(SCREEN_CAPTURE_INTENT)
        if (screenCaptureIntent != null) {
            mediaProjection = getSystemService(MediaProjectionManager::class.java)!!.getMediaProjection(Activity.RESULT_OK, screenCaptureIntent)
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
                            val videoFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 1280, 800)
                                .apply {
                                    setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)  // 硬件加速
                                    setInteger(MediaFormat.KEY_BIT_RATE, 1_000_000)  // 足够的比特率，避免过低影响质量
                                    setInteger(MediaFormat.KEY_FRAME_RATE, 60)  // 高帧率减少延迟
                                    setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)  // 每帧都是 I 帧
                                }



                            val mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
                                .apply {
                                    configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                                }

                            override fun channelActive(ctx: ChannelHandlerContext) {
                                try {
                                    virtualDisplay = mediaProjection.createVirtualDisplay(
                                        "VirtualScreen", screenWidth, screenHeight, screenDensity,
                                        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, mediaCodec.createInputSurface(), null, null
                                    )
                                } catch (e: java.lang.Exception) {
                                    e.printStackTrace()
                                    Toast.makeText(context, "virtualDisplay创建录屏异常，请退出重试！", Toast.LENGTH_SHORT)
                                        .show()
                                }

                                mediaCodec.start();
                                Thread {
                                    val bufferInfo = MediaCodec.BufferInfo()
                                    while (true) {
                                        val index = mediaCodec.dequeueOutputBuffer(bufferInfo, 10);//超时时间：10微秒
                                        if (index >= 0) {
                                            val buffer = mediaCodec.getOutputBuffer(index)!!
                                            val outData = ByteArray(bufferInfo.size)
                                            buffer.get(outData)
//                                            val sps = mediaCodec.getOutputFormat()
//                                                .getByteBuffer("csd-0");
//                                            val pps = mediaCodec.getOutputFormat()
//                                                .getByteBuffer("csd-1");
                                            ctx.writeAndFlush(outData)
                                            mediaCodec.releaseOutputBuffer(index, false);
                                        }
                                    }
                                }.start()
                            }

                            override fun channelInactive(ctx: ChannelHandlerContext) {
                                try {
                                    mediaCodec.stop()
                                    mediaCodec.reset()
                                    virtualDisplay.release()
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    //有异常，保存失败，弹出提示
                                    Toast.makeText(context, "录屏出现异常，视频保存失败！", Toast.LENGTH_SHORT)
                                        .show()
                                }
                                this@ScreenCaptureServiceNetty.stopSelf()
                            }

                            override fun channelRead0(ctx: ChannelHandlerContext, msg: ByteArray) {

                            }
                        })
                }
            })
            .bind(40002)
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
            val intent = Intent(context, ScreenCaptureServiceNetty::class.java)
                .putExtra(SCREEN_CAPTURE_INTENT, screenCaptureIntent)
            context.startService(intent)
        }

        @JvmStatic
        fun stop(context: Context) {
            val intent = Intent(context, ScreenCaptureServiceNetty::class.java)
            context.stopService(intent)
        }

    }
}