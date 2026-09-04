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
import androidx.core.content.ContextCompat
import com.jarvis.android.voice.CloudSttEngine
import com.jarvis.core.model.JarvisVisualState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * JarvisVoiceEngine — STT + TTS with a real conversation loop.
 *
 * CHANGED (voice pipeline rebuild):
 *
 * 1. Speech QUEUE — utterances sent via [speakQueued] (streaming TTS) play
 *    back-to-back instead of each call stopping the previous one. [speak]
 *    keeps flush semantics: it clears the queue first, so one-shot callers
 *    behave exactly as before.
 *
 * 2. Continuous conversation — with [continuousMode] set, JARVIS re-arms the
 *    recognizer automatically after each spoken reply drains, and recovers
 *    from recognizer errors with bounded backoff instead of going silent.
 *
 * 3. Barge-in — [stopSpeaking] aborts playback and drops queued sentences.
 *
 * 4. Never silent — per utterance: ElevenLabs (proxy → direct) → Android TTS,
 *    with VoiceDiagnostics reporting every failure honestly.
 */
class JarvisVoiceEngine(private val context: Context) : RecognitionListener, TextToSpeech.OnInitListener {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null

    private val speakScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val utteranceChannel = Channel<String>(Channel.UNLIMITED)
    private val ttsUtteranceDone = ConcurrentHashMap<String, kotlinx.coroutines.CompletableDeferred<Boolean>>()

    @Volatile
    private var utteranceInFlight = false

    /** Auto re-arm the recognizer after each reply drains (continuous conversation). */
    @Volatile
    var continuousMode: Boolean = false

    private var consecutiveRecognizerFailures = 0

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
                    override fun onStart(utteranceId: String?) {}

                    override fun onDone(utteranceId: String?) {
                        ttsUtteranceDone.remove(utteranceId ?: "")?.complete(true)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        ttsUtteranceDone.remove(utteranceId ?: "")?.complete(false)
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        ttsUtteranceDone.remove(utteranceId ?: "")?.complete(false)
                    }
                })
            } catch (_: Exception) {}
        }

        setupAudioFocus()
        startSpeechWorker()
    }

    // ─── Speech output queue ─────────────────────────────────────────────

    private fun startSpeechWorker() {
        speakScope.launch {
            for (text in utteranceChannel) {
                utteranceInFlight = true
                try {
                    speakOne(text)
                } catch (_: Exception) {}
                utteranceInFlight = false

                if (utteranceChannel.isEmpty) {
                    abandonAudioFocus()
                    if (continuousMode && _engineState.value == JarvisVisualState.SPEAKING) {
                        // Continuous conversation: listen again automatically.
                        startListening()
                    } else {
                        setState(JarvisVisualState.IDLE)
                    }
                }
            }
        }
    }

    private suspend fun speakOne(text: String) {
        setState(JarvisVisualState.SPEAKING)
        requestAudioFocus()

        val elevenLabsStarted = runCatching {
            com.jarvis.app.voice.ElevenLabsVoicePlayer.speak(context, text, com.jarvis.app.config.ApiConfig.selectedVoiceId)
        }.getOrDefault(false)

        if (!elevenLabsStarted) {
            speakWithAndroidTts(text)
        }
    }

    private suspend fun speakWithAndroidTts(text: String): Boolean {
        if (!isTtsReady) {
            var waited = 0
            while (!isTtsReady && waited < 15) {
                delay(100)
                waited++
            }
        }

        val done = kotlinx.coroutines.CompletableDeferred<Boolean>()
        val utteranceId = "JARVIS_TTS_${System.currentTimeMillis()}"
        ttsUtteranceDone[utteranceId] = done

        mainHandler.post {
            if (isTtsReady) {
                updateVoiceConfig()
                requestAudioFocus()
                com.jarvis.app.voice.VoiceDiagnostics.success("Android TTS")
                val res = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
                if (res != TextToSpeech.SUCCESS) {
                    done.complete(false)
                }
            } else {
                com.jarvis.app.voice.VoiceDiagnostics.report("Android TTS is not ready — nothing was spoken")
                done.complete(false)
            }
        }
        val ok = withTimeoutOrNull(60_000) { done.await() } ?: false
        ttsUtteranceDone.remove(utteranceId)
        return ok
    }

    /** Flush anything queued, then speak [text]. One-shot semantics. */
    fun speak(text: String) {
        if (text.isBlank()) return
        drainQueue()
        speakQueued(text)
    }

    /** Queue a sentence without flushing what is already playing (streaming TTS). */
    fun speakQueued(text: String) {
        if (text.isBlank()) return
        utteranceChannel.trySend(text.trim())
    }

    /** Barge-in: abort playback and drop every queued sentence. */
    fun stopSpeaking() {
        drainQueue()
        mainHandler.post {
            try { tts?.stop() } catch (_: Exception) {}
            com.jarvis.app.voice.ElevenLabsVoicePlayer.stop()
            utteranceInFlight = false
            if (_engineState.value == JarvisVisualState.SPEAKING) {
                setState(JarvisVisualState.IDLE)
            }
            abandonAudioFocus()
        }
    }

    private fun drainQueue() {
        while (utteranceChannel.tryReceive().isSuccess) { /* discard */ }
    }

    /** True while JARVIS is producing audio (playback or queued utterances). */
    val isSpeaking: Boolean
        get() = utteranceInFlight || !utteranceChannel.isEmpty || com.jarvis.app.voice.ElevenLabsVoicePlayer.isPlaying

    /** Suspend until everything queued/playing has drained (bounded by [timeoutMs]). */
    suspend fun awaitSpeechDone(timeoutMs: Long = 30_000) {
        withTimeoutOrNull(timeoutMs) {
            while (isSpeaking) delay(100)
        }
    }

    // ─── Audio focus ─────────────────────────────────────────────────────

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
                    AudioManager.AUDIOFOCUS_LOSS -> {
                        continuousMode = false
                        stopSpeaking()
                        stopListening()
                    }
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                        stopSpeaking()
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

    // ─── Speech recognition ──────────────────────────────────────────────

    fun startListening() {
        mainHandler.post {
            try {
                stopSpeaking()
                safeDestroyRecognizer()

                if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                    // AUDIT FIX (2026-09-03): don't give up — many devices ship
                    // without Google's recognizer. Fall back to cloud STT
                    // (Vercel AI Gateway via the Rork proxy).
                    com.jarvis.app.voice.VoiceDiagnostics.report("Device speech recognition unavailable — using cloud STT")
                    startCloudListening()
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
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
                }

                recognizer.startListening(intent)
                consecutiveRecognizerFailures = 0
                setState(JarvisVisualState.LISTENING)
            } catch (e: Throwable) {
                com.jarvis.app.voice.VoiceDiagnostics.report("Failed to start recognizer: ${e.message}")
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

    /**
     * AUDIT ADDITION (2026-09-03): cloud STT path (Vercel AI Gateway,
     * xai/grok-stt via the Rork proxy) for devices where the system
     * recognizer is missing or repeatedly fails.
     */
    fun startCloudListening() {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            com.jarvis.app.voice.VoiceDiagnostics.report("Cloud STT needs microphone permission")
            setState(JarvisVisualState.ERROR)
            return
        }
        setState(JarvisVisualState.LISTENING)
        speakScope.launch {
            val text = CloudSttEngine.listenAndTranscribe { level ->
                _audioRms.value = level
                com.jarvis.app.voice.VoiceBus.setAudioLevel(level)
            }
            _audioRms.value = 0f
            abandonAudioFocus()
            if (!text.isNullOrBlank()) {
                _lastRecognizedText.value = text
                consecutiveRecognizerFailures = 0
                setState(JarvisVisualState.THINKING)
                onSpeechResult?.invoke(text)
            } else if (continuousMode) {
                startListening()
            } else {
                setState(JarvisVisualState.IDLE)
            }
        }
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

    private fun scheduleRecognizerRestart(delayMs: Long) {
        mainHandler.postDelayed({
            val state = _engineState.value
            val mayRestart = continuousMode &&
                state != JarvisVisualState.SPEAKING &&
                state != JarvisVisualState.THINKING &&
                state != JarvisVisualState.EXECUTING
            if (mayRestart && consecutiveRecognizerFailures < MAX_RECOGNIZER_RESTARTS) {
                consecutiveRecognizerFailures++
                startListening()
            } else if (state == JarvisVisualState.LISTENING || state == JarvisVisualState.ERROR) {
                setState(JarvisVisualState.IDLE)
            }
        }, delayMs)
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
        } else {
            com.jarvis.app.voice.VoiceDiagnostics.report("Android TTS init failed (status $status)")
        }
    }

    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val text = matches?.firstOrNull()
        _audioRms.value = 0f
        abandonAudioFocus()
        if (!text.isNullOrBlank()) {
            _lastRecognizedText.value = text
            consecutiveRecognizerFailures = 0
            setState(JarvisVisualState.THINKING)
            onSpeechResult?.invoke(text)
        } else if (continuousMode) {
            // Nothing said — keep the loop alive.
            scheduleRecognizerRestart(RESTART_IDLE_DELAY_MS)
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
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                com.jarvis.app.voice.VoiceDiagnostics.report("Microphone permission missing — recognizer cannot start")
                continuousMode = false
                setState(JarvisVisualState.ERROR)
            }
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                if (continuousMode) scheduleRecognizerRestart(RESTART_IDLE_DELAY_MS) else setState(JarvisVisualState.IDLE)
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                scheduleRecognizerRestart(RESTART_BUSY_DELAY_MS)
            else ->
                if (continuousMode) scheduleRecognizerRestart(RESTART_ERROR_DELAY_MS) else setState(JarvisVisualState.IDLE)
        }
    }

    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onEvent(eventType: Int, params: Bundle?) {}

    fun destroy() {
        continuousMode = false
        drainQueue()
        stopListening()
        stopSpeaking()
        mainHandler.post {
            try {
                tts?.shutdown()
            } catch (_: Exception) {}
        }
        abandonAudioFocus()
    }

    companion object {
        private const val MAX_RECOGNIZER_RESTARTS = 5
        private const val RESTART_IDLE_DELAY_MS = 250L
        private const val RESTART_BUSY_DELAY_MS = 600L
        private const val RESTART_ERROR_DELAY_MS = 1000L
    }
}
