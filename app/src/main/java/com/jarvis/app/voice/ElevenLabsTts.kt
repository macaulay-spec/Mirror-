package com.jarvis.app.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import com.jarvis.app.config.ApiConfig
import com.jarvis.app.config.BackendConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
 * ElevenLabs TTS client — supports both backend proxy and direct API mode.
 *
 * Features:
 *   - Voice selection with preview
 *   - Multiple voice categories (premade, cloned, generated)
 *   - Streaming synthesis via backend proxy OR direct ElevenLabs API
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
        private const val TAG = "ElevenLabsTts"
        private const val PREFS_NAME = "jarvis_voice"
        private const val KEY_VOICE_ID = "elevenlabs_voice_id"
        private const val DEFAULT_VOICE_ID = "JBFqnCBsd6RMkjVDRZzb" // George (Classic JARVIS)
    }

    init {
        // Restore saved voice selection.
        //
        // VOICE-UNIFICATION FIX: there used to be TWO independent stores of the
        // selected ElevenLabs voice:
        //   - this class wrote/ read SharedPreferences("jarvis_voice")
        //   - JarvisVoiceEngine -> ElevenLabsVoicePlayer read ApiConfig.selectedVoiceId,
        //     which is SharedPreferences("jarvis_neural_prefs")
        // So picking a voice in Settings never changed what actually spoke.
        // ApiConfig is now the single source of truth: it is loaded once in
        // JarvisApp.onCreate() and used by the live speaking path. We keep the
        // local prefs as a compatibility mirror but seed ApiConfig from it on
        // first run, and every selectVoice() now writes through to ApiConfig too.
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_VOICE_ID, null)
        val apiVoice = ApiConfig.selectedVoiceId
        val resolved = when {
            !saved.isNullOrBlank() && saved != apiVoice -> {
                // Migrate any legacy selection into ApiConfig so the engine uses it.
                ApiConfig.saveVoicePreferences(context, ApiConfig.voiceEngineType, saved)
                saved
            }
            else -> apiVoice
        }
        _selectedVoiceId.value = resolved
    }

    // ── Public API ─────────────────────────────────────────────────────

    /**
     * Fetch available voices from ElevenLabs.
     * Works in both backend proxy and direct mode.
     */
    suspend fun refreshVoices(): Result<List<Voice>> = withContext(Dispatchers.IO) {
        try {
            val request = if (BackendConfig.USE_BACKEND) {
                // Backend proxy mode
                Request.Builder()
                    .url("${BackendConfig.WORKER_URL}${BackendConfig.TTS_VOICES_ENDPOINT}")
                    .get()
                    .build()
            } else {
                // Direct mode — call ElevenLabs API directly
                val apiKey = ApiConfig.ELEVENLABS_API_KEY
                if (apiKey.isBlank()) {
                    return@withContext Result.failure(Exception("ElevenLabs API key not configured"))
                }
                Request.Builder()
                    .url("https://api.elevenlabs.io/v1/voices")
                    .header("xi-api-key", apiKey)
                    .get()
                    .build()
            }

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed to fetch voices: ${response.code} - $body")
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
                            voiceId = v.getString("voice_id"),  // Note: voice_id not voiceId in direct mode
                            name = v.getString("name"),
                            category = v.optString("category", "premade"),
                            description = v.optString("description"),
                            previewUrl = v.optString("preview_url"),  // Note: preview_url not previewUrl
                            gender = labels?.optString("gender"),
                            accent = labels?.optString("accent")
                        )
                    )
                }

                _voices.value = voiceList
                Log.d(TAG, "Loaded ${voiceList.size} voices")
                return@use Result.success(voiceList)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching voices", e)
            Result.failure(e)
        }
    }

    /**
     * Select a voice for synthesis.
     *
     * VOICE-UNIFICATION FIX: writes the selection to BOTH the local mirror
     * prefs (for this screen's own StateFlow) AND to ApiConfig, which is what
     * the actual speaking path (JarvisVoiceEngine -> ElevenLabsVoicePlayer)
     * reads. Without writing through to ApiConfig, picking a voice in Settings
     * had no effect on what Jarvis said.
     */
    fun selectVoice(voiceId: String) {
        _selectedVoiceId.value = voiceId
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_VOICE_ID, voiceId).apply()
        ApiConfig.saveVoicePreferences(context, ApiConfig.voiceEngineType, voiceId)
        Log.d(TAG, "Selected voice: $voiceId (synced to ApiConfig)")
    }

    /**
     * Synthesize text to speech using ElevenLabs.
     * Works in both backend proxy and direct mode.
     * Returns the audio file path on success.
     */
    suspend fun speak(text: String): Result<String> = withContext(Dispatchers.IO) {
        val voiceId = _selectedVoiceId.value ?: DEFAULT_VOICE_ID
        Log.d(TAG, "Synthesizing with voice: $voiceId")

        try {
            val request = if (BackendConfig.USE_BACKEND) {
                // Backend proxy mode
                val payload = JSONObject()
                    .put("text", text)
                    .put("voiceId", voiceId)
                Request.Builder()
                    .url("${BackendConfig.WORKER_URL}${BackendConfig.TTS_SPEAK_ENDPOINT}")
                    .header("Content-Type", "application/json")
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .build()
            } else {
                // Direct mode — call ElevenLabs API directly
                val apiKey = ApiConfig.ELEVENLABS_API_KEY
                if (apiKey.isBlank()) {
                    return@withContext Result.failure(Exception("ElevenLabs API key not configured"))
                }

                val payload = JSONObject()
                    .put("text", text)
                    .put("model_id", "eleven_multilingual_v2")
                    .put("voice_settings", JSONObject()
                        .put("stability", 0.5)
                        .put("similarity_boost", 0.75)
                        .put("style", 0))

                Request.Builder()
                    .url("https://api.elevenlabs.io/v1/text-to-speech/$voiceId")
                    .header("xi-api-key", apiKey)
                    .header("Content-Type", "application/json")
                    .header("Accept", "audio/mpeg")
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .build()
            }

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val err = response.body?.string() ?: ""
                    Log.e(TAG, "TTS failed: ${response.code} - $err")
                    return@use Result.failure(Exception("TTS failed (${response.code}): ${err.take(200)}"))
                }

                // Save the audio to a temp file
                val audioBytes = response.body?.bytes()
                    ?: return@use Result.failure(Exception("Empty audio response"))

                val audioFile = File(context.cacheDir, "jarvis_tts_${System.currentTimeMillis()}.mp3")
                FileOutputStream(audioFile).use { it.write(audioBytes) }

                Log.d(TAG, "TTS audio saved: ${audioFile.absolutePath} (${audioBytes.size} bytes)")
                return@use Result.success(audioFile.absolutePath)
            }
        } catch (e: Exception) {
            Log.e(TAG, "TTS error", e)
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
            setOnErrorListener { _, what, extra ->
                Log.e(TAG, "MediaPlayer error: $what, $extra")
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

        Log.d(TAG, "Previewing voice: ${voice.name} ($previewUrl)")

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
            setOnErrorListener { _, what, extra ->
                Log.e(TAG, "Preview error: $what, $extra")
                _isSpeaking.value = false
                false
            }
            prepareAsync()
            setOnPreparedListener { it.start() }
        }
    }
}
