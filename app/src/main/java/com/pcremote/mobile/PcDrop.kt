package com.pcremote.mobile

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.URLEncoder

private fun fileName(ctx: android.content.Context, uri: Uri): String {
    return try {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "arquivo"
    } catch (_: Exception) {
        uri.lastPathSegment?.substringAfterLast('/') ?: "arquivo"
    }
}

@Composable
fun PcDrop() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Pronto para enviar") }
    var edgeOn by remember { mutableStateOf(false) }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        status = if (granted) "Notificações autorizadas" else "Notificações desativadas"
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                status = "Enviando arquivo…"
                try {
                    val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: byteArrayOf()
                    val name = fileName(ctx, uri).takeLast(180)
                    if (bytes.isEmpty()) {
                        status = "Arquivo vazio ou sem permissão de leitura"
                    } else {
                        val ok = RemoteClient.post("/upload/" + URLEncoder.encode(name, "UTF-8"), bytes)
                        status = if (ok) "Enviado: $name (${bytes.size / 1024} KB)" else "Falha no envio"
                    }
                } catch (_: Exception) {
                    status = "Falha no envio"
                }
            }
        }
    }

    fun enable() {
        if (Build.VERSION.SDK_INT >= 33 && ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (!Settings.canDrawOverlays(ctx)) {
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + ctx.packageName)))
            status = "Autorize a sobreposição e ative novamente"
        } else {
            ctx.startForegroundService(Intent(ctx, EdgeDropService::class.java))
            ctx.startForegroundService(Intent(ctx, PhoneInboxService::class.java))
            edgeOn = true
            status = "Drop Edge ativo"
        }
    }

    fun disable() {
        ctx.stopService(Intent(ctx, EdgeDropService::class.java))
        ctx.stopService(Intent(ctx, PhoneInboxService::class.java))
        edgeOn = false
        status = "Drop Edge desativado"
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Title("Drop", "Arquivos e texto entre celular e Windows")
        Surface(color = NexusUi.Panel, shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, NexusUi.Border), modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.background(Brush.linearGradient(listOf(NexusUi.AccentStrong.copy(alpha = .13f), androidx.compose.ui.graphics.Color.Transparent)))) {
                Column(Modifier.padding(15.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(42.dp).background(NexusUi.Accent.copy(alpha = .10f), CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Default.SwapHoriz, null, tint = NexusUi.Accent) }
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text("DROP EDGE", color = NexusUi.Text, fontWeight = FontWeight.Black)
                            Text("Solte no topo do celular ou no canto do PC", color = NexusUi.Muted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Spacer(Modifier.height(13.dp))
                    Button(onClick = { if (edgeOn) disable() else enable() }, modifier = Modifier.fillMaxWidth().height(46.dp), shape = RoundedCornerShape(15.dp), colors = ButtonDefaults.buttonColors(containerColor = if (edgeOn) NexusUi.PanelRaised else NexusUi.AccentStrong)) { Text(if (edgeOn) "DESATIVAR DROP EDGE" else "ATIVAR DROP EDGE", fontWeight = FontWeight.Bold) }
                }
            }
        }
        Surface(color = NexusUi.PanelRaised, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, NexusUi.Border), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(if (edgeOn) NexusUi.Success else NexusUi.Muted, CircleShape)); Spacer(Modifier.width(8.dp)); Text(status, color = NexusUi.Text, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
            }
        }
        Button(onClick = { picker.launch("*/*") }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = NexusUi.AccentStrong)) {
            Icon(Icons.Default.CloudUpload, null, Modifier.size(19.dp)); Spacer(Modifier.width(8.dp)); Text("ESCOLHER ARQUIVO", fontWeight = FontWeight.Bold)
        }
        OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Texto ou link") }, minLines = 3, maxLines = 6, shape = RoundedCornerShape(18.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NexusUi.Accent.copy(alpha = .7f), unfocusedBorderColor = NexusUi.Border, focusedContainerColor = NexusUi.Panel, unfocusedContainerColor = NexusUi.Panel))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { scope.launch(Dispatchers.IO) { val ok = RemoteClient.post("/clipboard", text.toByteArray()); status = if (ok) "Texto copiado no PC" else "Falha ao copiar" } }, modifier = Modifier.weight(1f).height(46.dp), shape = RoundedCornerShape(15.dp)) { Icon(Icons.Default.ContentCopy, null, Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text("COPIAR") }
            Button(onClick = { scope.launch(Dispatchers.IO) { Api.post("/text", text); status = if (Api.connected) "Texto digitado no PC" else "Falha ao digitar" } }, modifier = Modifier.weight(1f).height(46.dp), shape = RoundedCornerShape(15.dp), colors = ButtonDefaults.buttonColors(containerColor = NexusUi.AccentStrong)) { Icon(Icons.Default.Keyboard, null, Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text("DIGITAR") }
        }
        Spacer(Modifier.height(4.dp))
    }
}
