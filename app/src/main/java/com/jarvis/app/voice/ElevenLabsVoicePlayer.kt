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
    suspend fun speak(context: Context, text: String, voiceId: String = ApiConfig.ELEVENLABS_DEFAULT_VOICE_ID): Boolean = withContext(Dispatchers.IO) {
        val apiKey = ApiConfig.ELEVENLABS_API_KEY
        if (apiKey.isBlank()) {
            VoiceDiagnostics.report("ElevenLabs: no API key. Put ELEVENLABS_API_KEY in local.properties.")
            return@withContext false
        }
        if (text.isBlank()) return@withContext false

        try {
            stop()

            val url = "https://api.elevenlabs.io/v1/text-to-speech/$voiceId?output_format=mp3_44100_128"
            val payload = JSONObject().apply {
                put("text", text)
                put("model_id", "eleven_turbo_v2_5")
                put("voice_settings", JSONObject().apply {
                    put("stability", 0.5)
                    put("similarity_boost", 0.75)
                    put("style", 0.0)
                    put("use_speaker_boost", true)
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
                val detail = runCatching { response.body?.string()?.take(300) }.getOrNull()
                VoiceDiagnostics.report(
                    "ElevenLabs HTTP ${response.code}: ${detail ?: response.message}. " +
                        hintFor(response.code)
                )
                return@withContext false
            }

            val body = response.body
            if (body == null) {
                VoiceDiagnostics.report("ElevenLabs returned an empty response body.")
                return@withContext false
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
                return@withContext false
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
                    return@withContext false
                }
            }
            VoiceDiagnostics.success("ElevenLabs")
            return@withContext true
        } catch (e: Exception) {
            VoiceDiagnostics.report("ElevenLabs request failed: ${e.localizedMessage}")
            return@withContext false
        }
    }

    private fun hintFor(code: Int): String = when (code) {
        401 -> "The key was rejected — revoke it and paste a fresh one into local.properties."
        403 -> "The key is not allowed to use this voice. Try a different voice ID."
        429 -> "Quota exceeded on this ElevenLabs plan."
        404 -> "That voice ID does not exist on this account."
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
