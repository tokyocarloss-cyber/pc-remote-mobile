package com.pcremote.mobile

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
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
import kotlinx.coroutines.withContext
import org.json.JSONArray
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin

data class RefApp(val name: String, val store: String)

private val gameStores = setOf("Steam", "Epic", "Xbox", "EA", "Ubisoft", "Battle.net", "GOG")
private val launcherNames = setOf("steam", "epic games launcher", "epic games", "ea app", "xbox", "ubisoft connect", "battle.net", "gog galaxy")
private fun autoGame(app: RefApp): Boolean = app.store in gameStores && app.name.trim().lowercase() !in launcherNames

@Composable
fun ReferenceLibrary() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val favPrefs = remember { ctx.getSharedPreferences("nexus_favorites", Context.MODE_PRIVATE) }
    val customPrefs = remember { ctx.getSharedPreferences("nexus_library_custom", Context.MODE_PRIVATE) }
    var apps by remember { mutableStateOf<List<RefApp>>(emptyList()) }
    var search by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Todos") }
    var favorites by remember { mutableStateOf(favPrefs.getStringSet("apps", emptySet())?.toSet().orEmpty()) }
    var games by remember { mutableStateOf(customPrefs.getStringSet("games", emptySet())?.toSet().orEmpty()) }
    var radial by remember { mutableStateOf(customPrefs.getStringSet("radial", emptySet())?.toSet().orEmpty()) }
    var wheel by remember { mutableStateOf(false) }
    var manager by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    LaunchedEffect(Api.host, Api.connected, refresh) {
        if (Api.host.isBlank()) return@LaunchedEffect
        loading = true
        error = ""
        val raw = withContext(Dispatchers.IO) { RemoteClient.get("/apps") }
        if (raw != null) {
            try {
                val array = JSONArray(String(raw))
                apps = (0 until array.length()).map { i ->
                    val v = array.get(i)
                    if (v is org.json.JSONObject) RefApp(v.optString("name"), v.optString("store", "Outros"))
                    else RefApp(v.toString(), "Outros")
                }.filter { it.name.isNotBlank() }.distinctBy { it.name.lowercase() }
                if (apps.isEmpty()) error = "O PC respondeu, mas não enviou aplicativos."
            } catch (_: Exception) {
                error = "A biblioteca recebida do PC está inválida."
            }
        } else error = "O PC não respondeu à biblioteca."
        loading = false
    }

    LaunchedEffect(apps) {
        if (apps.isNotEmpty() && !customPrefs.getBoolean("games_initialized", false)) {
            games = apps.filter(::autoGame).map { it.name }.toSet()
            customPrefs.edit().putStringSet("games", games).putBoolean("games_initialized", true).apply()
        }
        if (apps.isNotEmpty() && !customPrefs.getBoolean("radial_initialized", false)) {
            val seed = games.ifEmpty { apps.filter(::autoGame).map { it.name }.toSet() }
            radial = apps.filter { it.name in seed }.take(12).map { it.name }.toSet()
            if (radial.isEmpty()) radial = apps.take(8).map { it.name }.toSet()
            customPrefs.edit().putStringSet("radial", radial).putBoolean("radial_initialized", true).apply()
        }
    }

    val order = loadStoreOrder(ctx)
    val base = apps.filter { isStoreEnabled(ctx, it.store) }.sortedWith(compareBy<RefApp> {
        val i = order.indexOf(it.store); if (i < 0) 999 else i
    }.thenBy { it.name.lowercase() })
    val shown = base.filter {
        (search.isBlank() || it.name.contains(search, true)) && when (category) {
            "Jogos" -> it.name in games
            "Favoritos" -> it.name in favorites
            else -> true
        }
    }

    if (wheel && base.isNotEmpty()) {
        RadialLauncherDialog(
            allApps = base,
            gameNames = games,
            favorites = favorites,
            radialNames = radial,
            onDismiss = { wheel = false },
            onCustomize = { wheel = false; manager = true },
            onLaunch = { app ->
                scope.launch(Dispatchers.IO) { Api.post("/launch", app.name) }
                wheel = false
            }
        )
    }

    if (manager && base.isNotEmpty()) {
        LibraryManagerDialog(
            apps = base,
            gameNames = games,
            radialNames = radial,
            onDismiss = { manager = false },
            onGameChanged = { name, enabled ->
                games = if (enabled) games + name else games - name
                customPrefs.edit().putStringSet("games", games).putBoolean("games_initialized", true).apply()
            },
            onRadialChanged = { name, enabled ->
                radial = if (enabled) radial + name else radial - name
                customPrefs.edit().putStringSet("radial", radial).putBoolean("radial_initialized", true).apply()
            },
            useGamesOnWheel = {
                radial = games
                customPrefs.edit().putStringSet("radial", radial).putBoolean("radial_initialized", true).apply()
            }
        )
    }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Apps e Jogos", color = NexusUi.Text, fontSize = 25.sp, fontWeight = FontWeight.Black)
                Text("Biblioteca do PC + roda rápida personalizável", color = NexusUi.Muted, fontSize = 11.sp)
            }
            IconButton(onClick = { refresh++ }, modifier = Modifier.size(42.dp).background(NexusUi.PanelRaised, RoundedCornerShape(13.dp))) {
                Icon(Icons.Default.Refresh, "Atualizar", tint = NexusUi.Accent)
            }
        }
        Spacer(Modifier.height(10.dp))

        Surface(
            color = NexusUi.Panel,
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, NexusUi.Accent.copy(alpha = .32f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(50.dp).background(NexusUi.Accent.copy(alpha = .13f), CircleShape).clickable(enabled = base.isNotEmpty()) { wheel = true },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.RadioButtonChecked, null, tint = NexusUi.Accent, modifier = Modifier.size(29.dp)) }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f).clickable(enabled = base.isNotEmpty()) { wheel = true }) {
                    Text("RODA DE APPS / JOGOS", color = NexusUi.Text, fontWeight = FontWeight.Black, fontSize = 14.sp)
                    Text(
                        if (base.isEmpty()) "Conecte ao PC para ativar" else "Segure no centro, arraste para o app e solte",
                        color = NexusUi.Muted,
                        fontSize = 9.sp
                    )
                    Text("${radial.count { it in base.map { a -> a.name }.toSet() }} selecionados • ${games.size} marcados como jogo", color = NexusUi.Accent, fontSize = 8.sp)
                }
                IconButton(onClick = { if (base.isNotEmpty()) manager = true }) {
                    Icon(Icons.Default.Tune, "Personalizar roda", tint = NexusUi.Accent)
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            placeholder = { Text("Pesquisar aplicativos…") },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = NexusUi.Accent) },
            shape = RoundedCornerShape(17.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = NexusUi.Panel, unfocusedContainerColor = NexusUi.Panel,
                focusedBorderColor = NexusUi.Accent.copy(alpha = .55f), unfocusedBorderColor = NexusUi.Border
            )
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf("Todos", "Jogos", "Favoritos").forEach { name ->
                FilterChip(
                    selected = category == name, onClick = { category = name }, label = { Text(name) },
                    leadingIcon = when (name) {
                        "Jogos" -> {{ Icon(Icons.Default.SportsEsports, null, Modifier.size(15.dp)) }}
                        "Favoritos" -> {{ Icon(Icons.Default.Star, null, Modifier.size(15.dp)) }}
                        else -> null
                    }
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        when {
            loading && apps.isEmpty() -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = NexusUi.Accent) }
            Api.host.isBlank() -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { Text("Conecte ao PC para carregar", color = NexusUi.Muted) }
            shown.isEmpty() -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { Text(error.ifBlank { "Nenhum item nessa categoria" }, color = NexusUi.Muted) }
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(3), modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                gridItems(shown, key = { "${it.store}:${it.name}" }) { app ->
                    RefAppCard(
                        app = app,
                        favorite = app.name in favorites,
                        game = app.name in games,
                        onFavorite = {
                            favorites = if (app.name in favorites) favorites - app.name else favorites + app.name
                            favPrefs.edit().putStringSet("apps", favorites).apply()
                        },
                        onClick = { scope.launch(Dispatchers.IO) { Api.post("/launch", app.name) } }
                    )
                }
            }
        }
    }
}

@Composable
private fun RefAppCard(app: RefApp, favorite: Boolean, game: Boolean, onFavorite: () -> Unit, onClick: () -> Unit) {
    Surface(
        color = NexusUi.Panel, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, NexusUi.Border),
        modifier = Modifier.fillMaxWidth().aspectRatio(.88f).clickable(onClick = onClick)
    ) {
        Box(Modifier.fillMaxSize().padding(8.dp)) {
            IconButton(onClick = onFavorite, modifier = Modifier.align(Alignment.TopEnd).size(27.dp)) {
                Icon(if (favorite) Icons.Default.Star else Icons.Default.StarBorder, null, tint = if (favorite) Color(0xFFFFD45C) else NexusUi.Muted, modifier = Modifier.size(16.dp))
            }
            if (game) {
                Icon(Icons.Default.SportsEsports, null, tint = NexusUi.Accent, modifier = Modifier.align(Alignment.TopStart).size(15.dp))
            }
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                RefAppIcon(app, 54)
                Spacer(Modifier.height(8.dp))
                Text(app.name, color = NexusUi.Text, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            }
            Text(if (app.store == "Alll") "Desktop / Alll" else app.store, color = NexusUi.Muted, fontSize = 7.sp, modifier = Modifier.align(Alignment.BottomCenter), maxLines = 1)
        }
    }
}

@Composable
private fun RefAppIcon(app: RefApp, size: Int) {
    val shape = RoundedCornerShape((size * .25f).dp)
    Box(Modifier.size(size.dp).background(NexusUi.PanelRaised, shape).clip(shape), contentAlignment = Alignment.Center) {
        Text(app.name.take(1).uppercase(), color = NexusUi.Accent, fontWeight = FontWeight.Black)
        if (Api.host.isNotBlank()) {
            AsyncImage(
                model = "http://${Api.host}:8765/app-icon/${Uri.encode(app.name)}?v=3",
                contentDescription = app.name,
                modifier = Modifier.fillMaxSize().padding(3.dp),
                contentScale = ContentScale.Fit
            )
        }
    }
}

@Composable
private fun LibraryManagerDialog(
    apps: List<RefApp>,
    gameNames: Set<String>,
    radialNames: Set<String>,
    onDismiss: () -> Unit,
    onGameChanged: (String, Boolean) -> Unit,
    onRadialChanged: (String, Boolean) -> Unit,
    useGamesOnWheel: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val shown = apps.filter { query.isBlank() || it.name.contains(query, true) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            color = NexusUi.Background,
            modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(10.dp),
            shape = RoundedCornerShape(26.dp),
            border = BorderStroke(1.dp, NexusUi.Border)
        ) {
            Column(Modifier.fillMaxSize().padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null, tint = NexusUi.Text) }
                    Column(Modifier.weight(1f)) {
                        Text("Personalizar Biblioteca", color = NexusUi.Text, fontWeight = FontWeight.Black, fontSize = 20.sp)
                        Text("Marque exatamente o que é jogo e o que entra na roda", color = NexusUi.Muted, fontSize = 10.sp)
                    }
                }
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    placeholder = { Text("Buscar app ou jogo") }, leadingIcon = { Icon(Icons.Default.Search, null) }
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = useGamesOnWheel, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.SportsEsports, null, Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text("USAR SÓ OS JOGOS NA RODA")
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
                    Text("APLICATIVO", color = NexusUi.Muted, fontSize = 8.sp, modifier = Modifier.weight(1f))
                    Text("JOGO", color = NexusUi.Muted, fontSize = 8.sp, modifier = Modifier.width(58.dp), textAlign = TextAlign.Center)
                    Text("RODA", color = NexusUi.Muted, fontSize = 8.sp, modifier = Modifier.width(58.dp), textAlign = TextAlign.Center)
                }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(shown, key = { "manage:${it.store}:${it.name}" }) { app ->
                        Surface(color = NexusUi.Panel, shape = RoundedCornerShape(15.dp), border = BorderStroke(1.dp, NexusUi.Border)) {
                            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                RefAppIcon(app, 38)
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(app.name, color = NexusUi.Text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(app.store, color = NexusUi.Muted, fontSize = 8.sp)
                                }
                                Checkbox(checked = app.name in gameNames, onCheckedChange = { onGameChanged(app.name, it) }, modifier = Modifier.width(58.dp))
                                Checkbox(checked = app.name in radialNames, onCheckedChange = { onRadialChanged(app.name, it) }, modifier = Modifier.width(58.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RadialLauncherDialog(
    allApps: List<RefApp>,
    gameNames: Set<String>,
    favorites: Set<String>,
    radialNames: Set<String>,
    onDismiss: () -> Unit,
    onCustomize: () -> Unit,
    onLaunch: (RefApp) -> Unit
) {
    if (allApps.isEmpty()) return
    val haptic = LocalHapticFeedback.current
    var category by remember { mutableStateOf("Minha roda") }
    var page by remember { mutableIntStateOf(0) }
    var selected by remember { mutableIntStateOf(-1) }
    var dragging by remember { mutableStateOf(false) }

    val customPool = allApps.filter { it.name in radialNames }
    val pool = when (category) {
        "Jogos" -> allApps.filter { it.name in gameNames }
        "Favoritos" -> allApps.filter { it.name in favorites }
        else -> customPool
    }.ifEmpty { allApps.take(8) }

    val pageCount = ceil(pool.size / 8.0).toInt().coerceAtLeast(1)
    if (page > pageCount - 1) page = pageCount - 1
    val slots = pool.drop(page * 8).take(8)
    val chosen = selected.takeIf { it in slots.indices }?.let { slots[it] }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .97f)).statusBarsPadding().navigationBarsPadding()) {
            Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Fechar", tint = NexusUi.Text) }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Início Rápido", color = NexusUi.Text, fontWeight = FontWeight.Black, fontSize = 21.sp)
                        Text("SEGURE NO CENTRO • ARRASTE • SOLTE", color = NexusUi.Accent, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = onCustomize) { Icon(Icons.Default.Tune, "Personalizar", tint = NexusUi.Accent) }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Minha roda", "Jogos", "Favoritos").forEach { name ->
                        FilterChip(
                            selected = category == name,
                            onClick = { category = name; page = 0; selected = -1 },
                            enabled = when (name) {
                                "Jogos" -> gameNames.isNotEmpty()
                                "Favoritos" -> favorites.isNotEmpty()
                                else -> true
                            },
                            label = { Text(name, fontSize = 9.sp) }
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))
                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    val diameter = if (maxWidth < maxHeight) maxWidth else maxHeight
                    val ringSize = diameter * .94f
                    val radius = diameter * .36f
                    val itemSize = 66.dp

                    Box(Modifier.size(ringSize), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize()) {
                            val sweep = if (slots.isEmpty()) 360f else 360f / slots.size
                            val inset = size.minDimension * .045f
                            slots.forEachIndexed { index, _ ->
                                drawArc(
                                    color = if (index == selected) NexusUi.Accent.copy(alpha = .30f) else NexusUi.Panel,
                                    startAngle = -90f + index * sweep - sweep / 2f + 1.2f,
                                    sweepAngle = sweep - 2.4f,
                                    useCenter = true,
                                    topLeft = Offset(inset, inset),
                                    size = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2)
                                )
                            }
                            drawCircle(Color.Black, radius = size.minDimension * .245f)
                            drawCircle(
                                if (dragging) NexusUi.Accent else NexusUi.Border,
                                radius = size.minDimension * .245f,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = if (dragging) 5f else 2f)
                            )
                        }

                        slots.forEachIndexed { index, app ->
                            val angle = -PI / 2.0 + 2.0 * PI * index / slots.size
                            val x = ringSize / 2 + radius * cos(angle).toFloat() - itemSize / 2
                            val y = ringSize / 2 + radius * sin(angle).toFloat() - itemSize / 2
                            Column(Modifier.offset(x, y).width(itemSize), horizontalAlignment = Alignment.CenterHorizontally) {
                                Surface(
                                    color = if (selected == index) NexusUi.PanelRaised else NexusUi.Panel,
                                    shape = CircleShape,
                                    border = BorderStroke(if (selected == index) 2.dp else 1.dp, if (selected == index) NexusUi.Accent else NexusUi.Border),
                                    modifier = Modifier.size(itemSize).clickable { onLaunch(app) }
                                ) { Box(contentAlignment = Alignment.Center) { RefAppIcon(app, 50) } }
                                Spacer(Modifier.height(3.dp))
                                Text(app.name, color = if (selected == index) NexusUi.Text else NexusUi.Muted, fontSize = 7.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                            }
                        }

                        Surface(
                            color = if (dragging) NexusUi.PanelRaised else Color.Black,
                            shape = CircleShape,
                            border = BorderStroke(2.dp, if (dragging) NexusUi.Accent else NexusUi.Border),
                            modifier = Modifier
                                .size(diameter * .31f)
                                .pointerInput(slots, category, page) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            dragging = true
                                            selected = -1
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            if (slots.isEmpty()) return@detectDragGesturesAfterLongPress
                                            val center = Offset(size.width / 2f, size.height / 2f)
                                            val dx = change.position.x - center.x
                                            val dy = change.position.y - center.y
                                            val distance = hypot(dx.toDouble(), dy.toDouble()).toFloat()
                                            val next = if (distance < size.minDimension * .55f) -1 else {
                                                var a = atan2(dy.toDouble(), dx.toDouble()) + PI / 2.0
                                                while (a < 0) a += 2.0 * PI
                                                val step = 2.0 * PI / slots.size
                                                floor((a + step / 2.0) / step).toInt() % slots.size
                                            }
                                            if (next != selected) {
                                                selected = next
                                                if (next >= 0) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            }
                                        },
                                        onDragEnd = {
                                            val target = selected.takeIf { it in slots.indices }?.let { slots[it] }
                                            dragging = false
                                            selected = -1
                                            if (target != null) onLaunch(target)
                                        },
                                        onDragCancel = { dragging = false; selected = -1 }
                                    )
                                }
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.padding(8.dp)) {
                                if (chosen != null) {
                                    RefAppIcon(chosen, 52)
                                    Spacer(Modifier.height(5.dp))
                                    Text(chosen.name, color = NexusUi.Text, fontWeight = FontWeight.Bold, fontSize = 9.sp, maxLines = 2, textAlign = TextAlign.Center)
                                    Text("SOLTE PARA ABRIR", color = NexusUi.Accent, fontSize = 7.sp, fontWeight = FontWeight.Black)
                                } else {
                                    Icon(Icons.Default.TouchApp, null, tint = NexusUi.Accent, modifier = Modifier.size(31.dp))
                                    Spacer(Modifier.height(5.dp))
                                    Text(if (dragging) "ARRASTE" else "SEGURE", color = NexusUi.Text, fontWeight = FontWeight.Black, fontSize = 12.sp)
                                    Text("NO CENTRO", color = NexusUi.Muted, fontSize = 7.sp)
                                }
                            }
                        }
                    }
                }

                if (pageCount > 1) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        IconButton(onClick = { page = (page - 1 + pageCount) % pageCount; selected = -1 }) { Icon(Icons.Default.ChevronLeft, null, tint = NexusUi.Text) }
                        Text("${page + 1} / $pageCount", color = NexusUi.Muted, fontSize = 9.sp)
                        IconButton(onClick = { page = (page + 1) % pageCount; selected = -1 }) { Icon(Icons.Default.ChevronRight, null, tint = NexusUi.Text) }
                    }
                }
            }
        }
    }
}
