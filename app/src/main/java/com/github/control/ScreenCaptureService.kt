package com.github.control

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import com.blankj.utilcode.util.DeviceUtils
import com.blankj.utilcode.util.ScreenUtils
import com.blankj.utilcode.util.ServiceUtils
import com.blankj.utilcode.util.ThreadUtils
import com.blankj.utilcode.util.ToastUtils
import com.github.control.gesture.Controller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import org.webrtc.DataChannel
import org.webrtc.EglBase
import org.webrtc.HardwareVideoDecoderFactory
import org.webrtc.HardwareVideoEncoderFactory
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.ScreenCapturerAndroid
import org.webrtc.SdpObserver
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
        .setVideoEncoderFactory(HardwareVideoEncoderFactory(eglBaseContext, false, false))
        .setVideoDecoderFactory(HardwareVideoDecoderFactory(eglBaseContext))
        .createPeerConnectionFactory()
    private val surfaceTextureHelper = SurfaceTextureHelper.create("surface_texture_thread", eglBaseContext, true)
    private val videoSource = peerConnectionFactory.createVideoSource(true, true)
    private val videoTrack = peerConnectionFactory.createVideoTrack("local_video_track", videoSource)
    private val rtcConfig = PeerConnection.RTCConfiguration(emptyList())


    private lateinit var peerConnection: PeerConnection
    private var screenCapturerAndroid: ScreenCapturerAndroid? = null
    private var serverSocket: ServerSocket? = null
    private var socket: Socket? = null
    private lateinit var inputStream: DataInputStream
    private lateinit var outputStream: DataOutputStream

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            ThreadUtils.getSinglePool()
                .submit {
                    sendConfigurationChanged(outputStream)
                }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate: ")
        startForegroundService()
        startNsdService()
        registerReceiver(receiver, IntentFilter(Intent.ACTION_CONFIGURATION_CHANGED))

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket(40000)
                socket = serverSocket?.accept()
                inputStream = DataInputStream(socket?.inputStream)
                outputStream = DataOutputStream(socket?.outputStream)
                sendConfigurationChanged(outputStream)
                createPeerConnection()
                startReadThread()
            } catch (e: Exception) {
                stopSelf()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy: ")
        stopForegroundService()
        stopNsdService()
        unregisterReceiver(receiver)
        eglBase.release()
        screenCapturerAndroid?.stopCapture()
        screenCapturerAndroid?.dispose()
        serverSocket?.close()
        socket?.close()
    }


    private fun createPeerConnection() {
        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onSignalingChange(p0: PeerConnection.SignalingState?) {

            }

            override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {

            }

            override fun onIceConnectionReceivingChange(p0: Boolean) {

            }

            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {

            }

            override fun onIceCandidate(iceCandidate: IceCandidate) {
                sendIceCandidate(outputStream, iceCandidate)
            }

            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate?>?) {

            }

            override fun onAddStream(p0: MediaStream?) {

            }

            override fun onRemoveStream(p0: MediaStream?) {

            }

            override fun onDataChannel(p0: DataChannel?) {

            }

            override fun onRenegotiationNeeded() {

            }
        })!!
        peerConnection.addTrack(videoTrack)
        peerConnection.createOffer(object : SdpObserver {
            override fun onCreateSuccess(description: SessionDescription) {
                peerConnection.setLocalDescription(object : SdpObserver {
                    override fun onCreateSuccess(sdp: SessionDescription) {

                    }

                    override fun onSetSuccess() {

                    }

                    override fun onCreateFailure(error: String) {

                    }

                    override fun onSetFailure(error: String) {

                    }
                }, description)
                sendSessionDescription(outputStream, description)
            }

            override fun onSetSuccess() {

            }

            override fun onCreateFailure(error: String) {

            }

            override fun onSetFailure(error: String) {

            }
        }, MediaConstraints())
    }


    private fun startReadThread() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                while (isActive) {
                    val type = inputStream.readInt()
                    when (type) {
                        ACTION_EVENT -> receiveGlobalActionEvent(inputStream, controller)
                        TOUCH_EVENT -> receiveTouchEvent(inputStream, controller)
                        ICE_CANDIDATE -> receiveIceCandidate(inputStream, peerConnection)
                        SESSION_DESCRIPTION -> receiveSessionDescription(inputStream, peerConnection)
                    }
                }
            } catch (e: Exception) {
                ToastUtils.showLong("对端关闭")
                withContext(Dispatchers.Main) {
                    stopSelf()
                }
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
        serviceName = DeviceUtils.getModel()
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