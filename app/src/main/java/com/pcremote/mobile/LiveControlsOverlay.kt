package com.pcremote.mobile

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private data class LivePadKey(val id: String, val label: String, val command: String)

@Composable
fun LiveControlsOverlay(editMode: Boolean) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("pad_layout", Context.MODE_PRIVATE) }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val orientationKey = if (landscape) "land" else "port"
    val scope = rememberCoroutineScope()

    val keys = remember {
        listOf(
            LivePadKey("lt", "LT", "LT"), LivePadKey("rt", "RT", "RT"),
            LivePadKey("lb", "LB", "LB"), LivePadKey("rb", "RB", "RB"),
            LivePadKey("up", "↑", "UP"), LivePadKey("left", "←", "LEFT"),
            LivePadKey("right", "→", "RIGHT"), LivePadKey("down", "↓", "DOWN"),
            LivePadKey("y", "Y", "Y"), LivePadKey("b", "B", "B"),
            LivePadKey("x", "X", "X"), LivePadKey("a", "A", "A"),
            LivePadKey("l3", "L3", "L3"), LivePadKey("select", "SEL", "SELECT"),
            LivePadKey("start", "START", "START"), LivePadKey("r3", "R3", "R3")
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
    var scale by remember { mutableFloatStateOf(1f) }
    var alpha by remember { mutableFloatStateOf(.78f) }
    var positions by remember { mutableStateOf(defaults(landscape)) }
    var hidden by remember { mutableStateOf(emptySet<String>()) }

    fun prefKey(id: String, suffix: String) = "$profile:$orientationKey:$id:$suffix"

    fun reload() {
        profile = prefs.getString("profile", "Padrão") ?: "Padrão"
        val base = defaults(landscape)
        scale = prefs.getFloat("$profile:$orientationKey:scale", 1f)
        alpha = prefs.getFloat("$profile:$orientationKey:alpha", .78f)
        positions = keys.associate { k ->
            val d = base[k.id] ?: Offset.Zero
            k.id to Offset(
                prefs.getFloat("$profile:$orientationKey:${k.id}:x", d.x),
                prefs.getFloat("$profile:$orientationKey:${k.id}:y", d.y)
            )
        }
        hidden = keys.filter { !prefs.getBoolean("$profile:$orientationKey:${it.id}:visible", true) }
            .map { it.id }.toSet()
    }

    LaunchedEffect(profile, orientationKey, editMode) { reload() }

    fun send(json: String) {
        if (!editMode) scope.launch(Dispatchers.IO) { Api.post("/gamepad", json) }
    }

    fun button(command: String, down: Boolean) =
        send("{\"kind\":\"button\",\"button\":\"$command\",\"down\":$down}")

    fun trigger(command: String, value: Float) =
        send("{\"kind\":\"trigger\",\"trigger\":\"$command\",\"value\":$value}")

    fun stick(side: String, x: Float, y: Float) =
        send("{\"kind\":\"stick\",\"stick\":\"$side\",\"x\":$x,\"y\":${-y}}")

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = maxWidth
        val h = maxHeight
        val base = defaults(landscape)
        val buttonSize = if (landscape) 48 else 46

        keys.filter { it.id !in hidden }.forEach { key ->
            val p = positions[key.id] ?: base[key.id] ?: Offset.Zero
            val accent = when (key.id) {
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
                        if (editMode) NexusUi.Accent.copy(alpha = .24f)
                        else NexusUi.PanelRaised.copy(alpha = alpha.coerceIn(.25f, .95f)),
                        CircleShape
                    )
                    .pointerInput(editMode, key.id, profile, orientationKey) {
                        if (editMode) {
                            detectDragGestures { change, drag ->
                                change.consume()
                                val current = positions[key.id] ?: p
                                val nx = (current.x + drag.x / constraints.maxWidth).coerceIn(0f, .90f)
                                val ny = (current.y + drag.y / constraints.maxHeight).coerceIn(0f, .92f)
                                positions = positions + (key.id to Offset(nx, ny))
                                prefs.edit()
                                    .putFloat(prefKey(key.id, "x"), nx)
                                    .putFloat(prefKey(key.id, "y"), ny)
                                    .apply()
                            }
                        }
                    }
                    .pointerInput(editMode, key.command) {
                        if (!editMode) {
                            detectTapGestures(onPress = {
                                if (key.command == "LT" || key.command == "RT") trigger(key.command, 1f)
                                else button(key.command, true)
                                tryAwaitRelease()
                                if (key.command == "LT" || key.command == "RT") trigger(key.command, 0f)
                                else button(key.command, false)
                            })
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    key.label,
                    color = if (editMode) NexusUi.Text else accent,
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }

        val stickBases = if (landscape) {
            listOf("left" to Offset(.24f, .60f), "right" to Offset(.62f, .60f))
        } else {
            listOf("left" to Offset(.11f, .61f), "right" to Offset(.61f, .61f))
        }
        val stickSize = if (landscape) 88 else 82

        if (!editMode) {
            stickBases.forEach { (side, basePos) ->
                var knob by remember(side, orientationKey) { mutableStateOf(Offset.Zero) }
                Box(
                    Modifier
                        .offset(w * basePos.x, h * basePos.y)
                        .size((stickSize * scale).dp)
                        .background(NexusUi.PanelRaised.copy(alpha = (alpha * .72f).coerceAtLeast(.25f)), CircleShape)
                        .pointerInput(side, profile) {
                            detectDragGestures(
                                onDragEnd = { knob = Offset.Zero; stick(side, 0f, 0f) },
                                onDragCancel = { knob = Offset.Zero; stick(side, 0f, 0f) }
                            ) { change, drag ->
                                change.consume()
                                val radius = constraints.maxWidth / 2f
                                val next = knob + drag
                                val length = next.getDistance()
                                knob = if (length > radius) next * (radius / length) else next
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
                            .background(NexusUi.AccentStrong.copy(alpha = alpha.coerceIn(.35f, .95f)), CircleShape)
                    )
                }
            }
        }
    }
}
