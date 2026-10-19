package com.clipvault.smartclipboard.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.clipvault.smartclipboard.data.prefs.UserPreferences
import com.clipvault.smartclipboard.monitor.ClipboardMonitorService

/**
 * Restarts the monitor after reboot or an update, but only when the user left
 * monitoring enabled. BOOT_COMPLETED is an allowed reason to start a foreground
 * service on Android 12+.
 */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        if (!UserPreferences(context).enableMonitoring()) return
        ClipboardMonitorService.start(context)
    }
}
