package com.jarvis.android.voice

import android.media.AudioManager
import android.media.ToneGenerator

object JarvisSoundManager {
    private var toneGen: ToneGenerator? = null

    private fun getOrInitTone(): ToneGenerator? {
        if (toneGen == null) {
            try {
                toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70)
            } catch (_: Throwable) {
                toneGen = null
            }
        }
        return toneGen
    }

    fun play(event: SoundEvent) {
        try {
            val gen = getOrInitTone() ?: return
            when (event) {
                SoundEvent.ACTIVATE -> gen.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
                SoundEvent.LISTENING_START -> gen.startTone(ToneGenerator.TONE_CDMA_PIP, 80)
                SoundEvent.THINKING -> gen.startTone(ToneGenerator.TONE_DTMF_0, 60)
                SoundEvent.SPEAKING_START -> gen.startTone(ToneGenerator.TONE_PROP_ACK, 100)
                SoundEvent.SUCCESS -> gen.startTone(ToneGenerator.TONE_PROP_PROMPT, 150)
                SoundEvent.ERROR -> gen.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 200)
                SoundEvent.CLOSE -> gen.startTone(ToneGenerator.TONE_PROP_NACK, 80)
            }
        } catch (_: Throwable) {}
    }

    fun release() {
        try {
            toneGen?.release()
        } catch (_: Throwable) {}
        toneGen = null
    }
}

enum class SoundEvent {
    ACTIVATE,
    LISTENING_START,
    THINKING,
    SPEAKING_START,
    SUCCESS,
    ERROR,
    CLOSE
}
