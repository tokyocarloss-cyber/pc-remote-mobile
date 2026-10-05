package com.pcremote.mobile

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun SmoothLiveScreen() {
    var frame by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var fps by remember { mutableIntStateOf(0) }
    var count by remember { mutableIntStateOf(0) }
    var lastFpsAt by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Api.host, Api.connected) {
        while (Api.connected) {
            val bytes = withContext(Dispatchers.IO) { RemoteClient.get("/screen.jpg?t=${System.nanoTime()}") }
            if (bytes != null && bytes.isNotEmpty()) {
                val bmp = withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
                if (bmp != null) frame = bmp.asImageBitmap()
                count++
                val now = System.currentTimeMillis()
                if (now - lastFpsAt >= 1000) {
                    fps = count
                    count = 0
                    lastFpsAt = now
                }
            }
            delay(45)
        }
    }

    Column(Modifier.fillMaxSize()) {
        if (!NexusFullscreen.active) {
            Title("Tela do PC", "Visualização de baixa latência pela rede local")
            Spacer(Modifier.height(10.dp))
        }
        Surface(
            color = Color.Black,
            shape = if (NexusFullscreen.active) RoundedCornerShape(0.dp) else RoundedCornerShape(24.dp),
            border = if (NexusFullscreen.active) null else BorderStroke(1.dp, NexusUi.Border),
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
            Box(
                Modifier.fillMaxSize().clip(if (NexusFullscreen.active) RoundedCornerShape(0.dp) else RoundedCornerShape(24.dp)).background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                val image = frame
                if (image != null) {
                    Image(
                        bitmap = image,
                        contentDescription = "Tela do PC",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
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
            Surface(color = NexusUi.Panel, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, NexusUi.Border), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).background(if (Api.connected) NexusUi.Success else NexusUi.Muted, CircleShape))
                        Spacer(Modifier.width(7.dp))
                        Text(if (Api.connected) "REDE LOCAL" else "OFFLINE", color = if (Api.connected) NexusUi.Success else NexusUi.Muted, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                    }
                    Text(if (fps > 0) "$fps FPS" else "…", color = NexusUi.Muted, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
