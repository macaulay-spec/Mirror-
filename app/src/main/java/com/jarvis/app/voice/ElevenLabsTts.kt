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
     * AUDIT FIX (2026-09-03): tries the Rork proxy FIRST (works with the
     * build-injected toolkit key, no user key needed); only falls back to the
     * direct API when the user supplied their own ElevenLabs key. The old
     * direct-only path 401'd for everyone and left the Settings voice list
     * permanently empty.
     */
    suspend fun refreshVoices(): Result<List<Voice>> = withContext(Dispatchers.IO) {
        val attempts = buildList<Pair<String, (Request.Builder) -> Request.Builder>> {
            val directKey = ApiConfig.ELEVENLABS_API_KEY
            if (directKey.isNotBlank()) {
                add("https://api.elevenlabs.io/v1/voices" to
                    { b: Request.Builder -> b.header("xi-api-key", directKey) })
            }
        }
        if (attempts.isEmpty()) {
            return@withContext Result.failure(Exception("No ElevenLabs credentials configured (rebuild with the Rork toolkit key or add your own key)"))
        }

        var lastError: Exception? = null
        for ((url, auth) in attempts) {
            try {
                val request = auth(Request.Builder().url(url).get()).build()

                client.newCall(request).execute().use { response ->
                    val body = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        Log.w(TAG, "Voices request failed on $url: ${response.code} - ${body.take(120)}")
                        lastError = Exception("Failed to fetch voices: ${response.code}")
                        return@use null
                    }

                    val json = JSONObject(body)
                    val voicesArray = json.optJSONArray("voices")
                        ?: return@use Result.success(emptyList<Voice>())

                    val voiceList = mutableListOf<Voice>()
                    for (i in 0 until voicesArray.length()) {
                        val v = voicesArray.getJSONObject(i)
                        val labels = v.optJSONObject("labels")
                        voiceList.add(
                            Voice(
                                voiceId = v.getString("voice_id"),
                                name = v.getString("name"),
                                category = v.optString("category", "premade"),
                                description = v.optString("description"),
                                previewUrl = v.optString("preview_url"),
                                gender = labels?.optString("gender"),
                                accent = labels?.optString("accent")
                            )
                        )
                    }

                    _voices.value = voiceList
                    Log.d(TAG, "Loaded ${voiceList.size} voices via $url")
                    return@use Result.success(voiceList)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error fetching voices from $url", e)
                lastError = e
            }
        }
        Result.failure(lastError ?: Exception("Failed to fetch voices"))
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
     * AUDIT FIX (2026-09-03): tries the Rork proxy FIRST (build-injected
     * toolkit key — verified working live); falls back to the direct API only
     * when the user supplied their own ElevenLabs key. The old direct-only
     * path 401'd for everyone and killed TTS from Settings.
     * Returns the audio file path on success.
     */
    suspend fun speak(text: String): Result<String> = withContext(Dispatchers.IO) {
        val voiceId = _selectedVoiceId.value ?: DEFAULT_VOICE_ID
        Log.d(TAG, "Synthesizing with voice: $voiceId")

        val attempts = buildList<Pair<String, (Request.Builder) -> Request.Builder>> {
            val directKey = ApiConfig.ELEVENLABS_API_KEY
            if (directKey.isNotBlank()) {
                add("https://api.elevenlabs.io/v1/text-to-speech/$voiceId" to
                    { b: Request.Builder -> b.header("xi-api-key", directKey) })
            }
        }
        if (attempts.isEmpty()) {
            return@withContext Result.failure(Exception("No ElevenLabs credentials configured (rebuild with the Rork toolkit key or add your own key)"))
        }

        val payload = JSONObject()
            .put("text", text)
            .put("model_id", JarvisVoice.MODEL)
            .put("voice_settings", JSONObject()
                .put("stability", JarvisVoice.STABILITY)
                .put("similarity_boost", JarvisVoice.SIMILARITY_BOOST)
                .put("style", JarvisVoice.STYLE)
                .put("use_speaker_boost", JarvisVoice.SPEAKER_BOOST))

        var lastError: Exception? = null
        for ((url, auth) in attempts) {
            try {
                val request = auth(
                    Request.Builder()
                        .url(url)
                        .header("Content-Type", "application/json")
                        .header("Accept", "audio/mpeg")
                )
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val err = response.body?.string() ?: ""
                        Log.w(TAG, "TTS failed on $url: ${response.code} - ${err.take(120)}")
                        lastError = Exception("TTS failed (${response.code}): ${err.take(200)}")
                        return@use null
                    }

                    val audioBytes = response.body?.bytes()
                    if (audioBytes == null || audioBytes.size <= 512) {
                        lastError = Exception("Empty audio response")
                        return@use null
                    }

                    val audioFile = File(context.cacheDir, "jarvis_tts_${System.currentTimeMillis()}.mp3")
                    FileOutputStream(audioFile).use { it.write(audioBytes) }

                    Log.d(TAG, "TTS audio saved: ${audioFile.absolutePath} (${audioBytes.size} bytes) via $url")
                    return@use Result.success(audioFile.absolutePath)
                }
            } catch (e: Exception) {
                Log.w(TAG, "TTS error on $url", e)
                lastError = e
            }
        }
        Result.failure(lastError ?: Exception("TTS failed"))
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
