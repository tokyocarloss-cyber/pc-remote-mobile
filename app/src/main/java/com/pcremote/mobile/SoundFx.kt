package com.pcremote.mobile

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class SoundFx(private val context: Context) {
    private val prefs = context.getSharedPreferences("nexus", Context.MODE_PRIVATE)
    private val generator = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 28) }.getOrNull()
    var enabled by mutableStateOf(prefs.getBoolean("sounds", true))
        private set

    fun setEnabled(value: Boolean) {
        enabled = value
        prefs.edit().putBoolean("sounds", value).apply()
        if (value) success()
    }

    fun click() {
        if (enabled) runCatching { generator?.startTone(ToneGenerator.TONE_PROP_BEEP, 35) }
    }

    fun success() {
        if (enabled) runCatching { generator?.startTone(ToneGenerator.TONE_PROP_ACK, 70) }
    }

    fun error() {
        if (enabled) runCatching { generator?.startTone(ToneGenerator.TONE_PROP_NACK, 100) }
    }

    fun release() { generator?.release() }
}
