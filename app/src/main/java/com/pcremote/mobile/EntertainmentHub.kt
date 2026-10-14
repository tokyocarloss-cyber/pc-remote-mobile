package com.pcremote.mobile

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

private data class HubEntry(
    val id: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val image: String,
    val url: String,
    val kind: String
) {
    companion object {
        fun parse(json: JSONObject) = HubEntry(
            json.optString("id"), json.optString("title"),
            json.optString("subtitle"), json.optString("description"),
            json.optString("image"), json.optString("url"), json.optString("kind")
        )
    }
}

private object HubApi {
    private fun encode(text: String): String = URLEncoder.encode(text, "UTF-8")

    suspend fun request(context: Context, path: String, payload: JSONObject? = null): JSONObject =
        withContext(Dispatchers.IO) {
            val key = context.getSharedPreferences("nexus_entertainment", Context.MODE_PRIVATE)
                .getString("key", "").orEmpty()
            if (Api.host.isBlank()) throw IllegalStateException("PC não conectado")
            val connection = URL("http://${Api.host}:8765$path").openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 2500
                connection.readTimeout = 14500
                connection.useCaches = false
                connection.setRequestProperty("X-Nexus-Central-Key", key)
                connection.setRequestProperty("Cache-Control", "no-cache")
                if (payload != null) {
                    val bytes = payload.toString().toByteArray(Charsets.UTF_8)
                    connection.requestMethod = "POST"
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    connection.setFixedLengthStreamingMode(bytes.size)
                    connection.outputStream.use { it.write(bytes) }
                }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val result = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                val json = JSONObject(if (result.isBlank()) "{}" else result)
                if (code !in 200..299) {
                    throw IllegalStateException(json.optString("error", "Erro HTTP $code"))
                }
                json
            } finally {
                connection.disconnect()
            }
        }

    suspend fun list(context: Context, kind: String, query: String, games: Boolean): List<HubEntry> {
        val path = when (kind) {
            "installed" -> "/entertainment/library?type=game"
            "video" -> "/entertainment/library?type=video"
            "game", "steamrip", "movie", "series" ->
                "/entertainment/search?type=$kind&q=${encode(query)}"
            else -> return emptyList()
        }
        val json = request(context, path)
        val array = json.optJSONArray("items") ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.let(HubEntry::parse)
        }
    }
}

@Composable
fun EntertainmentHub(games: Boolean) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { ctx.getSharedPreferences("nexus_entertainment", Context.MODE_PRIVATE) }
    var code by rememberSaveable { mutableStateOf(prefs.getString("key", "").orEmpty()) }
    var paired by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var term by rememberSaveable(games) { mutableStateOf("") }
    var directLink by rememberSaveable { mutableStateOf("") }
    var mode by remember(games) { mutableStateOf(if (games) "game" else "movie") }
    var entries by remember { mutableStateOf<List<HubEntry>>(emptyList()) }
    var jobs by remember { mutableStateOf<org.json.JSONArray?>(null) }

    fun command(path: String, payload: JSONObject, after: (() -> Unit)? = null) {
        scope.launch {
            working = true
            try {
                val res = HubApi.request(ctx, path, payload)
                message = res.optString("message", "Comando enviado ao PC.")
                after?.invoke()
            } catch (e: Exception) {
                message = e.message ?: "Não foi possível conectar ao PC."
            } finally {
                working = false
            }
        }
    }

    fun refresh(section: String, q: String = term) {
        if (!paired || working) return
        scope.launch {
            working = true
            message = ""
            try {
                entries = HubApi.list(ctx, section, q, games)
                if (entries.isEmpty()) message = "Nenhum resultado nesta categoria."
            } catch (e: Exception) {
                message = e.message ?: "Falha ao pesquisar."
            } finally {
                working = false
            }
        }
    }

    LaunchedEffect(games, Api.host) {
        paired = false
        if (prefs.getString("key", "").isNullOrBlank()) return@LaunchedEffect
        try {
            HubApi.request(ctx, "/entertainment/status")
            paired = true
        } catch (_: Exception) {
            paired = false
        }
    }

    LaunchedEffect(paired, mode) {
        if (paired && mode in setOf("installed", "video")) {
            try {
                entries = HubApi.list(ctx, mode, "", games)
            } catch (e: Exception) {
                message = e.message.orEmpty()
            }
        }
    }

    LaunchedEffect(paired, mode) {
        if (paired && mode == "downloads") {
            while (true) {
                try {
                    jobs = HubApi.request(ctx, "/entertainment/downloads").optJSONArray("items")
                } catch (e: Exception) {
                    message = e.message.orEmpty()
                }
                delay(1200)
            }
        }
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            if (games) "NEXUS Jogos" else "NEXUS Cinema",
            color = NexusUi.Text, style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            if (games) "Pesquise na Steam e instale pela sua conta no PC."
            else "Encontre filmes e séries e reproduza arquivos que já estão no PC.",
            color = NexusUi.Muted, style = MaterialTheme.typography.bodySmall
        )

        if (!paired) {
            Surface(
                color = NexusUi.Panel,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, NexusUi.Border)
            ) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Parear a Central", fontWeight = FontWeight.Bold, color = NexusUi.Text)
                    Text(
                        "No Windows: ícone do NEXUS ao lado do relógio → Mostrar código NEXUS Central. Digite o código abaixo.",
                        color = NexusUi.Muted, style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(value = code, onValueChange = { code = it.trim() },
                        label = { Text("Código de pareamento") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth())
                    Button(enabled = code.isNotBlank() && !working, onClick = {
                        scope.launch {
                            working = true
                            prefs.edit().putString("key", code).apply()
                            try {
                                HubApi.request(ctx, "/entertainment/status")
                                paired = true
                                message = "Central conectada!"
                            } catch (e: Exception) {
                                paired = false
                                message = e.message ?: "Não foi possível parear."
                            } finally { working = false }
                        }
                    }) { Text("Conectar Central") }
                }
            }
        } else {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val sections = if (games) listOf("game" to "Steam", "steamrip" to "SteamRIP", "installed" to "Instalados")
                               else listOf("movie" to "Filmes", "series" to "Séries",
                                           "video" to "Meus vídeos", "downloads" to "Downloads")
                sections.forEach { (id, label) ->
                    FilterChip(selected = mode == id, onClick = {
                        mode = id
                        entries = emptyList()
                        message = ""
                    }, label = { Text(label) })
                }
            }

            if (mode in setOf("movie", "series", "game", "steamrip")) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = term, onValueChange = { term = it },
                        label = { Text(if (games) "Nome do jogo" else "Título do filme ou série") },
                        singleLine = true, modifier = Modifier.weight(1f)
                    )
                    Button(enabled = !working && term.trim().length >= 2,
                        onClick = { refresh(mode) }) {
                        Icon(Icons.Default.Search, contentDescription = "Buscar")
                    }
                }
                if (mode == "steamrip") {
                    Text(
                        "Fonte externa: os links e arquivos não são verificados pelo NEXUS. " +
                            "Downloads, extração e execução automáticos estão desativados.",
                        color = NexusUi.Muted, style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedButton(
                        enabled = !working && term.trim().length >= 2,
                        onClick = {
                            command(
                                "/entertainment/steamrip",
                                JSONObject().put("query", term.trim())
                            )
                        }
                    ) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Abrir pesquisa no navegador do PC")
                    }
                }
            }

            if (mode == "downloads") {
                Text("Downloads diretamente no PC", color = NexusUi.Text, fontWeight = FontWeight.Bold)
                Text("Cole um link direto autorizado de vídeo do Internet Archive (archive.org/download/).",
                    color = NexusUi.Muted, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = directLink, onValueChange = { directLink = it },
                    label = { Text("Endereço HTTPS do arquivo") },
                    modifier = Modifier.fillMaxWidth(), maxLines = 2
                )
                Button(enabled = directLink.isNotBlank() && !working, onClick = {
                    command("/entertainment/download", JSONObject().put("url", directLink))
                }) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Baixar no PC")
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val snapshot = jobs
                    items(snapshot?.length() ?: 0) { i ->
                        val job = snapshot?.optJSONObject(i) ?: JSONObject()
                        val downloaded = job.optLong("downloaded")
                        val total = job.optLong("total")
                        Surface(color = NexusUi.Panel, shape = RoundedCornerShape(12.dp)) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(job.optString("title"), color = NexusUi.Text, fontWeight = FontWeight.SemiBold)
                                Text("${job.optString("state")} · ${downloaded / 1048576} MB" +
                                    if (total > 0) " / ${total / 1048576} MB" else "",
                                    color = NexusUi.Muted)
                                if (total > 0) {
                                    LinearProgressIndicator(
                                        progress = { (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                if (job.optString("error").isNotBlank()) {
                                    Text(job.optString("error"), color = MaterialTheme.colorScheme.error)
                                }
                                if (job.optString("state") in setOf("queued", "connecting", "downloading")) {
                                    TextButton(onClick = {
                                        command("/entertainment/cancel",
                                            JSONObject().put("id", job.optString("id")))
                                    }) { Text("Cancelar") }
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(entries, key = { it.kind + it.id + it.title }) { item ->
                        Surface(
                            color = NexusUi.Panel,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, NexusUi.Border)
                        ) {
                            Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (item.image.startsWith("https://")) {
                                    AsyncImage(
                                        model = item.image, contentDescription = item.title,
                                        modifier = Modifier.size(width = 80.dp, height = 106.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(Modifier.size(width = 60.dp, height = 92.dp)
                                        .background(NexusUi.PanelRaised, RoundedCornerShape(8.dp)),
                                        contentAlignment = Alignment.Center) {
                                        Icon(if (games) Icons.Default.SportsEsports else Icons.Default.Movie,
                                            null, tint = NexusUi.Accent)
                                    }
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text(item.title, color = NexusUi.Text, fontWeight = FontWeight.Bold,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(item.subtitle, color = NexusUi.Muted,
                                        style = MaterialTheme.typography.labelSmall)
                                    if (item.description.isNotBlank()) {
                                        Text(item.description, color = NexusUi.Muted,
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 3, overflow = TextOverflow.Ellipsis)
                                    }
                                    when (item.kind) {
                                        "movie", "series" -> {
                                            OutlinedButton(enabled = item.url.isNotBlank() && !working,
                                                onClick = { command("/entertainment/open",
                                                    JSONObject().put("url", item.url)) }) {
                                                Text("Abrir informações no PC")
                                            }
                                        }
                                        "game" -> {
                                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                                OutlinedButton(enabled = !working, onClick = {
                                                    command("/entertainment/steam",
                                                        JSONObject().put("id", item.id).put("action", "store"))
                                                }) { Text("Steam") }
                                                Button(enabled = !working, onClick = {
                                                    command("/entertainment/steam",
                                                        JSONObject().put("id", item.id).put("action", "install"))
                                                }) { Text("Instalar") }
                                            }
                                        }
                                        "steamrip" -> {
                                            Button(
                                                enabled = !working,
                                                onClick = {
                                                    command(
                                                        "/entertainment/steamrip",
                                                        JSONObject().put("url", item.url)
                                                    )
                                                }
                                            ) { Text("Abrir página no PC") }
                                        }
                                        "installed-game" -> {
                                            Button(enabled = !working, onClick = {
                                                command("/entertainment/launch-game",
                                                    JSONObject().put("id", item.id))
                                            }) { Text("Jogar no PC") }
                                        }
                                        "local-video" -> {
                                            Button(enabled = !working, onClick = {
                                                command("/entertainment/play",
                                                    JSONObject().put("id", item.id))
                                            }) { Text("Reproduzir no PC") }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (working) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        if (message.isNotBlank()) {
            Text(message, color = NexusUi.Accent, style = MaterialTheme.typography.bodySmall)
        }
    }
}
