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
 */
class SystemSpeechRecognizerEngine(private val context: Context) : WakeWordEngine {

    override val name: String = "System recognizer"

    private var recognizer: SpeechRecognizer? = null
    private var running = false

    override val isListening: Boolean
        get() = running

    override fun start(
        onPartial: (String) -> Unit,
        onDetected: () -> Unit,
        onError: (String) -> Unit
    ) {
        stop()
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
            }

            override fun onError(error: Int) {
                running = false
                onError("recognizer error $error")
            }
        }

        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(listener)
            startListening(intent)
        }
        running = true
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
