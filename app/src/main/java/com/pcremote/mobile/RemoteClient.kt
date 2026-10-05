package com.pcremote.mobile

import java.net.HttpURLConnection
import java.net.URL

object RemoteClient {
    fun get(path: String): ByteArray? {
        if (Api.host.isBlank()) return null
        return try {
            val c = URL("http://${Api.host}:8765$path").openConnection() as HttpURLConnection
            c.connectTimeout = 1200
            c.readTimeout = 2500
            c.useCaches = false
            c.setRequestProperty("Cache-Control", "no-cache")
            val bytes = if (c.responseCode in 200..299) c.inputStream.use { it.readBytes() } else null
            Api.connected = bytes != null
            c.disconnect()
            bytes
        } catch (_: Exception) {
            Api.connected = false
            null
        }
    }

    fun post(path: String, data: ByteArray): Boolean {
        if (Api.host.isBlank()) return false
        return try {
            val c = URL("http://${Api.host}:8765$path").openConnection() as HttpURLConnection
            c.requestMethod = "POST"
            c.connectTimeout = 1500
            c.readTimeout = 5000
            c.doOutput = true
            c.useCaches = false
            c.setRequestProperty("Content-Type", "application/octet-stream")
            c.setFixedLengthStreamingMode(data.size)
            c.outputStream.use {
                it.write(data)
                it.flush()
            }
            val ok = c.responseCode in 200..299
            Api.connected = ok
            c.disconnect()
            ok
        } catch (_: Exception) {
            Api.connected = false
            false
        }
    }
}
