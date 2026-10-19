package com.clipvault.smartclipboard.monitor

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import com.clipvault.smartclipboard.R

/**
 * Short confirmation bubble. Uses [WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY]
 * only when the user has granted [Settings.canDrawOverlays]; otherwise a toast.
 */
object OverlayConfirmation {
    fun show(context: Context, message: String) {
        val appContext = context.applicationContext
        if (Build.VERSION.SDK_INT < 23 || !Settings.canDrawOverlays(appContext)) {
            Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
            return
        }
        val wm = appContext.getSystemService(WindowManager::class.java)
        val view = LayoutInflater.from(appContext).inflate(R.layout.overlay_confirmation, null)
        view.findViewById<TextView>(R.id.overlayText).text = message
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = (context.resources.displayMetrics.density * 72).toInt()
        }
        runCatching {
            wm.addView(view, params)
            view.postDelayed({ runCatching { wm.removeView(view) } }, 1800)
        }.onFailure {
            Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
        }
    }
}
