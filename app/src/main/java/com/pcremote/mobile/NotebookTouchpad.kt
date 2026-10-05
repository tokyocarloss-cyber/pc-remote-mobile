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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun NotebookTouchpad(openKeyboard: () -> Unit) {
    val scope = rememberCoroutineScope()
    fun post(path: String, body: String) = scope.launch(Dispatchers.IO) { Api.post(path, body) }

    Column(Modifier.fillMaxSize()) {
        Title("Touchpad", "Toque, toque duas vezes ou deslize como em um notebook")
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(
                    Brush.radialGradient(listOf(androidx.compose.ui.graphics.Color(0xFF152331), NexusUi.Panel, NexusUi.BackgroundSoft)),
                    RoundedCornerShape(28.dp)
                )
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { post("/click", "left") },
                        onDoubleTap = {
                            scope.launch(Dispatchers.IO) {
                                Api.post("/click", "left")
                                kotlinx.coroutines.delay(70)
                                Api.post("/click", "left")
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, d ->
                        change.consume()
                        val x = (d.x * 1.20f).toInt()
                        val y = (d.y * 1.20f).toInt()
                        if (x != 0 || y != 0) post("/mouse", "$x,$y")
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(64.dp).background(NexusUi.Accent.copy(alpha = .08f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Mouse, null, tint = NexusUi.Accent, modifier = Modifier.size(30.dp))
                }
                Spacer(Modifier.height(10.dp))
                Text("ÁREA DO TOUCHPAD", color = NexusUi.Text, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("1 toque = clique • 2 toques = duplo clique", color = NexusUi.Muted, fontSize = 9.sp)
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
