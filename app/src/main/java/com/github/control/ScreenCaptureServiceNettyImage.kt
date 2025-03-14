package com.github.control

import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Environment
import android.os.HandlerThread
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.blankj.utilcode.util.ScreenUtils
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPipeline
import io.netty.channel.SimpleChannelInboundHandler
import java.io.File
import java.io.FileOutputStream


class ScreenCaptureServiceNettyImage : ScreenCaptureService0() {
    private var ctx: ChannelHandlerContext? = null

    private val handlerThread = HandlerThread("screenshot", 10).apply {
        start()
    }
    var count = 0
    private val imageReader = ImageReader.newInstance(ScreenUtils.getScreenWidth(), ScreenUtils.getScreenHeight(), PixelFormat.RGBA_8888, 2)
        .apply {
            val data = ByteArray((ScreenUtils.getScreenWidth() + 8) * ScreenUtils.getScreenHeight() * 4)

            setOnImageAvailableListener({ reader ->

                val img = reader.acquireLatestImage()
                val planes = img.planes
                val buffer = planes[0].buffer
                if (buffer == null) return@setOnImageAvailableListener


                val width = img.width
                val height = img.height
                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride
                val rowPadding = rowStride - pixelStride * width
//                val newData = ByteArray(width * height * 4)

                val bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888)
                bitmap.copyPixelsFromBuffer(buffer)

//                var offset = 0
//                val bitmap = Bitmap.createBitmap(resources.displayMetrics, width, height, Bitmap.Config.ARGB_8888)
//                val buffer = planes[0].buffer
//                for (i in 0 until height) {
//                    for (j in 0 until width) {
//                        var pixel = 0
//                        pixel = (pixel or (buffer.get(offset)
//                            .toInt() and 0xff shl 16))   // R
//                        pixel = (pixel or (buffer.get(offset + 1)
//                            .toInt() and 0xff shl 8))  // G
//                        pixel = (pixel or (buffer.get(offset + 2)
//                            .toInt() and 0xff))       // B
//                        pixel = (pixel or (buffer.get(offset + 3)
//                            .toInt() and 0xff shl 24)) // A
//                        bitmap.setPixel(j, i, pixel)
//                        offset += pixelStride
//                    }
//                    offset += rowPadding
//                }


//                val name = "/myscreen$count.png"
//                count++
//                val file = File(cacheDir, name)
//                val fos = FileOutputStream(file)
//                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos)
//                Log.i("TAG", "image saved in ${Environment.getExternalStorageDirectory()}$name")
//                fos.close()

                bitmap.recycle()
                img.close()
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