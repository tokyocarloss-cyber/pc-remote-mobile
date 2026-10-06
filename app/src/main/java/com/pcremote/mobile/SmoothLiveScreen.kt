package com.pcremote.mobile

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.net.HttpURLConnection
import java.net.URL

private data class ScreenQuality(val name: String, val w: Int, val h: Int, val jpeg: Int)
private val ScreenQualities = listOf(
    ScreenQuality("HD", 1280, 720, 55),
    ScreenQuality("Full HD", 1920, 1080, 68),
    ScreenQuality("Full HD+", 2560, 1440, 78)
)

@Composable
fun SmoothLiveScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { ctx.getSharedPreferences("nexus_stream", Context.MODE_PRIVATE) }
    var frame by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var actualFps by remember { mutableIntStateOf(0) }
    var count by remember { mutableIntStateOf(0) }
    var lastFpsAt by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var targetFps by remember { mutableIntStateOf(prefs.getInt("fps", 60).let { if (it in listOf(30,60,120)) it else 60 }) }
    var qualityIndex by remember { mutableIntStateOf(prefs.getInt("quality", 0).coerceIn(0, ScreenQualities.lastIndex)) }
    var streamError by remember { mutableStateOf("") }
    var transport by remember { mutableStateOf("STREAM") }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var shelfOpen by remember { mutableStateOf(false) }
    var controlsOpen by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(false) }
    var controlsEdit by remember { mutableStateOf(false) }
    val quality = ScreenQualities[qualityIndex]

    fun registerFrame(bytes: ByteArray) {
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return
        frame = bmp.asImageBitmap()
        Api.connected = true
        count++
        val now = System.currentTimeMillis()
        if (now - lastFpsAt >= 1000) {
            actualFps = count
            count = 0
            lastFpsAt = now
        }
    }

    fun hotkey(name: String) = scope.launch(Dispatchers.IO) { Api.post("/hotkey", name) }

    fun clickAt(pos: Offset, double: Boolean = false) {
        val s = viewport
        if (s.width <= 0 || s.height <= 0) return
        val imageAspect = quality.w.toFloat() / quality.h.toFloat()
        val boxAspect = s.width.toFloat() / s.height.toFloat()
        val drawW: Float
        val drawH: Float
        val left: Float
        val top: Float
        if (boxAspect > imageAspect) {
            drawH = s.height.toFloat(); drawW = drawH * imageAspect
            left = (s.width - drawW) / 2f; top = 0f
        } else {
            drawW = s.width.toFloat(); drawH = drawW / imageAspect
            left = 0f; top = (s.height - drawH) / 2f
        }
        if (pos.x < left || pos.x > left + drawW || pos.y < top || pos.y > top + drawH) return
        val nx = ((pos.x - left) / drawW).coerceIn(0f, 1f)
        val ny = ((pos.y - top) / drawH).coerceIn(0f, 1f)
        scope.launch(Dispatchers.IO) {
            Api.post("/mouse-abs", "$nx,$ny")
            Api.post("/click", "left")
            if (double) {
                delay(55)
                Api.post("/click", "left")
            }
        }
    }

    suspend fun rawStreamSession(): Boolean = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val path = "http://${Api.host}:8765/stream.raw?w=${quality.w}&h=${quality.h}&q=${quality.jpeg}&fps=$targetFps"
            conn = (URL(path).openConnection() as HttpURLConnection).apply {
                connectTimeout = 1800
                readTimeout = 2800
                useCaches = false
                setRequestProperty("Connection", "close")
            }
            if (conn.responseCode !in 200..299) return@withContext false
            transport = "STREAM"
            streamError = ""
            DataInputStream(BufferedInputStream(conn.inputStream, 1024 * 1024)).use { input ->
                while (currentCoroutineContext().isActive) {
                    val length = input.readInt()
                    if (length !in 512..20_000_000) return@withContext false
                    val bytes = ByteArray(length)
                    input.readFully(bytes)
                    withContext(Dispatchers.Main) { registerFrame(bytes) }
                }
            }
            true
        } catch (_: Exception) {
            false
        } finally {
            try { conn?.disconnect() } catch (_: Exception) {}
        }
    }

    suspend fun fallbackBurst(durationMs: Long = 4500L) = withContext(Dispatchers.IO) {
        transport = "COMPAT"
        val started = System.currentTimeMillis()
        while (currentCoroutineContext().isActive && System.currentTimeMillis() - started < durationMs && Api.host.isNotBlank()) {
            var conn: HttpURLConnection? = null
            try {
                val url = "http://${Api.host}:8765/screen.jpg?w=${quality.w}&h=${quality.h}&q=${quality.jpeg}&fps=$targetFps&t=${System.nanoTime()}"
                conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 1200
                    readTimeout = 1800
                    useCaches = false
                    setRequestProperty("Connection", "close")
                }
                if (conn.responseCode in 200..299) {
                    val bytes = conn.inputStream.use { it.readBytes() }
                    if (bytes.isNotEmpty()) withContext(Dispatchers.Main) {
                        registerFrame(bytes)
                        streamError = ""
                    }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) { streamError = "Reconectando transmissão…" }
            } finally {
                try { conn?.disconnect() } catch (_: Exception) {}
            }
            delay((1000L / targetFps.coerceIn(15, 30)).coerceAtLeast(25L))
        }
    }

    LaunchedEffect(Api.host, targetFps, qualityIndex) {
        if (Api.host.isBlank()) return@LaunchedEffect
        actualFps = 0
        count = 0
        while (isActive && Api.host.isNotBlank()) {
            streamError = if (frame == null) "Conectando transmissão…" else ""
            val rawOk = rawStreamSession()
            if (!rawOk && isActive) {
                streamError = if (frame == null) "Usando modo compatível…" else ""
                fallbackBurst()
            }
            if (isActive) delay(120)
        }
    }

    Column(Modifier.fillMaxSize()) {
        if (!NexusFullscreen.active) {
            Title("Tela do PC", "Transmissão de baixa latência com toque")
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(30, 60, 120).forEach { value ->
                    StreamOption("$value FPS", targetFps == value, Modifier.weight(1f)) {
                        targetFps = value
                        prefs.edit().putInt("fps", value).apply()
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ScreenQualities.forEachIndexed { index, item ->
                    StreamOption(item.name, qualityIndex == index, Modifier.weight(1f)) {
                        qualityIndex = index
                        prefs.edit().putInt("quality", index).apply()
                    }
                }
            }
            Spacer(Modifier.height(9.dp))
        }

        Surface(
            color = Color.Black,
            shape = if (NexusFullscreen.active) RoundedCornerShape(0.dp) else RoundedCornerShape(22.dp),
            border = if (NexusFullscreen.active) null else BorderStroke(1.dp, NexusUi.Border),
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(if (NexusFullscreen.active) RoundedCornerShape(0.dp) else RoundedCornerShape(22.dp))
                    .background(Color.Black)
                    .onSizeChanged { viewport = it }
                    .pointerInput(qualityIndex, viewport) {
                        detectTapGestures(
                            onTap = { clickAt(it, false) },
                            onDoubleTap = { clickAt(it, true) }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                val image = frame
                if (image != null) {
                    Image(bitmap = image, contentDescription = "Tela do PC", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    if (streamError.isNotBlank()) {
                        Surface(
                            color = Color.Black.copy(alpha = .58f),
                            shape = RoundedCornerShape(99.dp),
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)
                        ) { Text(streamError, color = NexusUi.Text, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall) }
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = NexusUi.Accent, strokeWidth = 3.dp, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(14.dp))
                        Text(if (Api.host.isNotBlank()) streamError.ifBlank { "Carregando primeira imagem…" } else "Aguardando conexão", color = NexusUi.Muted)
                    }
                }

                if (controlsVisible) {
                    LiveControlsOverlay(editMode = controlsEdit)
                    if (controlsEdit) {
                        Surface(
                            color = Color.Black.copy(alpha = .72f),
                            shape = RoundedCornerShape(99.dp),
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp)
                        ) {
                            Row(
                                Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("EDITANDO CONTROLES", color = NexusUi.Accent, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                                Spacer(Modifier.width(8.dp))
                                TextButton(onClick = { controlsEdit = false }) { Text("CONCLUIR") }
                            }
                        }
                    }
                }

                if (NexusFullscreen.active && controlsOpen) {
                    Box(Modifier.fillMaxSize()) {
                        AdvancedGamepad(overlay = true)
                    }
                }

                if (NexusFullscreen.active) {
                    Box(
                        Modifier.align(Alignment.TopCenter).padding(top = 3.dp).width(74.dp).height(12.dp)
                            .background(Color(0x66000000), RoundedCornerShape(99.dp)).clickable { shelfOpen = !shelfOpen },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(Modifier.width(38.dp).height(3.dp).background(NexusUi.Accent, RoundedCornerShape(99.dp)))
                    }

                    if (shelfOpen) {
                        Surface(
                            color = NexusUi.Panel,
                            shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(1.dp, NexusUi.Border),
                            shadowElevation = 16.dp,
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = 18.dp)
                        ) {
                            Row(Modifier.padding(6.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                                EdgeShortcut("ALT F4", Icons.Default.Close) { hotkey("ALT_F4") }
                                EdgeShortcut("ALT TAB", Icons.Default.SwapHoriz) { hotkey("ALT_TAB") }
                                EdgeShortcut("PRINT", Icons.Default.CropFree) { hotkey("SNIP") }
                                EdgeShortcut("WIN D", Icons.Default.DesktopWindows) { hotkey("WIN_D") }
                                EdgeShortcut(if (controlsVisible) "CTRL ON" else "CTRL", Icons.Default.SportsEsports) {
                                    controlsVisible = !controlsVisible
                                    if (!controlsVisible) controlsEdit = false
                                }
                                if (controlsVisible) {
                                    EdgeShortcut(if (controlsEdit) "OK EDIT" else "EDITAR", Icons.Default.Edit) {
                                        controlsEdit = !controlsEdit
                                    }
                                }
                                EdgeShortcut("ESC", Icons.Default.ArrowBack) { hotkey("ESC") }
                                EdgeShortcut("SAIR", Icons.Default.FullscreenExit) {
                                    NexusFullscreen.active = false
                                    shelfOpen = false
                                    controlsEdit = false
                                }
                            }
                        }
                    }
                }
            }
        }

        if (!NexusFullscreen.active) {
            Spacer(Modifier.height(8.dp))
            Surface(color = NexusUi.Panel, shape = RoundedCornerShape(15.dp), border = BorderStroke(1.dp, NexusUi.Border), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).background(if (frame != null) NexusUi.Success else NexusUi.Muted, CircleShape))
                        Spacer(Modifier.width(7.dp))
                        Text(
                            if (frame != null) "${quality.name.uppercase()} • TOUCH • $transport" else "SEM VÍDEO",
                            color = if (frame != null) NexusUi.Success else NexusUi.Muted,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Text("$actualFps FPS real • alvo $targetFps", color = NexusUi.Muted, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun EdgeShortcut(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, action: () -> Unit) {
    Column(
        Modifier.width(54.dp).height(50.dp).clickable(onClick = action),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, label, tint = NexusUi.Accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, color = NexusUi.Text, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

@Composable
private fun StreamOption(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        color = if (selected) NexusUi.Accent.copy(alpha = .13f) else NexusUi.Panel,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (selected) NexusUi.Accent else NexusUi.Border),
        modifier = modifier.height(38.dp).clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, color = if (selected) NexusUi.Accent else NexusUi.Muted, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
        }
    }
}
