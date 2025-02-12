package com.github.control

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import androidx.appcompat.app.AppCompatActivity
import com.github.control.databinding.ActivityPullBinding
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.PooledByteBufAllocator
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
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RendererCommon
import org.webrtc.RtpReceiver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoTrack


class PullActivity : AppCompatActivity() {
    private val context = this
    private lateinit var binding: ActivityPullBinding

    private val eventLoopGroup = NioEventLoopGroup()
    private val eventLoopGroup2 = NioEventLoopGroup()
    private val inetHost by lazy { intent.getStringExtra("host") }


    private val eglBase = EglBase.create()
    private val eglBaseContext = eglBase.getEglBaseContext()
    private val peerConnectionFactory = PeerConnectionFactory.builder()
        .setOptions(PeerConnectionFactory.Options())
        .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBaseContext, true, true))
        .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBaseContext))
        .createPeerConnectionFactory()
    private val surfaceTextureHelper = SurfaceTextureHelper.create("surface_texture_thread", eglBaseContext, true)
    private val videoSource = peerConnectionFactory.createVideoSource(true, true)
    private val videoTrack = peerConnectionFactory.createVideoTrack("local_video_track", videoSource)
    private val rtcConfig = PeerConnection.RTCConfiguration(listOf())
        .apply {
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            keyType = PeerConnection.KeyType.ECDSA
        }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPullBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.SurfaceViewRenderer.init(eglBaseContext, object : RendererCommon.RendererEvents {
            override fun onFirstFrameRendered() {
                Log.d("TAG", "onFirstFrameRendered: ")
            }

            override fun onFrameResolutionChanged(videoWidth: Int, videoHeight: Int, rotation: Int) {
                Log.d("TAG", "onFrameResolutionChanged:videoWidth:${videoWidth} videoHeight:${videoHeight} rotation:${rotation}")
            }
        })
        binding.SurfaceViewRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT,RendererCommon.ScalingType.SCALE_ASPECT_FIT);
        initService()
        initService2()
    }

    private val eventChannel = Channel<MotionEvent>(Channel.BUFFERED)
    private val actionChannel = Channel<Int>(Channel.BUFFERED)

    override fun onStart() {
        super.onStart()
        binding.SurfaceViewRenderer.setOnTouchListener { _, event ->
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
                            private var peerConnection: PeerConnection? = null

                            override fun channelActive(ctx: ChannelHandlerContext) {
                                peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, object : EmptyPeerConnectionObserver() {
                                    override fun onAddTrack(rtpReceiver: RtpReceiver, mediaStreams: Array<out MediaStream>) {
                                        val track = rtpReceiver.track()
                                        when (track) {
                                            is VideoTrack -> {
                                                track.addSink(binding.SurfaceViewRenderer)
                                            }
                                        }
                                    }

                                    override fun onIceCandidate(iceCandidate: IceCandidate) {
                                        send(ctx, iceCandidate)
                                    }
                                })
                            }

                            //信道不活跃消息
                            override fun channelInactive(ctx: ChannelHandlerContext?) {
                                binding.SurfaceViewRenderer.clearImage()
                                peerConnection?.dispose()
                                peerConnection = null
                            }

                            //信道读消息
                            override fun channelRead0(ctx: ChannelHandlerContext, msg: ByteArray) {
                                val byteBuf = PooledByteBufAllocator.DEFAULT.buffer(msg.size)
                                byteBuf.writeBytes(msg)
                                val type = byteBuf.readInt()

                                when (type) {
                                    1 -> {
                                        val ice = readIceCandidate(byteBuf)
                                        peerConnection?.addIceCandidate(ice)
                                    }

                                    2 -> {
                                        val sdp = readSessionDescription(byteBuf)
                                        peerConnection?.setRemoteDescription(EmptySdpObserver(), sdp)
                                        peerConnection?.createAnswer(object : EmptySdpObserver() {
                                            override fun onCreateSuccess(description: SessionDescription) {
                                                peerConnection?.setLocalDescription(EmptySdpObserver(), description)
                                                send(ctx, description)
                                            }
                                        }, MediaConstraints())
                                    }

                                    else -> {}
                                }
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

    private fun initService2() {
        Bootstrap()
            .group(eventLoopGroup2)
            .channel(NioSocketChannel::class.java)
            .handler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(channel: SocketChannel) {
                    channel.pipeline()
                        .addLast(LengthFieldBasedFrameDecoder(Int.MAX_VALUE, 0, 4, 0, 4))
                        .addLast(LengthFieldPrepender(4))
                        .addLast(ByteArrayDecoder())
                        .addLast(ByteArrayEncoder())
                        .addLast(object : SimpleChannelInboundHandler<ByteArray>() {
                            val coroutineScope = MainScope()
                            override fun channelActive(ctx: ChannelHandlerContext) {
                                coroutineScope.launch {
                                    eventChannel.consumeEach { event ->
                                        send(ctx, event)
                                    }
                                }
                                coroutineScope.launch {
                                    actionChannel.consumeEach { action ->
                                        send(ctx, action)
                                    }
                                }

                            }

                            override fun channelInactive(ctx: ChannelHandlerContext?) {
                                coroutineScope.cancel()
                            }

                            override fun channelRead0(ctx: ChannelHandlerContext, msg: ByteArray) {
                            }
                        })
                }
            })
            .connect(inetHost, 40001)
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
                    eventLoopGroup2.shutdownGracefully()
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
}