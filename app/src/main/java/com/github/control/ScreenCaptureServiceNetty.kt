package com.github.control

import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.widget.Toast
import com.blankj.utilcode.util.ScreenUtils
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPipeline
import io.netty.channel.SimpleChannelInboundHandler


class ScreenCaptureServiceNetty : ScreenCaptureService0() {
    private val context = this

    private var virtualDisplay: VirtualDisplay? = null

    private val videoFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, ScreenUtils.getScreenWidth(), ScreenUtils.getScreenHeight())
        .apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)  // 硬件加速
            setInteger(MediaFormat.KEY_BIT_RATE, 1_000_000)  // 足够的比特率，避免过低影响质量
            setInteger(MediaFormat.KEY_FRAME_RATE, 60)  // 高帧率减少延迟
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)  // 每帧都是 I 帧
        }


    private val mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        .apply {
            configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        }

    override fun onDestroy() {
        super.onDestroy()
        virtualDisplay?.release()
        mediaCodec.stop()
        mediaCodec.reset()
    }

    override fun initChannel(pipeline: ChannelPipeline) {
        pipeline.addLast(object : SimpleChannelInboundHandler<ByteArray>() {
            override fun channelActive(ctx: ChannelHandlerContext) {
                try {
                    virtualDisplay = mediaProjection!!.createVirtualDisplay(
                        "VirtualScreen", ScreenUtils.getScreenWidth(), ScreenUtils.getScreenHeight(), ScreenUtils.getScreenDensityDpi(),
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
//                        val sps = mediaCodec.getOutputFormat()
//                            .getByteBuffer("csd-0");
//                        val pps = mediaCodec.getOutputFormat()
//                            .getByteBuffer("csd-1");
                            ctx.writeAndFlush(outData)
                            mediaCodec.releaseOutputBuffer(index, false);
                        }
                    }
                }.start()
            }

            override fun channelInactive(ctx: ChannelHandlerContext) {
                this@ScreenCaptureServiceNetty.stopSelf()
            }

            override fun channelRead0(ctx: ChannelHandlerContext, msg: ByteArray) {

            }
        })
    }


}