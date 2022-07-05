package com.example.test_webrtc

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
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
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule

class PullActivity : AppCompatActivity() {
    private val mainScope = MainScope()

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pull)
        initService()

        val surfaceViewRenderer = findViewById<SurfaceViewRenderer>(R.id.srv)
        surfaceViewRenderer.setOnTouchListener { v, event ->

            return@setOnTouchListener true
        }
    }


    private fun initService() {
        mainScope.launch(Dispatchers.IO) {
            val eventLoopGroup = NioEventLoopGroup()
            try {
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

                                    override fun channelActive(ctx: ChannelHandlerContext?) {
                                        super.channelActive(ctx)
                                        //EglBase
                                        val eglBase = EglBase.create()
                                        val eglBaseContext = eglBase.getEglBaseContext()
                                        //初始化
                                        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(applicationContext).createInitializationOptions())
                                        //视频编码器
                                        val encoderFactory = DefaultVideoEncoderFactory(eglBaseContext, true, true)
                                        //视频解码器
                                        val decoderFactory = DefaultVideoDecoderFactory(eglBaseContext)
                                        //音频设备模块
                                        val audioDeviceModule = JavaAudioDeviceModule.builder(this@PullActivity).createAudioDeviceModule()
                                        val options = PeerConnectionFactory.Options()
                                        //对等连接Factory
                                        val peerConnectionFactory = PeerConnectionFactory.builder()
                                                .setOptions(options)
                                                .setVideoEncoderFactory(encoderFactory)
                                                .setVideoDecoderFactory(decoderFactory)
                                                .setAudioDeviceModule(audioDeviceModule)
                                                .createPeerConnectionFactory()

                                        val surfaceViewRenderer = findViewById<SurfaceViewRenderer>(R.id.srv)
                                        runOnUiThread {
                                            surfaceViewRenderer.init(eglBaseContext, null)
                                        }


                                        val rtcConfig = PeerConnection.RTCConfiguration(emptyList())
                                        rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED
                                        rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
                                        rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
                                        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
                                        rtcConfig.keyType = PeerConnection.KeyType.ECDSA


                                        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, object : SimplePeerConnectionObserver("pull") {
                                            override fun onAddStream(mediaStream: MediaStream) {
                                                super.onAddStream(mediaStream)
                                                if (mediaStream.videoTracks.isNotEmpty()) {
                                                    //显示
                                                    mediaStream.videoTracks[0].addSink(surfaceViewRenderer)
                                                }
                                                if (mediaStream.audioTracks.isNotEmpty()) {

                                                    mediaStream.audioTracks[0]
                                                }
                                            }

                                            override fun onIceCandidate(iceCandidate: IceCandidate) {
                                                super.onIceCandidate(iceCandidate)
                                                ctx?.writeAndFlush(WebrtcMessage(type = "ice", iceCandidate = iceCandidate).toString())
                                            }
                                        })

                                    }

                                    override fun channelInactive(ctx: ChannelHandlerContext?) {
                                        super.channelInactive(ctx)
                                        peerConnection?.dispose()
                                        peerConnection = null
                                    }

                                    override fun channelRead0(ctx: ChannelHandlerContext, msg: String) {
                                        val webrtcMessage = WebrtcMessage(msg)
                                        Log.d(TAG, "channelRead0: ${msg}")
                                        when (webrtcMessage.type) {
                                            "sdp" -> {
                                                peerConnection?.setRemoteDescription(SimpleSdpObserver("pull-setRemoteDescription"), webrtcMessage.description)
                                                peerConnection?.createAnswer(object : SimpleSdpObserver("pull-createAnswer") {
                                                    override fun onCreateSuccess(description: SessionDescription) {
                                                        peerConnection?.setLocalDescription(SimpleSdpObserver("pull-setLocalDescription"), description)
                                                        ctx.writeAndFlush(WebrtcMessage(type = "sdp", description = description).toString())
                                                    }
                                                }, MediaConstraints())
                                            }
                                            "ice" -> {
                                                peerConnection?.addIceCandidate(webrtcMessage.iceCandidate)
                                            }
                                            "move" -> {}
                                        }


                                    }
                                })
                            }
                        })
                        .connect("192.168.8.102", 8888).sync().channel().closeFuture().sync()
            } catch (e: Exception) {
                e.printStackTrace()
                eventLoopGroup.shutdownGracefully()
            }

        }
    }


    override fun onDestroy() {
        super.onDestroy()
        mainScope.cancel()

    }

    companion object {
        private const val TAG = "PullActivity"
    }
}