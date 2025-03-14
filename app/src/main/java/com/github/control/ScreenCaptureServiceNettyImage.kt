package com.github.control

import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.HandlerThread
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.blankj.utilcode.util.ScreenUtils
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPipeline
import io.netty.channel.SimpleChannelInboundHandler


class ScreenCaptureServiceNettyImage : ScreenCaptureService0() {
    var ctx: ChannelHandlerContext? = null

    private val handlerThread = HandlerThread("screenshot", 10).apply {
        start()
    }
    private val imageReader = ImageReader.newInstance(ScreenUtils.getScreenWidth(), ScreenUtils.getScreenHeight(), PixelFormat.RGBA_8888, 2)
        .apply {
            setOnImageAvailableListener({
                val image = it.acquireLatestImage()
                val planes = image.planes
                val buffer = planes[0].buffer
                val data = kotlin.ByteArray(buffer.remaining())
                buffer.get(data)
                //todo
                image.close()
            }, android.os.Handler(handlerThread.looper))
        }

    var virtualDisplay: VirtualDisplay? = null

    override fun onInitMediaProjection(mediaProjection: MediaProjection) {
        virtualDisplay = mediaProjection.createVirtualDisplay(
            "VirtualScreen", ScreenUtils.getScreenWidth(), ScreenUtils.getScreenHeight(), ScreenUtils.getScreenDensityDpi(),
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, imageReader.surface, null, null,
        )
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                virtualDisplay?.release()
            }
        })
    }

    override fun initChannel(pipeline: ChannelPipeline) {
        pipeline.addLast(object : SimpleChannelInboundHandler<ByteArray>() {
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


}