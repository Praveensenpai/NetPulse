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

    private fun contentIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    fun buildNotification(
        speed: SpeedSnapshot,
        usage: DailyUsage,
        unitInBits: Boolean = false,
        showUpload: Boolean = true,
        useDynamicIcon: Boolean = true
    ): Notification {
        val dl = SpeedFormatter.formatSpeed(speed.downloadBytesPerSec, unitInBits)
        val ul = SpeedFormatter.formatSpeed(speed.uploadBytesPerSec, unitInBits)

        val title = if (showUpload) {
            "\u2193 ${dl.displayString}   \u2191 ${ul.displayString}"
        } else {
            "Speed: ${dl.displayString}"
        }

        val mobileStr = SpeedFormatter.formatDataUsage(usage.mobileBytes)
        val wifiStr = SpeedFormatter.formatDataUsage(usage.wifiBytes)
        val content = "Today: Mobile $mobileStr | Wi-Fi $wifiStr"

        val builder = baseBuilder()
            .setContentTitle(title)
            .setContentText(content)

        if (useDynamicIcon) {
            builder.setSmallIcon(dynamicIcon(speed, unitInBits))
        } else {
            builder.setSmallIcon(R.drawable.ic_stat_speed)
        }

        return builder.build()
    }

    /**
     * Shown when the live readout is disabled. A foreground service must always
     * display a notification, so this states what is happening instead of freezing
     * the last speed value forever.
     */
    fun buildServiceNotification(): Notification = baseBuilder()
        .setContentTitle(context.getString(R.string.app_name))
        .setContentText("Monitoring in the background. Live speed display is off.")
        .setSmallIcon(R.drawable.ic_stat_speed)
        .build()

    private fun baseBuilder(): NotificationCompat.Builder =
        NotificationCompat.Builder(context, Constants.CHANNEL_ID)
            .setContentIntent(contentIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

    private fun dynamicIcon(speed: SpeedSnapshot, unitInBits: Boolean): IconCompat {
        val totalSpeed = SpeedFormatter.formatSpeed(speed.totalBytesPerSec, unitInBits)
        return IconCompat.createWithBitmap(SpeedIconGenerator.generateSpeedBitmap(totalSpeed))
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