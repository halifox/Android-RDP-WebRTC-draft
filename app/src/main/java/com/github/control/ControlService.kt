package com.github.control

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.accessibility.AccessibilityEvent
import com.github.control.anydesk.MotionEventHandler
import com.github.control.scrcpy.Controller
import com.github.control.scrcpy.Position
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
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel


class ControlService : AccessibilityService() {
    companion object {
        const val TYPE_GLOBAL_ACTION = 1
        const val TYPE_MOTION_EVENT = 2
    }

    private val context = this
    private val controller = Controller()
    private val motionEventHandler = MotionEventHandler(this)
    private val lifecycleScope = MainScope()

    private val bossGroup = NioEventLoopGroup()
    private val workerGroup = NioEventLoopGroup()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
        }
    }

    override fun onCreate() {
        super.onCreate()
        registerReceiver(receiver, IntentFilter(Intent.ACTION_CONFIGURATION_CHANGED))
        controller.setInjectorDelegate(motionEventHandler)
        startServer()
    }


    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(receiver)
        lifecycleScope.cancel()
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
                            override fun channelActive(ctx: ChannelHandlerContext) {
                            }

                            override fun channelInactive(ctx: ChannelHandlerContext) {
                            }

                            override fun channelRead0(ctx: ChannelHandlerContext, msg: ByteArray) {
                                val byteBuf = PooledByteBufAllocator.DEFAULT.buffer(msg.size)
                                byteBuf.writeBytes(msg)
                                val type = byteBuf.readInt()
                                when (type) {
                                    TYPE_MOTION_EVENT -> {
                                        val action = byteBuf.readInt()
                                        val pointerId = byteBuf.readInt()
                                        val x = byteBuf.readInt()
                                        val y = byteBuf.readInt()
                                        val screenWidth = byteBuf.readInt()
                                        val screenHeight = byteBuf.readInt()
                                        val pressure = byteBuf.readFloat()
                                        val actionButton = byteBuf.readInt()
                                        val buttons = byteBuf.readInt()
                                        val position = Position(x, y, screenWidth, screenHeight)
                                        controller.injectTouch(action, pointerId, position, pressure, actionButton, buttons)
                                    }

                                    TYPE_GLOBAL_ACTION -> {
                                        val action = byteBuf.readInt()
                                        performGlobalAction(action)
                                    }
                                }
                            }
                        })
                }
            })
            .bind(40001)
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

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
}

