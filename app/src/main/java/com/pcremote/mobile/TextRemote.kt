package com.pcremote.mobile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun TextRemote() {
    var value by remember { mutableStateOf("") }
    var sent by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Title("Teclado remoto", "Digite no Windows usando o celular")

        Surface(
            color = NexusUi.Panel,
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, NexusUi.Border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(13.dp)) {
                Text("DIGITAÇÃO RÁPIDA", color = NexusUi.Accent, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it; sent = false },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Escreva aqui") },
                    minLines = 4,
                    maxLines = 8,
                    shape = RoundedCornerShape(17.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NexusUi.Accent.copy(alpha = .7f),
                        unfocusedBorderColor = NexusUi.Border,
                        focusedContainerColor = NexusUi.BackgroundSoft,
                        unfocusedContainerColor = NexusUi.BackgroundSoft
                    )
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            Api.post("/text", value)
                            sent = Api.connected
                        }
                    },
                    enabled = value.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(15.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NexusUi.AccentStrong)
                ) {
                    Icon(Icons.Default.Send, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(7.dp))
                    Text(if (sent) "ENVIADO" else "ENVIAR PARA O PC", fontWeight = FontWeight.Bold)
                }
            }
        }

        Text("TECLAS RÁPIDAS", color = NexusUi.Muted, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf("ENTER", "ESC", "SPACE").forEach { key ->
                OutlinedButton(
                    onClick = { scope.launch(Dispatchers.IO) { Api.post("/key", key) } },
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(key, fontWeight = FontWeight.Bold)
                }
            }
        }

        Surface(
            color = NexusUi.PanelRaised,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, NexusUi.Border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(Modifier.padding(12.dp)) {
                Icon(Icons.Default.Keyboard, null, tint = NexusUi.Accent, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(9.dp))
                Text("Acentos e caracteres especiais são enviados pelo clipboard do Windows.", color = NexusUi.Muted, style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(Modifier.height(4.dp))
    }
}
