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
import com.jarvis.android.voice.MicArbiter
import com.jarvis.app.MainActivity
import com.rork.jarvisaiassistant.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
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
 *
 * ## Microphone ownership (audit P1-D)
 *
 * This service and [com.jarvis.android.voice.JarvisVoiceEngine] each own a
 * `SpeechRecognizer`, and Android allows only one to be live at a time. They used to
 * coordinate by polling `VoiceBus.engineState` for `IDLE` — for up to 120 seconds — and
 * then starting the recognizer anyway. Because the conversation engine kept re-arming
 * itself, the state rarely settled, and when it briefly did both recognizers came up
 * together: `ERROR_RECOGNIZER_BUSY`, both back off, hands-free listening dies.
 *
 * Both now go through [MicArbiter]. This service asks for the mic, is told no while a
 * conversation holds it, and resumes by collecting [MicArbiter.holder] instead of
 * polling. The conversation preempts this service through a yield hook registered in
 * [onCreate], so the background recognizer is always stopped *before* the foreground one
 * starts.
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
        private const val WAKE_LOCK_TTL_MS = 10 * 60 * 1000L
        private const val WAKE_LOCK_RENEWAL_MS = 9 * 60 * 1000L

        var running = false
            private set
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var engine: WakeWordEngine? = null
    private var lastWake = 0L
    private var consecutiveErrors = 0
    private var wakeLock: PowerManager.WakeLock? = null

    /** Single waiter for the microphone; guards against stacking retries. */
    private var micWaitJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        createChannel()
        startForeground(NOTIF_ID, buildNotification())
        acquireWakeLock()

        // Let a conversation take the microphone away from us. The arbiter stops this
        // engine BEFORE granting the mic to JarvisVoiceEngine, so there is no instant
        // where two recognizers are live.
        MicArbiter.setWakeWordOwner { stopEngine() }

        // FIX (production repair): the wake lock expired after 10 minutes and was
        // never renewed, so Doze quietly killed wake-word listening overnight.
        // Renew it on a schedule for as long as the service lives.
        scope.launch {
            while (running) {
                delay(WAKE_LOCK_RENEWAL_MS)
                if (running) {
                    try {
                        wakeLock?.acquire(WAKE_LOCK_TTL_MS)
                    } catch (_: Exception) {}
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

    private fun startListening() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            // Can't listen without permission — will retry when permission is granted
            return
        }

        // Ask the arbiter rather than assuming the mic is free. If a conversation holds
        // it, wait for the release event instead of starting a second recognizer.
        if (!MicArbiter.acquireWakeWord()) {
            awaitMicThenStart()
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
        MicArbiter.releaseWakeWord()
    }

    /**
     * Waits for the microphone to be released, then listens again.
     *
     * Replaces the old `resumeWhenIdle()`, which polled `VoiceBus.engineState` every two
     * seconds for up to two minutes and then started the recognizer regardless of whether
     * anything else held the mic. This is event-driven and cannot start a recognizer while
     * one is already live.
     */
    private fun awaitMicThenStart() {
        if (micWaitJob?.isActive == true) return
        micWaitJob = scope.launch {
            MicArbiter.holder.first { it == MicArbiter.Holder.NONE }
            micWaitJob = null
            if (running) startListening()
        }
    }

    private fun tryWake() {
        val now = System.currentTimeMillis()
        if (now - lastWake < COOLDOWN_MS) return
        lastWake = now

        VoiceBus.onWakeWord()
        VoiceBus.clearTranscript()

        // Stop background listening — the foreground engine takes the mic…
        stopEngine()

        // …and resume as soon as it gives the mic back. This used to be a 120-second poll
        // of VoiceBus.engineState that started the recognizer whether or not the
        // conversation was actually finished; now it is driven by the arbiter's release.
        awaitMicThenStart()
    }

    private fun restartSoon() {
        scope.launch {
            delay(RESTART_DELAY_MS)
            // No engineState check here: startListening() asks the arbiter, and parks on
            // awaitMicThenStart() if a conversation holds the mic. Checking a state flow
            // here is what allowed two recognizers to overlap in the first place.
            if (running) startListening()
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
        micWaitJob?.cancel()
        micWaitJob = null
        MicArbiter.setWakeWordOwner { }
        stopEngine()
        try {
            wakeLock?.release()
        } catch (_: Exception) {}
        wakeLock = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
