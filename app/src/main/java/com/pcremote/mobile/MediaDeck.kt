package com.pcremote.mobile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun MediaDeck() {
    val scope = rememberCoroutineScope()
    var playing by remember { mutableStateOf(false) }
    fun hit(key: String) { scope.launch(Dispatchers.IO) { Api.post("/key", key) } }

    Column(Modifier.fillMaxSize()) {
        Title("Mídia", "Controle reprodução e volume do Windows")
        Spacer(Modifier.height(12.dp))
        Surface(color = NexusUi.Panel, shape = RoundedCornerShape(26.dp), border = BorderStroke(1.dp, NexusUi.Border), modifier = Modifier.fillMaxWidth().weight(1f)) {
            Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(NexusUi.AccentStrong.copy(alpha = .12f), NexusUi.Panel)))) {
                Column(Modifier.fillMaxSize().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceEvenly) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(72.dp).background(NexusUi.Accent.copy(alpha = .10f), RoundedCornerShape(22.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.MusicNote, null, tint = NexusUi.Accent, modifier = Modifier.size(34.dp)) }
                        Spacer(Modifier.height(8.dp))
                        Text("CONTROLE DE MÍDIA", color = NexusUi.Text, fontWeight = FontWeight.Black)
                        Text("Comandos globais do Windows", color = NexusUi.Muted, style = MaterialTheme.typography.bodySmall)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        MediaKey(Icons.Default.SkipPrevious, 58) { hit("MEDIA_PREV") }
                        MediaKey(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, 78, primary = true) { playing = !playing; hit("MEDIA_PLAY") }
                        MediaKey(Icons.Default.SkipNext, 58) { hit("MEDIA_NEXT") }
                    }
                    Surface(color = NexusUi.BackgroundSoft, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, NexusUi.Border), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                            MediaKey(Icons.Default.VolumeDown, 48) { hit("VOLUME_DOWN") }
                            MediaKey(Icons.Default.VolumeOff, 48) { hit("VOLUME_MUTE") }
                            MediaKey(Icons.Default.VolumeUp, 48) { hit("VOLUME_UP") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaKey(icon: ImageVector, size: Int, primary: Boolean = false, onClick: () -> Unit) {
    Box(Modifier.size(size.dp).background(if (primary) NexusUi.AccentStrong else NexusUi.PanelRaised, CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = if (primary) androidx.compose.ui.graphics.Color.White else NexusUi.Accent, modifier = Modifier.size((size * .42f).dp))
    }
}
