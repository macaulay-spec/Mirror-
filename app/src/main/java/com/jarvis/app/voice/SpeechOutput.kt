package com.jarvis.app.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class SpeechOutput(context: Context) : TextToSpeech.OnInitListener {
    private var ready = false
    private var tts: TextToSpeech? = null

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        tts?.language = Locale.US
    }

    fun speak(text: String, rate: Float = 1.0f) {
        if (!ready) return
        tts?.setSpeechRate(rate)
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
