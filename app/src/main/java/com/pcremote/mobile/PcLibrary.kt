package com.pcremote.mobile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray

@Composable
fun PcLibrary() {
    var apps by remember { mutableStateOf<List<String>>(emptyList()) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Api.host, Api.connected) {
        if (Api.connected) {
            val raw = RemoteClient.get("/apps")
            if (raw != null) try {
                val a = JSONArray(String(raw))
                apps = (0 until a.length()).map { a.getString(it) }
            } catch (_: Exception) {}
        }
    }
    Column {
        Title("Apps & Jogos", "Biblioteca encontrada no PC")
        Spacer(Modifier.height(12.dp))
        if (apps.isEmpty()) Text("Conecte ao PC para carregar a biblioteca")
        LazyColumn {
            items(apps) { name ->
                Surface(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                    scope.launch(Dispatchers.IO) { Api.post("/launch", name) }
                }) { Text(name, Modifier.padding(16.dp)) }
            }
        }
    }
}
