package com.jarvis.app.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import com.jarvis.app.config.ApiConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class VoiceSettingsManager(private val context: Context) {

    data class Voice(
        val voiceId: String,
        val name: String,
        val description: String
    )

    private val _voices = MutableStateFlow<List<Voice>>(emptyList())
    val voices: StateFlow<List<Voice>> = _voices.asStateFlow()

    private val _selectedVoiceId = MutableStateFlow(ApiConfig.selectedVoiceId)
    val selectedVoiceId: StateFlow<String> = _selectedVoiceId.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private var fallbackTts: TextToSpeech? = null

    init {
        _voices.value = ApiConfig.PRESET_VOICES.map {
            Voice(it.id, it.name, "${it.accent} • ${it.gender} • ${it.description}")
        }
        try {
            fallbackTts = TextToSpeech(context.applicationContext) { }
        } catch (_: Exception) {}
    }

    fun selectVoice(voiceId: String) {
        ApiConfig.selectedVoiceId = voiceId
        _selectedVoiceId.value = voiceId
        ApiConfig.saveVoicePreferences(context, "gemini", voiceId)
    }

    fun previewVoice(voice: Voice) {
        if (_isSpeaking.value) {
            GeminiVoicePlayer.stop()
            fallbackTts?.stop()
            _isSpeaking.value = false
            return
        }

        _isSpeaking.value = true

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val played = GeminiVoicePlayer.speak(
                    context = context,
                    text = "Hello, my name is ${voice.name}. How can I help you?",
                    targetVoice = voice.voiceId
                )
                if (!played) {
                    playDeviceFallback(voice)
                }
            } finally {
                _isSpeaking.value = false
            }
        }
    }

    private fun playDeviceFallback(voice: Voice) {
        try {
            val tts = fallbackTts ?: return
            val voices = tts.voices
            val isMale = voice.voiceId.lowercase() in listOf("charon", "fenrir", "puck", "rex")
            if (!voices.isNullOrEmpty()) {
                val matched = if (isMale) {
                    voices.firstOrNull { v ->
                        val n = v.name.lowercase()
                        n.contains("male") || n.contains("en-gb-x-rjd") || n.contains("en-us-x-iom") || n.contains("en-us-x-sfg")
                    } ?: voices.firstOrNull { !it.name.lowercase().contains("female") }
                } else {
                    voices.firstOrNull { v ->
                        val n = v.name.lowercase()
                        n.contains("female") || n.contains("en-us-x-tpd") || n.contains("en-gb-x-fis")
                    } ?: voices.firstOrNull { it.name.lowercase().contains("female") }
                }
                if (matched != null) tts.voice = matched
            }

            val pitch = when (voice.voiceId.lowercase()) {
                "charon", "rex" -> 0.70f
                "fenrir" -> 0.85f
                "puck" -> 1.22f
                "kore" -> 0.98f
                "aoede", "eve", "eva" -> 1.08f
                else -> 1.0f
            }
            tts.setPitch(pitch)

            val rate = when (voice.voiceId.lowercase()) {
                "puck" -> 1.08f
                "charon" -> 0.92f
                else -> 0.98f
            }
            tts.setSpeechRate(rate)

            tts.speak(
                "Hello, my name is ${voice.name}. How can I help you?",
                TextToSpeech.QUEUE_FLUSH,
                null,
                "preview_${voice.voiceId}"
            )
        } catch (_: Exception) {}
    }
}
