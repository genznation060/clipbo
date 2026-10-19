package com.clipvault.smartclipboard.monitor

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build

/** Fired by [AutoClearScheduler]. Writes are attempted directly; failures are swallowed. */
class AutoClearReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= 28) {
                clipboard.clearPrimaryClip()
            } else {
                @Suppress("DEPRECATION")
                clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        }
    }
}
