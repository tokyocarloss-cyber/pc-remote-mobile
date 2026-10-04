package com.pcremote.mobile

import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URL

object Discovery {
    private fun localPrefixes(): List<String> = try {
        NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .filter { !it.isLoopbackAddress }
            .mapNotNull { a -> a.hostAddress?.substringBeforeLast('.') }
            .distinct()
    } catch (_: Exception) { emptyList() }

    suspend fun findPc(): String? = coroutineScope {
        val prefixes = localPrefixes()
        if (prefixes.isEmpty()) return@coroutineScope null
        val jobs = prefixes.flatMap { prefix ->
            (1..254).map { n -> async(Dispatchers.IO) {
                val ip = "$prefix.$n"
                try {
                    val c = URL("http://$ip:8765/ping").openConnection() as HttpURLConnection
                    c.connectTimeout = 180; c.readTimeout = 180
                    val ok = c.responseCode == 200 && c.inputStream.bufferedReader().readText().contains("PC Remote")
                    c.disconnect(); if (ok) ip else null
                } catch (_: Exception) { null }
            }}
        }
        var found: String? = null
        for (j in jobs) { val v = j.await(); if (v != null && found == null) found = v }
        found
    }
}
