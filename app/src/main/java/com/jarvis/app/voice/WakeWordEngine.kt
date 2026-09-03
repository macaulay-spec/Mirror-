package com.jarvis.app.voice

import android.content.Context
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.jarvis.app.config.AssistantPrefs

/**
 * The seam that lets the wake-word engine be swapped.
 *
 * Implementations:
 *  - [SystemSpeechRecognizerEngine] — what runs today. Zero setup, zero downloads, works
 *    offline on most devices, but costs more battery because the recognizer is restarted
 *    in a loop.
 *  - **Vosk** — the target: fully offline, no account, no key. See `docs/VOSK_SETUP.md`
 *    for the three steps to enable it. It is deliberately not wired by default because it
 *    needs a Gradle dependency and a model file downloaded inside Android Studio, and
 *    adding an unverified dependency here would break your build.
 */
interface WakeWordEngine {

    /** Human name, shown in Settings and diagnostics. */
    val name: String

    /** True while actively listening for the wake phrase. */
    val isListening: Boolean

    /** Starts listening. [onDetected] fires when the wake phrase is heard. */
    fun start(onPartial: (String) -> Unit, onDetected: () -> Unit, onError: (String) -> Unit)

    fun stop()

    fun release()
}

/**
 * Current engine: Android's built-in SpeechRecognizer, restarted on error or timeout.
 *
 * Kept as the default so the app works on the very first run, before any model download.
 *
 * FIX (2026-09-03, "wake word stops responding"): onResults() used to set
 * running = false and NEVER restart the recognizer — only onError() did. After
 * the recognizer produced one final result (i.e. the first time you said
 * anything, or it heard a noise and finalized), the engine went silent
 * forever until some other error happened to fire. Now EVERY final result
 * re-arms the recognizer, and silence/no-match errors (6/7) — which are the
 * recognizer's normal way of saying "heard nothing" every few seconds — no
 * longer count toward the service's consecutive-error backoff.
 */
class SystemSpeechRecognizerEngine(private val context: Context) : WakeWordEngine {

    override val name: String = "System recognizer"

    private var recognizer: SpeechRecognizer? = null
    private var running = false

    // True while the OWNER (service) wants continuous listening. Distinct from
    // `running`, which tracks whether a recognizer instance is alive right now.
    // rearm() only restarts when wantListening is true, so a stop() request is
    // always respected even if a re-arm was already scheduled.
    @Volatile private var wantListening = false
    private var restartPending = false

    override val isListening: Boolean
        get() = running

    override fun start(
        onPartial: (String) -> Unit,
        onDetected: () -> Unit,
        onError: (String) -> Unit
    ) {
        stop()
        wantListening = true
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("Speech recognition is not available on this device.")
            return
        }

        val intent = android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }

        val wakeWord = AssistantPrefs.wakeWord

        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit

            override fun onPartialResults(partialResults: Bundle?) {
                handle(partialResults, onPartial, onDetected, wakeWord)
            }

            override fun onResults(results: Bundle?) {
                handle(results, onPartial, onDetected, wakeWord)
                running = false
                // CRITICAL FIX: a final result is the normal end of every
                // recognition burst. Re-arm immediately so listening never dies.
                rearm(onPartial, onDetected, onError, wakeWord)
            }

            override fun onError(error: Int) {
                running = false
                // Errors 6 (no speech input) and 7 (no match) are the
                // recognizer's NORMAL idle heartbeat in a continuous loop —
                // not failures. Restart quietly; only real errors (mic busy,
                // network, client) are reported upward for backoff counting.
                if (error == 6 || error == 7) {
                    rearm(onPartial, onDetected, onError, wakeWord)
                } else {
                    onError("recognizer error $error")
                }
            }
        }

        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(listener)
            startListening(intent)
        }
        running = true
    }

    /** Re-arms the recognizer if this engine is still supposed to be running. */
    private fun rearm(
        onPartial: (String) -> Unit,
        onDetected: () -> Unit,
        onError: (String) -> Unit,
        wakeWord: String
    ) {
        if (restartPending) return
        restartPending = true
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            restartPending = false
            if (wantListening && !running) {
                try {
                    start(onPartial, onDetected, onError)
                } catch (_: Exception) {
                    onError("failed to re-arm recognizer")
                }
            }
        }, 250L)
    }

    private fun handle(
        bundle: Bundle?,
        onPartial: (String) -> Unit,
        onDetected: () -> Unit,
        wakeWord: String
    ) {
        val text = bundle
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
        if (text.isNullOrBlank()) return
        onPartial(text)
        if (text.contains(wakeWord, ignoreCase = true)) onDetected()
    }

    override fun stop() {
        running = false
        wantListening = false
        try {
            recognizer?.stopListening()
            recognizer?.cancel()
            recognizer?.destroy()
        } catch (_: Exception) {
            // nothing we can do, and nothing worth crashing for
        }
        recognizer = null
    }

    override fun release() = stop()
}
