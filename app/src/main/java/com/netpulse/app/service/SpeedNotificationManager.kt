package com.netpulse.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import com.netpulse.app.MainActivity
import com.netpulse.app.R
import com.netpulse.app.core.Constants
import com.netpulse.app.core.SpeedFormatter
import com.netpulse.app.domain.model.DailyUsage
import com.netpulse.app.domain.model.SpeedSnapshot

class SpeedNotificationManager(private val context: Context) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun buildNotification(
        speed: SpeedSnapshot,
        usage: DailyUsage,
        unitInBits: Boolean = false,
        showUpload: Boolean = true,
        useDynamicIcon: Boolean = true
    ): Notification {
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val dl = SpeedFormatter.formatSpeed(speed.downloadBytesPerSec, unitInBits)
        val ul = SpeedFormatter.formatSpeed(speed.uploadBytesPerSec, unitInBits)

        val title = if (showUpload) {
            "↓ ${dl.displayString}   ↑ ${ul.displayString}"
        } else {
            "Speed: ${dl.displayString}"
        }

        val mobileStr = SpeedFormatter.formatDataUsage(usage.mobileBytes)
        val wifiStr = SpeedFormatter.formatDataUsage(usage.wifiBytes)
        val content = "Today: Mobile $mobileStr | Wi-Fi $wifiStr"

        val builder = NotificationCompat.Builder(context, Constants.CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        if (useDynamicIcon) {
            val totalSpeed = SpeedFormatter.formatSpeed(speed.totalBytesPerSec, unitInBits)
            val iconBitmap = SpeedIconGenerator.generateSpeedBitmap(totalSpeed)
            builder.setSmallIcon(IconCompat.createWithBitmap(iconBitmap))
        } else {
            builder.setSmallIcon(R.drawable.ic_stat_speed)
        }

        return builder.build()
    }

    fun updateNotification(notification: Notification) {
        notificationManager.notify(Constants.NOTIFICATION_ID, notification)
    }

    companion object {
        fun createNotificationChannel(context: Context) {
            val channel = NotificationChannel(
                Constants.CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.notification_channel_desc)
                setShowBadge(false)
                enableVibration(false)
                enableLights(false)
                setSound(null, null)
            }

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
