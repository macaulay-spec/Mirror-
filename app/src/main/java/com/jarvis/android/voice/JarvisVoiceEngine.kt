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

    // ── Barge-in / streaming speech state ─────────────────────────────────
    // While JARVIS is speaking we keep a SECOND recognizer running ("hot
    // mic"). It listens only for stop-phrases ("stop", "quiet", "silence"…)
    // and cuts audio the instant one is heard. Previously speak() called
    // stopListening() first, which made interruption physically impossible.
    @Volatile private var bargeInActive = false
    @Volatile private var speakGeneration = 0

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
        startListeningInternal(bargeIn = false)
    }

    /**
     * Starts speech recognition.
     *
     * @param bargeIn true = the hot-mic recognizer that runs WHILE JARVIS is
     * speaking; it only reacts to stop-phrases (see [isStopPhrase]) and never
     * changes the visual state or the live transcript.
     */
    private fun startListeningInternal(bargeIn: Boolean) {
        mainHandler.post {
            try {
                if (!bargeIn) {
                    stopSpeaking()
                }
                safeDestroyRecognizer()

                if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                    if (!bargeIn) setState(JarvisVisualState.ERROR)
                    return@post
                }

                if (!bargeIn) requestAudioFocus()

                bargeInActive = bargeIn

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
                    if (bargeIn) {
                        // Hot mic: settle quickly so a spoken "stop" cuts audio fast.
                        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 800L)
                        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 500L)
                    } else {
                        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
                        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
                    }
                }

                recognizer.startListening(intent)
                if (!bargeIn) setState(JarvisVisualState.LISTENING)
            } catch (e: Throwable) {
                if (!bargeIn) {
                    setState(JarvisVisualState.ERROR)
                    abandonAudioFocus()
                }
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
            bargeInActive = false
            safeDestroyRecognizer()
            if (_engineState.value == JarvisVisualState.LISTENING) {
                setState(JarvisVisualState.IDLE)
                abandonAudioFocus()
            }
            _audioRms.value = 0f
        }
    }

    /**
     * True when [text] is an interruption command spoken while JARVIS is
     * talking — "stop", "quiet", "shut up", "that's enough", "never mind"…
     * Word-boundary matching so "stopwatch" doesn't cut the voice off.
     */
    private fun isStopPhrase(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val t = text.lowercase().replace("[^a-z0-9\\s']".toRegex(), " ").trim()
        if (t.isEmpty()) return false
        val tokens = t.split("\\s+".toRegex())
        val stopTokens = setOf(
            "stop", "stops", "stopping", "stopped", "quiet", "silence", "hush",
            "halt", "cease", "enough", "cancel", "shh", "shhh", "nevermind", "stopit"
        )
        if (tokens.any { it in stopTokens }) return true
        val phrases = listOf(
            "shut up", "never mind", "that's enough", "thats enough",
            "be quiet", "okay stop", "ok stop", "stop talking", "stop speaking",
            "stop it", "enough already"
        )
        return phrases.any { t.contains(it) }
    }

    fun updateVoiceConfig() {
        mainHandler.post {
            try {
                val ukLocale = Locale.UK
                val result = tts?.setLanguage(ukLocale)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.language = Locale.US
                }
                
                val voices = tts?.voices
                if (!voices.isNullOrEmpty()) {
                    val selectedVoiceId = com.jarvis.app.config.ApiConfig.selectedVoiceId
                    val matchedVoice = voices.firstOrNull { v -> v.name == selectedVoiceId }
                        ?: voices.firstOrNull { voice ->
                            val name = voice.name.lowercase()
                            name.contains("male") || name.contains("en-gb-x-rjd") || name.contains("en-us-x-iom") || name.contains("en-us-x-sfg") || name.contains("en-us-x-iob")
                        } ?: voices.firstOrNull { voice ->
                            voice.locale.language == "en" && !voice.name.lowercase().contains("female")
                        }
                    if (matchedVoice != null) {
                        tts?.voice = matchedVoice
                    }
                }
                
                tts?.setPitch(0.78f) // Deep, calm male pitch
                tts?.setSpeechRate(0.96f) // Measured, articulate pace
            } catch (_: Exception) {}
        }
    }

    fun speak(text: String) {
        if (text.isBlank()) return
        val generation = ++speakGeneration
        mainHandler.post {
            // BARGE-IN FIX: no stopListening() here. The mic stays open while
            // JARVIS speaks so the user can interrupt with "stop" / "quiet".
            setState(JarvisVisualState.SPEAKING)

            // Re-apply latest voice config
            updateVoiceConfig()

            // Launch coroutine to try preferred voice engine, fallback if needed
            CoroutineScope(Dispatchers.IO).launch {
                val useElevenLabs = com.jarvis.app.config.ApiConfig.voiceEngineType == "elevenlabs"
                val success = if (useElevenLabs) {
                    com.jarvis.app.voice.ElevenLabsVoicePlayer.speak(context, text, com.jarvis.app.config.ApiConfig.selectedVoiceId)
                } else {
                    false
                }

                // Stale guard: if stopSpeaking() (or a newer speak) ran while
                // we were synthesizing, drop this audio instead of playing it.
                if (generation != speakGeneration) return@launch

                if (!success) {
                    mainHandler.post {
                        if (generation != speakGeneration) return@post
                        if (isTtsReady) {
                            requestAudioFocus()
                            com.jarvis.app.voice.VoiceDiagnostics.success("Android TTS")
                            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "JARVIS_TTS_${System.currentTimeMillis()}")
                        } else {
                            com.jarvis.app.voice.VoiceDiagnostics.report(
                                "Android TTS is not ready yet — nothing was spoken."
                            )
                            setState(JarvisVisualState.IDLE)
                        }
                    }
                }
            }

            // HOT MIC: start the barge-in recognizer while speaking.
            startListeningInternal(bargeIn = true)
        }
    }

    /**
     * Streaming speech: queues a single sentence for sequential playback.
     * Called by the orchestrator as streamed text forms complete sentences,
     * so JARVIS starts talking while the rest of the reply is still arriving.
     * Falls back to Android TTS (queued) when ElevenLabs is unavailable.
     */
    fun speakQueued(sentence: String) {
        if (sentence.isBlank()) return
        setState(JarvisVisualState.SPEAKING)
        CoroutineScope(Dispatchers.IO).launch {
            val accepted = try {
                com.jarvis.app.voice.ElevenLabsVoicePlayer.enqueueSentence(
                    context, sentence, com.jarvis.app.config.ApiConfig.selectedVoiceId
                )
            } catch (_: Exception) {
                false
            }
            if (!accepted) {
                mainHandler.post {
                    if (isTtsReady) {
                        requestAudioFocus()
                        tts?.speak(sentence, TextToSpeech.QUEUE_ADD, null, "JARVIS_TTS_${System.currentTimeMillis()}")
                    }
                }
            }
        }
    }

    fun stopSpeaking() {
        // Synchronous on purpose: every path that starts new audio must first
        // observe this generation bump, and the mic/recognizer teardown order
        // matters (a posted variant could destroy a recognizer that a newer
        // call had just started).
        speakGeneration++
        try {
            // FIX: this never stopped ElevenLabs audio before — only the
            // Android TTS engine. "Stop" had no effect on the British voice.
            com.jarvis.app.voice.ElevenLabsVoicePlayer.stop()
        } catch (_: Exception) {}
        if (bargeInActive) {
            bargeInActive = false
            safeDestroyRecognizer()
        }
        try {
            tts?.stop()
        } catch (_: Exception) {}
        if (_engineState.value == JarvisVisualState.SPEAKING) {
            setState(JarvisVisualState.IDLE)
        }
        abandonAudioFocus()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            try {
                val ukLocale = Locale.UK
                val result = tts?.setLanguage(ukLocale)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.language = Locale.US
                }
                
                // Select a male voice if available in the installed voices
                try {
                    val voices = tts?.voices
                    val maleVoice = voices?.firstOrNull { voice ->
                        val name = voice.name.lowercase()
                        !voice.isNetworkConnectionRequired && (name.contains("male") || name.contains("en-gb") || name.contains("en-us-x-sfg") || name.contains("en-us-x-iob"))
                    } ?: voices?.firstOrNull { voice ->
                        voice.locale.language == "en" && !voice.name.lowercase().contains("female")
                    }
                    if (maleVoice != null) {
                        tts?.voice = maleVoice
                    }
                } catch (_: Exception) {}

                tts?.setPitch(0.85f) // Deep, calm male pitch
                tts?.setSpeechRate(0.98f) // Articulate, measured pace
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

        // Hot-mic result while JARVIS is speaking: only stop-phrases act
        // (anything else the mic hears is usually the speaker's own voice).
        if (bargeInActive) {
            bargeInActive = false
            if (isStopPhrase(text)) {
                stopSpeaking()
            }
            return
        }

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
        // During barge-in listening, partials are the TTS output echoing in
        // the mic — never show them as the user's transcript.
        if (bargeInActive) return
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

        // The hot-mic recognizer failing while JARVIS is speaking (mic busy,
        // timeout, no-match) is normal — it must never knock the SPEAKING
        // state back to IDLE.
        if (bargeInActive) {
            bargeInActive = false
            safeDestroyRecognizer()
            if (_engineState.value != JarvisVisualState.SPEAKING) setState(JarvisVisualState.IDLE)
            return
        }

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
