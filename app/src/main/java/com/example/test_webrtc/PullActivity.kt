package com.example.test_webrtc

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
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
import kotlinx.coroutines.*
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule

class PullActivity : AppCompatActivity() {


    private val mainScope = MainScope()
    private var peerConnection: PeerConnection? = null
    private var peerConnection2: PeerConnection? = null
    private var localDescription: SessionDescription? = null
    private var localDescription2: SessionDescription? = null
    private val iceCandidates = mutableListOf<IceCandidate>()
    private val iceCandidates2 = mutableListOf<IceCandidate>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pull)
        initService()
        initService2()
        pull()
        pullCore2()
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
                iceCandidates.add(iceCandidate)
            }
        })
    }

    private fun pullCore2() {

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

        val surfaceViewRenderer = findViewById<SurfaceViewRenderer>(R.id.srv2)
        surfaceViewRenderer.init(eglBaseContext, null)

        val rtcConfig = PeerConnection.RTCConfiguration(emptyList())
        rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED
        rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
        rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        rtcConfig.keyType = PeerConnection.KeyType.ECDSA

        peerConnection2 = peerConnectionFactory.createPeerConnection(rtcConfig, object : SimplePeerConnectionObserver("pull") {
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
                iceCandidates2.add(iceCandidate)
            }
        })
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
                                    override fun channelActive(ctx: ChannelHandlerContext?) {
                                        super.channelActive(ctx)
                                    }

                                    override fun channelRead0(ctx: ChannelHandlerContext, msg: String) {
                                        val webrtcMessage = Gson().fromJson(msg, WebrtcMessage::class.java)
                                        webrtcMessage.description?.let {
                                            peerConnection?.setRemoteDescription(SimpleSdpObserver("pull-setRemoteDescription"), it)
                                        }
                                        webrtcMessage.iceCandidates.forEach {
                                            peerConnection?.addIceCandidate(it)
                                        }

                                        peerConnection?.createAnswer(object : SimpleSdpObserver("pull-createAnswer") {
                                            override fun onCreateSuccess(description: SessionDescription) {
                                                peerConnection?.setLocalDescription(SimpleSdpObserver("pull-setLocalDescription"), description)
                                                localDescription = description
                                            }
                                        }, MediaConstraints())


                                        mainScope.launch {
                                            while (localDescription == null && iceCandidates.isEmpty()) {
                                                delay(100)
                                            }
                                            ctx.writeAndFlush(WebrtcMessage(localDescription, iceCandidates).toString())
                                        }
                                    }
                                })
                            }
                        })
                        .connect("192.168.8.101", 8888).sync().channel().closeFuture().sync()
            } catch (e: Exception) {
                e.printStackTrace()
                eventLoopGroup.shutdownGracefully()
            }

        }
    }

    private fun initService2() {
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
                                    override fun channelActive(ctx: ChannelHandlerContext?) {
                                        super.channelActive(ctx)
                                    }

                                    override fun channelRead0(ctx: ChannelHandlerContext, msg: String) {
                                        val webrtcMessage = Gson().fromJson(msg, WebrtcMessage::class.java)
                                        webrtcMessage.description?.let {
                                            peerConnection2?.setRemoteDescription(SimpleSdpObserver("pull-setRemoteDescription"), it)
                                        }
                                        webrtcMessage.iceCandidates.forEach {
                                            peerConnection2?.addIceCandidate(it)
                                        }

                                        peerConnection2?.createAnswer(object : SimpleSdpObserver("pull-createAnswer") {
                                            override fun onCreateSuccess(description: SessionDescription) {
                                                peerConnection2?.setLocalDescription(SimpleSdpObserver("pull-setLocalDescription"), description)
                                                localDescription2 = description
                                            }
                                        }, MediaConstraints())


                                        mainScope.launch {
                                            while (localDescription2 == null && iceCandidates2.isEmpty()) {
                                                delay(100)
                                            }
                                            ctx.writeAndFlush(WebrtcMessage(localDescription2, iceCandidates2).toString())
                                        }
                                    }
                                })
                            }
                        })
                        .connect("192.168.8.101", 8888).sync().channel().closeFuture().sync()
            } catch (e: Exception) {
                e.printStackTrace()
                eventLoopGroup.shutdownGracefully()
            }

        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mainScope.cancel()
        peerConnection?.dispose()
    }

    companion object {
        private const val TAG = "PullActivity"
    }
}