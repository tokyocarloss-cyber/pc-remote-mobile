package com.pcremote.mobile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Records the Windows desktop into the actual NEXUS companion, not a fake
 * phone-side recording switch or an unconfigurable Xbox Game Bar shortcut.
 */
@Composable
fun PcRecorderTools() {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var accessKey by remember { mutableStateOf(HubStore.key(ctx)) }
    var open by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Gravações e prints são salvos no PC") }
    var resolution by remember { mutableIntStateOf(720) }
    var fps by remember { mutableIntStateOf(30) }
    var jpegQuality by remember { mutableIntStateOf(75) }

    fun send(path: String, json: JSONObject) {
        scope.launch(Dispatchers.IO) {
            busy = true
            try {
                val ok = RecorderClient.post(ctx, path, json.toString().toByteArray(Charsets.UTF_8))
                if (ok) {
                    status = when (path) {
                        "/record/start" -> "Gravando tela do PC • salvo em Vídeos/NEXUS PC Remote"
                        "/record/stop" -> "Gravação encerrada no PC"
                        else -> "Print salvo em Vídeos/NEXUS PC Remote"
                    }
                    if (path == "/record/start") recording = true
                    if (path == "/record/stop") recording = false
                } else status = "Falha no gravador. Confira o código NEXUS Central e a conexão."
            } catch (e: Exception) {
                status = "Erro: ${e.message}"
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(Api.host) {
        while (isActive) {
            if (Api.host.isNotBlank()) {
                val response = kotlinx.coroutines.withContext(Dispatchers.IO) { RecorderClient.get(ctx, "/record/status") }
                if (response != null) {
                    try {
                        val pc = JSONObject(String(response, Charsets.UTF_8)).getJSONObject("pc")
                        recording = pc.optBoolean("active", false)
                    } catch (_: Exception) {}
                }
            }
            delay(2500)
        }
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Button(
            enabled = Api.connected && !busy,
            onClick = {
                val height = resolution
                val width = when (height) { 720 -> 1280; 1080 -> 1920; else -> 2560 }
                if (recording) send("/record/stop", JSONObject().put("device", "pc"))
                else send("/record/start", JSONObject()
                    .put("device", "pc").put("width", width).put("height", height)
                    .put("fps", fps).put("quality", jpegQuality))
            },
            modifier = Modifier.weight(1f).height(42.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(if (recording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                null, Modifier.size(17.dp))
            Spacer(Modifier.width(5.dp))
            Text(if (recording) "PARAR REC" else "GRAVAR")
        }
        OutlinedButton(enabled = Api.connected && !busy,
            onClick = { send("/record/screenshot", JSONObject().put("device", "pc")) },
            shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f).height(42.dp)) {
            Icon(Icons.Default.Screenshot, null, Modifier.size(18.dp))
            Spacer(Modifier.width(5.dp)); Text("PRINT")
        }
        IconButton(onClick = { open = true }, modifier = Modifier.size(42.dp)) {
            Icon(Icons.Default.Tune, "Configurar gravação")
        }
    }

    Text(status, style = MaterialTheme.typography.labelSmall, color = NexusUi.Muted)
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("Gravação do PC") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Resolução do arquivo", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf(720,1080,1440).forEach { value ->
                            FilterChip(selected = resolution == value, onClick = {resolution=value},
                                label = { Text("${value}p") })
                        }
                    }
                    Text("FPS solicitado", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf(15,30,60).forEach { value ->
                            FilterChip(selected = fps == value, onClick = { fps=value },
                                label = { Text("$value") })
                        }
                    }
                    Text("Qualidade do arquivo", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf(55,75,90).forEach { value ->
                            FilterChip(selected = jpegQuality == value,
                                onClick = {jpegQuality=value}, label = {Text("$value")})
                        }
                    }
                    Text("Código NEXUS Central (veja no menu do PC)", style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(
                        value = accessKey,
                        onValueChange = { accessKey = it.trim() },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Código de autorização") }
                    )
                    Text("Formato AVI/MJPEG, sem áudio. FPS real depende do desempenho.",
                        style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { HubStore.setKey(ctx, accessKey); open = false }) { Text("SALVAR") } }
        )
    }
}
