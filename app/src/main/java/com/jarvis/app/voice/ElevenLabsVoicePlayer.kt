package com.jarvis.app.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.jarvis.app.config.ApiConfig
import com.jarvis.core.model.JarvisVisualState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * ElevenLabsVoicePlayer — streams HD TTS audio from ElevenLabs.
 *
 * Improvements over previous version:
 *   - Uses eleven_turbo_v2_5 model (fast + high quality)
 *   - Voice preset cascade: tries configured voice first, falls back through
 *     the preset list so the app never goes silent due to a missing voice ID
 *   - VoiceDiagnostics reports the real failure reason
 *   - stop() is safe to call at any time (barge-in support)
 *   - Returns true/false so JarvisVoiceEngine knows whether to fall back to Android TTS
 */
object ElevenLabsVoicePlayer {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .build()

    private var mediaPlayer: MediaPlayer? = null

    // Voice fallback chain — tried in order if the first ID fails
    private val BRITISH_FALLBACK_IDS = listOf(
        "JBFqnCBsd6RMkjVDRZzb", // George — warm, British male
        "N2lVS1w4EtoT3dr4eOWO", // Callum — intense, British male
        "Xb7hH8MSUJpSbSDYk0k2", // Alice — British female
        "pNInz6obpgDQGcFmaJgB", // Adam — deep US male (last resort)
        "21m00Tcm4TlvDq8ikWAM"  // Rachel — always exists on every ElevenLabs account
    )

    /**
     * Speak [text] via ElevenLabs.
     * Returns true if audio started playing, false if ElevenLabs is unavailable (caller should use Android TTS).
     */
    suspend fun speak(context: Context, text: String, voiceId: String = ApiConfig.selectedVoiceId): Boolean =
        withContext(Dispatchers.IO) {
            if (text.isBlank()) return@withContext false
            val apiKey = ApiConfig.ELEVENLABS_API_KEY
            if (apiKey.isBlank()) {
                VoiceDiagnostics.report("ElevenLabs key not configured — using Android TTS")
                return@withContext false
            }

            stop()  // barge-in: stop any current playback

            // Try configured voice, then fall back through the cascade
            val voiceOrder = buildList {
                add(voiceId)
                BRITISH_FALLBACK_IDS.forEach { if (it != voiceId) add(it) }
            }

            for (vid in voiceOrder) {
                val result = trySpeak(context, text, vid, apiKey)
                if (result) {
                    VoiceDiagnostics.success("ElevenLabs voice: $vid")
                    return@withContext true
                }
            }

            VoiceDiagnostics.report("ElevenLabs: all voices failed — using Android TTS fallback")
            return@withContext false
        }

    private suspend fun trySpeak(context: Context, text: String, voiceId: String, apiKey: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val url = "https://api.elevenlabs.io/v1/text-to-speech/$voiceId"
                val payload = JSONObject().apply {
                    put("text", text)
                    put("model_id", "eleven_turbo_v2_5")
                    put("voice_settings", JSONObject().apply {
                        put("stability", 0.40)
                        put("similarity_boost", 0.80)
                        put("style", 0.28)
                        put("use_speaker_boost", true)
                    })
                }

                val request = Request.Builder()
                    .url(url)
                    .addHeader("xi-api-key", apiKey)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Accept", "audio/mpeg")
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val response = httpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    VoiceDiagnostics.report("ElevenLabs HTTP ${response.code} for voice $voiceId")
                    return@withContext false
                }

                val body = response.body ?: return@withContext false
                val tempFile = File.createTempFile("jarvis_tts_", ".mp3", context.cacheDir)
                tempFile.deleteOnExit()

                FileOutputStream(tempFile).use { fos ->
                    body.byteStream().use { input -> input.copyTo(fos) }
                }
                if (tempFile.length() <= 512) return@withContext false   // empty/corrupt

                withContext(Dispatchers.Main) {
                    try {
                        mediaPlayer?.release()
                        mediaPlayer = MediaPlayer().apply {
                            setAudioAttributes(
                                AudioAttributes.Builder()
                                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                                    .build()
                            )
                            setDataSource(tempFile.absolutePath)
                            prepare()
                            start()
                            setOnCompletionListener {
                                try {
                                    VoiceBus.setEngineState(JarvisVisualState.IDLE)
                                    it.release()
                                    tempFile.delete()
                                } catch (_: Exception) {}
                            }
                        }
                        VoiceBus.setEngineState(JarvisVisualState.SPEAKING)
                    } catch (e: Exception) {
                        VoiceDiagnostics.report("MediaPlayer error: ${e.message}")
                        return@withContext false
                    }
                }
                true
            } catch (e: Exception) {
                VoiceDiagnostics.report("ElevenLabs exception for $voiceId: ${e.message}")
                false
            }
        }

    /** Stop current playback immediately (supports barge-in). */
    fun stop() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
            mediaPlayer = null
        } catch (_: Exception) {}
    }

    val isPlaying: Boolean
        get() = try { mediaPlayer?.isPlaying == true } catch (_: Exception) { false }
}
