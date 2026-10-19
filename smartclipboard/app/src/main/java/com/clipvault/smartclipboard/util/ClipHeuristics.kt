package com.clipvault.smartclipboard.util

/** Pure text detectors shared by the detail screen and the keyboard. */
object ClipHeuristics {
    private val phone = Regex("""(?<!\d)(?:\+\d{1,3}[\s.-]?)?(?:\(?\d{2,4}\)?[\s.-]?)?\d{3,4}[\s.-]?\d{3,4}(?!\d)""")
    private val email = Regex("""[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}""", RegexOption.IGNORE_CASE)
    private val url = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)
    private val addressHint = Regex(
        """\d{1,6}\s+\w+.*\b(street|st|avenue|ave|road|rd|boulevard|blvd|lane|ln|drive|dr|way|court|ct)\b""",
        RegexOption.IGNORE_CASE,
    )

    fun firstPhone(text: String): String? = phone.find(text)?.value?.trim()
    fun firstEmail(text: String): String? = email.find(text)?.value?.trim()
    fun firstUrl(text: String): String? = url.find(text)?.value?.trim()?.trimEnd('.', ',', ')')
    fun looksLikeAddress(text: String): Boolean = addressHint.containsMatchIn(text)

    fun googleQuery(title: String, body: String): String {
        val basis = if (title.isNotBlank() && title != "Untitled") title else body
        return basis.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(120).orEmpty()
    }
}
