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
                    try {
                        recognizer?.stopListening()
                    } catch (_: Exception) {}
                    _isListening = false
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

    private fun startListening() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) return
        try {
            if (recognizer == null || !_isListening) {
                recognizer?.destroy()
                recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                    setRecognitionListener(this@WakeWordForegroundService)
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                    }
                    startListening(intent)
                    _isListening = true
                }
            }
        } catch (_: Exception) { }
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
        // Stop our own background recognizer so the foreground engine can take the mic
        try {
            recognizer?.stopListening()
        } catch (_: Exception) {}
        _isListening = false
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
        recognizer?.destroy()
        recognizer = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
