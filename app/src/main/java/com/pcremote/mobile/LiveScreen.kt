package com.pcremote.mobile

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

@Composable
fun LivePcScreen() {
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Api.host, Api.connected) {
        while (Api.connected) {
            tick++
            delay(550)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Title("Tela do PC", "Visualização ao vivo pela rede local")
        Spacer(Modifier.height(12.dp))
        Surface(
            color = Color.Black,
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, NexusUi.Border),
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (Api.connected) {
                    AsyncImage(
                        model = "http://${Api.host}:8765/screen.jpg?t=$tick",
                        contentDescription = "Tela do PC",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.size(58.dp).background(NexusUi.PanelRaised, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.DesktopWindows, null, tint = NexusUi.Muted, modifier = Modifier.size(27.dp))
                        }
                        Spacer(Modifier.height(10.dp))
                        Text("Aguardando conexão", color = NexusUi.Muted)
                    }
                }
            }
        }
        Spacer(Modifier.height(9.dp))
        Surface(
            color = NexusUi.Panel,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, NexusUi.Border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).background(if (Api.connected) NexusUi.Success else NexusUi.Muted, CircleShape))
                    Spacer(Modifier.width(7.dp))
                    Text(if (Api.connected) "REDE LOCAL" else "OFFLINE", color = if (Api.connected) NexusUi.Success else NexusUi.Muted, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                }
                Text("~2 quadros/s", color = NexusUi.Muted, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
