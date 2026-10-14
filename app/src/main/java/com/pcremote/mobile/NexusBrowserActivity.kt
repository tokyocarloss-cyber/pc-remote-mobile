package com.pcremote.mobile

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.ViewGroup
import android.webkit.DownloadListener
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.URI

/**
 * Small first-party browser. A website never receives access to NEXUS commands:
 * only the Android WebView download callback may enqueue an HTTPS URL, without
 * cookies, arbitrary headers, credentials, or file access.
 */
class NexusBrowserActivity : ComponentActivity() {
    private lateinit var browser: WebView
    private lateinit var address: EditText
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private var games = false
    private var lastDownloadUrl = ""
    private var lastDownloadTime = 0L

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun message(value: String) {
        status.text = value
        Toast.makeText(this, value, Toast.LENGTH_SHORT).show()
    }

    private fun destination(raw: String): String? = try {
        val parsed = URI(raw.trim())
        if (parsed.scheme?.lowercase() != "https" || parsed.host.isNullOrBlank() ||
            parsed.userInfo != null || parsed.port !in -1..443) null
        else parsed.toASCIIString()
    } catch (_: Exception) { null }

    private fun navigate(raw: String) {
        val value = if (raw.contains("://")) raw else "https://" + raw
        val link = destination(value)
        if (link == null) message("Use um endereço HTTPS válido.")
        else browser.loadUrl(link)
    }

    private fun sendDownload(raw: String, disposition: String? = null) {
        val url = destination(raw)
        if (url == null) {
            message("Não é possível enviar este tipo de link ao PC.")
            return
        }
        // Prevent a single click from being handled by both the WebViewClient and DownloadListener.
        val now = SystemClock.elapsedRealtime()
        if (url == lastDownloadUrl && now - lastDownloadTime < 3500L) return
        lastDownloadUrl = url
        lastDownloadTime = now
        val filename = URLUtil.guessFileName(url, disposition, null)
            .take(100).ifBlank { "Download pelo navegador NEXUS" }
        val payload = JSONObject().put("url", url).put("games", games)
        HubStore.enqueue(this, filename, "web_download", payload)
        message("Download salvo na fila! O PC baixará quando estiver conectado.")
        if (Api.connected && HubStore.key(this).isNotBlank()) {
            lifecycleScope.launch {
                HubSync.trySync(this@NexusBrowserActivity, Api.host)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        games = intent.getBooleanExtra("games", false)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(18, 21, 30))
        }
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun control(label: String, click: () -> Unit): Button =
            Button(this).apply {
                text = label
                minWidth = dp(44)
                setPadding(dp(3), 0, dp(3), 0)
                setOnClickListener { click() }
                bar.addView(this, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)))
            }
        control("‹") { if (browser.canGoBack()) browser.goBack() else finish() }
        control("›") { if (browser.canGoForward()) browser.goForward() }
        address = EditText(this).apply {
            singleLine = true
            textSize = 12f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            hint = "https://"
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_GO
            setOnEditorActionListener { _, _, _ ->
                navigate(text.toString())
                clearFocus()
                true
            }
        }
        bar.addView(address, LinearLayout.LayoutParams(0, dp(48), 1f))
        control("↻") { browser.reload() }
        root.addView(bar)
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
        }
        root.addView(progress, LinearLayout.LayoutParams(-1, dp(3)))
        browser = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.setSupportMultipleWindows(false)
            settings.safeBrowsingEnabled = true
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    if (!request.isForMainFrame) return false
                    val link = request.url.toString()
                    if (destination(link) == null) {
                        message("NEXUS bloqueou um link que não usa HTTPS.")
                        return true
                    }
                    if (Regex("(?i)\\.(zip|rar|7z|tar|gz|iso|mp4|mkv|webm|avi|pdf)(\\?|$)")
                            .containsMatchIn(link)) {
                        sendDownload(link)
                        return true
                    }
                    return false
                }
                override fun onPageFinished(view: WebView, url: String) {
                    address.setText(url)
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    progress.progress = newProgress
                }
            }
            setDownloadListener(DownloadListener { url, _, disposition, _, _ ->
                sendDownload(url, disposition)
            })
        }
        root.addView(browser, LinearLayout.LayoutParams(-1, 0, 1f))
        status = TextView(this).apply {
            setTextColor(Color.LTGRAY)
            textSize = 12f
            text = "Downloads são enviados para a fila do PC, não para o celular."
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        root.addView(status)
        setContentView(root)
        if (savedInstanceState == null) {
            navigate(intent.getStringExtra("url") ?: "https://store.steampowered.com/")
        } else browser.restoreState(savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        browser.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (browser.canGoBack()) browser.goBack()
        else super.onBackPressed()
    }

    override fun onDestroy() {
        browser.destroy()
        super.onDestroy()
    }
}
