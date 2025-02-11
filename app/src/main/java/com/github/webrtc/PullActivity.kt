package com.github.webrtc

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.github.webrtc.databinding.ActivityPullBinding
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoTrack
import java.nio.charset.Charset


class PullActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPullBinding
    private val mainScope = MainScope()
    private var eventLoopGroup: NioEventLoopGroup? = null
    private var inetHost = "192.168.31.167"
    private var inetPort = 8888

    private val context = this

    //EglBase`
    // EglBase
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
    val rtcConfig = PeerConnection.RTCConfiguration(listOf()).apply {
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
        binding.SurfaceViewRenderer.init(eglBaseContext, null)
        initService()
    }


    private fun initService() {
        mainScope.launch(Dispatchers.IO) {
            runCatching {
                eventLoopGroup = NioEventLoopGroup()
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

                                    @SuppressLint("ClickableViewAccessibility")
                                    //信道激活消息
                                    override fun channelActive(ctx: ChannelHandlerContext) {
                                        super.channelActive(ctx)


                                        //创建对等连接
                                        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, object : SimplePeerConnectionObserver() {

                                            override fun onAddTrack(rtpReceiver: RtpReceiver, mediaStreams: Array<out MediaStream>) {
                                                super.onAddTrack(rtpReceiver, mediaStreams)
                                                val track = rtpReceiver.track()
                                                when (track) {
                                                    is VideoTrack -> {
                                                        track.addSink(binding.SurfaceViewRenderer)
                                                    }
                                                }
                                            }

                                            override fun onIceCandidate(iceCandidate: IceCandidate) {
                                                super.onIceCandidate(iceCandidate)
                                                val buffer = PooledByteBufAllocator.DEFAULT.buffer(
                                                    4 * Int.SIZE_BYTES + iceCandidate.sdpMid.length + iceCandidate.sdp.length
                                                )
                                                buffer.writeInt(1)
                                                buffer.writeInt(iceCandidate.sdpMid.length)
                                                buffer.writeCharSequence(iceCandidate.sdpMid, Charset.defaultCharset())
                                                buffer.writeInt(iceCandidate.sdpMLineIndex)
                                                buffer.writeInt(iceCandidate.sdp.length)
                                                buffer.writeCharSequence(iceCandidate.sdp, Charset.defaultCharset())
                                                ctx.writeAndFlush(buffer)

                                            }
                                        })

                                    }

                                    //信道不活跃消息
                                    override fun channelInactive(ctx: ChannelHandlerContext?) {
                                        super.channelInactive(ctx)
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
                                                val sdpMid = byteBuf.readCharSequence(byteBuf.readInt(), Charset.defaultCharset()).toString()
                                                val sdpMLineIndex = byteBuf.readInt()
                                                val sdp = byteBuf.readCharSequence(byteBuf.readInt(), Charset.defaultCharset()).toString()
                                                val iceCandidate = IceCandidate(sdpMid, sdpMLineIndex, sdp)
                                                peerConnection?.addIceCandidate(iceCandidate)
                                            }

                                            3 -> {
                                                val type = byteBuf.readCharSequence(byteBuf.readInt(), Charset.defaultCharset()).toString()
                                                val description = byteBuf.readCharSequence(byteBuf.readInt(), Charset.defaultCharset()).toString()
                                                val sdp = SessionDescription(SessionDescription.Type.valueOf(type), description)

                                                peerConnection?.setRemoteDescription(SimpleSdpObserver(), sdp)
                                                peerConnection?.createAnswer(object : SimpleSdpObserver() {
                                                    override fun onCreateSuccess(description: SessionDescription) {
                                                        peerConnection?.setLocalDescription(SimpleSdpObserver(), description)

                                                        val buffer = PooledByteBufAllocator.DEFAULT.buffer(
                                                            3 * Int.SIZE_BYTES + description.type.name.length + description.description.length
                                                        )
                                                        buffer.writeInt(2)
                                                        buffer.writeInt(description.type.name.length)
                                                        buffer.writeCharSequence(description.type.name, Charset.defaultCharset())
                                                        buffer.writeInt(description.description.length)
                                                        buffer.writeCharSequence(description.description, Charset.defaultCharset())
                                                        ctx.writeAndFlush(buffer)

                                                    }
                                                }, MediaConstraints())
                                            }


                                            else -> {}
                                        }


                                    }
                                })
                        }
                    })
                    .connect(inetHost, inetPort).sync()
                    .channel()
                    .closeFuture().sync()
            }.onFailure {
                it.printStackTrace()
                eventLoopGroup?.shutdownGracefully()
                mainScope.launch(Dispatchers.Main) {
                    AlertDialog.Builder(context)
                        .setTitle("${it.message}")
                        .setNegativeButton("ok") { _, _ ->

                        }
                        .show()
                }
            }
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        binding.SurfaceViewRenderer.clearImage()
        binding.SurfaceViewRenderer.release()
        eglBase.release()
        eventLoopGroup?.shutdownGracefully()
        mainScope.cancel()
    }
}