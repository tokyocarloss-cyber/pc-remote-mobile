package com.pcremote.mobile

import android.app.Activity
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

class NexusActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = NexusUi.AccentStrong,
                    secondary = NexusUi.Accent,
                    background = Color(0xFF020508),
                    surface = Color(0xFF0B1117)
                )
            ) { NexusReferenceApp() }
        }
    }
}

private data class RefNav(val page: Int, val label: String, val icon: ImageVector)
private val refNav = listOf(
    RefNav(0, "Início", Icons.Default.Home),
    RefNav(1, "Apps", Icons.Default.Apps),
    RefNav(2, "Controle", Icons.Default.SportsEsports),
    RefNav(3, "Arquivos", Icons.Default.Folder),
    RefNav(4, "Tela", Icons.Default.DesktopWindows)
)

@Composable
private fun NexusReferenceApp() {
    var page by remember { mutableIntStateOf(0) }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val activity = LocalContext.current as? Activity

    LaunchedEffect(Unit) {
        while (true) {
            if (!Api.connected) {
                val found = Discovery.findPc()
                if (found != null) connectToHost(found, Discovery.lastMethod)
            } else {
                val alive = withContext(Dispatchers.IO) { Discovery.checkHost(Api.host) }
                if (!alive) {
                    Api.connected = false
                    Api.connectionMethod = "Reconectando"
                }
            }
            delay(if (Api.connected) 4500 else 1800)
        }
    }

    LaunchedEffect(page) {
        if (page != 2 && page != 4) NexusFullscreen.active = false
    }
    LaunchedEffect(NexusFullscreen.active) {
        activity?.window?.decorView?.systemUiVisibility = if (NexusFullscreen.active) {
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        } else View.SYSTEM_UI_FLAG_VISIBLE
    }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF071019), Color(0xFF020508), Color.Black)))) {
        if (NexusFullscreen.active && (page == 2 || page == 4)) {
            Box(Modifier.fillMaxSize()) { ReferencePage(page) { page = it } }
            IconButton(
                onClick = { NexusFullscreen.active = false },
                modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).size(44.dp)
                    .background(Color(0xD90B1117), RoundedCornerShape(14.dp))
            ) { Icon(Icons.Default.FullscreenExit, "Sair da tela cheia", tint = Color.White) }
        } else if (landscape && page == 2) {
            Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(8.dp)) {
                AdvancedGamepad()
                IconButton(
                    onClick = { NexusFullscreen.active = true },
                    modifier = Modifier.align(Alignment.TopEnd).size(42.dp).background(Color(0xCC0B1117), RoundedCornerShape(13.dp))
                ) { Icon(Icons.Default.Fullscreen, null, tint = NexusUi.Accent) }
            }
        } else {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                ReferenceHeader(onSettings = { page = 7 })
                Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp)) {
                    ReferencePage(page) { page = it }
                    if (page == 2 || page == 4) {
                        IconButton(
                            onClick = { NexusFullscreen.active = true },
                            modifier = Modifier.align(Alignment.TopEnd).padding(top = 4.dp).size(40.dp)
                                .background(Color(0xD90B1117), RoundedCornerShape(13.dp))
                        ) { Icon(Icons.Default.Fullscreen, null, tint = NexusUi.Accent) }
                    }
                }
                ReferenceBottomNav(page) { page = it }
            }
        }
    }
}

@Composable
private fun ReferencePage(page: Int, go: (Int) -> Unit) {
    when (page) {
        0 -> ReferenceHome(go)
        1 -> ReferenceLibrary()
        2 -> AdvancedGamepad()
        3 -> PcDrop()
        4 -> SmoothLiveScreen()
        5 -> NotebookTouchpad { go(6) }
        6 -> TextRemote()
        7 -> NexusSettings()
        8 -> MediaDeck()
        else -> ReferenceHome(go)
    }
}

@Composable
private fun ReferenceHeader(onSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.DesktopWindows, null, tint = NexusUi.Accent, modifier = Modifier.size(30.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("Meu PC", color = Color.White, fontWeight = FontWeight.Black, fontSize = 17.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).background(if (Api.connected) NexusUi.Success else NexusUi.Muted, CircleShape))
                Spacer(Modifier.width(6.dp))
                Text(
                    if (Api.connected) "Conectado (${Api.host})" else "Procurando PC…",
                    color = if (Api.connected) NexusUi.Success else NexusUi.Muted,
                    fontSize = 10.sp
                )
            }
        }
        IconButton(
            onClick = onSettings,
            modifier = Modifier.size(44.dp).background(Color(0xFF101820), RoundedCornerShape(15.dp))
        ) { Icon(Icons.Default.Settings, "Configurações", tint = Color.White) }
    }
}

@Composable
private fun ReferenceBottomNav(selected: Int, setPage: (Int) -> Unit) {
    Surface(
        color = Color(0xFF080D12),
        border = BorderStroke(1.dp, Color(0xFF17222D)),
        shadowElevation = 18.dp
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 5.dp, vertical = 5.dp)) {
            refNav.forEach { item ->
                val active = selected == item.page
                Column(
                    Modifier.weight(1f).height(58.dp).clickable { setPage(item.page) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        Modifier.size(32.dp).background(if (active) Color(0xFF0B3345) else Color.Transparent, RoundedCornerShape(11.dp)),
                        contentAlignment = Alignment.Center
                    ) { Icon(item.icon, item.label, tint = if (active) NexusUi.Accent else Color(0xFF84909F), modifier = Modifier.size(20.dp)) }
                    Spacer(Modifier.height(2.dp))
                    Text(item.label, color = if (active) Color.White else Color(0xFF84909F), fontSize = 8.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium)
                }
            }
        }
    }
}

private data class HomeAction(val icon: ImageVector, val title: String, val page: Int, val accent: Color = NexusUi.Accent)

@Composable
private fun ReferenceHome(go: (Int) -> Unit) {
    val scope = rememberCoroutineScope()
    var volume by remember { mutableFloatStateOf(70f) }
    var sentVolume by remember { mutableFloatStateOf(70f) }
    var playing by remember { mutableStateOf(false) }
    var power by remember { mutableStateOf<String?>(null) }

    if (power != null) {
        val action = power!!
        AlertDialog(
            onDismissRequest = { power = null },
            title = { Text(when (action) { "shutdown" -> "Desligar PC?"; "restart" -> "Reiniciar PC?"; else -> "Suspender PC?" }) },
            text = { Text("O comando será enviado imediatamente para o Windows.") },
            confirmButton = {
                Button(onClick = { scope.launch(Dispatchers.IO) { Api.post("/power", action) }; power = null }) { Text("CONFIRMAR") }
            },
            dismissButton = { TextButton(onClick = { power = null }) { Text("CANCELAR") } }
        )
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(color = Color(0xFF0A1117), shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, Color(0xFF172430))) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).background(Color(0xFF111D27), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.VolumeUp, null, tint = Color.White)
                    }
                    Spacer(Modifier.width(10.dp))
                    Text("Volume", color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    Text("${volume.toInt()}%", color = Color.White, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = volume,
                    onValueChange = { volume = it },
                    onValueChangeFinished = {
                        val delta = volume - sentVolume
                        if (abs(delta) >= 2f) {
                            val key = if (delta > 0) "VOLUME_UP" else "VOLUME_DOWN"
                            val steps = (abs(delta) / 5f).toInt().coerceIn(1, 12)
                            scope.launch(Dispatchers.IO) { repeat(steps) { Api.post("/key", key) } }
                            sentVolume = volume
                        }
                    },
                    valueRange = 0f..100f,
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color(0xFF159DFF), inactiveTrackColor = Color(0xFF22303C))
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MediaButton(Icons.Default.SkipPrevious, Modifier.weight(1f)) { scope.launch(Dispatchers.IO) { Api.post("/key", "MEDIA_PREV") } }
                    MediaButton(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, Modifier.weight(1f)) {
                        playing = !playing; scope.launch(Dispatchers.IO) { Api.post("/key", "MEDIA_PLAY") }
                    }
                    MediaButton(Icons.Default.SkipNext, Modifier.weight(1f)) { scope.launch(Dispatchers.IO) { Api.post("/key", "MEDIA_NEXT") } }
                }
            }
        }

        val actions = listOf(
            HomeAction(Icons.Default.Mouse, "Mouse", 5),
            HomeAction(Icons.Default.Keyboard, "Teclado", 6),
            HomeAction(Icons.Default.SportsEsports, "Controle", 2),
            HomeAction(Icons.Default.DesktopWindows, "Tela ao vivo", 4),
            HomeAction(Icons.Default.Apps, "Apps", 1),
            HomeAction(Icons.Default.Folder, "Arquivos", 3)
        )
        actions.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                row.forEach { item -> HomeTile(item.icon, item.title, item.accent, Modifier.weight(1f)) { go(item.page) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            HomeTile(Icons.Default.PowerSettingsNew, "Desligar", Color(0xFFFF4A4A), Modifier.weight(1f)) { power = "shutdown" }
            HomeTile(Icons.Default.RestartAlt, "Reiniciar", Color(0xFF2D9CFF), Modifier.weight(1f)) { power = "restart" }
            HomeTile(Icons.Default.Bedtime, "Suspender", Color(0xFF9E63FF), Modifier.weight(1f)) { power = "sleep" }
        }
        TextButton(onClick = { go(8) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.MusicNote, null, tint = NexusUi.Accent, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text("CONTROLES DE MÍDIA", color = NexusUi.Accent)
        }
    }
}

@Composable
private fun MediaButton(icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Surface(color = Color(0xFF111A22), shape = RoundedCornerShape(13.dp), modifier = modifier.height(50.dp).clickable(onClick = onClick)) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = Color.White) }
    }
}

@Composable
private fun HomeTile(icon: ImageVector, title: String, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        color = Color(0xFF0B1218), shape = RoundedCornerShape(17.dp),
        border = BorderStroke(1.dp, Color(0xFF17232D)),
        modifier = modifier.aspectRatio(1f).clickable(onClick = onClick)
    ) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, title, tint = accent, modifier = Modifier.size(29.dp))
            Spacer(Modifier.height(10.dp))
            Text(title, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        }
    }
}
