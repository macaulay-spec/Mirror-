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
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.jarvis.app.MainActivity
import com.rork.jarvisaiassistant.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Continuous foreground listening for the "Hey JARVIS" wake word.
 *
 * Current implementation: Android SpeechRecognizer in continuous mode,
 * scanning for "jarvis" in partial results.
 *
 * Production upgrade path:
 *   1. Vosk (offline, no account needed) — see docs/VOSK_SETUP.md
 *   2. Picovoice Porcupine (most accurate, requires license)
 *   3. microWakeWord (lightweight, TFLite-based)
 *
 * The service runs as a foreground service with a persistent notification.
 * It automatically pauses when the engine is active (processing a command)
 * and resumes when idle.
 */
class WakeWordForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "jarvis_listening"
        private const val NOTIF_ID = 1001
        private const val WAKE_WORDS = "jarvis"
        private const val RESTART_DELAY_MS = 800L
        private const val COOLDOWN_MS = 6000L
        private const val MAX_CONSECUTIVE_ERRORS = 5
        private const val ERROR_RESET_DELAY_MS = 30000L

        var running = false
            private set
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var engine: WakeWordEngine? = null
    private var lastWake = 0L
    private var consecutiveErrors = 0
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        createChannel()
        startForeground(NOTIF_ID, buildNotification())
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "stop") {
            stopSelf()
            return START_NOT_STICKY
        }
        startListening()
        return START_STICKY
    }

    private fun startListening() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            // Can't listen without permission — will retry when permission is granted
            return
        }

        stopEngine()

        engine = SystemSpeechRecognizerEngine(this)

        try {
            engine!!.start(
                onPartial = { text ->
                    consecutiveErrors = 0 // Reset on successful recognition
                    VoiceBus.onPartial(text)
                    if (text.contains(WAKE_WORDS, ignoreCase = true)) {
                        tryWake()
                    }
                },
                onDetected = {
                    tryWake()
                },
                onError = {
                    consecutiveErrors++
                    if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
                        // Too many errors — back off
                        scope.launch {
                            delay(ERROR_RESET_DELAY_MS)
                            consecutiveErrors = 0
                            if (running) startListening()
                        }
                    } else {
                        restartSoon()
                    }
                }
            )
        } catch (_: Exception) {
            consecutiveErrors++
            restartSoon()
        }
    }

    private fun stopEngine() {
        try {
            engine?.stop()
            engine?.release()
        } catch (_: Exception) {}
        engine = null
    }

    private fun tryWake() {
        val now = System.currentTimeMillis()
        if (now - lastWake < COOLDOWN_MS) return
        lastWake = now

        // Bring MainActivity to the front
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("WAKE_WORD_ACTIVATED", true)
        }
        startActivity(intent)

        VoiceBus.onWakeWord()
        VoiceBus.clearTranscript()

        // Stop background listening — the foreground engine takes the mic…
        stopEngine()

        // …FIX (2026-09-03): …and RESUME background listening once the
        // foreground interaction settles. This used to stop the engine and
        // never restart it, so a single successful "Hey JARVIS" permanently
        // killed hands-free listening until the service was restarted.
        resumeWhenIdle()
    }

    /** Re-arms background wake-word listening after a foreground interaction. */
    private fun resumeWhenIdle() {
        scope.launch {
            var waited = 0L
            while (running && waited < 120_000L) {
                delay(2000L)
                waited += 2000L
                val state = VoiceBus.engineState.value
                if (state == com.jarvis.core.model.JarvisVisualState.IDLE ||
                    state == com.jarvis.core.model.JarvisVisualState.ERROR
                ) {
                    if (running) startListening()
                    return@launch
                }
            }
        }
    }

    private fun restartSoon() {
        scope.launch {
            delay(RESTART_DELAY_MS)
            if (running && VoiceBus.engineState.value == com.jarvis.core.model.JarvisVisualState.IDLE) {
                startListening()
            }
        }
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "jarvis:wakeword"
        ).apply {
            acquire(10 * 60 * 1000L) // 10 minutes, auto-releases
        }
        // FIX: the wake lock used to expire silently after 10 minutes, after
        // which doze mode could delay the recognizer re-arm loop indefinitely
        // ("wake word stops working after a while"). Renew it on a rolling
        // 9-minute cycle for as long as the service is running.
        scope.launch {
            while (running) {
                delay(9 * 60 * 1000L)
                if (!running) break
                try {
                    wakeLock?.let { if (!it.isHeld) it.acquire(10 * 60 * 1000L) }
                } catch (_: Exception) {}
            }
        }
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, WakeWordForegroundService::class.java).apply { action = "stop" },
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
            val channel = NotificationChannel(
                CHANNEL_ID,
                "JARVIS Listening",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Always-available wake word listening"
            }
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        running = false
        stopEngine()
        try {
            wakeLock?.release()
        } catch (_: Exception) {}
        wakeLock = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
