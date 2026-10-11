package com.pcremote.mobile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class RemoteFile(val name: String, val size: Long)
private data class Reply(val status: Int, val bytes: ByteArray) {
    val json: JSONObject get() = runCatching { JSONObject(String(bytes, Charsets.UTF_8)) }.getOrDefault(JSONObject())
    val ok: Boolean get() = status in 200..299
}

object RemoteApi {
    private const val PORT = 8765
    var host by mutableStateOf("")
        private set
    private var token = ""
    var connected by mutableStateOf(false)
        private set
    var error by mutableStateOf("")
        private set
    var gamepadReady by mutableStateOf(false)
        private set
    var computer by mutableStateOf("Meu PC")
        private set
    var uploadProgress by mutableIntStateOf(0)
        private set

    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences("nexus", Context.MODE_PRIVATE)
        host = prefs.getString("host", "") ?: ""
        token = prefs.getString("token", "") ?: ""
    }

    private fun isLanIPv4(value: String): Boolean {
        val parts = value.split(".")
        if (parts.size != 4) return false
        val p = parts.map { it.toIntOrNull() ?: return false }
        if (p.any { it !in 0..255 }) return false
        return p[0] == 10 || p[0] == 127 || (p[0] == 192 && p[1] == 168) ||
            (p[0] == 172 && p[1] in 16..31) || (p[0] == 169 && p[1] == 254)
    }

    private fun request(method: String, path: String, body: ByteArray? = null, auth: Boolean = true): Reply {
        val conn = (URL("http://$host:$PORT$path").openConnection() as HttpURLConnection)
        try {
            conn.requestMethod = method
            conn.connectTimeout = 3000
            conn.readTimeout = 6500
            conn.useCaches = false
            if (auth) conn.setRequestProperty("X-Nexus-Token", token)
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "text/plain; charset=utf-8")
                conn.setFixedLengthStreamingMode(body.size)
                conn.outputStream.use { it.write(body) }
            }
            val code = conn.responseCode
            val bytes = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.use { it.readBytes() } ?: byteArrayOf()
            return Reply(code, bytes)
        } finally { conn.disconnect() }
    }

    suspend fun pair(context: Context, ip: String, pin: String): Boolean = withContext(Dispatchers.IO) {
        val target = ip.trim()
        if (!isLanIPv4(target)) {
            error = "Use um IPv4 local, por exemplo 192.168.1.10."
            return@withContext false
        }
        if (!pin.matches(Regex("[0-9]{6}"))) {
            error = "Digite o PIN de 6 números exibido no PC."
            return@withContext false
        }
        host = target
        try {
            val reply = request("POST", "/pair", pin.toByteArray(), auth = false)
            if (!reply.ok) {
                error = reply.json.optString("error", "Não foi possível parear.")
                connected = false
                return@withContext false
            }
            token = reply.json.getString("token")
            context.getSharedPreferences("nexus", Context.MODE_PRIVATE)
                .edit().putString("host", host).putString("token", token).apply()
            return@withContext status()
        } catch (e: Exception) {
            error = "Sem conexão com o PC. Confira IP, rede Wi-Fi e firewall."
            connected = false
            return@withContext false
        }
    }

    suspend fun status(): Boolean = withContext(Dispatchers.IO) {
        if (host.isBlank() || token.isBlank()) {
            connected = false
            return@withContext false
        }
        try {
            val reply = request("GET", "/status")
            connected = reply.ok
            if (reply.ok) {
                computer = reply.json.optString("computer", "Meu PC")
                gamepadReady = reply.json.optBoolean("gamepad", false)
                error = ""
            } else {
                gamepadReady = false
                error = if (reply.status == 401) "Pareamento expirado. Informe o PIN novamente."
                    else reply.json.optString("error", "PC indisponível")
            }
            reply.ok
        } catch (e: Exception) {
            connected = false
            gamepadReady = false
            error = "PC desconectado. Confirme que o NEXUS está aberto no Windows."
            false
        }
    }

    fun forget(context: Context) {
        token = ""
        host = ""
        connected = false
        gamepadReady = false
        context.getSharedPreferences("nexus", Context.MODE_PRIVATE).edit().clear().apply()
    }

    suspend fun send(path: String, value: String): Boolean = withContext(Dispatchers.IO) {
        if (!connected) return@withContext false
        try {
            val result = request("POST", path, value.toByteArray(Charsets.UTF_8))
            if (!result.ok) error = result.json.optString("error", "Falha ao enviar comando.")
            if (result.status == 401) connected = false
            result.ok
        } catch (e: Exception) {
            error = "Falha na comunicação com o PC."
            connected = false
            false
        }
    }

    suspend fun apps(): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        try {
            val reply = request("GET", "/apps")
            if (!reply.ok) return@withContext emptyList()
            val a = reply.json.getJSONArray("apps")
            (0 until a.length()).map { i ->
                val obj = a.getJSONObject(i)
                obj.getString("id") to obj.getString("name")
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun files(): List<RemoteFile> = withContext(Dispatchers.IO) {
        try {
            val reply = request("GET", "/files")
            if (!reply.ok) return@withContext emptyList()
            val a = reply.json.getJSONArray("files")
            (0 until a.length()).map { i ->
                val obj = a.getJSONObject(i)
                RemoteFile(obj.getString("name"), obj.getLong("size"))
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun screenshot(quality: Int): Bitmap? = withContext(Dispatchers.IO) {
        if (!connected) return@withContext null
        try {
            val reply = request("GET", "/screen.jpg?q=$quality")
            if (!reply.ok || reply.bytes.isEmpty()) null
            else BitmapFactory.decodeByteArray(reply.bytes, 0, reply.bytes.size)
        } catch (e: Exception) { null }
    }

    suspend fun upload(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        if (!connected) return@withContext "Conecte ao PC primeiro."
        var name = "arquivo"
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) name = it.getString(0) ?: name
        }
        name = name.substringAfterLast('/').substringAfterLast('\\').take(120)
        val temp = File.createTempFile("nexus-", ".tmp", context.cacheDir)
        try {
            var count = 0L
            context.contentResolver.openInputStream(uri)?.use { source ->
                FileOutputStream(temp).use { destination ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = source.read(buffer)
                        if (n < 0) break
                        count += n
                        if (count > 512L * 1024 * 1024) return@withContext "Limite: 512 MB por arquivo."
                        destination.write(buffer, 0, n)
                    }
                }
            } ?: return@withContext "Não foi possível abrir o arquivo."
            uploadProgress = 0
            val conn = URL("http://$host:$PORT/upload/" + Uri.encode(name)).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.connectTimeout = 5000
                conn.readTimeout = 20000
                conn.doOutput = true
                conn.setRequestProperty("X-Nexus-Token", token)
                conn.setFixedLengthStreamingMode(temp.length())
                temp.inputStream().use { input ->
                    conn.outputStream.use { out ->
                        val buffer = ByteArray(64 * 1024)
                        var sent = 0L
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            out.write(buffer, 0, n)
                            sent += n
                            uploadProgress = if (count > 0) ((100 * sent) / count).toInt() else 100
                        }
                    }
                }
                val code = conn.responseCode
                if (code in 200..299) "Arquivo enviado: $name"
                else "Falha no envio (HTTP $code)."
            } finally { conn.disconnect() }
        } catch (e: Exception) {
            "Falha ao enviar arquivo: " + (e.localizedMessage ?: "erro de rede")
        } finally {
            temp.delete()
            uploadProgress = 0
        }
    }

    suspend fun download(context: Context, name: String, output: Uri): String = withContext(Dispatchers.IO) {
        if (!connected) return@withContext "Conecte ao PC primeiro."
        val conn = URL("http://$host:$PORT/download/" + Uri.encode(name)).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 5000
            conn.readTimeout = 20000
            conn.setRequestProperty("X-Nexus-Token", token)
            if (conn.responseCode != 200) return@withContext "Não foi possível baixar o arquivo."
            val dest = context.contentResolver.openOutputStream(output) ?: return@withContext "Destino inválido."
            conn.inputStream.use { source -> dest.use { source.copyTo(it, 64 * 1024) } }
            "Arquivo salvo: $name"
        } catch (e: Exception) {
            "Falha no download: " + (e.localizedMessage ?: "erro de rede")
        } finally { conn.disconnect() }
    }
}
