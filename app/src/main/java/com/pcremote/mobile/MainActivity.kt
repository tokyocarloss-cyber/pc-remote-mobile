package com.pcremote.mobile

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL

private const val PORT = 8765

class MainActivity : ComponentActivity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = NexusUi.Accent,
                    secondary = NexusUi.AccentStrong,
                    surface = NexusUi.Panel,
                    background = NexusUi.Background
                )
            ) { RemoteApp() }
        }
    }
}

object Api {
    var host by mutableStateOf("")
    var connected by mutableStateOf(false)

    fun post(path: String, b: String = "") {
        if (host.isBlank()) return
        try {
            val c = URL("http://$host:$PORT$path").openConnection() as HttpURLConnection
            c.requestMethod = "POST"
            c.connectTimeout = 700
            c.readTimeout = 900
            c.doOutput = true
            c.outputStream.use { it.write(b.toByteArray()) }
            connected = c.responseCode in 200..299
            c.disconnect()
        } catch (_: Exception) {
            connected = false
        }
    }
}

@Composable
fun RemoteApp() {
    var page by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    LaunchedEffect(Unit) {
        while (true) {
            if (!Api.connected) Discovery.findPc()?.let {
                Api.host = it
                Api.connected = true
            }
            delay(if (Api.connected) 5000 else 2500)
        }
    }

    PremiumShell(page.coerceIn(0, 8), { page = it }) {
        when (page) {
            0 -> Home(scope) { page = it }
            1 -> PcLibrary()
            2 -> Touch(scope) { page = 6 }
            3 -> if (landscape) LandscapeGamepad() else AdvancedGamepad()
            4 -> PcDrop()
            5 -> LivePcScreen()
            6 -> TextRemote()
            7 -> MediaDeck()
            8 -> NexusSettings()
            else -> Home(scope) { page = it }
        }
    }
}

@Composable
fun Home(scope: kotlinx.coroutines.CoroutineScope, go: (Int) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ConnectionHero(go)

        SectionLabel("CONTROLE")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FeatureCard(Icons.Default.Apps, "Biblioteca", "Apps e jogos", Modifier.weight(1f)) { go(1) }
            FeatureCard(Icons.Default.Mouse, "Touchpad", "Mouse e rolagem", Modifier.weight(1f)) { go(2) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FeatureCard(Icons.Default.SportsEsports, "Gamepad", "Controle customizável", Modifier.weight(1f)) { go(3) }
            FeatureCard(Icons.Default.SwapHoriz, "Drop", "Arquivos e texto", Modifier.weight(1f)) { go(4) }
        }

        SectionLabel("FERRAMENTAS")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ToolButton(Icons.Default.DesktopWindows, "Tela", Modifier.weight(1f)) { go(5) }
            ToolButton(Icons.Default.Keyboard, "Teclado", Modifier.weight(1f)) { go(6) }
            ToolButton(Icons.Default.MusicNote, "Mídia", Modifier.weight(1f)) { go(7) }
        }

        Surface(
            color = NexusUi.Panel,
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, NexusUi.Border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.fillMaxWidth().padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickControl(Icons.Default.VolumeDown, "Diminuir", Modifier.weight(1f)) {
                    scope.launch(Dispatchers.IO) { Api.post("/key", "VOLUME_DOWN") }
                }
                QuickControl(Icons.Default.VolumeOff, "Mudo", Modifier.weight(1f)) {
                    scope.launch(Dispatchers.IO) { Api.post("/key", "VOLUME_MUTE") }
                }
                QuickControl(Icons.Default.VolumeUp, "Aumentar", Modifier.weight(1f)) {
                    scope.launch(Dispatchers.IO) { Api.post("/key", "VOLUME_UP") }
                }
            }
        }

        Spacer(Modifier.height(2.dp))
    }
}

@Composable
private fun ConnectionHero(go: (Int) -> Unit) {
    Surface(
        color = NexusUi.Panel,
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, NexusUi.Border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            Modifier.background(
                Brush.linearGradient(
                    listOf(NexusUi.AccentStrong.copy(alpha = .16f), Color.Transparent, NexusUi.Violet.copy(alpha = .08f))
                )
            )
        ) {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (Api.connected) "PC conectado" else "Procurando seu PC",
                            color = NexusUi.Text,
                            fontSize = 25.sp,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (Api.connected) Api.host else "Mantenha celular e PC na mesma rede",
                            color = NexusUi.Muted,
                            fontSize = 11.sp
                        )
                    }
                    Box(
                        Modifier
                            .size(44.dp)
                            .background(
                                if (Api.connected) NexusUi.Success.copy(alpha = .12f) else NexusUi.PanelRaised,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (Api.connected) Icons.Default.Check else Icons.Default.WifiFind,
                            null,
                            tint = if (Api.connected) NexusUi.Success else NexusUi.Accent,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = { go(5) },
                    enabled = Api.connected,
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(15.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NexusUi.AccentStrong)
                ) {
                    Icon(Icons.Default.DesktopWindows, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("ABRIR TELA DO PC", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun FeatureCard(icon: ImageVector, title: String, subtitle: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        color = NexusUi.Panel,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, NexusUi.Border),
        modifier = modifier.height(126.dp).clickable(onClick = onClick)
    ) {
        Column(
            Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                Modifier.size(40.dp).background(NexusUi.Accent.copy(alpha = .10f), RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, title, tint = NexusUi.Accent, modifier = Modifier.size(21.dp))
            }
            Column {
                Text(title, color = NexusUi.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = NexusUi.Muted, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun ToolButton(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        color = NexusUi.PanelRaised,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, NexusUi.Border),
        modifier = modifier.height(76.dp).clickable(onClick = onClick)
    ) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, label, tint = NexusUi.Accent, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(6.dp))
            Text(label, color = NexusUi.Text, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun QuickControl(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, label, tint = NexusUi.Accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, color = NexusUi.Muted, fontSize = 8.sp, maxLines = 1)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = NexusUi.Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
}

@Composable
fun Touch(scope: kotlinx.coroutines.CoroutineScope, keyboard: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Title("Touchpad", "Controle o cursor sem sair do celular")
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(
                    Brush.radialGradient(listOf(Color(0xFF152331), NexusUi.Panel, NexusUi.BackgroundSoft)),
                    RoundedCornerShape(28.dp)
                )
                .pointerInput(Unit) {
                    detectDragGestures { change, d ->
                        change.consume()
                        val x = (d.x * 1.20f).toInt()
                        val y = (d.y * 1.20f).toInt()
                        if (x != 0 || y != 0) scope.launch(Dispatchers.IO) { Api.post("/mouse", "$x,$y") }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(64.dp).background(NexusUi.Accent.copy(alpha = .08f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Mouse, null, tint = NexusUi.Accent, modifier = Modifier.size(30.dp))
                }
                Spacer(Modifier.height(10.dp))
                Text("DESLIZE PARA MOVER", color = NexusUi.Text, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("Movimento em tempo real", color = NexusUi.Muted, fontSize = 9.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MouseAction("CLIQUE", Modifier.weight(1f), scope, "left")
            MouseAction("DIREITO", Modifier.weight(1f), scope, "right")
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ScrollAction("ROLAR ↑", Modifier.weight(1f), scope, 480)
            ScrollAction("ROLAR ↓", Modifier.weight(1f), scope, -480)
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = keyboard,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = NexusUi.AccentStrong)
        ) {
            Icon(Icons.Default.Keyboard, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("ABRIR TECLADO", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun MouseAction(t: String, m: Modifier, s: kotlinx.coroutines.CoroutineScope, k: String) {
    Surface(
        color = NexusUi.PanelRaised,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, NexusUi.Border),
        modifier = m.clickable { s.launch(Dispatchers.IO) { Api.post("/click", k) } }
    ) {
        Text(t, Modifier.padding(14.dp), color = NexusUi.Text, fontSize = 10.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ScrollAction(t: String, m: Modifier, s: kotlinx.coroutines.CoroutineScope, v: Int) {
    Surface(
        color = NexusUi.PanelRaised,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, NexusUi.Border),
        modifier = m.clickable { s.launch(Dispatchers.IO) { Api.post("/scroll", v.toString()) } }
    ) {
        Text(t, Modifier.padding(12.dp), color = NexusUi.Accent, fontSize = 10.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun Title(a: String, b: String) {
    Column {
        Text(a, color = NexusUi.Text, fontSize = 25.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(2.dp))
        Text(b, color = NexusUi.Muted, fontSize = 11.sp)
    }
}
