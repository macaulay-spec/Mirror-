package com.jarvis.app.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Base64
import com.jarvis.app.config.BackendConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * ElevenLabs TTS client — routes through the JARVIS backend proxy.
 *
 * Features:
 *   - Voice selection with preview
 *   - Multiple voice categories (premade, cloned, generated)
 *   - Streaming synthesis via backend proxy
 *   - Falls back to Android TTS if backend is unavailable
 */
class ElevenLabsTts(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private var mediaPlayer: MediaPlayer? = null

    // ── Voice state ────────────────────────────────────────────────────

    data class Voice(
        val voiceId: String,
        val name: String,
        val category: String,
        val description: String?,
        val previewUrl: String?,
        val gender: String?,
        val accent: String?
    )

    private val _voices = MutableStateFlow<List<Voice>>(emptyList())
    val voices: StateFlow<List<Voice>> = _voices

    private val _selectedVoiceId = MutableStateFlow<String?>(null)
    val selectedVoiceId: StateFlow<String?> = _selectedVoiceId

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking

    companion object {
        private const val PREFS_NAME = "jarvis_voice"
        private const val KEY_VOICE_ID = "elevenlabs_voice_id"
        private const val DEFAULT_VOICE_ID = "21m00Tcm4TlvDq8ikWAM" // Rachel
    }

    init {
        // Restore saved voice selection
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _selectedVoiceId.value = prefs.getString(KEY_VOICE_ID, DEFAULT_VOICE_ID)
    }

    // ── Public API ─────────────────────────────────────────────────────

    /**
     * Fetch available voices from the backend.
     */
    suspend fun refreshVoices(): Result<List<Voice>> = withContext(Dispatchers.IO) {
        if (!BackendConfig.USE_BACKEND) {
            return@withContext Result.failure(Exception("Backend not configured"))
        }

        try {
            val request = Request.Builder()
                .url("${BackendConfig.WORKER_URL}${BackendConfig.TTS_VOICES_ENDPOINT}")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return@use Result.failure(Exception("Failed to fetch voices: ${response.code}"))
                }

                val json = JSONObject(body)
                val voicesArray = json.optJSONArray("voices") ?: return@use Result.success(emptyList())

                val voiceList = mutableListOf<Voice>()
                for (i in 0 until voicesArray.length()) {
                    val v = voicesArray.getJSONObject(i)
                    val labels = v.optJSONObject("labels")
                    voiceList.add(
                        Voice(
                            voiceId = v.getString("voiceId"),
                            name = v.getString("name"),
                            category = v.optString("category", "premade"),
                            description = v.optString("description"),
                            previewUrl = v.optString("previewUrl"),
                            gender = labels?.optString("gender"),
                            accent = labels?.optString("accent")
                        )
                    )
                }

                _voices.value = voiceList
                return@use Result.success(voiceList)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Select a voice for synthesis.
     */
    fun selectVoice(voiceId: String) {
        _selectedVoiceId.value = voiceId
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_VOICE_ID, voiceId).apply()
    }

    /**
     * Synthesize text to speech using ElevenLabs via the backend proxy.
     * Returns the audio file path on success.
     */
    suspend fun speak(text: String): Result<String> = withContext(Dispatchers.IO) {
        if (!BackendConfig.USE_BACKEND) {
            return@withContext Result.failure(Exception("Backend not configured"))
        }

        val voiceId = _selectedVoiceId.value ?: DEFAULT_VOICE_ID

        try {
            val payload = JSONObject()
                .put("text", text)
                .put("voiceId", voiceId)

            val request = Request.Builder()
                .url("${BackendConfig.WORKER_URL}${BackendConfig.TTS_SPEAK_ENDPOINT}")
                .header("Content-Type", "application/json")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val err = response.body?.string() ?: ""
                    return@use Result.failure(Exception("TTS failed (${response.code}): ${err.take(200)}"))
                }

                // Save the audio to a temp file
                val audioBytes = response.body?.bytes()
                    ?: return@use Result.failure(Exception("Empty audio response"))

                val audioFile = File(context.cacheDir, "jarvis_tts_${System.currentTimeMillis()}.mp3")
                FileOutputStream(audioFile).use { it.write(audioBytes) }

                return@use Result.success(audioFile.absolutePath)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Play audio from a file path.
     */
    fun playAudio(filePath: String) {
        stopPlayback()
        _isSpeaking.value = true

        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            setDataSource(filePath)
            setOnCompletionListener {
                _isSpeaking.value = false
                it.release()
            }
            setOnErrorListener { _, _, _ ->
                _isSpeaking.value = false
                false
            }
            prepare()
            start()
        }
    }

    /**
     * Stop current playback.
     */
    fun stopPlayback() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
        _isSpeaking.value = false
    }

    /**
     * Synthesize and play in one step.
     */
    suspend fun speakAndPlay(text: String): Result<Unit> {
        val result = speak(text)
        return result.fold(
            onSuccess = { filePath ->
                playAudio(filePath)
                Result.success(Unit)
            },
            onFailure = { Result.failure(it) }
        )
    }

    /**
     * Preview a voice by playing its sample audio from ElevenLabs.
     */
    fun previewVoice(voice: Voice) {
        val previewUrl = voice.previewUrl ?: return
        stopPlayback()
        _isSpeaking.value = true

        // Play the preview URL directly
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            setDataSource(previewUrl)
            setOnCompletionListener {
                _isSpeaking.value = false
                it.release()
            }
            prepareAsync()
            setOnPreparedListener { it.start() }
        }
    }
}
