package com.pcremote.mobile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.*
import java.net.*

object Discovery {
    var detail by mutableStateOf("Aguardando busca")
    var lastMethod by mutableStateOf("—")
    var scanning by mutableStateOf(false)

    fun localIPv4s(): List<String> = try {
        NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .filter { !it.isLoopbackAddress }
            .mapNotNull { it.hostAddress }
            .distinct()
    } catch (_: Exception) { emptyList() }

    private fun localPrefixes(): List<String> = localIPv4s().map { it.substringBeforeLast('.') }.distinct()

    fun networkSummary(): String = localIPv4s().joinToString().ifBlank { "sem IPv4 local" }

    suspend fun checkHost(host: String): Boolean = withContext(Dispatchers.IO) {
        val ip = host.trim().removePrefix("http://").removePrefix("https://").substringBefore(':').substringBefore('/')
        if (ip.isBlank()) return@withContext false
        try {
            val c = URL("http://$ip:8765/ping").openConnection() as HttpURLConnection
            c.connectTimeout = 1500; c.readTimeout = 1500
            val ok = c.responseCode == 200 && c.inputStream.bufferedReader().readText().contains("PC Remote")
            c.disconnect(); ok
        } catch (_: Exception) { false }
    }

    private suspend fun udpDiscover(): String? = withContext(Dispatchers.IO) {
        try {
            DatagramSocket().use { socket ->
                socket.broadcast = true; socket.soTimeout = 1200
                val payload = "NEXUS_DISCOVER".toByteArray()
                val targets = mutableSetOf(InetAddress.getByName("255.255.255.255"))
                localPrefixes().forEach { prefix -> runCatching { targets += InetAddress.getByName("$prefix.255") } }
                targets.forEach { target -> runCatching { socket.send(DatagramPacket(payload,payload.size,target,8766)) } }
                val buffer = ByteArray(256); val response = DatagramPacket(buffer,buffer.size); socket.receive(response)
                val text = String(response.data,0,response.length)
                if(text.startsWith("NEXUS|")) text.split('|').getOrNull(1)?.takeIf{it.isNotBlank()} else response.address.hostAddress
            }
        } catch (_: Exception) { null }
    }

    suspend fun findPc(): String? {
        scanning = true; detail = "Descoberta rápida na rede…"
        try {
            val udp=udpDiscover()
            if(udp!=null && checkHost(udp)){lastMethod="Descoberta rápida";detail="PC encontrado em $udp";return udp}
            val prefixes=localPrefixes()
            if(prefixes.isEmpty()){lastMethod="Sem rede local";detail="Celular sem IPv4 local utilizável";return null}
            detail="Busca detalhada em ${prefixes.joinToString()}…"
            val found=coroutineScope { prefixes.flatMap{prefix->(1..254).map{n->async(Dispatchers.IO){val ip="$prefix.$n";if(checkHost(ip))ip else null}}}.awaitAll().firstOrNull{it!=null} }
            if(found!=null){lastMethod="Varredura local";detail="PC encontrado em $found"}
            else{lastMethod="Não encontrado";detail="Nenhum NEXUS respondeu na rede local"}
            return found
        } finally { scanning=false }
    }
}
