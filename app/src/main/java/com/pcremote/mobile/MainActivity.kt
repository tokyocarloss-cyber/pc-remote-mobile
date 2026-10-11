package com.pcremote.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlin.math.roundToInt

private val Bg = Color(0xFF070B14)
private val Panel = Color(0xFF151D2E)
private val Accent = Color(0xFF46DDFC)
private val Secondary = Color(0xFFA785FF)
private val Light = Color(0xFFF6F9FF)
private val Muted = Color(0xFF9AA9BF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RemoteApi.initialize(applicationContext)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Accent, surface = Panel, background = Bg)) {
                NexusApp()
            }
        }
    }
}

@Composable
private fun NexusApp() {
    val context = LocalContext.current
    val sound = remember { SoundFx(context) }
    DisposableEffect(Unit) { onDispose { sound.release() } }
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Início", "Mouse", "Controle", "Apps", "Tela", "Arquivos", "Ajustes")
    LaunchedEffect(RemoteApi.host) {
        while (true) {
            RemoteApi.status()
            delay(12000)
        }
    }
    Column(Modifier.fillMaxSize().background(Bg).systemBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("NEXUS", color = Light, fontSize = 29.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                Text("PC Remote 2.0", color = Muted, fontSize = 12.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(if (RemoteApi.connected) "● Conectado" else "● Desconectado",
                    color = if (RemoteApi.connected) Color(0xFF76E4B8) else Secondary,
                    fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(RemoteApi.computer, color = Muted, fontSize = 11.sp, maxLines = 1)
            }
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            when (tab) {
                0 -> HomeScreen(sound)
                1 -> MouseScreen(sound)
                2 -> GamepadScreen(sound)
                3 -> AppsScreen(sound)
                4 -> LiveScreen(sound)
                5 -> FilesScreen(sound)
                else -> SettingsScreen(sound)
            }
        }
        Row(
            Modifier.fillMaxWidth().background(Panel).horizontalScroll(rememberScrollState()).padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            tabs.forEachIndexed { i, title ->
                Surface(
                    modifier = Modifier.clickable { sound.click(); tab = i },
                    color = if (tab == i) Accent.copy(alpha = .16f) else Color.Transparent,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(title, Modifier.padding(horizontal = 13.dp, vertical = 13.dp),
                        color = if (tab == i) Accent else Muted, fontSize = 13.sp,
                        fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}

@Composable
private fun Header(title: String, subtitle: String) {
    Spacer(Modifier.height(12.dp))
    Text(title, fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Light)
    Text(subtitle, fontSize = 13.sp, color = Muted)
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun PanelBox(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(bottom = 12.dp).background(Panel, RoundedCornerShape(20.dp)).padding(16.dp),
        content = content
    )
}

@Composable
private fun Action(label: String, modifier: Modifier = Modifier, enabled: Boolean = RemoteApi.connected, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = enabled, modifier = modifier.padding(3.dp),
        shape = RoundedCornerShape(13.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF26334C),
            contentColor = Light,
            disabledContainerColor = Color(0xFF1B2331),
            disabledContentColor = Muted
        )
    ) { Text(label, fontSize = 13.sp, maxLines = 1) }
}

@Composable
private fun HomeScreen(sound: SoundFx) {
    val scope = rememberCoroutineScope()
    var power by remember { mutableStateOf("") }
    Header("Central de controle", "Seus comandos principais em um só lugar.")
    if (!RemoteApi.connected) {
        PanelBox {
            Text("Conecte primeiro", color = Light, fontWeight = FontWeight.Bold)
            Text("Abra o NEXUS no Windows e vá em Ajustes para inserir o IP e PIN do computador.",
                color = Muted, fontSize = 13.sp)
            if (RemoteApi.error.isNotBlank()) {
                Spacer(Modifier.height(7.dp))
                Text(RemoteApi.error, color = Secondary, fontSize = 12.sp)
            }
        }
    }
    PanelBox {
        Text("Mídia e volume", color = Light, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf("⏮" to "MEDIA_PREV", "⏯" to "MEDIA_PLAY", "⏭" to "MEDIA_NEXT").forEach { (label, key) ->
                Action(label) { sound.click(); scope.launch { RemoteApi.send("/key", key) } }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf("− Volume" to "VOLUME_DOWN", "Silenciar" to "VOLUME_MUTE", "+ Volume" to "VOLUME_UP")
                .forEach { (label, key) ->
                    Action(label) { sound.click(); scope.launch { RemoteApi.send("/key", key) } }
                }
        }
    }
    PanelBox {
        Text("Teclado remoto", color = Light, fontWeight = FontWeight.Bold)
        var text by remember { mutableStateOf("") }
        OutlinedTextField(value = text, onValueChange = { text = it.take(2000) },
            modifier = Modifier.fillMaxWidth(), singleLine = false, maxLines = 3,
            label = { Text("Digite um texto") })
        Action("Enviar texto ao PC", Modifier.fillMaxWidth(), enabled = RemoteApi.connected && text.isNotEmpty()) {
            scope.launch {
                if (RemoteApi.send("/text", text)) { text = ""; sound.success() } else sound.error()
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf("Enter" to "ENTER", "Esc" to "ESC", "Espaço" to "SPACE", "Apagar" to "BACKSPACE")
                .forEach { (label, key) ->
                    Action(label) { sound.click(); scope.launch { RemoteApi.send("/key", key) } }
                }
        }
    }
    PanelBox {
        Text("Energia do PC", color = Light, fontWeight = FontWeight.Bold)
        Text("Requer confirmação para evitar desligamentos acidentais.", color = Muted, fontSize = 12.sp)
        Row {
            Action("Desligar") { sound.click(); power = "shutdown" }
            Action("Reiniciar") { sound.click(); power = "restart" }
        }
    }
    if (power.isNotEmpty()) {
        AlertDialog(onDismissRequest = { power = "" },
            title = { Text("Confirmar comando") },
            text = { Text("Deseja realmente " + (if (power == "shutdown") "desligar" else "reiniciar") +
                " o computador? O Windows iniciará a ação em 10 segundos.") },
            confirmButton = {
                TextButton(onClick = {
                    val selected = power
                    power = ""
                    scope.launch { if (RemoteApi.send("/power", selected)) sound.success() else sound.error() }
                }) { Text("Confirmar") }
            },
            dismissButton = { TextButton(onClick = { power = "" }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun MouseScreen(sound: SoundFx) {
    val scope = rememberCoroutineScope()
    Header("Mouse e touchpad", "Movimento contínuo, clique, rolagem e gestos.")
    PanelBox {
        var pendingX by remember { mutableFloatStateOf(0f) }
        var pendingY by remember { mutableFloatStateOf(0f) }
        Box(
            Modifier.fillMaxWidth().height(300.dp)
                .background(Color(0xFF1E2A40), RoundedCornerShape(22.dp))
                .pointerInput(RemoteApi.connected) {
                    var lastSent = 0L
                    detectDragGestures(
                        onDragEnd = {
                            val x = pendingX.roundToInt()
                            val y = pendingY.roundToInt()
                            pendingX = 0f
                            pendingY = 0f
                            if (RemoteApi.connected && (x != 0 || y != 0)) {
                                scope.launch { RemoteApi.send("/mouse", "$x,$y") }
                            }
                        },
                        onDragCancel = { pendingX = 0f; pendingY = 0f }
                    ) { change, delta ->
                        change.consume()
                        pendingX += delta.x
                        pendingY += delta.y
                        val now = android.os.SystemClock.uptimeMillis()
                        if (now - lastSent >= 35L && RemoteApi.connected) {
                            val x = pendingX.roundToInt()
                            val y = pendingY.roundToInt()
                            if (x != 0 || y != 0) {
                                pendingX -= x
                                pendingY -= y
                                lastSent = now
                                scope.launch { RemoteApi.send("/mouse", "$x,$y") }
                            }
                        }
                    }
                }
                .pointerInput(RemoteApi.connected) {
                    detectTapGestures(onTap = {
                        if (RemoteApi.connected) scope.launch { RemoteApi.send("/click", "left") }
                    })
                },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("◎", color = Accent, fontSize = 54.sp)
                Text("Deslize para mover • toque para clicar", color = Muted, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row {
            Action("Clique esquerdo", Modifier.weight(1f)) {
                sound.click(); scope.launch { RemoteApi.send("/click", "left") }
            }
            Action("Clique direito", Modifier.weight(1f)) {
                sound.click(); scope.launch { RemoteApi.send("/click", "right") }
            }
        }
        Row {
            Action("Rolar ↑", Modifier.weight(1f)) {
                sound.click(); scope.launch { RemoteApi.send("/scroll", "240") }
            }
            Action("Rolar ↓", Modifier.weight(1f)) {
                sound.click(); scope.launch { RemoteApi.send("/scroll", "-240") }
            }
        }
    }
    PanelBox {
        Text("Setas direcionais", color = Light, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Action("↑") { sound.click(); scope.launch { RemoteApi.send("/key", "UP") } }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            listOf("←" to "LEFT", "↓" to "DOWN", "→" to "RIGHT").forEach { (n, k) ->
                Action(n) { sound.click(); scope.launch { RemoteApi.send("/key", k) } }
            }
        }
    }
}

@Composable
private fun HoldButton(name: String, enabled: Boolean, sound: SoundFx) {
    val scope = rememberCoroutineScope()
    Surface(
        color = Color(0xFF24334D), shape = RoundedCornerShape(40.dp),
        modifier = Modifier.padding(3.dp).size(67.dp)
            .pointerInput(name, enabled) {
                detectTapGestures(onPress = {
                    if (enabled) {
                        sound.click()
                        scope.launch {
                            RemoteApi.send("/gamepad", JSONObject().put("button", name).put("pressed", true).toString())
                        }
                        tryAwaitRelease()
                        scope.launch {
                            RemoteApi.send("/gamepad", JSONObject().put("button", name).put("pressed", false).toString())
                        }
                    }
                })
            }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(name, fontWeight = FontWeight.Bold, color = if (enabled) Accent else Muted)
        }
    }
}

@Composable
private fun GamepadScreen(sound: SoundFx) {
    Header("Controle virtual", "Botões XInput com pressionar e soltar reais.")
    if (!RemoteApi.gamepadReady) {
        PanelBox {
            Text("XInput ainda não disponível", color = Secondary, fontWeight = FontWeight.Bold)
            Text("No PC, instale vgamepad e o driver ViGEmBus. Enquanto isso, mouse, teclado e mídia continuam funcionando.",
                color = Muted, fontSize = 13.sp)
        }
    }
    val enabled = RemoteApi.connected && RemoteApi.gamepadReady
    PanelBox {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            HoldButton("LB", enabled, sound)
            HoldButton("RB", enabled, sound)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Column {
                Row { Spacer(Modifier.width(65.dp)); HoldButton("UP", enabled, sound) }
                Row {
                    HoldButton("LEFT", enabled, sound)
                    HoldButton("DOWN", enabled, sound)
                    HoldButton("RIGHT", enabled, sound)
                }
            }
            Column {
                Row { Spacer(Modifier.width(56.dp)); HoldButton("Y", enabled, sound) }
                Row { HoldButton("X", enabled, sound); HoldButton("B", enabled, sound) }
                Row { Spacer(Modifier.width(56.dp)); HoldButton("A", enabled, sound) }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            HoldButton("BACK", enabled, sound)
            HoldButton("START", enabled, sound)
        }
        Text("Controle básico digital. Analógicos e gatilhos proporcionais ainda não estão incluídos.",
            color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun AppsScreen(sound: SoundFx) {
    val scope = rememberCoroutineScope()
    var apps by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var filter by remember { mutableStateOf("") }
    LaunchedEffect(RemoteApi.connected) {
        apps = if (RemoteApi.connected) RemoteApi.apps() else emptyList()
    }
    Header("Apps e programas", "Abra aplicativos permitidos no Windows.")
    OutlinedTextField(filter, { filter = it }, Modifier.fillMaxWidth(), label = { Text("Buscar") }, singleLine = true)
    Spacer(Modifier.height(8.dp))
    apps.filter { it.second.contains(filter, ignoreCase = true) }.forEach { (id, name) ->
        PanelBox {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text(name, color = Light, fontWeight = FontWeight.Medium)
                Action("Abrir") {
                    sound.click()
                    scope.launch { if (RemoteApi.send("/launch", id)) sound.success() else sound.error() }
                }
            }
        }
    }
    if (apps.isEmpty()) Text("Conecte-se ao PC para carregar os aplicativos.", color = Muted)
}

@Composable
private fun LiveScreen(sound: SoundFx) {
    val scope = rememberCoroutineScope()
    var quality by remember { mutableIntStateOf(65) }
    var screenshot by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    Header("Tela ao vivo", "Captura JPEG atualizada automaticamente pela rede local.")
    LaunchedEffect(RemoteApi.connected, quality) {
        while (RemoteApi.connected) {
            val frame = RemoteApi.screenshot(quality)
            if (frame != null) screenshot = frame
            delay(650)
        }
    }
    PanelBox {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (screenshot != null && RemoteApi.connected) {
                Image(screenshot!!.asImageBitmap(), "Tela do computador",
                    Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            } else Text("Aguardando imagem do PC...", color = Muted)
        }
        Spacer(Modifier.height(10.dp))
        Text("Qualidade JPEG: $quality%", color = Light)
        Slider(value = quality.toFloat(), onValueChange = { quality = it.roundToInt() },
            valueRange = 30f..85f)
        Text("Modo de compatibilidade: capturas periódicas, não streaming de 60/120 FPS.",
            color = Muted, fontSize = 12.sp)
        Row {
            Action("Esc") { sound.click(); scope.launch { RemoteApi.send("/key", "ESC") } }
            Action("Atualizar (F5)") { sound.click(); scope.launch { RemoteApi.send("/key", "F5") } }
        }
    }
}

@Composable
private fun FilesScreen(sound: SoundFx) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var files by remember { mutableStateOf<List<RemoteFile>>(emptyList()) }
    var message by remember { mutableStateOf("") }
    var selectedName by remember { mutableStateOf("") }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) scope.launch {
            message = RemoteApi.download(context, selectedName, uri)
            if (message.startsWith("Arquivo salvo")) sound.success() else sound.error()
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            message = "Enviando..."
            message = RemoteApi.upload(context, uri)
            if (message.startsWith("Arquivo enviado")) {
                sound.success()
                files = RemoteApi.files()
            } else sound.error()
        }
    }
    LaunchedEffect(RemoteApi.connected) {
        files = if (RemoteApi.connected) RemoteApi.files() else emptyList()
    }
    Header("Transferir arquivos", "Envie e receba arquivos com segurança na rede local.")
    PanelBox {
        Action("Selecionar e enviar arquivo", Modifier.fillMaxWidth()) {
            sound.click()
            open.launch("*/*")
        }
        if (RemoteApi.uploadProgress > 0) {
            LinearProgressIndicator(
                progress = { RemoteApi.uploadProgress / 100f },
                modifier = Modifier.fillMaxWidth()
            )
            Text("Enviando: " + RemoteApi.uploadProgress + "%", color = Muted)
        }
        if (message.isNotBlank()) Text(message, color = Accent, fontSize = 12.sp)
        Text("Limite: 512 MB por arquivo. Arquivos recebidos ficam na pasta Downloads/NEXUS PC Remote.",
            color = Muted, fontSize = 12.sp)
    }
    PanelBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Arquivos disponíveis", color = Light, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f))
            Action("Atualizar") {
                sound.click()
                scope.launch { files = RemoteApi.files() }
            }
        }
        if (files.isEmpty()) Text("Nenhum arquivo disponível.", color = Muted)
        files.forEach { f ->
            HorizontalDivider(color = Muted.copy(alpha = 0.15f))
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(f.name, color = Light, fontSize = 13.sp, maxLines = 2)
                    Text("%.1f MB".format(f.size / 1048576.0), color = Muted, fontSize = 11.sp)
                }
                Action("Baixar") {
                    sound.click()
                    selectedName = f.name
                    save.launch(f.name)
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(sound: SoundFx) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var ip by remember { mutableStateOf(RemoteApi.host) }
    var pin by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    Header("Ajustes e conexão", "Pareamento seguro para controlar seu próprio computador.")
    PanelBox {
        Text("Conectar ao Windows", color = Light, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("Use o IP e o código de seis dígitos que aparecem no programa Windows.",
            color = Muted, fontSize = 12.sp)
        OutlinedTextField(ip, { ip = it }, Modifier.fillMaxWidth(),
            label = { Text("IPv4 do PC") }, placeholder = { Text("192.168.1.10") }, singleLine = true)
        OutlinedTextField(pin, { pin = it.take(6) }, Modifier.fillMaxWidth(),
            label = { Text("PIN de pareamento") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        Action(if (working) "Conectando..." else "Parear e conectar", Modifier.fillMaxWidth(), enabled = !working) {
            working = true
            scope.launch {
                if (RemoteApi.pair(context, ip, pin)) {
                    pin = ""
                    sound.success()
                } else sound.error()
                working = false
            }
        }
        Action("Verificar conexão", Modifier.fillMaxWidth(), enabled = RemoteApi.host.isNotBlank()) {
            scope.launch { if (RemoteApi.status()) sound.success() else sound.error() }
        }
        if (RemoteApi.error.isNotBlank()) Text(RemoteApi.error, color = Secondary, fontSize = 12.sp)
    }
    PanelBox {
        Text("Personalização", color = Light, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("Efeitos sonoros", color = Light)
                Text("Cliques, conexão e erros", color = Muted, fontSize = 12.sp)
            }
            Switch(checked = sound.enabled, onCheckedChange = { sound.setEnabled(it) })
        }
    }
    PanelBox {
        Text("Privacidade e segurança", color = Light, fontWeight = FontWeight.Bold)
        Text("Funciona na rede local. Não abra a porta 8765 na Internet. O token fica salvo no aparelho.",
            color = Muted, fontSize = 12.sp)
        Action("Esquecer este PC", Modifier.fillMaxWidth(), enabled = RemoteApi.host.isNotBlank()) {
            sound.click()
            RemoteApi.forget(context)
            ip = ""
            pin = ""
        }
    }
}
