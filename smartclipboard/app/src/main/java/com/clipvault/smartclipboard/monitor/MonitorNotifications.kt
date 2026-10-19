package com.clipvault.smartclipboard.monitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.clipvault.smartclipboard.R
import com.clipvault.smartclipboard.data.prefs.UserPreferences
import com.clipvault.smartclipboard.ui.MainActivity

/** Low-priority ongoing channel plus a separate default-importance "save this clip" channel. */
object MonitorNotifications {
    const val CHANNEL_MONITOR = "clipvault_monitor"
    const val CHANNEL_CAPTURE = "clipvault_capture"
    const val NOTIFICATION_ID = 1001
    private const val CAPTURE_ID = 1002

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val monitor = NotificationChannel(
            CHANNEL_MONITOR,
            context.getString(R.string.channel_monitor),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.channel_monitor_desc)
            setShowBadge(false)
        }
        val capture = NotificationChannel(
            CHANNEL_CAPTURE,
            context.getString(R.string.channel_capture),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.channel_capture_desc)
        }
        manager.createNotificationChannel(monitor)
        manager.createNotificationChannel(capture)
    }

    fun ongoing(context: Context): Notification {
        val prefs = UserPreferences(context)
        val open = PendingIntent.getActivity(
            context,
            1,
            Intent(context, MainActivity::class.java),
            pendingFlags(),
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_MONITOR)
            .setSmallIcon(R.drawable.ic_stat_clip)
            .setContentTitle(context.getString(R.string.notif_monitoring_title))
            .setContentText(context.getString(R.string.notif_monitoring_text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        if (prefs.notifShowActions()) {
            val clear = PendingIntent.getService(
                context,
                2,
                Intent(context, ClipboardMonitorService::class.java).setAction(ClipboardMonitorService.ACTION_CLEAR),
                pendingFlags(),
            )
            builder.addAction(R.drawable.ic_stat_clip, context.getString(R.string.action_clear), clear)
            val stop = PendingIntent.getService(
                context,
                3,
                Intent(context, ClipboardMonitorService::class.java).setAction(ClipboardMonitorService.ACTION_STOP),
                pendingFlags(),
            )
            builder.addAction(R.drawable.ic_stat_clip, context.getString(R.string.action_pause), stop)
        }
        if (prefs.notifShowRecent()) {
            val recent = PendingIntent.getActivity(
                context,
                4,
                Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_RECENT, true),
                pendingFlags(),
            )
            builder.addAction(R.drawable.ic_stat_clip, context.getString(R.string.action_open_recent), recent)
        }
        return builder.build()
    }

    /** Shown when Android 10+ hides clipboard contents from a background listener. */
    fun askToCapture(context: Context) {
        val open = PendingIntent.getActivity(
            context,
            5,
            Intent(context, ClipCaptureActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            pendingFlags(),
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_CAPTURE)
            .setSmallIcon(R.drawable.ic_stat_clip)
            .setContentTitle(context.getString(R.string.notif_capture_title))
            .setContentText(context.getString(R.string.notif_capture_text))
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(CAPTURE_ID, notification)
    }

    fun cancelCapture(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(CAPTURE_ID)
    }

    private fun pendingFlags(): Int {
        val immutable = if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
        return PendingIntent.FLAG_UPDATE_CURRENT or immutable
    }
}
