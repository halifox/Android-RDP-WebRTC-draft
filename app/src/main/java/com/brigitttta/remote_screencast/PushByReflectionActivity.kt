package com.brigitttta.remote_screencast

import android.graphics.Rect
import android.os.*
import android.util.Log
import android.util.Size
import android.view.InputEvent
import android.view.MotionEvent
import android.view.Surface
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
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

class PushByReflectionActivity : AppCompatActivity() {


    private val mainScope = MainScope()
    lateinit var mediaStream: MediaStream
    lateinit var peerConnectionFactory: PeerConnectionFactory


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_push)
        startServer()
        push()
        testTextView()
    }

    //显示时间用于测试延迟
    private fun testTextView() {
        val textView = findViewById<TextView>(R.id.tv_time)
        val handler = Handler()
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    textView.text = LocalTime.now().toString()
                }
                handler.postDelayed(this, 1)
            }
        }, 1)
    }

    private fun startServer() {
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

                                    // 通过反射获取注入触摸事件需要的类,方法与变量
                                    private val inputManagerClass = Class.forName("android.hardware.input.InputManager")
                                    private val inputManagerInstance = inputManagerClass.getDeclaredMethod("getInstance")
                                    private val inputManager = inputManagerInstance.invoke(null) as android.hardware.input.InputManager
                                    private val injectInputEvent = inputManager.javaClass.getMethod("injectInputEvent", InputEvent::class.java, Int::class.javaPrimitiveType)

                                    private val screenHeight = ScreenUtils.getScreenHeight()
                                    private val screenWidth = ScreenUtils.getScreenWidth()
                                    private var downTime = 0L

                                    override fun channelActive(ctx: ChannelHandlerContext) {
                                        createOffer(ctx)
                                    }

                                    override fun channelInactive(ctx: ChannelHandlerContext) {
                                        disposeOffer(ctx)
                                    }

                                    override fun channelRead0(ctx: ChannelHandlerContext, msg: String) {
                                        handlerMsg(ctx, msg)
                                    }

                                    private fun createOffer(ctx: ChannelHandlerContext) {
                                        //发送设备尺寸
                                        ctx.writeAndFlush(WebrtcMessage(type = WebrtcMessage.Type.SIZE, size = Size(screenWidth, screenHeight)).toString())

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
                                                //发送 连接两端的主机的网络地址
                                                ctx.writeAndFlush(WebrtcMessage(type = WebrtcMessage.Type.ICE, iceCandidate = iceCandidate).toString())
                                            }

                                            override fun onAddStream(mediaStream: MediaStream) {
                                            }
                                        })

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

                                    private fun disposeOffer(ctx: ChannelHandlerContext) {
                                        peerConnection?.removeStream(mediaStream)
                                        peerConnection?.dispose()
                                        peerConnection = null
                                    }

                                    private fun handlerMsg(ctx: ChannelHandlerContext, msg: String) {
                                        val webrtcMessage = WebrtcMessage(msg)

                                        when (webrtcMessage.type) {
                                            WebrtcMessage.Type.SDP -> {
                                                // 接收远端sdp并将自身sdp发送给远端完成sdp交换
                                                peerConnection?.setRemoteDescription(SimpleSdpObserver("pull-setRemoteDescription"), webrtcMessage.description)
                                                // 只有设置了远端sdp才能createAnswer
                                                peerConnection?.createAnswer(object : SimpleSdpObserver("pull-createAnswer") {
                                                    override fun onCreateSuccess(description: SessionDescription) {
                                                        peerConnection?.setLocalDescription(SimpleSdpObserver("pull-setLocalDescription"), description)
                                                        ctx.writeAndFlush(WebrtcMessage(type = WebrtcMessage.Type.SDP, description = description).toString())
                                                    }
                                                }, MediaConstraints())
                                            }
                                            WebrtcMessage.Type.ICE -> {
                                                // 接收 连接两端的主机的网络地址
                                                // 发送在PeerConnectionFactory.createPeerConnection.onIceCandidate(iceCandidate: IceCandidate)
                                                peerConnection?.addIceCandidate(webrtcMessage.iceCandidate)
                                            }
                                            WebrtcMessage.Type.MOVE -> {
                                                // 注入触摸事件
                                                runCatching {
                                                    val model = webrtcMessage.motionModel ?: return
                                                    // 处理时钟差异导致的ANR
                                                    val now = SystemClock.uptimeMillis()
                                                    if (model.action == MotionEvent.ACTION_DOWN) {
                                                        downTime = now
                                                    }
                                                    model.eventTime = now
                                                    model.downTime = downTime
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


    private fun push() {
        initCore()
    }

    fun computeVideoSize(w: Int, h: Int, maxSize: Int): Size {
        // Compute the video size and the padding of the content inside this video.
        // Principle:
        // - scale down the great side of the screen to maxSize (if necessary);
        // - scale down the other side so that the aspect ratio is preserved;
        // - round this value to the nearest multiple of 8 (H.264 only accepts multiples of 8)
        var w = w
        var h = h
        w = w and 7.inv() // in case it's not a multiple of 8
        h = h and 7.inv()
        if (maxSize > 0) {
            if (BuildConfig.DEBUG && maxSize % 8 != 0) {
                throw AssertionError("Max size must be a multiple of 8")
            }
            val portrait = h > w
            var major = if (portrait) h else w
            var minor = if (portrait) w else h
            if (major > maxSize) {
                val minorExact = minor * maxSize / major
                // +4 to round the value to the nearest multiple of 8
                minor = minorExact + 4 and 7.inv()
                major = maxSize
            }
            w = if (portrait) minor else major
            h = if (portrait) major else minor
        }
        return Size(w, h)
    }

    private fun initCore() {
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
        val audioTrack = peerConnectionFactory.createAudioTrack("local_audio_track", audioSource)

        //屏幕捕获
        //创建视频源
        val videoSource = peerConnectionFactory.createVideoSource(true)
        val surfaceTextureHelper = SurfaceTextureHelper.create("surface_texture_thread", eglBaseContext)
        //创建视频轨
        val videoTrack = peerConnectionFactory.createVideoTrack("local_video_track", videoSource)

        kotlin.run {
            val ServiceManagerClass = Class.forName("android.os.ServiceManager")
            val ServiceManagerService = ServiceManagerClass.getDeclaredMethod("getService", String::class.java)
            val displayBinder = ServiceManagerService.invoke(null, "display") as IBinder

            val IDisplayManagerStubClass = Class.forName("android.hardware.display.IDisplayManager\$Stub")
            val IDisplayManagerInterface = IDisplayManagerStubClass.getMethod("asInterface", IBinder::class.java);
            val IDisplayManager = IDisplayManagerInterface.invoke(null, displayBinder) as IInterface

            val IDisplayManagerStubProxyClass = IDisplayManager.javaClass
            val displayInfo = IDisplayManagerStubProxyClass.getMethod("getDisplayInfo", Int::class.java).invoke(IDisplayManager, 0)!!
            val cls = displayInfo.javaClass
            val width = cls.getDeclaredField("logicalWidth").getInt(displayInfo);
            val height = cls.getDeclaredField("logicalHeight").getInt(displayInfo);
            val rotation = cls.getDeclaredField("rotation").getInt(displayInfo);
            val layerStack = cls.getDeclaredField("layerStack").getInt(displayInfo);
            val flags = cls.getDeclaredField("flags").getInt(displayInfo);

            val contentRect = Rect(0, 0, width, height)
            val videoSize = computeVideoSize(contentRect.width(), contentRect.height(), 0)
            val videoRect = Rect(0, 0, videoSize.width, videoSize.height)

            surfaceTextureHelper.setTextureSize(videoSize.width, videoSize.height)
            val surface = Surface(surfaceTextureHelper.surfaceTexture)

            // Since Android 12 (preview), secure displays could not be created with shell permissions anymore.
            // On Android 12 preview, SDK_INT is still R (not S), but CODENAME is "S".
            var secure = Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Build.VERSION.SDK_INT == Build.VERSION_CODES.R && "S" != Build.VERSION.CODENAME
            val SurfaceControlClass = Class.forName("android.view.SurfaceControl")
            val display = SurfaceControlClass.getMethod("createDisplay", String::class.java, Boolean::class.java).invoke(null, "scrcpy", secure) as IBinder


            SurfaceControlClass.getMethod("openTransaction").invoke(null)
            try {
                SurfaceControlClass.getMethod("setDisplaySurface", IBinder::class.java, Surface::class.java).invoke(null, display, surface)
                SurfaceControlClass.getMethod("setDisplayProjection", IBinder::class.java, Int::class.java, Rect::class.java, Rect::class.java).invoke(null, display, 0, contentRect, videoRect)
                SurfaceControlClass.getMethod("setDisplayLayerStack", IBinder::class.java, Int::class.java).invoke(null, display, layerStack)
            } finally {
                SurfaceControlClass.getMethod("closeTransaction").invoke(null);
            }
            videoSource.capturerObserver.onCapturerStarted(true)
            surfaceTextureHelper.startListening { videoFrame ->
                videoSource.capturerObserver.onFrameCaptured(videoFrame)
            }
        }


        //创建媒体流
        mediaStream = peerConnectionFactory.createLocalMediaStream("102")
//        mediaStream.addTrack(audioTrack)
        mediaStream.addTrack(videoTrack)

    }

    override fun onDestroy() {
        super.onDestroy()
        mainScope.cancel()
    }


}