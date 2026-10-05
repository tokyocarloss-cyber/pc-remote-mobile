package com.pcremote.mobile

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private data class ScreenQuality(val name: String, val w: Int, val h: Int, val jpeg: Int)
private val ScreenQualities = listOf(
    ScreenQuality("HD", 1280, 720, 56),
    ScreenQuality("Full HD", 1920, 1080, 68),
    ScreenQuality("Full HD+", 2560, 1440, 78)
)

@Composable
fun SmoothLiveScreen() {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("nexus_stream", Context.MODE_PRIVATE) }
    var frame by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var actualFps by remember { mutableIntStateOf(0) }
    var count by remember { mutableIntStateOf(0) }
    var lastFpsAt by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var targetFps by remember { mutableIntStateOf(prefs.getInt("fps", 60).let { if (it in listOf(30,60,120)) it else 60 }) }
    var qualityIndex by remember { mutableIntStateOf(prefs.getInt("quality", 0).coerceIn(0, ScreenQualities.lastIndex)) }
    val quality = ScreenQualities[qualityIndex]

    LaunchedEffect(Api.host, Api.connected, targetFps, qualityIndex) {
        while (Api.connected && Api.host.isNotBlank()) {
            val started = System.nanoTime()
            val path = "/screen.jpg?w=${quality.w}&h=${quality.h}&q=${quality.jpeg}&fps=$targetFps&t=$started"
            val bytes = withContext(Dispatchers.IO) { RemoteClient.get(path) }
            if (bytes != null && bytes.isNotEmpty()) {
                val bmp = withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
                if (bmp != null) frame = bmp.asImageBitmap()
                count++
                val now = System.currentTimeMillis()
                if (now - lastFpsAt >= 1000) {
                    actualFps = count
                    count = 0
                    lastFpsAt = now
                }
            }
            val budgetNs = 1_000_000_000L / targetFps
            val elapsed = System.nanoTime() - started
            val waitMs = ((budgetNs - elapsed) / 1_000_000L).coerceAtLeast(0)
            if (waitMs > 0) delay(waitMs)
        }
    }

    Column(Modifier.fillMaxSize()) {
        if (!NexusFullscreen.active) {
            Title("Tela do PC", "Streaming pela rede local")
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(30,60,120).forEach { value ->
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
                Modifier.fillMaxSize().clip(if (NexusFullscreen.active) RoundedCornerShape(0.dp) else RoundedCornerShape(22.dp)).background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                val image = frame
                if (image != null) {
                    Image(bitmap = image, contentDescription = "Tela do PC", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(58.dp).background(NexusUi.PanelRaised, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.DesktopWindows, null, tint = NexusUi.Muted, modifier = Modifier.size(27.dp))
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(if (Api.connected) "Carregando primeira imagem…" else "Aguardando conexão", color = NexusUi.Muted)
                    }
                }
            }
        }

        if (!NexusFullscreen.active) {
            Spacer(Modifier.height(8.dp))
            Surface(color = NexusUi.Panel, shape = RoundedCornerShape(15.dp), border = BorderStroke(1.dp, NexusUi.Border), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).background(if (Api.connected) NexusUi.Success else NexusUi.Muted, CircleShape))
                        Spacer(Modifier.width(7.dp))
                        Text(if (Api.connected) quality.name.uppercase() else "OFFLINE", color = if (Api.connected) NexusUi.Success else NexusUi.Muted, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                    }
                    Text("$actualFps FPS real • alvo $targetFps", color = NexusUi.Muted, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun StreamOption(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        color = if (selected) Color(0xFF0B3345) else Color(0xFF0B1117),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (selected) NexusUi.Accent else NexusUi.Border),
        modifier = modifier.height(38.dp).clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, color = if (selected) NexusUi.Accent else NexusUi.Muted, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
        }
    }
}
