package com.clipvault.smartclipboard.monitor

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import com.clipvault.smartclipboard.data.prefs.UserPreferences

/**
 * One-shot alarm that clears the system clipboard after the preference period.
 * Exact alarms are used when the user has allowed them; otherwise we fall back
 * to an idle-friendly inexact alarm so the feature still works.
 */
object AutoClearScheduler {
    private const val REQUEST = 71

    fun schedule(context: Context) {
        val delay = UserPreferences(context).autoClearMillis()
        if (delay == null) {
            cancel(context)
            return
        }
        val alarm = context.getSystemService(AlarmManager::class.java)
        val trigger = SystemClock.elapsedRealtime() + delay
        val pi = pending(context)
        try {
            val canExact = Build.VERSION.SDK_INT < 31 || alarm.canScheduleExactAlarms()
            if (canExact && Build.VERSION.SDK_INT >= 23) {
                alarm.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pi)
            } else if (canExact) {
                alarm.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pi)
            } else {
                alarm.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pi)
            }
        } catch (_: SecurityException) {
            alarm.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pi)
        }
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(pending(context))
    }

    private fun pending(context: Context): PendingIntent {
        val intent = Intent(context, AutoClearReceiver::class.java)
        val immutable = if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
        return PendingIntent.getBroadcast(context, REQUEST, intent, PendingIntent.FLAG_UPDATE_CURRENT or immutable)
    }
}
