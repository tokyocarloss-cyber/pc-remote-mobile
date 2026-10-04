package com.pcremote.mobile

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class PcApp(val name: String, val store: String = "Outros")

@Composable
fun PcLibrary() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var apps by remember { mutableStateOf<List<PcApp>>(emptyList()) }
    var search by remember { mutableStateOf("") }

    LaunchedEffect(Api.host, Api.connected) {
        if (Api.connected) {
            RemoteClient.get("/apps")?.let { raw ->
                try {
                    val array = JSONArray(String(raw))
                    apps = (0 until array.length()).map { i ->
                        val v = array.get(i)
                        if (v is org.json.JSONObject) {
                            PcApp(v.optString("name"), v.optString("store", "Outros"))
                        } else PcApp(v.toString())
                    }
                } catch (_: Exception) { }
            }
        }
    }

    val order = loadStoreOrder(ctx)
    val filtered = apps
        .filter { isStoreEnabled(ctx, it.store) }
        .sortedWith(compareBy<PcApp> {
            val i = order.indexOf(it.store)
            if (i < 0) 999 else i
        }.thenBy { it.name.lowercase() })
        .filter { search.isBlank() || it.name.contains(search, true) }

    Column(Modifier.fillMaxSize()) {
        Title("Biblioteca", "Seus apps e jogos em um só lugar")
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Buscar app ou jogo") },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = NexusUi.Accent) },
            shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NexusUi.Accent.copy(alpha = .65f),
                unfocusedBorderColor = NexusUi.Border,
                focusedContainerColor = NexusUi.Panel,
                unfocusedContainerColor = NexusUi.Panel
            )
        )

        Spacer(Modifier.height(10.dp))
        if (search.isBlank() && filtered.isNotEmpty()) {
            RadialLauncher(filtered.take(6)) { app ->
                scope.launch(Dispatchers.IO) { Api.post("/launch", app.name) }
            }
            Spacer(Modifier.height(10.dp))
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("TODOS", color = NexusUi.Muted, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Text("${filtered.size} ITENS", color = NexusUi.Muted, style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(6.dp))

        when {
            apps.isEmpty() -> EmptyLibrary(
                if (Api.connected) "Carregando biblioteca…" else "Conecte ao PC para carregar",
                Modifier.weight(1f)
            )
            filtered.isEmpty() -> EmptyLibrary("Nenhum item encontrado", Modifier.weight(1f))
            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(7.dp),
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                items(filtered, key = { "${it.store}:${it.name}" }) { app ->
                    AppRow(app) { scope.launch(Dispatchers.IO) { Api.post("/launch", app.name) } }
                }
            }
        }
    }
}

@Composable
private fun RadialLauncher(apps: List<PcApp>, launch: (PcApp) -> Unit) {
    var selected by remember(apps) { mutableIntStateOf(0) }
    if (apps.isEmpty()) return
    if (selected > apps.lastIndex) selected = 0
    val chosen = apps[selected]

    Surface(
        color = NexusUi.Panel,
        shape = RoundedCornerShape(26.dp),
        border = BorderStroke(1.dp, NexusUi.Border),
        modifier = Modifier.fillMaxWidth().height(220.dp)
    ) {
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension * .34f
                drawCircle(NexusUi.Border.copy(alpha = .8f), radius = r, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f))
                drawCircle(NexusUi.Accent.copy(alpha = .035f), radius = r * .78f)
            }

            val diameter = if (maxWidth < maxHeight) maxWidth else maxHeight
            val radius = diameter * .34f
            val itemSize = 54.dp

            apps.forEachIndexed { index, app ->
                val angle = (-PI / 2.0) + (2.0 * PI * index / apps.size)
                val x = maxWidth / 2 + radius * cos(angle).toFloat() - itemSize / 2
                val y = maxHeight / 2 + radius * sin(angle).toFloat() - itemSize / 2
                Box(
                    Modifier
                        .offset(x = x, y = y)
                        .size(itemSize)
                        .border(
                            if (index == selected) 2.dp else 1.dp,
                            if (index == selected) NexusUi.Accent else NexusUi.Border,
                            RoundedCornerShape(17.dp)
                        )
                        .clickable { selected = index },
                    contentAlignment = Alignment.Center
                ) {
                    AppIcon(app, 52)
                }
            }

            Surface(
                color = NexusUi.PanelRaised,
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, NexusUi.Border),
                modifier = Modifier.width(112.dp).height(86.dp).clickable { launch(chosen) }
            ) {
                Column(
                    Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(chosen.name, color = NexusUi.Text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(5.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PlayArrow, null, tint = NexusUi.Accent, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("ABRIR", color = NexusUi.Accent, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRow(app: PcApp, onClick: () -> Unit) {
    Surface(
        color = NexusUi.Panel,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, NexusUi.Border),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(app, 42)
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(app.name, color = NexusUi.Text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(app.store, color = NexusUi.Muted, style = MaterialTheme.typography.labelSmall)
            }
            Text("ABRIR", color = NexusUi.Accent, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun EmptyLibrary(message: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(message, color = NexusUi.Muted)
    }
}

@Composable
private fun AppIcon(app: PcApp, size: Int) {
    val shape = RoundedCornerShape((size * .28f).dp)
    Box(
        Modifier
            .size(size.dp)
            .background(NexusUi.PanelRaised, shape)
            .clip(shape),
        contentAlignment = Alignment.Center
    ) {
        Text(app.name.take(1).uppercase(), color = NexusUi.Accent, fontWeight = FontWeight.Black)
        if (Api.host.isNotBlank()) {
            AsyncImage(
                model = "http://${Api.host}:8765/app-icon/${Uri.encode(app.name)}",
                contentDescription = app.name,
                modifier = Modifier.fillMaxSize().padding(6.dp),
                contentScale = ContentScale.Fit
            )
        }
    }
}
