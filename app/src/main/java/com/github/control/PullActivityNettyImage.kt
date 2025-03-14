package com.github.control

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.SurfaceHolder
import androidx.appcompat.app.AppCompatActivity
import com.blankj.utilcode.util.ScreenUtils
import com.github.control.databinding.ActivityPullTcpBinding
import com.github.control.databinding.ActivityPullTcpImageBinding
import io.netty.bootstrap.Bootstrap
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.LengthFieldBasedFrameDecoder
import io.netty.handler.codec.LengthFieldPrepender
import io.netty.handler.codec.bytes.ByteArrayDecoder
import io.netty.handler.codec.bytes.ByteArrayEncoder
import kotlinx.coroutines.channels.Channel


class PullActivityNettyImage : AppCompatActivity() {
    private val context = this
    private lateinit var binding: ActivityPullTcpImageBinding

    private val eventLoopGroup = NioEventLoopGroup()
    private val inetHost by lazy { intent.getStringExtra("host") }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPullTcpImageBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initService()
    }

    private val eventChannel = Channel<MotionEvent>(Channel.BUFFERED)
    private val actionChannel = Channel<Int>(Channel.BUFFERED)

    override fun onStart() {
        super.onStart()
        binding.SurfaceView.setOnTouchListener { _, event ->
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
    }


    private fun initService() {
        Bootstrap()
            .group(eventLoopGroup)
            .channel(NioSocketChannel::class.java)
            .handler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(channel: SocketChannel) {
                    channel.pipeline()
                        .addLast(LengthFieldBasedFrameDecoder(Int.MAX_VALUE, 0, 4, 0, 4))
                        .addLast(LengthFieldPrepender(4))
                        .addLast(ByteArrayDecoder())
                        .addLast(ByteArrayEncoder())
                        .addLast(object : SimpleChannelInboundHandler<ByteArray>() {

                            override fun channelActive(ctx: ChannelHandlerContext) {
                            }

                            //信道不活跃消息
                            override fun channelInactive(ctx: ChannelHandlerContext?) {
                            }


                            //信道读消息
                            override fun channelRead0(ctx: ChannelHandlerContext, data: ByteArray) {
                                Log.d("TAG", "data:$data ")
                                // 将data转换为Bitmap并设置到ImageView
                                val bitmap = Bitmap.createBitmap(1080, 2400, Bitmap.Config.ARGB_8888)
                                val intArray = IntArray(1080 * 2400)

                                // 将ByteArray中的RGBA数据转为IntArray
                                for (i in intArray.indices) {
                                    val baseIndex = i * 4
                                    val r = data[baseIndex].toInt() and 0xFF
                                    val g = data[baseIndex + 1].toInt() and 0xFF
                                    val b = data[baseIndex + 2].toInt() and 0xFF
                                    val a = data[baseIndex + 3].toInt() and 0xFF
                                    intArray[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
                                }

                                // 将像素数据设置到Bitmap中
                                bitmap.setPixels(intArray, 0, 1080, 0, 0, 1080, 2400)

                                runOnUiThread {
                                    Log.d("TAG", "bitmap:${bitmap} ")
                                    binding.SurfaceView.setImageBitmap(bitmap)
                                }
//                                val canvas = binding.SurfaceView.holder.lockCanvas()
//                                canvas.drawBitmap(bitmap, 0f, 0f, null)
//                                binding.SurfaceView.holder.unlockCanvasAndPost(canvas)
                            }
                        })
                }
            })
            .connect(inetHost, 40000)
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
            }
            .channel()//
            .closeFuture()
            .apply {
                addListener {
                    eventLoopGroup.shutdownGracefully()
                }
            }
    }


    override fun onDestroy() {
        super.onDestroy()
        eventLoopGroup.shutdownGracefully()
    }
}