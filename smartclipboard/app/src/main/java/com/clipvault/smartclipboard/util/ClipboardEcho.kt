package com.clipvault.smartclipboard.util

/**
 * The monitor hears every clipboard write, including ones ClipVault itself
 * makes (copy, share). Call [suppressNext] immediately before those writes.
 */
object ClipboardEcho {
    @Volatile private var pending: String? = null

    fun suppressNext(text: String) {
        pending = text.trim()
    }

    fun consume(text: String): Boolean {
        val expected = pending ?: return false
        return if (expected == text.trim()) {
            pending = null
            true
        } else {
            false
        }
    }
}
