package com.jarvis.app.voice

import android.util.Log

/**
 * Why did that sound like the robot voice?
 *
 * ElevenLabs failures used to be swallowed: every `catch` returned `false` and the engine
 * silently fell back to Android's built-in TTS, so there was no way to tell a dead key
 * from quota exhaustion from a network blip. Now every failure is recorded and can be
 * shown in Settings.
 */
object VoiceDiagnostics {

    private const val TAG = "VoiceDiagnostics"

    @Volatile var lastError: String? = null
        private set

    @Volatile var lastErrorAt: Long = 0L
        private set

    @Volatile var lastSuccessAt: Long = 0L
        private set

    @Volatile var lastProvider: String? = null
        private set

    fun report(message: String) {
        lastError = message
        lastErrorAt = System.currentTimeMillis()
        Log.w(TAG, message)
    }

    fun success(provider: String) {
        lastProvider = provider
        lastSuccessAt = System.currentTimeMillis()
        lastError = null
    }

    val summary: String
        get() {
            val error = lastError
            return when {
                error != null -> error
                lastProvider == null -> "No speech has been generated yet."
                else -> "$lastProvider is working."
            }
        }
}
