package com.pcremote.mobile

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class RefApp(val name: String, val store: String)

@Composable
fun ReferenceLibrary() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { ctx.getSharedPreferences("nexus_favorites", Context.MODE_PRIVATE) }
    var apps by remember { mutableStateOf<List<RefApp>>(emptyList()) }
    var search by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Todos") }
    var favorites by remember { mutableStateOf(prefs.getStringSet("apps", emptySet())?.toSet().orEmpty()) }
    var wheel by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(Api.host, refresh) {
        if (Api.host.isBlank()) return@LaunchedEffect
        loading = true
        val raw = RemoteClient.get("/apps")
        if (raw != null) {
            try {
                val array = JSONArray(String(raw))
                apps = (0 until array.length()).map { i ->
                    val v = array.get(i)
                    if (v is org.json.JSONObject) RefApp(v.optString("name"), v.optString("store", "Outros"))
                    else RefApp(v.toString(), "Outros")
                }.filter { it.name.isNotBlank() }.distinctBy { it.name.lowercase() }
            } catch (_: Exception) { }
        }
        loading = false
    }

    val order = loadStoreOrder(ctx)
    val base = apps.filter { isStoreEnabled(ctx, it.store) }.sortedWith(compareBy<RefApp> {
        val i = order.indexOf(it.store); if (i < 0) 999 else i
    }.thenBy { it.name.lowercase() })
    val shown = base.filter {
        (search.isBlank() || it.name.contains(search, true)) && when (category) {
            "Jogos" -> it.store in setOf("Steam", "Epic", "Xbox", "EA", "Ubisoft", "Battle.net", "GOG", "Alll")
            "Favoritos" -> it.name in favorites
            else -> true
        }
    }

    if (wheel && base.isNotEmpty()) {
        QuickWheelDialog(
            apps = (if (favorites.isNotEmpty()) base.filter { it.name in favorites } else base).take(8),
            onDismiss = { wheel = false },
            onLaunch = { app -> scope.launch(Dispatchers.IO) { Api.post("/launch", app.name) }; wheel = false }
        )
    }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Apps e Jogos", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Black)
                Text("Todos os aplicativos do seu PC", color = NexusUi.Muted, fontSize = 11.sp)
            }
            IconButton(onClick = { refresh++ }, modifier = Modifier.size(40.dp).background(Color(0xFF101820), RoundedCornerShape(13.dp))) {
                Icon(Icons.Default.Refresh, "Atualizar", tint = NexusUi.Accent)
            }
            Spacer(Modifier.width(6.dp))
            IconButton(
                onClick = { if (base.isNotEmpty()) wheel = true },
                modifier = Modifier.size(40.dp).background(Color(0xFF0C3445), RoundedCornerShape(13.dp))
            ) { Icon(Icons.Default.RadioButtonChecked, "Roda rápida", tint = NexusUi.Accent) }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            placeholder = { Text("Pesquisar aplicativos…") },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = NexusUi.Accent) },
            shape = RoundedCornerShape(17.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF0B1218), unfocusedContainerColor = Color(0xFF0B1218),
                focusedBorderColor = NexusUi.Accent.copy(alpha = .55f), unfocusedBorderColor = Color(0xFF17232D)
            )
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf("Todos", "Jogos", "Favoritos").forEach { name ->
                FilterChip(
                    selected = category == name, onClick = { category = name }, label = { Text(name) },
                    leadingIcon = if (name == "Favoritos") {{ Icon(Icons.Default.Star, null, Modifier.size(15.dp)) }} else null
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        if (base.isNotEmpty()) {
            TextButton(onClick = { wheel = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.RadioButtonChecked, null, tint = NexusUi.Accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp)); Text("INÍCIO RÁPIDO • RODA DE APPS", color = NexusUi.Accent, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
        }
        when {
            loading && apps.isEmpty() -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = NexusUi.Accent) }
            Api.host.isBlank() -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { Text("Conecte ao PC para carregar", color = NexusUi.Muted) }
            shown.isEmpty() -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { Text(if (apps.isEmpty()) "Nenhum aplicativo recebido do PC" else "Nenhum item nessa categoria", color = NexusUi.Muted) }
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(3), modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                items(shown, key = { "${it.store}:${it.name}" }) { app ->
                    RefAppCard(
                        app = app, favorite = app.name in favorites,
                        onFavorite = {
                            favorites = if (app.name in favorites) favorites - app.name else favorites + app.name
                            prefs.edit().putStringSet("apps", favorites).apply()
                        },
                        onClick = { scope.launch(Dispatchers.IO) { Api.post("/launch", app.name) } }
                    )
                }
            }
        }
    }
}

@Composable
private fun RefAppCard(app: RefApp, favorite: Boolean, onFavorite: () -> Unit, onClick: () -> Unit) {
    Surface(
        color = Color(0xFF0A1016), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Color(0xFF17232D)),
        modifier = Modifier.fillMaxWidth().aspectRatio(.88f).clickable(onClick = onClick)
    ) {
        Box(Modifier.fillMaxSize().padding(8.dp)) {
            IconButton(onClick = onFavorite, modifier = Modifier.align(Alignment.TopEnd).size(27.dp)) {
                Icon(if (favorite) Icons.Default.Star else Icons.Default.StarBorder, null, tint = if (favorite) Color(0xFFFFD45C) else NexusUi.Muted, modifier = Modifier.size(16.dp))
            }
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                RefAppIcon(app, 52)
                Spacer(Modifier.height(8.dp))
                Text(app.name, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            }
            Text(app.store, color = NexusUi.Muted, fontSize = 7.sp, modifier = Modifier.align(Alignment.BottomCenter), maxLines = 1)
        }
    }
}

@Composable
private fun RefAppIcon(app: RefApp, size: Int) {
    val shape = RoundedCornerShape((size * .25f).dp)
    Box(Modifier.size(size.dp).background(Color(0xFF111A22), shape).clip(shape), contentAlignment = Alignment.Center) {
        Text(app.name.take(1).uppercase(), color = NexusUi.Accent, fontWeight = FontWeight.Black)
        if (Api.host.isNotBlank()) {
            AsyncImage(
                model = "http://${Api.host}:8765/app-icon/${Uri.encode(app.name)}",
                contentDescription = app.name, modifier = Modifier.fillMaxSize().padding(4.dp), contentScale = ContentScale.Fit
            )
        }
    }
}

@Composable
private fun QuickWheelDialog(apps: List<RefApp>, onDismiss: () -> Unit, onLaunch: (RefApp) -> Unit) {
    if (apps.isEmpty()) return
    var selected by remember(apps) { mutableIntStateOf(0) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color(0xF5000306)).statusBarsPadding().navigationBarsPadding()) {
            Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null, tint = Color.White) }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Início Rápido", color = Color.White, fontWeight = FontWeight.Black, fontSize = 21.sp)
                        Text("Toque em um app e abra pelo centro", color = NexusUi.Muted, fontSize = 9.sp)
                    }
                    Spacer(Modifier.width(48.dp))
                }
                Spacer(Modifier.height(20.dp))
                BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
                    val diameter = if (maxWidth < maxHeight) maxWidth else maxHeight
                    val radius = diameter * .36f
                    val itemSize = 66.dp
                    Box(Modifier.size(diameter * .78f).background(Color(0xFF080E14), CircleShape))
                    Box(Modifier.size(diameter * .56f).background(Color(0xFF05090D), CircleShape))
                    apps.forEachIndexed { index, app ->
                        val angle = -PI / 2.0 + 2.0 * PI * index / apps.size
                        val x = maxWidth / 2 + radius * cos(angle).toFloat() - itemSize / 2
                        val y = maxHeight / 2 + radius * sin(angle).toFloat() - itemSize / 2
                        Surface(
                            color = if (selected == index) Color(0xFF0C2A38) else Color(0xFF0C1218),
                            shape = CircleShape,
                            border = BorderStroke(if (selected == index) 2.dp else 1.dp, if (selected == index) NexusUi.Accent else Color(0xFF26323D)),
                            modifier = Modifier.offset(x, y).size(itemSize).clickable { selected = index }
                        ) { Box(contentAlignment = Alignment.Center) { RefAppIcon(app, 50) } }
                    }
                    val chosen = apps[selected.coerceIn(0, apps.lastIndex)]
                    Surface(
                        color = Color(0xFF05090D), shape = CircleShape,
                        border = BorderStroke(2.dp, NexusUi.Accent),
                        modifier = Modifier.size(142.dp).clickable { onLaunch(chosen) }
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            RefAppIcon(chosen, 58); Spacer(Modifier.height(7.dp))
                            Text(chosen.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp, maxLines = 2, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 10.dp))
                            Spacer(Modifier.height(4.dp)); Text("ABRIR", color = NexusUi.Accent, fontSize = 9.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }
    }
}
