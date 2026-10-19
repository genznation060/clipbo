package com.clipvault.smartclipboard.util

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import com.clipvault.smartclipboard.data.db.NoteEntity
import java.text.DateFormat
import java.util.Date

/** Renders a note to a small HTML document and hands it to [PrintManager]. */
object PrintHelper {
    fun print(context: Context, note: NoteEntity) {
        val webView = WebView(context)
        val html = template(note)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                val manager = context.getSystemService(PrintManager::class.java) ?: return
                val adapter = webView.createPrintDocumentAdapter(note.title.ifBlank { "Note" })
                manager.print(
                    note.title.ifBlank { "ClipVault note" },
                    adapter,
                    PrintAttributes.Builder().build(),
                )
            }
        }
        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }

    private fun template(note: NoteEntity): String {
        val whenText = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(note.createdAt))
        return """
            <!DOCTYPE html>
            <html><head><meta charset="utf-8">
            <style>
              body { font-family: Georgia, serif; color: #1a1814; margin: 32px; }
              h1 { font-size: 22px; font-weight: 600; margin: 0 0 8px; }
              .meta { color: #5c564c; font-family: sans-serif; font-size: 12px; margin-bottom: 20px; }
              pre { white-space: pre-wrap; font-family: Georgia, serif; font-size: 15px; line-height: 1.5; }
            </style></head>
            <body>
              <h1>${escape(note.title.ifBlank { "Untitled" })}</h1>
              <div class="meta">${escape(whenText)}</div>
              <pre>${escape(note.body)}</pre>
            </body></html>
        """.trimIndent()
    }

    private fun escape(value: String): String = buildString(value.length) {
        value.forEach { ch ->
            when (ch) {
                '&' -> append("&")
                '<' -> append("<")
                '>' -> append(">")
                '"' -> append(""")
                else -> append(ch)
            }
        }
    }
}
