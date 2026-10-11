package com.pcremote.mobile

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PairingDialog() {
    if (SecureConnection.pendingHost.isBlank()) return
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var verified by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = { if (!busy) SecureConnection.dismiss() },
        title = { Text("Autorizar este PC") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("No ícone NEXUS da bandeja do Windows, escolha Parear celular. Compare o certificado abaixo com o exibido no PC antes de digitar o PIN.")
            Text(SecureConnection.pendingHost)
            Text(SecureConnection.pendingFingerprint.chunked(8).joinToString(" "), style = MaterialTheme.typography.labelSmall)
            Row { Checkbox(checked = verified, onCheckedChange = { verified = it }); Text("O certificado é igual ao do meu PC") }
            OutlinedTextField(value = pin, onValueChange = { pin = it.filter(Char::isDigit).take(6) }, label = { Text("PIN de 6 dígitos") })
            if (SecureConnection.error.isNotBlank()) Text(SecureConnection.error, color = MaterialTheme.colorScheme.error)
        } },
        confirmButton = { Button(enabled = verified && pin.length == 6 && !busy, onClick = {
            busy = true; scope.launch { withContext(Dispatchers.IO) { SecureConnection.pair(pin) }; busy = false }
        }) { Text(if (busy) "Conectando…" else "AUTORIZAR") } },
        dismissButton = { TextButton(enabled = !busy, onClick = { SecureConnection.dismiss() }) { Text("CANCELAR") } })
}
