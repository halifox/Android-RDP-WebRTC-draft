package com.github.control

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.github.control.databinding.ActivityPullBinding
import io.netty.bootstrap.Bootstrap
import io.netty.channel.ChannelInitializer
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.LengthFieldBasedFrameDecoder
import io.netty.handler.codec.LengthFieldPrepender
import io.netty.handler.codec.bytes.ByteArrayDecoder
import io.netty.handler.codec.bytes.ByteArrayEncoder
import kotlinx.coroutines.channels.Channel
import org.webrtc.EglBase
import org.webrtc.HardwareVideoDecoderFactory
import org.webrtc.HardwareVideoEncoderFactory
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RendererCommon
import org.webrtc.SurfaceTextureHelper


class PullActivityWebRTC : AppCompatActivity() {
    private val context = this
    private lateinit var binding: ActivityPullBinding

    private val eventLoopGroup = NioEventLoopGroup()
    private val eventLoopGroup2 = NioEventLoopGroup()
    private val inetHost by lazy { intent.getStringExtra("host") }


    private val eglBase = EglBase.create()
    private val eglBaseContext = eglBase.getEglBaseContext()
    private val peerConnectionFactory = PeerConnectionFactory.builder()
        .setOptions(PeerConnectionFactory.Options())
        .setVideoEncoderFactory(HardwareVideoEncoderFactory(eglBaseContext, true, true))
        .setVideoDecoderFactory(HardwareVideoDecoderFactory(eglBaseContext))
        .createPeerConnectionFactory()
    private val surfaceTextureHelper = SurfaceTextureHelper.create("surface_texture_thread", eglBaseContext, true)
    private val videoSource = peerConnectionFactory.createVideoSource(true, true)
    private val videoTrack = peerConnectionFactory.createVideoTrack("local_video_track", videoSource)
    private val rtcConfig = PeerConnection.RTCConfiguration(listOf())


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPullBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.SurfaceViewRenderer.init(eglBaseContext, null)
        binding.SurfaceViewRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT, RendererCommon.ScalingType.SCALE_ASPECT_FIT);
        initScreenCaptureService()
    }

    private val eventChannel = Channel<Event>(Channel.BUFFERED)

    override fun onStart() {
        super.onStart()
        binding.SurfaceViewRenderer.setOnTouchListener { _, event ->
            eventChannel.trySend(TouchEvent(event, binding.SurfaceViewRenderer.width, binding.SurfaceViewRenderer.height))
            true
        }

        binding.back.setOnClickListener {
            eventChannel.trySend(GlobalActionEvent(AccessibilityService.GLOBAL_ACTION_BACK))
        }
        binding.home.setOnClickListener {
            eventChannel.trySend(GlobalActionEvent(AccessibilityService.GLOBAL_ACTION_HOME))
        }
        binding.recents.setOnClickListener {
            eventChannel.trySend(GlobalActionEvent(AccessibilityService.GLOBAL_ACTION_RECENTS))
        }
    }


    private fun initScreenCaptureService() {
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
                        .addLast(ControlInboundHandler(eventChannel = eventChannel))
                        .addLast(PeerConnectionInboundHandler(peerConnectionFactory, videoSink= binding.SurfaceViewRenderer))

                }
            })
            .connect(inetHost, 40000)
            .apply {
                addListener { future ->
                    if (future.isSuccess) {
                        println("connect ScreenCaptureService");
                    } else {
                        println("Failed to connect ScreenCaptureService");
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
        binding.SurfaceViewRenderer.clearImage()
        binding.SurfaceViewRenderer.release()
        eventLoopGroup.shutdownGracefully()
        eventLoopGroup2.shutdownGracefully()
        eglBase.release()
    }

    companion object {
        @JvmStatic
        fun start(context: Context, host: String) {
            val starter = Intent(context, PullActivityWebRTC::class.java)
                .putExtra("host", host)
            context.startActivity(starter)
        }
    }
}