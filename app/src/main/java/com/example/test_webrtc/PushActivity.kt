package com.example.test_webrtc

import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.getSystemService
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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule
import java.time.LocalTime

class PushActivity : AppCompatActivity() {

    private var peerConnection: PeerConnection? = null
    private var localDescription: SessionDescription? = null
    private val iceCandidates = mutableListOf<IceCandidate>()
    private val mainScope = MainScope()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_push)

        initService()
        push()
        testTextView()
    }

    private fun testTextView() {
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
            val bossGroup = NioEventLoopGroup()
            val workerGroup = NioEventLoopGroup()
            try {
                ServerBootstrap()
                        .group(bossGroup, workerGroup)
                        .channel(NioServerSocketChannel::class.java)
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
                                        ctx.writeAndFlush(Message(localDescription, iceCandidates).toString())
                                    }

                                    override fun channelRead0(ctx: ChannelHandlerContext, msg: String) {
                                        val message = Message(msg)
                                        peerConnection?.setRemoteDescription(SimpleSdpObserver("push-setRemoteDescription"), message.description)
                                        message.iceCandidates.forEach { iceCandidate ->
                                            peerConnection?.addIceCandidate(IceCandidate(iceCandidate.sdpMid, iceCandidate.sdpMLineIndex, iceCandidate.sdp))
                                        }
                                    }
                                })
                            }
                        }).bind(8888).sync().channel().closeFuture().sync()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                bossGroup.shutdownGracefully()
                workerGroup.shutdownGracefully()
            }
        }
    }


    private val registerMediaProjectionPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            pushCore(it.data)
        } else {
            runOnUiThread {
                Toast.makeText(this, "Need Media Projection Permission", Toast.LENGTH_LONG).show()
                finish()
            }
        }
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
        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, object : SimplePeerConnectionObserver("push") {
            override fun onIceCandidate(iceCandidate: IceCandidate) {
                super.onIceCandidate(iceCandidate)
                iceCandidates.add(iceCandidate)
            }

            override fun onAddStream(mediaStream: MediaStream) {
                super.onAddStream(mediaStream)
            }
        })

        val mediaStream = peerConnectionFactory.createLocalMediaStream("102")
        mediaStream.addTrack(audioTrack)
        mediaStream.addTrack(videoTrack)
        peerConnection?.addStream(mediaStream)

        val sdpConstraints = MediaConstraints()
        sdpConstraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        sdpConstraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        peerConnection?.createOffer(object : SimpleSdpObserver("push-createOffer") {
            override fun onCreateSuccess(description: SessionDescription) {
                peerConnection?.setLocalDescription(SimpleSdpObserver("push-setLocalDescription"), description)
                localDescription = description
            }
        }, sdpConstraints)
    }

    override fun onDestroy() {
        super.onDestroy()
        mainScope.cancel()
        peerConnection?.dispose()
    }


}