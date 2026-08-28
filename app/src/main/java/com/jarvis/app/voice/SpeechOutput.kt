package com.jarvis.app.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class SpeechOutput(context: Context) : TextToSpeech.OnInitListener {
    private var ready = false
    private var pendingText: String? = null
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

        try {
            val ukLocale = Locale.UK
            engine.language = ukLocale
            val voices = engine.voices
            val maleVoice = voices?.firstOrNull { voice ->
                val name = voice.name.lowercase()
                name.contains("male") || name.contains("en-gb-x-rjd") || name.contains("en-us-x-iom") || name.contains("en-us-x-sfg") || name.contains("en-us-x-iob")
            } ?: voices?.firstOrNull { voice ->
                voice.locale.language == "en" && !voice.name.lowercase().contains("female")
            }
            if (maleVoice != null) {
                engine.voice = maleVoice
            }
            engine.setPitch(0.78f)
            engine.setSpeechRate(0.96f)
        } catch (_: Exception) {}

        pendingText?.let { text ->
            speak(text)
            pendingText = null
        }
    }

    fun speak(text: String, rate: Float = 1.0f) {
        if (!ready) {
            pendingText = text
            return
        }
        tts?.setSpeechRate(rate * 0.96f)
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
