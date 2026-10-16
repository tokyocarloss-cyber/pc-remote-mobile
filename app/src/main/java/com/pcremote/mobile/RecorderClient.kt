package com.pcremote.mobile

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL

/** Authenticated local recording/mirror API; never logs or displays the key. */
object RecorderClient {
    private fun connection(ctx: Context, path: String): HttpURLConnection? {
        if (Api.host.isBlank()) return null
        val key = HubStore.key(ctx)
        if (key.isBlank()) return null
        return (URL("http://${Api.host}:8765$path").openConnection() as HttpURLConnection).apply {
            connectTimeout = 1500
            readTimeout = 2800
            useCaches = false
            setRequestProperty("X-Nexus-Central-Key", key)
            setRequestProperty("Cache-Control", "no-store")
        }
    }

    fun get(ctx: Context, path: String): ByteArray? {
        var client: HttpURLConnection? = null
        return try {
            client = connection(ctx, path) ?: return null
            if (client.responseCode in 200..299) client.inputStream.use { it.readBytes() } else null
        } catch (_: Exception) {
            null
        } finally {
            client?.disconnect()
        }
    }

    fun post(ctx: Context, path: String, payload: ByteArray): Boolean {
        var client: HttpURLConnection? = null
        return try {
            client = connection(ctx, path) ?: return false
            client.requestMethod = "POST"
            client.setRequestProperty("Content-Type", "application/octet-stream")
            client.setFixedLengthStreamingMode(payload.size)
            client.doOutput = true
            client.outputStream.use { it.write(payload) }
            client.responseCode in 200..299
        } catch (_: Exception) {
            false
        } finally {
            client?.disconnect()
        }
    }
}
