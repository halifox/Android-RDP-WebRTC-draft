package com.github.control

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationChannelGroupCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.blankj.utilcode.util.ScreenUtils
import com.blankj.utilcode.util.ServiceUtils
import com.github.control.gesture.Controller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.webrtc.EglBase
import org.webrtc.HardwareVideoDecoderFactory
import org.webrtc.HardwareVideoEncoderFactory
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.ScreenCapturerAndroid
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.ServerSocket
import java.net.Socket


class ScreenCaptureService : LifecycleService() {
    companion object {
        const val SCREEN_CAPTURE_INTENT = "SCREEN_CAPTURE_INTENT"
        private const val TAG = "ScreenCaptureService"

        @JvmStatic
        fun start(context: Context, screenCaptureIntent: Intent) {
            val starter = Intent(context, ScreenCaptureService::class.java)
                .putExtra(SCREEN_CAPTURE_INTENT, screenCaptureIntent)
            context.startService(starter)
        }

        @JvmStatic
        fun stop(context: Context) {
            val starter = Intent(context, ScreenCaptureService::class.java)
            context.stopService(starter)
        }

        @JvmStatic
        fun isServiceRunning(context: Context): Boolean {
            return ServiceUtils.isServiceRunning(ScreenCaptureService::class.java)
        }

    }

    private val context = this
    private val controller by inject<Controller>()

    private val eglBase = EglBase.create()
    private val eglBaseContext = eglBase.getEglBaseContext()
    private val peerConnectionFactory = PeerConnectionFactory.builder()
        .setVideoEncoderFactory(HardwareVideoEncoderFactory(eglBaseContext, true, true))
        .setVideoDecoderFactory(HardwareVideoDecoderFactory(eglBaseContext))
        .createPeerConnectionFactory()
    private val surfaceTextureHelper = SurfaceTextureHelper.create("surface_texture_thread", eglBaseContext, true)
    private val videoSource = peerConnectionFactory.createVideoSource(true, true)
    private val videoTrack = peerConnectionFactory.createVideoTrack("local_video_track", videoSource)
    private val rtcConfig = PeerConnection.RTCConfiguration(emptyList())


    private lateinit var peerConnection: PeerConnection
    private lateinit var serverSocket: ServerSocket
    private lateinit var socket: Socket
    private lateinit var inputStream: DataInputStream
    private lateinit var outputStream: DataOutputStream


    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate: ")
        startForegroundService()
        startNsdService()

        lifecycleScope.launch(Dispatchers.IO) {
            serverSocket = ServerSocket(40000)
            socket = serverSocket.accept()
            inputStream = DataInputStream(socket.inputStream)
            outputStream = DataOutputStream(socket.outputStream)
            createPeerConnection()
            startReadThread()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy: ")
        stopForegroundService()
        stopNsdService()
        eglBase.release()
        screenCapturerAndroid?.stopCapture()
        screenCapturerAndroid?.dispose()
        inputStream.close()
        outputStream.close()
        socket.close()
        serverSocket.close()
    }


    private fun createPeerConnection() {
        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, object : EmptyPeerConnectionObserver() {
            override fun onIceCandidate(iceCandidate: IceCandidate) {
                sendIceCandidate(outputStream, iceCandidate)
            }
        })!!
        peerConnection.addTrack(videoTrack)
        peerConnection.createOffer(object : EmptySdpObserver() {
            override fun onCreateSuccess(description: SessionDescription) {
                peerConnection.setLocalDescription(EmptySdpObserver(), description)
                sendSessionDescription(outputStream, description)
            }
        }, MediaConstraints())
    }


    private fun startReadThread() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                while (isActive) {
                    val type = inputStream.readInt()
                    Log.d(TAG, "type:${type} ")
                    when (type) {
                        101 -> receiveGlobalActionEvent(inputStream, controller)
                        102 -> receiveTouchEvent(inputStream, controller)
                        201 -> receiveIceCandidate(inputStream, peerConnection)
                        202 -> receiveSessionDescription(inputStream, peerConnection)
                    }
                }
            } catch (e: Exception) {

            }
        }

    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand:")
        val screenCaptureIntent = intent?.getParcelableExtra<Intent?>(SCREEN_CAPTURE_INTENT)
        if (screenCaptureIntent != null) {
            initScreenCapturerAndroid(screenCaptureIntent)
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private var screenCapturerAndroid: ScreenCapturerAndroid? = null
    private fun initScreenCapturerAndroid(screenCaptureIntent: Intent) {
        screenCapturerAndroid = ScreenCapturerAndroid(screenCaptureIntent, object : MediaProjection.Callback() {
            override fun onStop() {
                stopSelf()
            }
        }).apply {
            initialize(surfaceTextureHelper, applicationContext, videoSource.capturerObserver)
            startCapture(ScreenUtils.getScreenWidth(), ScreenUtils.getScreenHeight(), 0)
        }
    }


    /**
     * ForegroundService
     */
    private fun startForegroundService() {
        val groupId = "screenRecordingGroup"
        val channelId = "screenRecordingChannel"
        val notificationManager = NotificationManagerCompat.from(context)
        val notificationChannelGroup = NotificationChannelGroupCompat.Builder(groupId)
            .setName("录屏")
            .setDescription("录屏通知组别")
            .build()
        notificationManager.createNotificationChannelGroup(notificationChannelGroup)
        val notificationChannel = NotificationChannelCompat.Builder(channelId, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName("录屏通知渠道")
            .setDescription("录屏通知渠道")
            .setGroup(groupId)
            .build()
        notificationManager.createNotificationChannelsCompat(mutableListOf(notificationChannel))
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.outline_screen_record_24)
            .setContentTitle("录屏服务正在运行")
            .setSilent(true)
            .setOngoing(true)
            .build()
        startForeground(1, notification)
    }

    private fun stopForegroundService() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    /**
     *  Nsd
     */
    private val nsdManager by inject<NsdManager>()
    private val nsdServiceInfo = NsdServiceInfo().apply {
        serviceName = "control"
        serviceType = "_control._tcp."
        port = 40000
    }
    private val registrationListener = object : NsdManager.RegistrationListener {
        private val TAG = "NsdManager"
        override fun onServiceRegistered(nsdServiceInfo: NsdServiceInfo) {
            Log.d(TAG, "onServiceRegistered:注册成功 ")
        }

        override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            Log.d(TAG, "onRegistrationFailed:注册失败 ")
        }

        override fun onServiceUnregistered(arg0: NsdServiceInfo) {
            Log.d(TAG, "onServiceUnregistered:取消注册 ")
        }

        override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            Log.d(TAG, "onUnregistrationFailed:取消注册失败 ")
        }
    }


    private fun startNsdService() {
        nsdManager.registerService(nsdServiceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
    }

    private fun stopNsdService() {
        nsdManager.unregisterService(registrationListener)
    }
}