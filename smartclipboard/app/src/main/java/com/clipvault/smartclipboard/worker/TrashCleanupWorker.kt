package com.clipvault.smartclipboard.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.clipvault.smartclipboard.app
import com.clipvault.smartclipboard.data.prefs.UserPreferences

/** Daily sweep. Retention comes from the "Auto-delete trash" preference. */
class TrashCleanupWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val retention = UserPreferences(applicationContext).trashRetentionMillis()
            ?: return Result.success()
        val before = System.currentTimeMillis() - retention
        return runCatching {
            applicationContext.app().repository.purgeTrashOlderThan(before)
            Result.success()
        }.getOrElse { Result.retry() }
    }

    companion object {
        const val UNIQUE_NAME = "clipvault-trash-cleanup"
    }
}
