package com.jarvis.android.voice

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.jarvis.core.model.JarvisVisualState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class JarvisVoiceEngine(private val context: Context) : RecognitionListener, TextToSpeech.OnInitListener {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null

    private val _engineState = MutableStateFlow(JarvisVisualState.IDLE)
    val engineState: StateFlow<JarvisVisualState> = _engineState.asStateFlow()

    private val _lastRecognizedText = MutableStateFlow<String?>(null)
    val lastRecognizedText: StateFlow<String?> = _lastRecognizedText.asStateFlow()

    private val _audioRms = MutableStateFlow(0f)
    val audioRms: StateFlow<Float> = _audioRms.asStateFlow()

    var onSpeechResult: ((String) -> Unit)? = null

    init {
        mainHandler.post {
            try {
                tts = TextToSpeech(context.applicationContext, this)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        setState(JarvisVisualState.SPEAKING)
                    }

                    override fun onDone(utteranceId: String?) {
                        abandonAudioFocus()
                        setState(JarvisVisualState.IDLE)
                    }

                    override fun onError(utteranceId: String?) {
                        abandonAudioFocus()
                        setState(JarvisVisualState.IDLE)
                    }
                })
            } catch (_: Exception) {}
        }
        
        setupAudioFocus()
    }

    private fun setupAudioFocus() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(audioAttributes)
            .setAcceptsDelayedFocusGain(true)
            .setOnAudioFocusChangeListener { focusChange ->
                when (focusChange) {
                    AudioManager.AUDIOFOCUS_LOSS,
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                        stopSpeaking()
                        stopListening()
                    }
                }
            }
            .build()
    }

    private fun requestAudioFocus(): Boolean {
        return try {
            audioFocusRequest?.let {
                audioManager.requestAudioFocus(it) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            } ?: false
        } catch (_: Exception) {
            false
        }
    }

    private fun abandonAudioFocus() {
        try {
            audioFocusRequest?.let {
                audioManager.abandonAudioFocusRequest(it)
            }
        } catch (_: Exception) {}
    }

    fun setState(state: JarvisVisualState) {
        _engineState.value = state
        com.jarvis.app.voice.VoiceBus.setEngineState(state)
    }

    fun startListening() {
        mainHandler.post {
            try {
                stopSpeaking()
                safeDestroyRecognizer()

                if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                    setState(JarvisVisualState.ERROR)
                    return@post
                }
                
                requestAudioFocus()

                val recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(this@JarvisVoiceEngine)
                }
                speechRecognizer = recognizer

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
                }

                recognizer.startListening(intent)
                setState(JarvisVisualState.LISTENING)
            } catch (e: Throwable) {
                setState(JarvisVisualState.ERROR)
                abandonAudioFocus()
            }
        }
    }

    private fun safeDestroyRecognizer() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        speechRecognizer = null
    }

    fun stopListening() {
        mainHandler.post {
            safeDestroyRecognizer()
            if (_engineState.value == JarvisVisualState.LISTENING) {
                setState(JarvisVisualState.IDLE)
                abandonAudioFocus()
            }
            _audioRms.value = 0f
        }
    }

    fun speak(text: String) {
        if (text.isBlank()) return
        mainHandler.post {
            stopListening()
            setState(JarvisVisualState.SPEAKING)
            
            // Launch coroutine to try preferred voice engine, fallback if needed
            CoroutineScope(Dispatchers.IO).launch {
                val useElevenLabs = com.jarvis.app.config.ApiConfig.voiceEngineType == "elevenlabs" && com.jarvis.app.config.ApiConfig.hasElevenLabs
                val success = if (useElevenLabs) {
                    com.jarvis.app.voice.ElevenLabsVoicePlayer.speak(context, text, com.jarvis.app.config.ApiConfig.selectedVoiceId)
                } else {
                    false
                }

                if (!success) {
                    mainHandler.post {
                        if (isTtsReady) {
                            requestAudioFocus()
                            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "JARVIS_TTS_${System.currentTimeMillis()}")
                        } else {
                            setState(JarvisVisualState.IDLE)
                        }
                    }
                }
            }
        }
    }

    fun stopSpeaking() {
        mainHandler.post {
            try {
                tts?.stop()
            } catch (_: Exception) {}
            if (_engineState.value == JarvisVisualState.SPEAKING) {
                setState(JarvisVisualState.IDLE)
            }
            abandonAudioFocus()
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            try {
                val loc = Locale.US
                val result = tts?.setLanguage(loc)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.language = Locale.getDefault()
                }
                tts?.setPitch(0.95f)
                tts?.setSpeechRate(1.05f)
                isTtsReady = true
            } catch (_: Exception) {
                isTtsReady = true
            }
        }
    }

    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val text = matches?.firstOrNull()
        _audioRms.value = 0f
        abandonAudioFocus()
        if (!text.isNullOrBlank()) {
            _lastRecognizedText.value = text
            setState(JarvisVisualState.THINKING)
            onSpeechResult?.invoke(text)
        } else {
            setState(JarvisVisualState.IDLE)
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        matches?.firstOrNull()?.let { 
            _lastRecognizedText.value = it
            com.jarvis.app.voice.VoiceBus.onPartial(it)
        }
    }

    override fun onRmsChanged(rmsdB: Float) {
        val level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
        _audioRms.value = level
        com.jarvis.app.voice.VoiceBus.setAudioLevel(level)
    }

    override fun onError(error: Int) {
        _audioRms.value = 0f
        abandonAudioFocus()
        safeDestroyRecognizer()
        
        when (error) {
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                setState(JarvisVisualState.IDLE)
            }
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
            SpeechRecognizer.ERROR_CLIENT -> {
                setState(JarvisVisualState.IDLE)
            }
            else -> {
                setState(JarvisVisualState.IDLE)
            }
        }
    }

    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onEvent(eventType: Int, params: Bundle?) {}

    fun destroy() {
        stopListening()
        stopSpeaking()
        mainHandler.post {
            try {
                tts?.shutdown()
            } catch (_: Exception) {}
        }
        abandonAudioFocus()
    }
}
