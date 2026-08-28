package com.jarvis.app.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.content.IntentFilter
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.jarvis.app.MainActivity
import com.jarvis.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Continuous foreground listening.
 *
 * Fallback wake-word path: uses the device speech recognizer in offline mode and looks for
 * "jarvis". This is the zero-cost, no-native-lib path. The clean upgrade for real always-available
 * on-device detection is OpenWakeWord / microWakeWord — swap `detectWakeWord()` for that model.
 */
class WakeWordForegroundService : Service(), RecognitionListener {

    companion object {
        const val CHANNEL_ID = "jarvis_listening"
        private const val NOTIF_ID = 1001
        private const val WAKE_WORDS = "jarvis"
        var running = false
            private set
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var recognizer: SpeechRecognizer? = null
    private var buffered = StringBuilder()
    private var lastWake = 0L

    override fun onCreate() {
        super.onCreate()
        running = true
        createChannel()
        startForeground(NOTIF_ID, buildNotification())
        
        scope.launch {
            com.jarvis.app.voice.VoiceBus.engineState.collect { state ->
                if (state == com.jarvis.core.model.JarvisVisualState.IDLE && running) {
                    startListening()
                } else {
                    stopEngine()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "stop") {
            stopSelf()
            return START_NOT_STICKY
        }
        startListening()
        return START_STICKY
    }

    private var _isListening = false

    /**
     * Swap this one line for `VoskWakeWordEngine(this)` after following docs/VOSK_SETUP.md
     * to get a fully offline wake word with no account and no key.
     */
    private val engine: WakeWordEngine = SystemSpeechRecognizerEngine(this)

    private fun startListening() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) return
        if (_isListening) return
        try {
            engine.start(
                onPartial = { text ->
                    VoiceBus.onPartial(text)
                    if (text.contains(WAKE_WORDS, ignoreCase = true)) tryWake()
                },
                onDetected = { tryWake() },
                onError = { restartSoon() }
            )
            _isListening = true
        } catch (_: Exception) {
            _isListening = false
        }
    }

    private fun stopEngine() {
        try {
            engine.stop()
        } catch (_: Exception) { }
        _isListening = false
    }

    override fun onResults(results: android.os.Bundle?) {
        process(results)
        restartSoon()
    }

    override fun onPartialResults(partialResults: android.os.Bundle?) {
        process(partialResults)
    }

    override fun onError(error: Int) {
        restartSoon()
    }

    override fun onEndOfSpeech() {
        restartSoon()
    }

    override fun onReadyForSpeech(params: android.os.Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEvent(eventType: Int, params: android.os.Bundle?) {}

    private fun process(bundle: android.os.Bundle?) {
        val text = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull() ?: return
        val lower = text.lowercase()
        VoiceBus.onPartial(text)
        if (lower.contains(WAKE_WORDS)) {
            buffered = StringBuilder(text)
            tryWake()
        }
    }

    private fun tryWake() {
        val now = System.currentTimeMillis()
        if (now - lastWake < 6000) return
        lastWake = now
        
        // Bring MainActivity to the front so it can handle the voice command via VoiceOrchestratorBridge
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("WAKE_WORD_ACTIVATED", true)
        }
        startActivity(intent)
        
        VoiceBus.onWakeWord()
        VoiceBus.clearTranscript()
        // Stop our own background listener so the foreground engine can take the mic
        stopEngine()
    }

    private fun restartSoon() {
        scope.launch {
            delay(600)
            if (running && com.jarvis.app.voice.VoiceBus.engineState.value == com.jarvis.core.model.JarvisVisualState.IDLE) {
                startListening()
            }
        }
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, WakeWordForegroundService::class.java).apply { action = "stop" },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("JARVIS is listening")
            .setContentText("Say \"Hey JARVIS\" to activate")
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentIntent(pi)
            .addAction(0, "Stop", stop)
            .setOngoing(true)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(CHANNEL_ID, "JARVIS Listening", NotificationManager.IMPORTANCE_LOW)
            channel.description = "Always-available wake word listening"
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        running = false
        try {
            engine.release()
        } catch (_: Exception) { }
        try {
            recognizer?.destroy()
        } catch (_: Exception) { }
        recognizer = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
