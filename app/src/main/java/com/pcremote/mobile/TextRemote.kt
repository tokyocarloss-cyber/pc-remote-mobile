package com.pcremote.mobile

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun TextRemote() {
    var value by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    Column {
        Title("Teclado remoto", "Digite no Windows pelo celular")
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(value, { value = it }, Modifier.fillMaxWidth(), label = { Text("Texto") })
        Spacer(Modifier.height(10.dp))
        Button(onClick = { scope.launch(Dispatchers.IO) { Api.post("/text", value) } }, modifier = Modifier.fillMaxWidth()) { Text("Enviar para o PC") }
        Spacer(Modifier.height(8.dp))
        Row {
            listOf("ENTER", "ESC", "SPACE").forEach { key ->
                OutlinedButton(onClick = { scope.launch(Dispatchers.IO) { Api.post("/key", key) } }, modifier = Modifier.weight(1f).padding(3.dp)) { Text(key) }
            }
        }
    }
}
