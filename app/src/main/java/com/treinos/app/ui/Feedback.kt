package com.treinos.app.ui

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator

/** O que o aparelho deve fazer quando o descanso chega a 0. */
data class RestFeedbackAction(val vibrate: Boolean, val sound: Boolean)

/**
 * Espelhos de `AudioManager.RINGER_MODE_*` para manter a regra pura
 * (testável em JVM sem Robolectric e sem classes Android).
 */
const val RINGER_MODE_SILENT = 0
const val RINGER_MODE_VIBRATE = 1
const val RINGER_MODE_NORMAL = 2

/**
 * Regra por ringer (H3-26): silencioso vibra sem som; vibrante só vibra;
 * normal toca e vibra. Beep em modo silencioso é o bug principal — por isso a
 * regra é pura e testada. Valor de ringer desconhecido é tratado como normal.
 */
fun restFeedbackForRinger(ringerMode: Int): RestFeedbackAction = when (ringerMode) {
    RINGER_MODE_SILENT -> RestFeedbackAction(vibrate = true, sound = false)
    RINGER_MODE_VIBRATE -> RestFeedbackAction(vibrate = true, sound = false)
    else -> RestFeedbackAction(vibrate = true, sound = true)
}

/**
 * Toca o fim do descanso no aparelho: vibração em waveform + beep curto,
 * respeitando o ringer. Sem estado interno além do `AudioManager` — cada fim
 * de descanso cria o que precisa e libera em seguida.
 */
class RestFeedbackPlayer(context: Context) {

    private val appContext = context.applicationContext
    private val audioManager =
        appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    /** Chamado quando o descanso chega a 0. */
    fun onRestFinished() {
        val ringer = audioManager?.ringerMode ?: RINGER_MODE_NORMAL
        val action = restFeedbackForRinger(ringer)
        if (action.vibrate) vibrate()
        if (action.sound) beep()
    }

    private fun vibrate() {
        val vibrator = appContext.getSystemService(Vibrator::class.java) ?: return
        vibrator.vibrate(VibrationEffect.createWaveform(VIBRATION_PATTERN, -1))
    }

    private fun beep() {
        try {
            val tone = ToneGenerator(AudioManager.STREAM_MUSIC, BEEP_VOLUME)
            val played = tone.startTone(ToneGenerator.TONE_PROP_BEEP, BEEP_DURATION_MS)
            if (!played) {
                tone.release()
            } else {
                Handler(Looper.getMainLooper())
                    .postDelayed({ tone.release() }, BEEP_DURATION_MS + RELEASE_DELAY_MS)
            }
        } catch (_: RuntimeException) {
            // Alguns aparelhos falham ao criar o ToneGenerator; a vibração já aconteceu.
        }
    }

    private companion object {
        /** Waveform: 0 ms de espera, 300 ms vibra, 120 ms pausa, 300 ms vibra. */
        val VIBRATION_PATTERN = longArrayOf(0L, 300L, 120L, 300L)
        const val BEEP_DURATION_MS = 200
        const val BEEP_VOLUME = 100
        const val RELEASE_DELAY_MS = 300L
    }
}
