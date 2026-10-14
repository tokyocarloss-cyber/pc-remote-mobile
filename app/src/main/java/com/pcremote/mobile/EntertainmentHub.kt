package com.pcremote.mobile

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.graphics.SolidColor
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
import java.net.URL

@Composable
fun EntertainmentHub(games: Boolean) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var mode by rememberSaveable(games) { mutableStateOf(if (games) "game" else "movie") }
    var query by rememberSaveable(games) { mutableStateOf("") }
    var videoUrl by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<HubItem>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf("") }
    var revision by remember { mutableIntStateOf(0) }
    var pcDownloads by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var pairingPanel by remember { mutableStateOf(false) }
    var sourceFor by remember { mutableStateOf<HubItem?>(null) }
    var pairingCode by remember { mutableStateOf(HubStore.key(ctx)) }
    val connected = Api.connected && HubStore.key(ctx).isNotBlank()
    val tabs = if (games)
        listOf("game" to "Steam", "steamrip" to "SteamRIP", "installed" to "No meu PC",
            "favorites" to "Favoritos", "queue" to "Fila")
    else listOf("movie" to "Filmes", "series" to "Séries")

    fun openUrl(url: String) {
        try {
            val uri = Uri.parse(url)
            if (uri.scheme != "https") {
                notice = "O navegador NEXUS aceita apenas HTTPS."
                return
            }
            ctx.startActivity(Intent(ctx, NexusBrowserActivity::class.java)
                .putExtra("url", url).putExtra("games", games))
        } catch (e: Exception) {
            notice = e.message ?: "Não foi possível abrir o navegador do NEXUS."
        }
    }

    fun enqueue(item: HubItem, action: String, payload: JSONObject) {
        HubStore.enqueue(ctx, item.title, action, payload)
        revision++
        notice = "Salvo no celular! A tarefa será enviada quando o PC conectar."
        if (Api.connected) scope.launch {
            HubSync.trySync(ctx, Api.host)
            revision++
        }
    }

    fun search() {
        if (query.trim().length < 2) return
        scope.launch {
            busy = true
            notice = ""
            val requestedMode = mode
            val requestedQuery = query.trim()
            // Show an earlier cached result immediately, even without Internet.
            results = HubStore.cachedResults(ctx, requestedMode, requestedQuery)
            try {
                val fresh = withContext(Dispatchers.IO) {
                    HubCatalog.search(requestedMode, requestedQuery)
                }
                HubStore.cacheResults(ctx, requestedMode, requestedQuery, fresh)
                if (mode == requestedMode) results = fresh
                notice = if (fresh.isEmpty()) "Sem resultados. Tente outro termo." else ""
            } catch (e: Exception) {
                notice = if (results.isNotEmpty())
                    "Sem Internet ou fonte indisponível; mostrando pesquisa salva."
                else "Não foi possível pesquisar: ${e.message}. Se for SteamRIP, abra o site no celular."
            } finally { busy = false }
        }
    }

    LaunchedEffect(Unit) {
        HubSync.schedule(ctx)
        while (true) {
            try {
                val host = if (Api.connected) Api.host else ""
                HubSync.trySync(ctx, host)
            } catch (_: Exception) { }
            revision++
            delay(9000)
        }
    }

    LaunchedEffect(mode) {
        results = when (mode) {
            "installed" -> HubStore.localItems(ctx, "game")
            "video" -> HubStore.localItems(ctx, "video")
            "favorites" -> HubStore.favorites(ctx, games)
            "game", "steamrip", "movie", "series" ->
                HubStore.cachedResults(ctx, mode, query.trim())
            else -> emptyList()
        }
        notice = ""
    }

    LaunchedEffect(mode, connected) {
        if (mode == "queue" && connected) {
            while (true) {
                try {
                    val jobs = HubPc.request(ctx, "/entertainment/downloads")
                        .optJSONArray("items")
                    if (jobs != null) pcDownloads = (0 until jobs.length())
                        .mapNotNull { jobs.optJSONObject(it) }
                } catch (_: Exception) { /* Retry while the PC is reachable. */ }
                delay(4000)
            }
        } else pcDownloads = emptyList()
    }

    val visible = remember(mode, games, revision, results) {
        when (mode) {
        "favorites" -> HubStore.favorites(ctx, games)
        "installed" -> HubStore.localItems(ctx, "game")
        "video" -> HubStore.localItems(ctx, "video")
        else -> results
        }
    }
    val tasks = remember(mode, games, revision) {
        if (mode == "queue") HubStore.tasks(ctx).filter { task ->
        if (games) task.action in setOf("steam_install", "steamrip_open", "game_launch") ||
            (task.action == "web_download" && task.payload.optBoolean("games"))
        else task.action in setOf("archive_download", "catalog_open", "source_open", "video_play") ||
            (task.action == "web_download" && !task.payload.optBoolean("games"))
    } else emptyList()
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(if (games) Icons.Default.SportsEsports else Icons.Default.Movie,
                contentDescription = null, tint = NexusUi.Accent)
            Text(if (games) "NEXUS Jogos" else "NEXUS Cinema",
                color = NexusUi.Text, fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f))
            IconButton(onClick = { mode = "favorites" }) {
                Icon(Icons.Default.FavoriteBorder, contentDescription = "Favoritos", tint = NexusUi.Accent)
            }
            IconButton(onClick = { mode = "queue" }) {
                Icon(Icons.Default.Download, contentDescription = "Downloads e fila", tint = NexusUi.Accent)
            }
            IconButton(onClick = { pairingPanel = !pairingPanel }) {
                Icon(Icons.Default.Computer, contentDescription = "Configurar PC",
                    tint = if (connected) NexusUi.Success else NexusUi.Muted)
            }
        }
        // Keep the search above the source/category chips, in a compact marketplace bar.
        if (mode in setOf("game", "steamrip", "movie", "series")) {
            Row(Modifier.fillMaxWidth().height(50.dp)
                .background(NexusUi.PanelRaised, RoundedCornerShape(14.dp))
                .padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Search, null, tint = NexusUi.Muted,
                    modifier = Modifier.size(20.dp))
                BasicTextField(value = query, onValueChange = { query = it },
                    singleLine = true, cursorBrush = SolidColor(NexusUi.Accent),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = NexusUi.Text),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { search() }),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        Box {
                            if (query.isEmpty()) Text("Buscar " +
                                if (games) "jogos..." else "filmes e séries...",
                                color = NexusUi.Muted,
                                style = MaterialTheme.typography.bodyMedium)
                            inner()
                        }
                    })
                IconButton(onClick = { search() },
                    enabled = !busy && query.trim().length >= 2,
                    modifier = Modifier.size(42.dp)
                        .background(NexusUi.AccentStrong, RoundedCornerShape(12.dp))) {
                    Icon(Icons.Default.Search, "Pesquisar", tint = androidx.compose.ui.graphics.Color.White)
                }
            }
        }
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text(if (Api.connected) "PC conectado"
                else "Biblioteca disponível sem o PC",
                color = if (Api.connected) NexusUi.Success else NexusUi.Muted,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.weight(1f))
            TextButton(onClick = { openUrl(if (games) "https://steamrip.com/"
                else "https://www.google.com/") }) {
                Icon(Icons.Default.Language, null, modifier=Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Navegador", style = MaterialTheme.typography.labelMedium)
            }
        }
        if (pairingPanel) {
            Surface(color = NexusUi.Panel, shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, NexusUi.Border)) {
                Column(Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Conexão com o PC (opcional)", color = NexusUi.Text,
                        fontWeight = FontWeight.SemiBold)
                    Text(
                        "O catálogo funciona sem PC. Para sincronizar, no Windows abra " +
                            "o ícone do NEXUS → Mostrar código NEXUS Central. " +
                            "O código é necessário somente para enviar comandos.",
                        color = NexusUi.Muted, style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = pairingCode,
                        onValueChange = { pairingCode = it.trim() },
                        label = { Text("Código do PC") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth())
                    Button(onClick = {
                        HubStore.setKey(ctx, pairingCode)
                        if (Api.connected) HubStore.setHost(ctx, Api.host)
                        pairingPanel = false
                        scope.launch {
                            val ok = HubSync.trySync(ctx, if (Api.connected) Api.host else "")
                            notice = if (ok) "PC sincronizado." else
                                "Código salvo. Você pode continuar usando a biblioteca."
                            revision++
                        }
                    }, enabled = pairingCode.isNotBlank()) {
                        Text("Salvar e tentar sincronizar")
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            tabs.forEach { (id, label) ->
                FilterChip(selected = mode == id, onClick = { mode = id },
                    label = { Text(label) })
            }
        }

        if (mode == "download") {
            Text("Agendar download no computador", color = NexusUi.Text,
                fontWeight = FontWeight.Bold)
            Text("Aceita arquivo de vídeo autorizado com link direto do Internet Archive. " +
                "A tarefa fica guardada mesmo com o PC desligado.",
                color = NexusUi.Muted, style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(value = videoUrl, onValueChange = { videoUrl = it },
                label = { Text("https://archive.org/download/.../video.mp4") },
                modifier = Modifier.fillMaxWidth(), maxLines = 3)
            Button(enabled = videoUrl.isNotBlank(), onClick = {
                try {
                    val u = URL(videoUrl.trim())
                    require(u.protocol == "https" && u.host == "archive.org" &&
                        u.path.startsWith("/download/") &&
                        listOf(".mp4", ".mkv", ".webm", ".avi", ".mov", ".m4v")
                            .any { u.path.lowercase().endsWith(it) })
                    val item = HubItem(videoUrl.trim(), "Vídeo do Internet Archive")
                    enqueue(item, "archive_download", JSONObject().put("url", videoUrl.trim()))
                    videoUrl = ""
                } catch (_: Exception) {
                    notice = "Use um link HTTPS direto de vídeo do Internet Archive."
                }
            }) { Text("Adicionar à fila do PC") }
        }

        if (mode == "sources") {
            CinemaSources(
                query = query,
                changeQuery = { query = it },
                open = ::openUrl,
                queue = { source, phrase ->
                    val item = HubItem(source.id, source.title)
                    enqueue(item, "source_open",
                        JSONObject().put("source", source.id).put("query", phrase))
                },
                revision = revision,
                refreshSaved = { revision++ }
            )
        } else if (mode == "queue") {
            Text("Tarefas salvas no celular", color = NexusUi.Text,
                fontWeight = FontWeight.Bold)
            Text("Não precisa do PC ligado. Depois do primeiro pareamento, o NEXUS " +
                "tenta enviar a fila quando reencontrar o computador.",
                color = NexusUi.Muted, style = MaterialTheme.typography.bodySmall)
            if (tasks.isEmpty()) Text("Nenhuma tarefa nesta fila.", color = NexusUi.Muted)
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(tasks, key = { it.id }) { task ->
                    Surface(color = NexusUi.Panel, shape = RoundedCornerShape(12.dp)) {
                        Row(Modifier.fillMaxWidth().padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(task.title, color = NexusUi.Text, fontWeight = FontWeight.Bold)
                                Text(if (task.status == "sent") "Enviado ao PC" else
                                    "Aguardando conexão com o PC",
                                    color = NexusUi.Muted)
                            }
                            IconButton(onClick = {
                                HubStore.removeTask(ctx, task.id)
                                revision++
                            }) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Excluir tarefa",
                                    tint = NexusUi.Muted)
                            }
                        }
                    }
                }
                if (pcDownloads.isNotEmpty()) {
                    item {
                        Text("Transferências no PC", color = NexusUi.Text,
                            fontWeight = FontWeight.Bold)
                    }
                    items(pcDownloads, key = { "job_" + it.optString("id") }) { job ->
                        val bytes = job.optLong("downloaded")
                        val total = job.optLong("total")
                        val progressText = if (total > 0)
                            "${(bytes * 100 / total)}% · ${bytes / 1048576} de ${total / 1048576} MB"
                        else "${bytes / 1048576} MB"
                        val state = job.optString("state")
                        Surface(color = NexusUi.Panel, shape = RoundedCornerShape(12.dp)) {
                            Row(Modifier.fillMaxWidth().padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(job.optString("title"), color = NexusUi.Text,
                                        fontWeight = FontWeight.Bold)
                                    Text(when (state) {
                                        "finished" -> "Concluído no PC"
                                        "error" -> "Erro: ${job.optString("error")}"
                                        "cancelled" -> "Cancelado"
                                        "downloading" -> "Baixando · $progressText"
                                        else -> "Aguardando · $state"
                                    }, color = NexusUi.Muted)
                                    if (state == "downloading" && total > 0) {
                                        LinearProgressIndicator(
                                            progress = { (bytes.toFloat() / total).coerceIn(0f, 1f) },
                                            modifier = Modifier.fillMaxWidth())
                                    }
                                }
                                if (state in setOf("connecting", "downloading", "queued")) {
                                    IconButton(onClick = {
                                        scope.launch {
                                            try {
                                                HubPc.request(ctx, "/entertainment/cancel",
                                                    JSONObject().put("id", job.optString("id")))
                                            } catch (e: Exception) {
                                                notice = e.message ?: "Falha ao cancelar"
                                            }
                                        }
                                    }) {
                                        Icon(Icons.Default.Cancel, contentDescription = "Cancelar download",
                                            tint = NexusUi.Muted)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else if (mode != "download" && mode != "sources") {
            if (mode == "installed" || mode == "video") {
                Text("Última biblioteca sincronizada com o PC", color = NexusUi.Text,
                    style = MaterialTheme.typography.bodySmall)
            }
            if (mode == "favorites") {
                Text("Seus favoritos ficam guardados neste celular.", color = NexusUi.Muted,
                    style = MaterialTheme.typography.bodySmall)
            }
            if (visible.isEmpty() && !busy) {
                Text(when (mode) {
                    "installed", "video" -> "Nenhuma biblioteca salva ainda. Conecte ao PC uma vez para importar."
                    "favorites" -> "Toque no coração de um título para salvá-lo."
                    else -> "Pesquise algum título para começar."
                }, color = NexusUi.Muted, style = MaterialTheme.typography.bodySmall)
            }
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {

                items(visible, key = { it.kind + it.id }) { item ->
                    EntertainmentResultCard(item = item, games = games,
                        isFavorite = HubStore.isFavorite(ctx, item),
                        onFavorite = {
                            HubStore.toggleFavorite(ctx, item)
                            revision++
                        },
                        onOpen = {
                            if (item.url.isNotBlank()) openUrl(item.url)
                            else notice = "Este item está na biblioteca do PC."
                        },
                        onSources = { sourceFor = item },
                        onAction = {
                            when (item.kind) {
                                "game" -> enqueue(item, "steam_install",
                                    JSONObject().put("id", item.id))
                                "steamrip" -> openUrl(item.url)
                                "movie", "series" -> sourceFor = item
                                "installed-game" -> enqueue(item, "game_launch",
                                    JSONObject().put("id", item.id))
                                "local-video" -> enqueue(item, "video_play",
                                    JSONObject().put("id", item.id))
                                else -> if (item.url.isNotBlank()) openUrl(item.url)
                            }
                        })
                }
            }
        }
        val selectedMedia = sourceFor
        if (selectedMedia != null) {
            AlertDialog(
                onDismissRequest = { sourceFor = null },
                title = { Text("Pesquisar: "+selectedMedia.title) },
                text = {
                    Column(verticalArrangement=Arrangement.spacedBy(7.dp)) {
                        Text("Fontes reunidas neste resultado. A disponibilidade dos arquivos, " +
                            "o idioma e a licença precisam ser conferidos no site.",
                            style=MaterialTheme.typography.bodySmall,
                            color=NexusUi.Muted)
                        LazyColumn(Modifier.heightIn(max=340.dp)) {
                            items(CinemaDirectories.all, key={it.id}) { source ->
                                TextButton(onClick = {
                                    val address = CinemaDirectories.url(
                                        source.id, selectedMedia.title)
                                    sourceFor = null
                                    openUrl(address)
                                }, modifier=Modifier.fillMaxWidth()) {
                                    Text(source.title, modifier=Modifier.weight(1f))
                                    Icon(Icons.Default.OpenInBrowser, null,
                                        modifier=Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick={sourceFor=null}) { Text("Fechar") } }
            )
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (notice.isNotBlank()) {
            Text(notice, color = NexusUi.Accent,
                style = MaterialTheme.typography.bodySmall)
        }
    }
}
