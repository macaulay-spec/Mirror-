package com.jarvis.app.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.jarvis.app.config.ApiConfig
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

object ElevenLabsVoicePlayer {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private var mediaPlayer: MediaPlayer? = null

    /**
     * Attempts to stream and play audio from ElevenLabs HD Voice API.
     * Returns true if audio played successfully, false if failed/rate limited.
     */
    suspend fun speak(context: Context, text: String, voiceId: String = ApiConfig.selectedVoiceId): Boolean = withContext(Dispatchers.IO) {
        val apiKey = ApiConfig.ELEVENLABS_API_KEY
        if (apiKey.isBlank()) {
            VoiceDiagnostics.report("ElevenLabs: no API key. Put ELEVENLABS_API_KEY in local.properties.")
            return@withContext false
        }
        if (text.isBlank()) return@withContext false

        // Walk the British voices until one exists on this account. A wrong voice ID used to
        // mean silence; now it means JARVIS finds another voice and carries on.
        var candidate = voiceId
        var attempts = 0
        while (true) {
            val result = attempt(context, candidate, text)
            if (result.played) return@withContext true
            val next = if (result.voiceMissing) JarvisVoice.fallbackFor(candidate) else null
            if (next == null || attempts >= 4) return@withContext false
            VoiceDiagnostics.report(
                "Voice ${JarvisVoice.labelFor(candidate)} unavailable on this account — trying ${JarvisVoice.labelFor(next)}."
            )
            candidate = next
            attempts++
        }
    }

    private data class Attempt(val played: Boolean, val voiceMissing: Boolean)

    private suspend fun attempt(context: Context, voiceId: String, text: String): Attempt = withContext(Dispatchers.IO) {
        val apiKey = ApiConfig.ELEVENLABS_API_KEY
        try {
            stop()

            val url = "https://api.elevenlabs.io/v1/text-to-speech/$voiceId?output_format=mp3_44100_128"
            val payload = JSONObject().apply {
                put("text", text)
                put("model_id", JarvisVoice.MODEL)
                put("voice_settings", JSONObject().apply {
                    put("stability", JarvisVoice.STABILITY)
                    put("similarity_boost", JarvisVoice.SIMILARITY_BOOST)
                    put("style", JarvisVoice.STYLE)
                    put("use_speaker_boost", JarvisVoice.SPEAKER_BOOST)
                })
            }

            val request = Request.Builder()
                .url(url)
                .addHeader("xi-api-key", apiKey)
                .addHeader("Content-Type", "application/json")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val code = response.code
                val detail = runCatching { response.body?.string()?.take(300) }.getOrNull()
                VoiceDiagnostics.report(
                    "ElevenLabs HTTP $code: ${detail ?: response.message}. " +
                        hintFor(code)
                )
                // 404 = unknown voice, 403 = not allowed on this plan. Both mean "try another".
                return@withContext Attempt(played = false, voiceMissing = code == 404 || code == 403)
            }

            val body = response.body
            if (body == null) {
                VoiceDiagnostics.report("ElevenLabs returned an empty response body.")
                return@withContext Attempt(played = false, voiceMissing = false)
            }
            val tempFile = File.createTempFile("jarvis_speech_", ".mp3", context.cacheDir)
            tempFile.deleteOnExit()

            FileOutputStream(tempFile).use { fos ->
                body.byteStream().use { input ->
                    input.copyTo(fos)
                }
            }

            if (tempFile.length() <= 0L) {
                VoiceDiagnostics.report("ElevenLabs returned an empty audio file.")
                return@withContext Attempt(played = false, voiceMissing = false)
            }

            withContext(Dispatchers.Main) {
                try {
                    mediaPlayer?.release()
                    mediaPlayer = MediaPlayer().apply {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                                .build()
                        )
                        setDataSource(tempFile.absolutePath)
                        prepare()
                        start()
                        setOnCompletionListener {
                            try {
                                com.jarvis.app.voice.VoiceBus.setEngineState(com.jarvis.core.model.JarvisVisualState.IDLE)
                                it.release()
                                tempFile.delete()
                            } catch (_: Exception) {}
                        }
                    }
                } catch (e: Exception) {
                    VoiceDiagnostics.report("Playback failed: ${e.localizedMessage}")
                    return@withContext Attempt(played = false, voiceMissing = false)
                }
            }
            JarvisVoice.rememberWorking(context, voiceId)
            VoiceDiagnostics.success("ElevenLabs (${JarvisVoice.labelFor(voiceId)})")
            return@withContext Attempt(played = true, voiceMissing = false)
        } catch (e: Exception) {
            VoiceDiagnostics.report("ElevenLabs request failed: ${e.localizedMessage}")
            return@withContext Attempt(played = false, voiceMissing = false)
        }
    }

    private fun hintFor(code: Int): String = when (code) {
        401 -> "The key was rejected — revoke it and paste a fresh one into local.properties."
        403 -> "The key is not allowed to use this voice. Try a different voice ID."
        429 -> "Quota exceeded on this ElevenLabs plan."
        404 -> "That voice ID does not exist on this account — trying the next British voice."
        else -> ""
    }

    fun stop() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
            mediaPlayer = null
        } catch (_: Exception) {}
    }
}
