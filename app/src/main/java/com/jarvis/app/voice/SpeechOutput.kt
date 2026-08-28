package com.jarvis.app.voice

import android.content.Context
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice

class SpeechOutput(context: Context) : TextToSpeech.OnInitListener {
    private var ready = false
    private var tts: TextToSpeech? = null

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        val engine = tts
        if (status != TextToSpeech.SUCCESS || engine == null) {
            ready = false
            return
        }
        ready = true

        // JARVIS is British, and so is the fallback. Prefer an engine voice that actually
        // speaks the accent rather than trusting setLanguage to find one.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val best = runCatching { engine.voices }.getOrNull()
                ?.filter { it.locale.language == JarvisVoice.LOCALE.language && !it.isNetworkConnectionRequired }
                ?.sortedWith(
                    compareByDescending<Voice> { it.locale.country == JarvisVoice.LOCALE.country }
                        .thenByDescending { it.quality >= Voice.QUALITY_VERY_HIGH }
                )
                ?.firstOrNull()
            if (best != null) {
                runCatching { engine.voice = best }
                return
            }
        }
        runCatching { engine.language = JarvisVoice.LOCALE }
    }

    fun speak(text: String, rate: Float = 1.0f) {
        if (!ready) return
        tts?.setSpeechRate(rate * JarvisVoice.RATE)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis")
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}
