package com.example.test_webrtc

import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationChannelGroupCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat


class MediaProjectionForegroundService : Service() {
    companion object {

        @JvmStatic
        fun start(context: AppCompatActivity) {
            context.startService(Intent(context, MediaProjectionForegroundService::class.java))
        }

        @JvmStatic
        fun stop(context: AppCompatActivity) {
            context.stopService(Intent(context, MediaProjectionForegroundService::class.java))
        }
    }


    private val groupId = "groupId"
    private val channelId = "channelId"


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notificationManager = NotificationManagerCompat.from(this)
        val notificationChannelGroup = NotificationChannelGroupCompat.Builder(groupId)
                .setName("录屏")
                .setDescription("录屏通知组别")
                .build()
        notificationManager.createNotificationChannelGroup(notificationChannelGroup)
        val notificationChannel = NotificationChannelCompat.Builder(channelId, 2)
                .setName("录屏通知渠道")
                .setDescription("录屏通知渠道")
                .setGroup(groupId)
                .build()
        notificationManager.createNotificationChannelsCompat(mutableListOf(notificationChannel))
        val notification = NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("投屏服务运行中")
                .setSilent(true)
                .setOngoing(true)
                .build()
        startForeground(1, notification)
        return super.onStartCommand(intent, flags, startId)
    }


    override fun onDestroy() {
        super.onDestroy()
        stopForeground(true)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }


}