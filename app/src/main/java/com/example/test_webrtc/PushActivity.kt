package com.example.test_webrtc

import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.view.InputEvent
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.getSystemService
import com.blankj.utilcode.util.ScreenUtils
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
import kotlinx.coroutines.*
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule
import java.time.LocalTime

class PushActivity : AppCompatActivity() {


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
                                    private var peerConnection: PeerConnection? = null

                                    private val inputManagerClass = Class.forName("android.hardware.input.InputManager")
                                    private val inputManagerInstance = inputManagerClass.getDeclaredMethod("getInstance")
                                    private val inputManager = inputManagerInstance.invoke(null) as android.hardware.input.InputManager
                                    private val injectInputEvent = inputManager.javaClass.getMethod("injectInputEvent", InputEvent::class.java, Int::class.javaPrimitiveType)
                                    private val screenHeight = ScreenUtils.getAppScreenHeight()
                                    private val screenWidth = ScreenUtils.getAppScreenWidth()

                                    override fun channelActive(ctx: ChannelHandlerContext) {
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
                                                ctx.writeAndFlush(WebrtcMessage(type = WebrtcMessage.Type.ICE, iceCandidate = iceCandidate).toString())
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
                                                ctx.writeAndFlush(WebrtcMessage(type = WebrtcMessage.Type.SDP, description = description).toString())
                                            }
                                        }, sdpConstraints)
                                    }

                                    override fun channelInactive(ctx: ChannelHandlerContext?) {
                                        super.channelInactive(ctx)
                                        peerConnection?.dispose()
                                        peerConnection = null
                                    }

                                    override fun channelRead0(ctx: ChannelHandlerContext, msg: String) {
                                        val webrtcMessage = WebrtcMessage(msg)

                                        when (webrtcMessage.type) {
                                            WebrtcMessage.Type.SDP -> {
                                                peerConnection?.setRemoteDescription(SimpleSdpObserver("pull-setRemoteDescription"), webrtcMessage.description)
                                                peerConnection?.createAnswer(object : SimpleSdpObserver("pull-createAnswer") {
                                                    override fun onCreateSuccess(description: SessionDescription) {
                                                        peerConnection?.setLocalDescription(SimpleSdpObserver("pull-setLocalDescription"), description)
                                                        ctx.writeAndFlush(WebrtcMessage(type = WebrtcMessage.Type.SDP, description = description).toString())
                                                    }
                                                }, MediaConstraints())
                                            }
                                            WebrtcMessage.Type.ICE -> {
                                                peerConnection?.addIceCandidate(webrtcMessage.iceCandidate)
                                            }
                                            WebrtcMessage.Type.MOVE -> {
                                                runCatching {
                                                    val model = webrtcMessage.motionModel!!
                                                    model.scaleByScreen(screenHeight, screenWidth)
                                                    val event = model.toMotionEvent()
                                                    injectInputEvent.invoke(inputManager, event, 0)
                                                }.onFailure {
                                                    it.printStackTrace()
                                                }
                                            }
                                            else -> {}
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

    lateinit var audioTrack: AudioTrack
    lateinit var videoTrack: VideoTrack
    lateinit var peerConnectionFactory: PeerConnectionFactory
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
        peerConnectionFactory = PeerConnectionFactory.builder()
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
        audioTrack = peerConnectionFactory.createAudioTrack("local_audio_track", audioSource)

        //屏幕捕获
        val videoCapturer = ScreenCapturerAndroid(mediaProjectionPermissionResultData, object : MediaProjection.Callback() {})
        //创建视频源
        val videoSource = peerConnectionFactory.createVideoSource(videoCapturer.isScreencast)
        val surfaceTextureHelper = SurfaceTextureHelper.create("surface_texture_thread", eglBaseContext)
        videoCapturer.initialize(surfaceTextureHelper, this, videoSource.capturerObserver)
        videoCapturer.startCapture(2160, 1080, 60)
        videoTrack = peerConnectionFactory.createVideoTrack("local_video_track", videoSource)


    }

    override fun onDestroy() {
        super.onDestroy()
        mainScope.cancel()
        MediaProjectionForegroundService.stop(this)
    }


}