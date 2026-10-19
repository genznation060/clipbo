package com.clipvault.smartclipboard.monitor

import android.content.ClipboardManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.clipvault.smartclipboard.R
import com.clipvault.smartclipboard.app
import com.clipvault.smartclipboard.data.prefs.UserPreferences
import kotlinx.coroutines.launch

/**
 * Transparent activity. A notification tap gives us window focus, which is what
 * Android 10+ requires before [ClipboardManager.getPrimaryClip] returns data.
 */
class ClipCaptureActivity : ComponentActivity() {

    private var handled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus || handled) return
        handled = true
        val clipboard = getSystemService(ClipboardManager::class.java)
        val text = clipboard?.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(this)
            ?.toString()
        if (text.isNullOrBlank()) {
            Toast.makeText(this, R.string.clip_unreadable, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        lifecycleScope.launch {
            val id = app().repository.captureFromClipboard(text)
            val prefs = UserPreferences(this@ClipCaptureActivity)
            if (prefs.showConfirmation()) {
                val msg = if (id != null) R.string.clip_saved else R.string.clip_duplicate
                Toast.makeText(this@ClipCaptureActivity, msg, Toast.LENGTH_SHORT).show()
            }
            MonitorNotifications.cancelCapture(this@ClipCaptureActivity)
            finish()
        }
    }
}
