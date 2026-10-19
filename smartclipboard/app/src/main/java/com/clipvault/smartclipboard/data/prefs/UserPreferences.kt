package com.clipvault.smartclipboard.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

/** Shared with [androidx.preference.PreferenceFragmentCompat] — same file, same keys. */
object PrefKeys {
    const val ENABLE_MONITORING = "enable_monitoring"
    const val AUTO_ADD = "auto_add"
    const val AUTO_CLEAR_PERIOD = "auto_clear_period"
    const val SHOW_CONFIRMATION = "show_confirmation"
    const val SKIP_DUPLICATES = "skip_duplicates"
    const val NEW_NOTE_POSITION = "new_note_position"

    const val NOTIF_SHOW_ACTIONS = "notif_show_actions"
    const val NOTIF_SHOW_RECENT = "notif_show_recent"

    const val KB_SIZE = "kb_size"
    const val KB_AUTO_SWITCH = "kb_auto_switch"

    const val DARK_MODE = "dark_mode"
    const val DEFAULT_ACTION = "default_action"
    const val NOTES_ORDER = "notes_order"
    const val MERGE_SEPARATOR = "merge_separator"

    const val TRASH_PERIOD = "trash_period"
}

enum class NotesOrder { MANUAL, NEWEST, OLDEST, ALPHA }

enum class NewNotePosition { TOP, BOTTOM }

enum class KeyboardSize { SMALL, NORMAL, LARGE }

enum class DefaultAction { VIEW, COPY, EDIT }

class UserPreferences(context: Context) {
    private val prefs: SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context.applicationContext)

    fun enableMonitoring(): Boolean = prefs.getBoolean(PrefKeys.ENABLE_MONITORING, false)
    fun autoAdd(): Boolean = prefs.getBoolean(PrefKeys.AUTO_ADD, true)
    fun showConfirmation(): Boolean = prefs.getBoolean(PrefKeys.SHOW_CONFIRMATION, true)
    fun skipDuplicates(): Boolean = prefs.getBoolean(PrefKeys.SKIP_DUPLICATES, true)
    fun notifShowActions(): Boolean = prefs.getBoolean(PrefKeys.NOTIF_SHOW_ACTIONS, true)
    fun notifShowRecent(): Boolean = prefs.getBoolean(PrefKeys.NOTIF_SHOW_RECENT, true)
    fun keyboardAutoSwitch(): Boolean = prefs.getBoolean(PrefKeys.KB_AUTO_SWITCH, true)

    fun autoClearPeriod(): String = prefs.getString(PrefKeys.AUTO_CLEAR_PERIOD, "off") ?: "off"
    fun newNotePosition(): NewNotePosition = when (prefs.getString(PrefKeys.NEW_NOTE_POSITION, "top")) {
        "bottom" -> NewNotePosition.BOTTOM
        else -> NewNotePosition.TOP
    }
    fun keyboardSize(): KeyboardSize = when (prefs.getString(PrefKeys.KB_SIZE, "normal")) {
        "small" -> KeyboardSize.SMALL
        "large" -> KeyboardSize.LARGE
        else -> KeyboardSize.NORMAL
    }
    fun darkMode(): String = prefs.getString(PrefKeys.DARK_MODE, "system") ?: "system"
    fun defaultAction(): DefaultAction = when (prefs.getString(PrefKeys.DEFAULT_ACTION, "view")) {
        "copy" -> DefaultAction.COPY
        "edit" -> DefaultAction.EDIT
        else -> DefaultAction.VIEW
    }
    fun notesOrder(): NotesOrder = when (prefs.getString(PrefKeys.NOTES_ORDER, "manual")) {
        "newest" -> NotesOrder.NEWEST
        "oldest" -> NotesOrder.OLDEST
        "alpha" -> NotesOrder.ALPHA
        else -> NotesOrder.MANUAL
    }
    fun mergeSeparator(): String =
        (prefs.getString(PrefKeys.MERGE_SEPARATOR, "\\n---\\n") ?: "\\n---\\n")
            .replace("\\n", "\n")
            .replace("\\t", "\t")

    /** never | 7d | 30d | 90d → millis, or null when disabled. */
    fun trashRetentionMillis(): Long? = when (prefs.getString(PrefKeys.TRASH_PERIOD, "never")) {
        "7d" -> 7L * DAY
        "30d" -> 30L * DAY
        "90d" -> 90L * DAY
        else -> null
    }

    /** off | 1m | 5m | 30m | 1h → millis, or null. */
    fun autoClearMillis(): Long? = when (autoClearPeriod()) {
        "1m" -> 60_000L
        "5m" -> 5 * 60_000L
        "30m" -> 30 * 60_000L
        "1h" -> 60 * 60_000L
        else -> null
    }

    fun register(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregister(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    private companion object {
        const val DAY = 24L * 60L * 60L * 1000L
    }
}
