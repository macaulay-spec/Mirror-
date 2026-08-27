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
        if (apiKey.isBlank() || text.isBlank()) return@withContext false

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
                return@withContext false
            }

            val body = response.body ?: return@withContext false
            val tempFile = File.createTempFile("jarvis_speech_", ".mp3", context.cacheDir)
            tempFile.deleteOnExit()

            FileOutputStream(tempFile).use { fos ->
                body.byteStream().use { input ->
                    input.copyTo(fos)
                }
            }

            if (tempFile.length() <= 0) return@withContext false

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
                                it.release()
                                tempFile.delete()
                            } catch (_: Exception) {}
                        }
                    }
                } catch (_: Exception) {
                    return@withContext false
                }
            }
            return@withContext true
        } catch (_: Exception) {
            return@withContext false
        }
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
