package com.example.test_webrtc

import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.getSystemService
import com.google.gson.Gson
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.LengthFieldBasedFrameDecoder
import io.netty.handler.codec.LengthFieldPrepender
import io.netty.handler.codec.string.StringDecoder
import io.netty.handler.codec.string.StringEncoder
import io.netty.util.CharsetUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule
import java.time.LocalTime

class PushActivity : AppCompatActivity() {

    val bossGroup = NioEventLoopGroup()
    val workerGroup = NioEventLoopGroup()
    val serverBootstrap = ServerBootstrap()

    var localDescription: SessionDescription? = null
    val iceCandidates = mutableListOf<IceCandidate>()
    var peerConnection: PeerConnection? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_push)

        initService()
        push()

        val textView = findViewById<TextView>(R.id.tv_time)
        val handler = Handler()
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    textView.text = LocalTime.now().toString()
                }
                handler.postDelayed(this, 16)
            }
        }, 16)

    }

    private fun initService() {
        mainScope.launch(Dispatchers.IO) {
            try {
                serverBootstrap.group(bossGroup, workerGroup) //设置nio双向通道
                        .channel(NioServerSocketChannel::class.java) //子处理器
                        .childHandler(object : ChannelInitializer<SocketChannel>() {
                            override fun initChannel(channel: SocketChannel) {
                                val pipeline = channel.pipeline()
                                //数据分包，组包，粘包
                                pipeline.addLast(LengthFieldBasedFrameDecoder(Int.MAX_VALUE, 0, 4, 0, 4))
                                pipeline.addLast(LengthFieldPrepender(4))
                                pipeline.addLast(StringDecoder(CharsetUtil.UTF_8))
                                pipeline.addLast(StringEncoder(CharsetUtil.UTF_8))
                                pipeline.addLast(object : SimpleChannelInboundHandler<String>() {
                                    override fun channelActive(ctx: ChannelHandlerContext) {
                                        super.channelActive(ctx)
                                        ctx.writeAndFlush(Gson().toJson(Message(localDescription, iceCandidates)))
                                    }

                                    override fun channelRead0(ctx: ChannelHandlerContext, msg: String) {
                                        val message = Gson().fromJson(msg, Message::class.java)
                                        peerConnection?.setRemoteDescription(SdpAdapter(""), message.description)
                                        message.iceCandidates.forEach { iceCandidate ->
                                            peerConnection?.addIceCandidate(IceCandidate(iceCandidate.sdpMid, iceCandidate.sdpMLineIndex, iceCandidate.sdp))
                                        }
                                    }
                                })
                            }
                        })
                val channelFuture = serverBootstrap.bind(8888).sync()
                channelFuture.channel().closeFuture().sync()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                bossGroup.shutdownGracefully()
                workerGroup.shutdownGracefully()
            }
        }
    }

    private val mainScope = MainScope()

    private val registerMediaProjectionPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        pushCore(it.data)
    }

    private fun push() {
        MediaProjectionForegroundService.start(this)
        val mediaProjectionManager = getSystemService<MediaProjectionManager>()
        registerMediaProjectionPermission.launch(mediaProjectionManager?.createScreenCaptureIntent())
    }


    private fun pushCore(mediaProjectionPermissionResultData: Intent?) {

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
        //对等连接Factory
        val peerConnectionFactory = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory)
                .setAudioDeviceModule(audioDeviceModule)
                .createPeerConnectionFactory()

        //音频处理
        val audioConstraints = MediaConstraints()
        //回声消除
        audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
        //自动增益
        audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
        //高音过滤
        audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
        //噪音处理
        audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
        //创建音频源
        val audioSource = peerConnectionFactory.createAudioSource(audioConstraints)
        //创建音轨
        val audioTrack = peerConnectionFactory.createAudioTrack("local_audio_track", audioSource)

        //屏幕捕获
        val videoCapturer = ScreenCapturerAndroid(mediaProjectionPermissionResultData, object : MediaProjection.Callback() {})
        //创建视频源
        val videoSource = peerConnectionFactory.createVideoSource(videoCapturer.isScreencast)
        val surfaceTextureHelper = SurfaceTextureHelper.create("surface_texture_thread", eglBaseContext)
        videoCapturer.initialize(surfaceTextureHelper, this, videoSource.capturerObserver)
        videoCapturer.startCapture(2160, 1080, 60)
        val videoTrack = peerConnectionFactory.createVideoTrack("local_video_track", videoSource)

        //RTC配置
        val rtcConfig = PeerConnection.RTCConfiguration(listOf())
        rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED
        rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
        rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        rtcConfig.keyType = PeerConnection.KeyType.ECDSA
        //创建对等连接
        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, object : PeerConnectionObserver() {
            override fun onIceCandidate(iceCandidate: IceCandidate) {
                super.onIceCandidate(iceCandidate)
                iceCandidates.add(iceCandidate)
            }
        })

        //creating local mediastream
        val stream = peerConnectionFactory.createLocalMediaStream("102")
        stream.addTrack(audioTrack)
        stream.addTrack(videoTrack)
        peerConnection?.addStream(stream)

        val sdpConstraints = MediaConstraints()
        sdpConstraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        sdpConstraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        peerConnection?.createOffer(object : SdpAdapter("createOffer") {
            override fun onCreateSuccess(description: SessionDescription) {
                peerConnection?.setLocalDescription(SdpAdapter("setLocalDescription"), description)
                localDescription = description
            }
        }, sdpConstraints)


    }


}