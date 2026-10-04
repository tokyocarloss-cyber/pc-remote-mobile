package com.pcremote.mobile

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private data class PadKey(val id: String, val label: String, val command: String)

@Composable
fun AdvancedGamepad() {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("pad_layout", Context.MODE_PRIVATE) }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val orientationKey = if (landscape) "land" else "port"
    val scope = rememberCoroutineScope()

    val keys = remember {
        listOf(
            PadKey("lt", "LT", "LT"), PadKey("rt", "RT", "RT"),
            PadKey("lb", "LB", "LB"), PadKey("rb", "RB", "RB"),
            PadKey("up", "↑", "UP"), PadKey("left", "←", "LEFT"),
            PadKey("right", "→", "RIGHT"), PadKey("down", "↓", "DOWN"),
            PadKey("y", "Y", "Y"), PadKey("b", "B", "B"),
            PadKey("x", "X", "X"), PadKey("a", "A", "A"),
            PadKey("l3", "L3", "L3"), PadKey("select", "SELECT", "SELECT"),
            PadKey("start", "START", "START"), PadKey("r3", "R3", "R3")
        )
    }

    fun defaults(isLandscape: Boolean): Map<String, Offset> = if (isLandscape) {
        mapOf(
            "lt" to Offset(.03f, .02f), "rt" to Offset(.88f, .02f),
            "lb" to Offset(.03f, .16f), "rb" to Offset(.88f, .16f),
            "up" to Offset(.12f, .30f), "left" to Offset(.04f, .44f),
            "right" to Offset(.20f, .44f), "down" to Offset(.12f, .58f),
            "y" to Offset(.78f, .30f), "x" to Offset(.70f, .44f),
            "b" to Offset(.86f, .44f), "a" to Offset(.78f, .58f),
            "l3" to Offset(.31f, .79f), "select" to Offset(.43f, .80f),
            "start" to Offset(.53f, .80f), "r3" to Offset(.66f, .79f)
        )
    } else {
        mapOf(
            "lt" to Offset(.03f, .02f), "rt" to Offset(.81f, .02f),
            "lb" to Offset(.03f, .14f), "rb" to Offset(.81f, .14f),
            "up" to Offset(.16f, .28f), "left" to Offset(.03f, .39f),
            "right" to Offset(.29f, .39f), "down" to Offset(.16f, .50f),
            "y" to Offset(.71f, .28f), "x" to Offset(.58f, .39f),
            "b" to Offset(.84f, .39f), "a" to Offset(.71f, .50f),
            "l3" to Offset(.12f, .85f), "select" to Offset(.36f, .86f),
            "start" to Offset(.54f, .86f), "r3" to Offset(.78f, .85f)
        )
    }

    var profile by remember { mutableStateOf(prefs.getString("profile", "Padrão") ?: "Padrão") }
    var profileMenu by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf(false) }
    var scale by remember { mutableFloatStateOf(1f) }
    var alpha by remember { mutableFloatStateOf(.88f) }
    var positions by remember { mutableStateOf(defaults(landscape)) }
    var hidden by remember { mutableStateOf(emptySet<String>()) }

    fun key(id: String, suffix: String) = "$profile:$orientationKey:$id:$suffix"

    fun loadProfile(name: String) {
        profile = name
        prefs.edit().putString("profile", name).apply()
        val base = defaults(landscape)
        scale = prefs.getFloat("$name:$orientationKey:scale", 1f)
        alpha = prefs.getFloat("$name:$orientationKey:alpha", .88f)
        positions = keys.associate { k ->
            val d = base[k.id] ?: Offset.Zero
            k.id to Offset(
                prefs.getFloat("$name:$orientationKey:${k.id}:x", d.x),
                prefs.getFloat("$name:$orientationKey:${k.id}:y", d.y)
            )
        }
        hidden = keys.filter { !prefs.getBoolean("$name:$orientationKey:${it.id}:visible", true) }.map { it.id }.toSet()
    }

    LaunchedEffect(profile, orientationKey) { loadProfile(profile) }

    fun pad(json: String) {
        if (!edit) scope.launch(Dispatchers.IO) { Api.post("/gamepad", json) }
    }
    fun button(k: String, down: Boolean) = pad("{\"kind\":\"button\",\"button\":\"$k\",\"down\":$down}")
    fun trigger(k: String, v: Float) = pad("{\"kind\":\"trigger\",\"trigger\":\"$k\",\"value\":$v}")
    fun stick(k: String, x: Float, y: Float) = pad("{\"kind\":\"stick\",\"stick\":\"$k\",\"x\":$x,\"y\":${-y}}")

    Column(Modifier.fillMaxSize()) {
        Surface(
            color = NexusUi.Panel,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, NexusUi.Border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box {
                    TextButton(onClick = { profileMenu = true }) {
                        Text("$profile  ▾", color = NexusUi.Text, fontWeight = FontWeight.Bold)
                    }
                    DropdownMenu(
                        expanded = profileMenu,
                        onDismissRequest = { profileMenu = false },
                        containerColor = NexusUi.PanelRaised
                    ) {
                        listOf("Padrão", "Corrida", "Ação", "Personalizado").forEach { name ->
                            DropdownMenuItem(
                                text = { Text(name, color = NexusUi.Text) },
                                onClick = { loadProfile(name); profileMenu = false }
                            )
                        }
                    }
                }
                Button(
                    onClick = { edit = !edit },
                    shape = RoundedCornerShape(13.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (edit) NexusUi.Success.copy(alpha = .8f) else NexusUi.AccentStrong)
                ) {
                    Text(if (edit) "CONCLUIR" else "EDITAR", fontWeight = FontWeight.Bold)
                }
            }
        }

        if (edit) {
            Spacer(Modifier.height(7.dp))
            Surface(
                color = NexusUi.Panel,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, NexusUi.Border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                    if (landscape) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Tamanho", color = NexusUi.Muted, style = MaterialTheme.typography.labelSmall)
                            Slider(scale, { scale = it }, Modifier.weight(1f).padding(horizontal = 8.dp), valueRange = .70f..1.30f)
                            Text("Opacidade", color = NexusUi.Muted, style = MaterialTheme.typography.labelSmall)
                            Slider(alpha, { alpha = it }, Modifier.weight(1f).padding(start = 8.dp), valueRange = .40f..1f)
                        }
                    } else {
                        Text("Tamanho", color = NexusUi.Muted, style = MaterialTheme.typography.labelSmall)
                        Slider(scale, { scale = it }, valueRange = .70f..1.30f)
                        Text("Opacidade", color = NexusUi.Muted, style = MaterialTheme.typography.labelSmall)
                        Slider(alpha, { alpha = it }, valueRange = .40f..1f)
                    }
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        keys.forEach { k ->
                            FilterChip(
                                selected = k.id !in hidden,
                                onClick = {
                                    hidden = if (k.id in hidden) hidden - k.id else hidden + k.id
                                    prefs.edit().putBoolean(key(k.id, "visible"), k.id !in hidden).apply()
                                },
                                label = { Text(k.label) }
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(7.dp))
        Surface(
            color = NexusUi.BackgroundSoft,
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, NexusUi.Border),
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
            BoxWithConstraints(
                Modifier.fillMaxSize().background(
                    Brush.radialGradient(listOf(Color(0xFF111D29), NexusUi.BackgroundSoft, Color(0xFF05070A)))
                )
            ) {
                val w = maxWidth
                val h = maxHeight
                val buttonSize = if (landscape) 48 else 46
                val base = defaults(landscape)

                keys.filter { it.id !in hidden }.forEach { k ->
                    val p = positions[k.id] ?: base[k.id] ?: Offset.Zero
                    val accent = when (k.id) {
                        "a" -> Color(0xFF65D98A)
                        "b" -> Color(0xFFFF6B6B)
                        "x" -> Color(0xFF5DADE2)
                        "y" -> Color(0xFFF6D365)
                        else -> NexusUi.Accent
                    }
                    Box(
                        Modifier
                            .offset(w * p.x, h * p.y)
                            .size((buttonSize * scale).dp)
                            .background(
                                if (edit) NexusUi.Accent.copy(alpha = .12f) else NexusUi.PanelRaised.copy(alpha = alpha),
                                CircleShape
                            )
                            .pointerInput(edit, k.id, profile, orientationKey) {
                                if (edit) detectDragGestures { change, d ->
                                    change.consume()
                                    val cur = positions[k.id] ?: p
                                    val maxX = .88f
                                    val maxY = .90f
                                    val nx = (cur.x + d.x / constraints.maxWidth).coerceIn(0f, maxX)
                                    val ny = (cur.y + d.y / constraints.maxHeight).coerceIn(0f, maxY)
                                    positions = positions + (k.id to Offset(nx, ny))
                                    prefs.edit().putFloat(key(k.id, "x"), nx).putFloat(key(k.id, "y"), ny).apply()
                                }
                            }
                            .pointerInput(edit, k.command) {
                                if (!edit) detectTapGestures(onPress = {
                                    if (k.command == "LT" || k.command == "RT") trigger(k.command, 1f) else button(k.command, true)
                                    tryAwaitRelease()
                                    if (k.command == "LT" || k.command == "RT") trigger(k.command, 0f) else button(k.command, false)
                                })
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(k.label, color = if (edit) NexusUi.Accent else accent, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelMedium)
                    }
                }

                val stickBases = if (landscape) {
                    listOf("left" to Offset(.24f, .60f), "right" to Offset(.62f, .60f))
                } else {
                    listOf("left" to Offset(.11f, .61f), "right" to Offset(.61f, .61f))
                }
                val stickSize = if (landscape) 88 else 82

                stickBases.forEach { (side, basePos) ->
                    var knob by remember(side, orientationKey) { mutableStateOf(Offset.Zero) }
                    Box(
                        Modifier
                            .offset(w * basePos.x, h * basePos.y)
                            .size((stickSize * scale).dp)
                            .background(NexusUi.PanelRaised.copy(alpha = .65f), CircleShape)
                            .pointerInput(edit, side) {
                                if (!edit) detectDragGestures(
                                    onDragEnd = { knob = Offset.Zero; stick(side, 0f, 0f) },
                                    onDragCancel = { knob = Offset.Zero; stick(side, 0f, 0f) }
                                ) { change, d ->
                                    change.consume()
                                    val radius = constraints.maxWidth / 2f
                                    val next = knob + d
                                    val len = next.getDistance()
                                    knob = if (len > radius) next * (radius / len) else next
                                    stick(
                                        side,
                                        (knob.x / radius).coerceIn(-1f, 1f),
                                        (knob.y / radius).coerceIn(-1f, 1f)
                                    )
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            Modifier
                                .offset(
                                    (knob.x / ctx.resources.displayMetrics.density).dp,
                                    (knob.y / ctx.resources.displayMetrics.density).dp
                                )
                                .size((38 * scale).dp)
                                .background(NexusUi.AccentStrong.copy(alpha = alpha), CircleShape)
                        )
                    }
                }
            }
        }

        if (edit) {
            Spacer(Modifier.height(7.dp))
            OutlinedButton(
                onClick = {
                    val editor = prefs.edit()
                    keys.forEach {
                        editor.remove(key(it.id, "x"))
                        editor.remove(key(it.id, "y"))
                        editor.remove(key(it.id, "visible"))
                    }
                    editor.remove("$profile:$orientationKey:scale")
                    editor.remove("$profile:$orientationKey:alpha")
                    editor.apply()
                    scale = 1f
                    alpha = .88f
                    hidden = emptySet()
                    positions = defaults(landscape)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("RESTAURAR ESTE LAYOUT")
            }
        }
    }

    LaunchedEffect(scale, alpha, profile, orientationKey) {
        prefs.edit()
            .putFloat("$profile:$orientationKey:scale", scale)
            .putFloat("$profile:$orientationKey:alpha", alpha)
            .apply()
    }
}
