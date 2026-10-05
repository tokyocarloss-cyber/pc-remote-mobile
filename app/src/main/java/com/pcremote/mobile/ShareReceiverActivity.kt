package com.pcremote.mobile

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import java.net.URLEncoder
import kotlin.concurrent.thread

class ShareReceiverActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle(intent)
    }

    private fun handle(i: Intent) {
        val uris = mutableListOf<Uri>()
        when (i.action) {
            Intent.ACTION_SEND -> i.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let(uris::add)
            Intent.ACTION_SEND_MULTIPLE -> i.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.let(uris::addAll)
        }
        val text = i.getStringExtra(Intent.EXTRA_TEXT)
        thread {
            var sent = 0
            uris.forEach { if (sendUri(it)) sent++ }
            if (!text.isNullOrBlank() && RemoteClient.post("/clipboard", text.toByteArray())) sent++
            runOnUiThread {
                Toast.makeText(this, if (sent > 0) "Enviado ao PC" else "Não foi possível enviar ao PC", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun displayName(uri: Uri): String = try {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "arquivo"
    } catch (_: Exception) { uri.lastPathSegment?.substringAfterLast('/') ?: "arquivo" }

    private fun sendUri(uri: Uri): Boolean = try {
        val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return false
        if (bytes.isEmpty()) return false
        RemoteClient.post("/upload/" + URLEncoder.encode(displayName(uri).takeLast(180), "UTF-8"), bytes)
    } catch (_: Exception) { false }
}
