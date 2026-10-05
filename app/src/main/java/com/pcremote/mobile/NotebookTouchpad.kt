package com.pcremote.mobile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.awaitPointerEvent
import androidx.compose.ui.input.pointer.awaitPointerEventScope
import androidx.compose.ui.input.pointer.consume
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot

@Composable
fun NotebookTouchpad(openKeyboard: () -> Unit) {
    val scope = rememberCoroutineScope()
    var multiTouch by remember { mutableStateOf(false) }
    var lastDistance by remember { mutableFloatStateOf(0f) }

    fun post(path: String, body: String) = scope.launch(Dispatchers.IO) { Api.post(path, body) }

    Column(Modifier.fillMaxSize()) {
        Title("Touchpad", "Gestos de notebook: 1 dedo, 2 dedos e pinça")
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(
                    Brush.radialGradient(listOf(NexusUi.PanelRaised, NexusUi.Panel, NexusUi.BackgroundSoft)),
                    RoundedCornerShape(28.dp)
                )
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { post("/click", "left") },
                        onDoubleTap = {
                            scope.launch(Dispatchers.IO) {
                                Api.post("/click", "left")
                                delay(65)
                                Api.post("/click", "left")
                            }
                        },
                        onLongPress = { post("/click", "right") }
                    )
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, d ->
                        if (multiTouch) return@detectDragGestures
                        change.consume()
                        val x = (d.x * 1.20f).toInt()
                        val y = (d.y * 1.20f).toInt()
                        if (x != 0 || y != 0) post("/mouse", "$x,$y")
                    }
                }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.size >= 2) {
                                multiTouch = true
                                val a = pressed[0]
                                val b = pressed[1]
                                val da = a.positionChange()
                                val db = b.positionChange()
                                val avgDy = (da.y + db.y) / 2f
                                if (abs(avgDy) > .7f) {
                                    val wheel = (-avgDy * 7f).toInt().coerceIn(-720, 720)
                                    if (wheel != 0) post("/scroll", wheel.toString())
                                }

                                val distance = hypot(
                                    (a.position.x - b.position.x).toDouble(),
                                    (a.position.y - b.position.y).toDouble()
                                ).toFloat()
                                if (lastDistance > 0f) {
                                    val delta = distance - lastDistance
                                    if (abs(delta) > 2.2f && abs(avgDy) < abs(delta) * .9f) {
                                        val zoom = (delta * 9f).toInt().coerceIn(-600, 600)
                                        if (zoom != 0) post("/zoom", zoom.toString())
                                    }
                                }
                                lastDistance = distance
                                pressed.forEach { it.consume() }
                            } else {
                                multiTouch = false
                                lastDistance = 0f
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(66.dp).background(NexusUi.Accent.copy(alpha = .09f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Mouse, null, tint = NexusUi.Accent, modifier = Modifier.size(31.dp))
                }
                Spacer(Modifier.height(10.dp))
                Text("ÁREA DO TOUCHPAD", color = NexusUi.Text, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("1 toque: clique • 2 toques: duplo • segurar: direito", color = NexusUi.Muted, fontSize = 8.sp)
                Spacer(Modifier.height(3.dp))
                Text("2 dedos: rolar • pinça: zoom", color = NexusUi.Accent, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MouseAction("CLIQUE ESQ.", Modifier.weight(1f), scope, "left")
            MouseAction("CLIQUE DIR.", Modifier.weight(1f), scope, "right")
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ScrollAction("ROLAR ↑", Modifier.weight(1f), scope, 480)
            ScrollAction("ROLAR ↓", Modifier.weight(1f), scope, -480)
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = openKeyboard,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = NexusUi.AccentStrong)
        ) {
            Icon(Icons.Default.Keyboard, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("ABRIR TECLADO", fontWeight = FontWeight.Bold)
        }
    }
}
