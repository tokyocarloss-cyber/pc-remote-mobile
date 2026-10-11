package com.pcremote.mobile

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.compose.runtime.*
import java.net.URL
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.X509Certificate
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.net.ssl.*
import org.json.JSONObject

/** Certificate-bound local transport. Untrusted discovery never sends credentials. */
object SecureConnection {
    private lateinit var context: Context
    var pendingHost by mutableStateOf("")
    var pendingFingerprint by mutableStateOf("")
    var error by mutableStateOf("")
    private val dismissed = mutableSetOf<String>()
    private fun prefs() = context.getSharedPreferences("nexus_secure", Context.MODE_PRIVATE)
    fun init(ctx: Context) { context = ctx.applicationContext }
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("nexus-device", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("nexus-device", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    private fun token(host: String): String = runCatching {
        val bytes = Base64.decode(prefs().getString("token:$host", ""), Base64.NO_WRAP)
        Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0,12)))
            String(doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
        }
    }.getOrDefault("")
    private fun save(host: String, fingerprint: String, token: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val encoded = Base64.encodeToString(cipher.iv + cipher.doFinal(token.toByteArray()), Base64.NO_WRAP)
        prefs().edit().putString("pin:$host", fingerprint).putString("token:$host", encoded).putString("last_host", host).apply()
    }
    fun lastHost(): String = prefs().getString("last_host", "").orEmpty()
    private fun fingerprint(cert: X509Certificate) = MessageDigest.getInstance("SHA-256").digest(cert.encoded).joinToString("") { "%02X".format(it) }
    private fun connection(host: String, path: String, pin: String?, observed: ((String)->Unit)? = null): HttpsURLConnection {
        val trust = object: X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                require(chain.isNotEmpty())
                val actual = fingerprint(chain[0])
                if (pin != null && actual != pin) throw java.security.cert.CertificateException("Certificado do PC mudou. Confira o pareamento.")
                observed?.invoke(actual)
            }
        }
        val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), null) }
        return (URL("https://$host:8765$path").openConnection() as HttpsURLConnection).apply {
            sslSocketFactory = ssl.socketFactory
            // Identity is the certificate SHA-256 checked above, independent of DHCP hostname.
            hostnameVerifier = HostnameVerifier { _, _ -> true }
            connectTimeout = 1500; readTimeout = 3000; useCaches = false
            instanceFollowRedirects = false
        }
    }
    fun open(path: String, host: String = Api.host): HttpsURLConnection {
        val pin = prefs().getString("pin:$host", null) ?: kotlin.error("PC não autorizado")
        return connection(host, path, pin).apply { setRequestProperty("Authorization", "Bearer ${token(host)}") }
    }
    fun probe(host: String): Boolean {
        var fingerprint = ""
        val c = connection(host, "/ping", null) { fingerprint = it }
        return try {
            val ok = c.responseCode == 200 && c.inputStream.bufferedReader().use { it.readText() }.contains("PC Remote")
            if (ok && token(host).isBlank() && host !in dismissed && pendingHost.isBlank()) {
                pendingFingerprint = fingerprint; pendingHost = host
            }
            ok
        } catch (_: Exception) { false } finally { c.disconnect() }
    }
    fun authenticated(host: String): Boolean {
        if (token(host).isBlank()) return false
        val c = runCatching { open("/status",host) }.getOrNull() ?: return false
        return try {
            if (c.responseCode == 401) {
                prefs().edit().remove("token:$host").remove("pin:$host").apply()
                false
            } else c.responseCode == 200
        } catch (_: Exception) { false } finally { c.disconnect() }
    }
    fun dismiss() { dismissed += pendingHost; pendingHost = "" }
    fun retryPairing() { dismissed.clear() }
    fun pair(pin: String): Boolean {
        val host = pendingHost; val fingerprint = pendingFingerprint
        val c = connection(host, "/pair", fingerprint)
        return try {
            c.requestMethod = "POST"; c.doOutput = true
            val body = JSONObject().put("pin",pin).put("name",android.os.Build.MODEL).toString().toByteArray()
            c.setFixedLengthStreamingMode(body.size)
            c.outputStream.use { it.write(body) }
            if (c.responseCode != 200) { error = "PIN inválido ou expirado. Abra Parear celular na bandeja do PC."; false }
            else {
                val token = JSONObject(c.inputStream.bufferedReader().use { it.readText() }).getString("token")
                save(host,fingerprint,token); pendingHost = ""; error = ""
                Api.host = host; Api.connected = true; Api.connectionMethod = "TLS • dispositivo autorizado"; true
            }
        } catch (_: Exception) { error = "Falha na conexão segura. Confira o certificado do PC."; false }
        finally { c.disconnect() }
    }
}
