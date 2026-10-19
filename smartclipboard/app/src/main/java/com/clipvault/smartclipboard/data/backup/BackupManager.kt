package com.clipvault.smartclipboard.data.backup

import android.content.Context
import android.net.Uri
import com.clipvault.smartclipboard.app
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** JSON backup through the Storage Access Framework. Nothing is written to a shared path. */
object BackupManager {
    suspend fun exportTo(context: Context, uri: Uri) = withContext(Dispatchers.IO) {
        val json = context.app().repository.exportJson()
        context.contentResolver.openOutputStream(uri)?.use { stream ->
            stream.write(json.toByteArray(Charsets.UTF_8))
        } ?: error("Could not open the destination file")
    }

    suspend fun importFrom(context: Context, uri: Uri) = withContext(Dispatchers.IO) {
        val raw = context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.readBytes().toString(Charsets.UTF_8)
        } ?: error("Could not read the backup file")
        context.app().repository.importJson(raw)
    }
}
