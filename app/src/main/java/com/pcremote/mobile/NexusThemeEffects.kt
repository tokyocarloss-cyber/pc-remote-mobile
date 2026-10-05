package com.pcremote.mobile

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class NexusEffect { TAP, TICK, OPEN, SELECT, ERROR }

val NexusUi.cardRadius: Int
    get() = when (currentTheme) {
        "Attack on Titan" -> 9
        "Frutiger Aero" -> 27
        "Dark Souls" -> 7
        "Anime Prism" -> 22
        "Naruto" -> 18
        "Cyber Samurai" -> 10
        "Sakura Night" -> 25
        "Retro CRT" -> 4
        "Arctic Glass" -> 24
        else -> 18
    }

object NexusEffects {
    fun play(context: Context, effect: NexusEffect) {
        val enabled = context.getSharedPreferences("nexus_settings", Context.MODE_PRIVATE)
            .getBoolean("theme_sounds", true)
        if (!enabled) return
        val tone = when (NexusUi.currentTheme) {
            "Attack on Titan" -> when (effect) {
                NexusEffect.SELECT -> ToneGenerator.TONE_PROP_ACK
                NexusEffect.ERROR -> ToneGenerator.TONE_PROP_NACK
                else -> ToneGenerator.TONE_DTMF_D
            }
            "Frutiger Aero" -> when (effect) {
                NexusEffect.SELECT -> ToneGenerator.TONE_PROP_ACK
                else -> ToneGenerator.TONE_DTMF_6
            }
            "Dark Souls" -> when (effect) {
                NexusEffect.SELECT -> ToneGenerator.TONE_DTMF_0
                NexusEffect.ERROR -> ToneGenerator.TONE_PROP_NACK
                else -> ToneGenerator.TONE_DTMF_8
            }
            "Naruto" -> when (effect) {
                NexusEffect.SELECT -> ToneGenerator.TONE_DTMF_A
                else -> ToneGenerator.TONE_DTMF_3
            }
            "Retro CRT" -> when (effect) {
                NexusEffect.SELECT -> ToneGenerator.TONE_DTMF_B
                else -> ToneGenerator.TONE_DTMF_1
            }
            "Cyber Samurai" -> when (effect) {
                NexusEffect.SELECT -> ToneGenerator.TONE_DTMF_C
                else -> ToneGenerator.TONE_DTMF_9
            }
            else -> when (effect) {
                NexusEffect.SELECT -> ToneGenerator.TONE_PROP_ACK
                NexusEffect.ERROR -> ToneGenerator.TONE_PROP_NACK
                NexusEffect.OPEN -> ToneGenerator.TONE_DTMF_5
                NexusEffect.TICK -> ToneGenerator.TONE_DTMF_2
                NexusEffect.TAP -> ToneGenerator.TONE_DTMF_4
            }
        }
        val duration = when (effect) {
            NexusEffect.TICK, NexusEffect.TAP -> 35
            NexusEffect.OPEN -> 55
            NexusEffect.SELECT -> 80
            NexusEffect.ERROR -> 110
        }
        thread(isDaemon = true, name = "NexusUiSound") {
            try {
                val tg = ToneGenerator(AudioManager.STREAM_SYSTEM, 18)
                tg.startTone(tone, duration)
                Thread.sleep((duration + 25).toLong())
                tg.release()
            } catch (_: Exception) {}
        }
    }
}

@Composable
fun NexusThemeBackdrop(modifier: Modifier = Modifier, subtle: Boolean = false) {
    val transition = rememberInfiniteTransition(label = "themeBackdrop")
    val duration = when (NexusUi.currentTheme) {
        "Attack on Titan" -> 11000
        "Frutiger Aero" -> 8500
        "Dark Souls" -> 14000
        "Anime Prism" -> 7000
        "Naruto" -> 8000
        "Cyber Samurai" -> 6500
        "Sakura Night" -> 12000
        "Retro CRT" -> 3500
        "Arctic Glass" -> 15000
        else -> 9000
    }
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(duration, easing = LinearEasing), RepeatMode.Restart),
        label = "themePhase"
    )
    val alpha = if (subtle) .12f else .20f

    Canvas(modifier) {
        when (NexusUi.currentTheme) {
            "Attack on Titan" -> {
                for (i in 0..7) {
                    val y = size.height * (i / 7f)
                    drawLine(NexusUi.AccentStrong.copy(alpha = alpha * .55f), Offset(0f, y), Offset(size.width, y - size.width * .16f), strokeWidth = 2f)
                }
                val sweepX = size.width * phase
                drawLine(NexusUi.Accent.copy(alpha = alpha), Offset(sweepX, 0f), Offset(sweepX - size.width * .35f, size.height), strokeWidth = 6f)
            }
            "Frutiger Aero" -> {
                for (i in 0..10) {
                    val x = size.width * ((i * .137f + phase * .16f) % 1f)
                    val y = size.height * ((i * .211f - phase * (.18f + i * .006f) + 1f) % 1f)
                    val r = 12f + (i % 4) * 10f
                    drawCircle(NexusUi.Accent.copy(alpha = alpha), r, Offset(x, y), style = Stroke(width = 2f))
                    drawCircle(NexusUi.Success.copy(alpha = alpha * .35f), r * .55f, Offset(x + r * .25f, y - r * .25f))
                }
            }
            "Dark Souls" -> {
                for (i in 0..18) {
                    val x = size.width * ((i * .193f + sin((phase + i) * PI).toFloat() * .04f + 1f) % 1f)
                    val y = size.height * (((1f - (phase * (.45f + (i % 5) * .06f) + i * .071f)) % 1f + 1f) % 1f)
                    drawCircle(NexusUi.Accent.copy(alpha = alpha * (0.35f + (i % 3) * .15f)), 2f + (i % 4), Offset(x, y))
                }
            }
            "Anime Prism" -> {
                for (i in -2..8) {
                    val x = size.width * (i / 6f + phase * .18f)
                    drawLine(NexusUi.Accent.copy(alpha = alpha * .7f), Offset(x, 0f), Offset(x - size.width * .6f, size.height), strokeWidth = 5f)
                    drawLine(NexusUi.Violet.copy(alpha = alpha * .45f), Offset(x + 20f, 0f), Offset(x - size.width * .6f + 20f, size.height), strokeWidth = 2f)
                }
            }
            "Naruto" -> {
                val c = Offset(size.width * .5f, size.height * .46f)
                for (i in 1..4) {
                    val r = size.minDimension * (.10f * i + phase * .015f)
                    drawCircle(NexusUi.Accent.copy(alpha = alpha * (1f - i * .13f)), r, c, style = Stroke(width = if (i == 1) 6f else 2f))
                }
                val angle = phase * PI * 2.0
                val end = Offset(c.x + cos(angle).toFloat() * size.minDimension * .35f, c.y + sin(angle).toFloat() * size.minDimension * .35f)
                drawLine(NexusUi.Violet.copy(alpha = alpha), c, end, strokeWidth = 4f)
            }
            "Cyber Samurai" -> {
                for (i in 0..6) {
                    val shift = size.width * ((phase + i * .19f) % 1f)
                    drawLine(NexusUi.Accent.copy(alpha = alpha), Offset(shift, 0f), Offset(shift - size.width * .45f, size.height), strokeWidth = if (i % 2 == 0) 6f else 2f)
                    drawLine(NexusUi.Violet.copy(alpha = alpha * .55f), Offset(size.width - shift, 0f), Offset(size.width - shift + size.width * .4f, size.height), strokeWidth = 2f)
                }
            }
            "Sakura Night" -> {
                for (i in 0..14) {
                    val x = size.width * ((i * .101f + phase * (.10f + (i % 4) * .025f)) % 1f)
                    val y = size.height * ((i * .173f + phase * (.32f + (i % 3) * .05f)) % 1f)
                    drawCircle(NexusUi.Accent.copy(alpha = alpha * .75f), 3f + (i % 4), Offset(x, y))
                    drawLine(NexusUi.Accent.copy(alpha = alpha * .45f), Offset(x - 5f, y), Offset(x + 6f, y + 5f), strokeWidth = 2f)
                }
            }
            "Retro CRT" -> {
                var y = 0f
                while (y < size.height) {
                    drawLine(NexusUi.Accent.copy(alpha = alpha * .35f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                    y += 7f
                }
                val scan = size.height * phase
                drawRect(NexusUi.Accent.copy(alpha = alpha * .18f), Offset(0f, scan), androidx.compose.ui.geometry.Size(size.width, 18f))
            }
            "Arctic Glass" -> {
                for (i in 0..12) {
                    val x = size.width * ((i * .173f + sin((phase + i) * PI * 2).toFloat() * .03f + 1f) % 1f)
                    val y = size.height * ((i * .119f + phase * (.09f + (i % 4) * .018f)) % 1f)
                    drawCircle(NexusUi.Accent.copy(alpha = alpha * .6f), 2f + (i % 3) * 2f, Offset(x, y))
                }
                drawCircle(NexusUi.Violet.copy(alpha = alpha * .18f), size.minDimension * .42f, Offset(size.width * .82f, size.height * .18f))
            }
            else -> {
                val spacing = 48f
                val shift = phase * spacing
                var x = -spacing + shift
                while (x < size.width + spacing) {
                    drawLine(NexusUi.Accent.copy(alpha = alpha * .32f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                    x += spacing
                }
                var y = -spacing + shift
                while (y < size.height + spacing) {
                    drawLine(NexusUi.Violet.copy(alpha = alpha * .18f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                    y += spacing
                }
            }
        }
    }
}
