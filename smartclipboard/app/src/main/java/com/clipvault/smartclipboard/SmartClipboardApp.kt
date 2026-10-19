package com.clipvault.smartclipboard

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.clipvault.smartclipboard.data.db.AppDatabase
import com.clipvault.smartclipboard.data.prefs.UserPreferences
import com.clipvault.smartclipboard.data.repo.NotesRepository
import com.clipvault.smartclipboard.monitor.ClipboardMonitorService
import com.clipvault.smartclipboard.monitor.MonitorNotifications
import com.clipvault.smartclipboard.worker.TrashCleanupWorker
import java.util.concurrent.TimeUnit

class SmartClipboardApp : Application() {
    lateinit var repository: NotesRepository
        private set
    lateinit var preferences: UserPreferences
        private set

    override fun onCreate() {
        super.onCreate()
        preferences = UserPreferences(this)
        repository = NotesRepository(AppDatabase.get(this), preferences)
        MonitorNotifications.ensureChannels(this)
        applyNightMode(preferences.darkMode())
        scheduleTrashCleanup()
        if (preferences.enableMonitoring()) {
            ClipboardMonitorService.start(this)
        }
    }

    private fun scheduleTrashCleanup() {
        val request = PeriodicWorkRequestBuilder<TrashCleanupWorker>(1, TimeUnit.DAYS)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            TrashCleanupWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    companion object {
        fun applyNightMode(mode: String) {
            val night = when (mode) {
                "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                "light" -> AppCompatDelegate.MODE_NIGHT_NO
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            AppCompatDelegate.setDefaultNightMode(night)
        }
    }
}

fun Context.app(): SmartClipboardApp = applicationContext as SmartClipboardApp
