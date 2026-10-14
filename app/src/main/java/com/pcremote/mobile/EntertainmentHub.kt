package com.pcremote.mobile

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
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
    var pairingPanel by remember { mutableStateOf(false) }
    var pairingCode by remember { mutableStateOf(HubStore.key(ctx)) }
    val connected = Api.connected && HubStore.key(ctx).isNotBlank()
    val tabs = if (games)
        listOf("game" to "Steam", "steamrip" to "SteamRIP", "installed" to "No meu PC",
            "favorites" to "Favoritos", "queue" to "Fila")
    else listOf("movie" to "Filmes", "series" to "Séries", "video" to "Meus vídeos",
        "favorites" to "Favoritos", "download" to "Download", "queue" to "Fila")

    fun openUrl(url: String) {
        try {
            val u = Uri.parse(url)
            if (u.scheme != "https") {
                notice = "Somente páginas HTTPS são abertas."
                return
            }
            ctx.startActivity(Intent(Intent.ACTION_VIEW, u).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) { notice = e.message ?: "Navegador indisponível" }
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

    val visible = when (mode) {
        "favorites" -> HubStore.favorites(ctx, games)
        "installed" -> HubStore.localItems(ctx, "game")
        "video" -> HubStore.localItems(ctx, "video")
        else -> results
    }
    val tasks = if (mode == "queue") HubStore.tasks(ctx).filter { task ->
        if (games) task.action in setOf("steam_install", "steamrip_open", "game_launch")
        else task.action in setOf("archive_download", "catalog_open", "video_play")
    } else emptyList()

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(if (games) Icons.Default.SportsEsports else Icons.Default.Movie,
                contentDescription = null, tint = NexusUi.Accent)
            Text(if (games) "NEXUS Jogos" else "NEXUS Cinema",
                color = NexusUi.Text, fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f))
            IconButton(onClick = { pairingPanel = !pairingPanel }) {
                Icon(Icons.Default.Computer, contentDescription = "Configurar PC",
                    tint = if (connected) NexusUi.Success else NexusUi.Muted)
            }
        }
        Text(
            if (games) "Explore lojas no celular, guarde jogos e prepare instalações."
            else "Pesquise no celular, salve favoritos e organize o que quer assistir.",
            color = NexusUi.Muted, style = MaterialTheme.typography.bodySmall
        )
        Text(
            if (connected) "PC conectado • tarefas sincronizadas automaticamente"
            else "Modo independente • PC desligado ou não pareado",
            color = if (connected) NexusUi.Success else NexusUi.Accent,
            style = MaterialTheme.typography.labelMedium
        )

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

        if (mode in setOf("game", "steamrip", "movie", "series")) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(value = query, onValueChange = { query = it },
                    label = { Text(if (games) "Pesquisar jogo" else "Pesquisar título") },
                    singleLine = true, modifier = Modifier.weight(1f))
                Button(onClick = { search() },
                    enabled = !busy && query.trim().length >= 2) {
                    Icon(Icons.Default.Search, contentDescription = "Pesquisar")
                }
            }
            if (mode == "steamrip") {
                Text("Fonte externa. Os links podem mudar; o NEXUS não instala nem executa cracks.",
                    color = NexusUi.Muted, style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { openUrl(HubCatalog.steamRipSearchUrl(query)) },
                    enabled = query.trim().length >= 2) {
                    Icon(Icons.Default.OpenInBrowser, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Pesquisar SteamRIP no celular")
                }
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

        if (mode == "queue") {
            Text("Salvo na memória do celular", color = NexusUi.Text,
                fontWeight = FontWeight.Bold)
            Text("Não precisa do PC ligado. Depois do primeiro pareamento, o NEXUS " +
                "tenta enviar a fila quando reencontrar o computador.",
                color = NexusUi.Muted, style = MaterialTheme.typography.bodySmall)
            if (tasks.isEmpty()) Text("Nenhuma tarefa nesta fila.", color = NexusUi.Muted)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            }
        } else if (mode != "download") {
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
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(visible, key = { it.kind + it.id }) { item ->
                    Surface(color = NexusUi.Panel, shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, NexusUi.Border)) {
                        Row(Modifier.fillMaxWidth().padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (item.image.startsWith("https://")) {
                                AsyncImage(model = item.image, contentDescription = item.title,
                                    modifier = Modifier.size(width = 74.dp, height = 94.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop)
                            } else {
                                Icon(if (games) Icons.Default.SportsEsports else Icons.Default.Movie,
                                    null, tint = NexusUi.Accent, modifier = Modifier.size(42.dp))
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Row(verticalAlignment = Alignment.Top) {
                                    Text(item.title, color = NexusUi.Text, fontWeight = FontWeight.Bold,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f))
                                    IconButton(onClick = {
                                        HubStore.toggleFavorite(ctx, item)
                                        revision++
                                    }, modifier = Modifier.size(34.dp)) {
                                        Icon(if (HubStore.isFavorite(ctx, item)) Icons.Default.Favorite
                                            else Icons.Default.FavoriteBorder,
                                            contentDescription = "Salvar favorito", tint = NexusUi.Accent,
                                            modifier = Modifier.size(21.dp))
                                    }
                                }
                                Text(item.subtitle, color = NexusUi.Muted,
                                    style = MaterialTheme.typography.labelSmall)
                                if (item.description.isNotBlank()) {
                                    Text(item.description, color = NexusUi.Muted,
                                        style = MaterialTheme.typography.bodySmall, maxLines = 2,
                                        overflow = TextOverflow.Ellipsis)
                                }
                                when (item.kind) {
                                    "game" -> {
                                        TextButton(onClick = { openUrl(item.url) }) {
                                            Text("Abrir Steam no celular")
                                        }
                                        Button(onClick = {
                                            enqueue(item, "steam_install",
                                                JSONObject().put("id", item.id))
                                        }) { Text("Instalar quando PC conectar") }
                                    }
                                    "steamrip" -> {
                                        TextButton(onClick = { openUrl(item.url) }) {
                                            Text("Ver SteamRIP no celular")
                                        }
                                        OutlinedButton(onClick = {
                                            enqueue(item, "steamrip_open",
                                                JSONObject().put("url", item.url))
                                        }) { Text("Abrir no PC depois") }
                                    }
                                    "movie", "series" -> {
                                        TextButton(onClick = { openUrl(item.url) }) {
                                            Text("Ver detalhes no celular")
                                        }
                                        OutlinedButton(onClick = {
                                            enqueue(item, "catalog_open",
                                                JSONObject().put("url", item.url))
                                        }) { Text("Abrir no PC depois") }
                                    }
                                    "installed-game" -> Button(onClick = {
                                        enqueue(item, "game_launch", JSONObject().put("id", item.id))
                                    }) { Text("Jogar no PC") }
                                    "local-video" -> Button(onClick = {
                                        enqueue(item, "video_play", JSONObject().put("id", item.id))
                                    }) { Text("Reproduzir no PC") }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (notice.isNotBlank()) {
            Text(notice, color = NexusUi.Accent,
                style = MaterialTheme.typography.bodySmall)
        }
    }
}
