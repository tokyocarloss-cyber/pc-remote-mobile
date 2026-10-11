package com.pcremote.mobile

import android.content.Context
import android.net.Uri
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicBoolean
import androidx.compose.runtime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object RemoteClient {
    var lastError by mutableStateOf("")
    private fun result(c: HttpURLConnection): Boolean {
        val code = c.responseCode
        if (code == 401) Api.connected = false
        lastError = when { code in 200..299 -> ""; code == 401 -> "Dispositivo não autorizado"; code == 503 -> "Recurso indisponível no PC"; else -> "Comando recusado ($code)" }
        return code in 200..299
    }
    fun get(path: String, readTimeoutMs: Int = 2500): ByteArray? {
        if (Api.host.isBlank()) return null
        var c: HttpURLConnection? = null
        return try {
            c = SecureConnection.open(path); c.readTimeout = readTimeoutMs
            if (!result(c)) return null
            c.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(65536)
                while (true) {
                    val n = input.read(buffer); if (n < 0) break
                    if (out.size() + n > 24 * 1024 * 1024) throw java.io.IOException("Use streaming for large files")
                    out.write(buffer,0,n)
                }
                out.toByteArray()
            }
        } catch (_: Exception) { lastError = "Falha na conexão com o PC"; null }
        finally { c?.disconnect() }
    }
    fun post(path: String, data: ByteArray): Boolean {
        if (Api.host.isBlank()) return false
        var c: HttpURLConnection? = null
        var reusable = false
        return try {
            c = SecureConnection.open(path); c.requestMethod = "POST"; c.readTimeout = if(path in setOf("/mouse","/scroll","/zoom","/click")) 1500 else 5000; c.doOutput = true
            c.setRequestProperty("Content-Type","application/octet-stream"); c.setFixedLengthStreamingMode(data.size)
            c.outputStream.use { it.write(data) }
            val ok = result(c)
            // Consume the small acknowledgement so Android can pool this TLS socket.
            // disconnect() after every movement otherwise defeats keep-alive.
            val response = if(ok) c.inputStream else c.errorStream
            response?.use { input ->
                val buffer=ByteArray(1024);var total=0
                while(true) {
                    val n=input.read(buffer);if(n<0) break
                    total+=n;if(total>65536) throw java.io.IOException("Command reply too large")
                }
            }
            reusable = response != null
            ok
        } catch (_: Exception) { lastError = "Falha na conexão com o PC"; false }
        finally { if(!reusable) c?.disconnect() }
    }

    /** Direct upload for explicitly granted phone storage, without a 4 GB temp copy. */
    fun uploadFile(file: java.io.File): Boolean {
        if (!file.isFile || !file.canRead() || Api.host.isBlank()) return false
        var c: HttpURLConnection? = null
        return try {
            c=SecureConnection.open("/upload/"+URLEncoder.encode(file.name,"UTF-8"))
            c.requestMethod="POST";c.doOutput=true;c.readTimeout=15000
            c.setFixedLengthStreamingMode(file.length())
            c.outputStream.use { output -> file.inputStream().use { input ->
                input.copyTo(output,65536)
            } }
            result(c)
        } catch (_: Exception) {lastError="Falha no envio de arquivo";false}
        finally {c?.disconnect()}
    }

    fun download(path: String, output: OutputStream, cancelled: AtomicBoolean = AtomicBoolean(false), progress: (Long,Long)->Unit = {_,_->}): Boolean {
        var c: HttpURLConnection? = null
        return try {
            c = SecureConnection.open(path); c.readTimeout = 15000
            if (!result(c)) return false
            val total = c.contentLengthLong; var done = 0L
            c.inputStream.use { input ->
                val buffer = ByteArray(65536)
                while (true) {
                    if (cancelled.get()) return false
                    val n = input.read(buffer); if (n < 0) break
                    output.write(buffer,0,n); done += n; progress(done,total)
                }
            }
            total < 0 || done == total
        } catch (_: Exception) { lastError = "Download interrompido"; false }
        finally { c?.disconnect() }
    }
    fun upload(ctx: Context, uri: Uri, name: String, cancelled: AtomicBoolean = AtomicBoolean(false), progress: (Long,Long)->Unit = {_,_->}): Boolean {
        var c: HttpURLConnection? = null
        // Providers may not report a reliable size. Stage on disk, never in the Java heap.
        val temp = java.io.File.createTempFile("nexus-upload-", ".part",ctx.cacheDir)
        return try {
            ctx.contentResolver.openInputStream(uri)?.use { input -> temp.outputStream().use { output ->
                val buffer = ByteArray(65536); var staged = 0L
                while (true) {
                    if (cancelled.get()) return false
                    val n = input.read(buffer); if (n < 0) break
                    staged += n; if (staged > 4L*1024*1024*1024) throw java.io.IOException("Máximo 4 GB")
                    output.write(buffer,0,n)
                }
            } } ?: return false
            c = SecureConnection.open("/upload/"+URLEncoder.encode(name,"UTF-8")); c.requestMethod="POST"; c.doOutput=true; c.readTimeout=15000
            c.setFixedLengthStreamingMode(temp.length())
            var done = 0L
            c.outputStream.use { output -> temp.inputStream().use { input ->
                val buffer = ByteArray(65536)
                while (true) {
                    if (cancelled.get()) return false
                    val n=input.read(buffer); if(n<0) break
                    output.write(buffer,0,n); done+=n; progress(done,temp.length())
                }
            } }
            result(c)
        } catch (_: Exception) { lastError="Envio interrompido ou sem espaço temporário"; false }
        finally { c?.disconnect(); temp.delete() }
    }
}

@Composable
fun secureImage(path: String): ByteArray? {
    val image = remember { mutableStateOf<ByteArray?>(null) }
    LaunchedEffect(Api.host, path, Api.connected) {
        image.value = withContext(Dispatchers.IO) { RemoteClient.get(path) }
    }
    return image.value
}
