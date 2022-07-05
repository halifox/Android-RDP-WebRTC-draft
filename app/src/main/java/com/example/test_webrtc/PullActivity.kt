package com.example.test_webrtc

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import io.netty.bootstrap.Bootstrap
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.EventLoopGroup
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule

class PullActivity : AppCompatActivity() {


    private var peerConnection: PeerConnection? = null
    private val mainScope = MainScope()

    var localDescription: SessionDescription? = null
    val iceCandidates = mutableListOf<IceCandidate>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pull)
        initService()
        pull()
    }

    private fun pull() {
        pullCore()
    }

    private fun pullCore() {

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
        val audioDeviceModule = JavaAudioDeviceModule.builder(this).createAudioDeviceModule()
        val options = PeerConnectionFactory.Options()
        //对等连接Factory
        val peerConnectionFactory = PeerConnectionFactory.builder()
                .setOptions(options)
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory)
                .setAudioDeviceModule(audioDeviceModule)
                .createPeerConnectionFactory()

        val surfaceViewRenderer = findViewById<SurfaceViewRenderer>(R.id.srv)
        surfaceViewRenderer.init(eglBaseContext, null)

        val rtcConfig = PeerConnection.RTCConfiguration(emptyList())
        rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED
        rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
        rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        rtcConfig.keyType = PeerConnection.KeyType.ECDSA

        val peerConnectionObserver = object : PeerConnectionObserver() {
            override fun onAddStream(mediaStream: MediaStream) {
                super.onAddStream(mediaStream)
                Log.d("REC", "onAddStream: ")
                if (mediaStream.videoTracks.isNotEmpty()) {
                    //显示
                    mediaStream.videoTracks[0].addSink(surfaceViewRenderer)
                }
                if (mediaStream.audioTracks.isNotEmpty()) {

                }
            }

            override fun onIceCandidate(iceCandidate: IceCandidate) {
                super.onIceCandidate(iceCandidate)
                Log.d("REC", "iceCandidate: ${iceCandidate}")
                iceCandidates.add(iceCandidate)
            }
        }
        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, peerConnectionObserver)
    }

    private fun initService() {
        mainScope.launch(Dispatchers.IO) {
            val eventLoopGroup: EventLoopGroup = NioEventLoopGroup()
            try {
                val bootstrap = Bootstrap()
                bootstrap.group(eventLoopGroup)
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
                                    override fun channelActive(ctx: ChannelHandlerContext?) {
                                        super.channelActive(ctx)
                                    }

                                    override fun channelRead0(ctx: ChannelHandlerContext, msg: String) {
                                        val message = Gson().fromJson(msg, Message::class.java)
                                        peerConnection?.setRemoteDescription(SdpAdapter("localSetRemote"), SessionDescription(SessionDescription.Type.OFFER, message.description?.description))
                                        message.iceCandidates.forEach { iceCandidate ->
                                            peerConnection?.addIceCandidate(IceCandidate(iceCandidate.sdpMid, iceCandidate.sdpMLineIndex, iceCandidate.sdp))
                                        }

                                        peerConnection?.createAnswer(object : SdpAdapter("2") {
                                            override fun onCreateSuccess(description: SessionDescription) {
                                                peerConnection?.setLocalDescription(SdpAdapter("setLocalDescription"), description)
                                                localDescription = description
                                            }
                                        }, MediaConstraints())


                                        mainScope.launch {
                                            while (localDescription == null) {
                                                delay(100)
                                            }
                                            while (iceCandidates.isEmpty()) {
                                                delay(2000)
                                            }
                                            ctx.writeAndFlush(Gson().toJson(Message(localDescription, iceCandidates)))
                                        }
                                    }
                                })
                            }
                        })
                val channelFuture = bootstrap.connect("192.168.8.101", 8888).sync()
                channelFuture.channel().closeFuture().sync()
            } catch (e: Exception) {
                e.printStackTrace()
                eventLoopGroup.shutdownGracefully()
            }

        }
    }

}