package com.brigitttta.remote_screencast

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.updateLayoutParams
import com.brigitttta.remote_screencast.databinding.ActivityPullBinding
import io.netty.bootstrap.Bootstrap
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.LengthFieldBasedFrameDecoder
import io.netty.handler.codec.LengthFieldPrepender
import io.netty.handler.codec.string.StringDecoder
import io.netty.handler.codec.string.StringEncoder
import io.netty.util.CharsetUtil
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
import org.webrtc.audio.JavaAudioDeviceModule


class PullActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPullBinding
    private val mainScope = MainScope()
    private var eventLoopGroup: NioEventLoopGroup? = null
    private var inetHost = "192.168.137.150"
    private var inetPort = 8888

    private val context = this

    //EglBase
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
                            val pipeline = channel.pipeline()
                            //数据分包，组包，粘包
                            pipeline.addLast(LengthFieldBasedFrameDecoder(Int.MAX_VALUE, 0, 4, 0, 4))
                            pipeline.addLast(LengthFieldPrepender(4))
                            pipeline.addLast(StringDecoder(CharsetUtil.UTF_8))
                            pipeline.addLast(StringEncoder(CharsetUtil.UTF_8))
                            pipeline.addLast(object : SimpleChannelInboundHandler<String>() {
                                private var peerConnection: PeerConnection? = null

                                @SuppressLint("ClickableViewAccessibility")
                                //信道激活消息
                                override fun channelActive(ctx: ChannelHandlerContext?) {
                                    super.channelActive(ctx)



                                    //rtc配置
                                    val rtcConfig = PeerConnection.RTCConfiguration(listOf()).apply {
                                        tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED
                                        bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
                                        rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
                                        continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
                                        keyType = PeerConnection.KeyType.ECDSA
//                                            sdpSemantics = PeerConnection.SdpSemantics.PLAN_B
                                    }


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
                                            ctx?.writeAndFlush(WebrtcMessage(type = WebrtcMessage.Type.ICE, iceCandidate = iceCandidate).toString())
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
                                override fun channelRead0(ctx: ChannelHandlerContext, msg: String) {
                                    val webrtcMessage = WebrtcMessage(msg)
                                    when (webrtcMessage.type) {
                                        WebrtcMessage.Type.SDP -> {
                                            peerConnection?.setRemoteDescription(SimpleSdpObserver(), webrtcMessage.description)
                                            peerConnection?.createAnswer(object : SimpleSdpObserver() {
                                                override fun onCreateSuccess(description: SessionDescription) {
                                                    peerConnection?.setLocalDescription(SimpleSdpObserver(), description)
                                                    ctx.writeAndFlush(WebrtcMessage(type = WebrtcMessage.Type.SDP, description = description).toString())
                                                }
                                            }, MediaConstraints())
                                        }

                                        WebrtcMessage.Type.ICE -> {
                                            peerConnection?.addIceCandidate(webrtcMessage.iceCandidate)
                                        }

                                        WebrtcMessage.Type.SIZE -> {
                                            launch(Dispatchers.Main) {
                                                binding.SurfaceViewRenderer.updateLayoutParams<ConstraintLayout.LayoutParams> {
                                                    dimensionRatio = "${webrtcMessage.size?.width}:${webrtcMessage.size?.height}"
                                                }
                                            }
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
        Log.d(TAG, "onDestroy: ")
        binding.SurfaceViewRenderer.clearImage()
        binding.SurfaceViewRenderer.release()
        eglBase.release()
        eventLoopGroup?.shutdownGracefully()
        mainScope.cancel()
    }

    companion object {
        private const val TAG = "PullFragment"
    }
}