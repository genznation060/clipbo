package com.clipvault.smartclipboard.monitor

import android.app.NotificationManager
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.clipvault.smartclipboard.R
import com.clipvault.smartclipboard.app
import com.clipvault.smartclipboard.util.ClipboardEcho
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground listener for [ClipboardManager.OnPrimaryClipChangedListener].
 *
 * On Android 10+ a background app cannot *read* the clipboard unless it is the
 * default IME or has an input-focused window. The callback still fires. When
 * the clip is hidden we raise a tap-to-save notification that opens
 * [ClipCaptureActivity], which is allowed to read once it has focus.
 */
class ClipboardMonitorService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var clipboard: ClipboardManager
    private var listening = false
    private var suppress = false

    private val listener = ClipboardManager.OnPrimaryClipChangedListener { onClipChanged() }

    override fun onCreate() {
        super.onCreate()
        clipboard = getSystemService(ClipboardManager::class.java)
        MonitorNotifications.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopWatching()
                if (Build.VERSION.SDK_INT >= 24) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_CLEAR -> clearClipboard(showToast = true)
        }
        promoteToForeground()
        if (!listening) {
            clipboard.addPrimaryClipChangedListener(listener)
            listening = true
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopWatching()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun promoteToForeground() {
        val notification = MonitorNotifications.ongoing(this)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                MonitorNotifications.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(MonitorNotifications.NOTIFICATION_ID, notification)
        }
    }

    private fun stopWatching() {
        if (listening) {
            clipboard.removePrimaryClipChangedListener(listener)
            listening = false
        }
        AutoClearScheduler.cancel(this)
    }

    private fun onClipChanged() {
        if (suppress) {
            suppress = false
            return
        }
        AutoClearScheduler.schedule(this)
        val text = readClip() ?: run {
            // Hidden by the platform. Ask the user to bring us to the foreground.
            MonitorNotifications.askToCapture(this)
            return
        }
        if (ClipboardEcho.consume(text)) return
        scope.launch(Dispatchers.IO) {
            val id = app().repository.captureFromClipboard(text)
            launch(Dispatchers.Main) {
                val prefs = UserPreferences(this@ClipboardMonitorService)
                if (!prefs.showConfirmation()) return@launch
                if (id != null) {
                    OverlayConfirmation.show(this@ClipboardMonitorService, getString(R.string.clip_saved))
                } else if (prefs.skipDuplicates()) {
                    OverlayConfirmation.show(this@ClipboardMonitorService, getString(R.string.clip_duplicate))
                }
            }
        }
    }

    private fun readClip(): String? {
        val clip = runCatching { clipboard.primaryClip }.getOrNull() ?: return null
        if (clip.itemCount == 0) return null
        return clip.getItemAt(0).coerceToText(this)?.toString()?.takeIf { it.isNotBlank() }
    }

    private fun clearClipboard(showToast: Boolean) {
        suppress = true
        runCatching {
            if (Build.VERSION.SDK_INT >= 28) {
                clipboard.clearPrimaryClip()
            } else {
                clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        }
        AutoClearScheduler.cancel(this)
        if (showToast) Toast.makeText(this, R.string.clipboard_cleared, Toast.LENGTH_SHORT).show()
        // Refresh the ongoing notification in case action visibility changed.
        getSystemService(NotificationManager::class.java)
            .notify(MonitorNotifications.NOTIFICATION_ID, MonitorNotifications.ongoing(this))
    }

    companion object {
        const val ACTION_CLEAR = "com.clipvault.smartclipboard.action.CLEAR_CLIP"
        const val ACTION_STOP = "com.clipvault.smartclipboard.action.STOP_MONITOR"

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, ClipboardMonitorService::class.java),
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, ClipboardMonitorService::class.java).setAction(ACTION_STOP),
            )
        }
    }
}
