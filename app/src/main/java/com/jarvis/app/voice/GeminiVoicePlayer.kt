package com.jarvis.app.voice

import android.content.Context
import android.media.MediaPlayer
import com.jarvis.app.config.ApiConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Gemini-powered neural text-to-speech: synthesises speech with a Gemini TTS model
 * and plays the returned 24 kHz mono PCM through [MediaPlayer].
 *
 * REWRITTEN (audit P0-C). The previous version wrapped the HTTP request, the base64
 * decode, the file write, `MediaPlayer.prepare()` AND the entire playback wait inside
 * a single `withTimeoutOrNull(2500L)`. Any utterance longer than roughly two seconds
 * of audio could not finish in that budget, so:
 *
 *   1. `speak()` returned false even though audio was already playing,
 *   2. [JarvisVoiceEngine.speakOne] treated that as "cloud voice failed" and spoke the
 *      SAME text again through Android TTS,
 *   3. the orphaned MediaPlayer was never stopped (it plays on its own thread and is
 *      invisible to coroutine cancellation),
 *
 * which produced two voices talking over each other. Separately,
 * `tempFile.deleteOnExit()` never fires on Android (there is no JVM exit), so every
 * spoken sentence leaked a WAV into cacheDir forever.
 *
 * Fixes applied here:
 *  - synthesis and playback have SEPARATE budgets, and playback is bounded by the
 *    player's own completion callback rather than a wall-clock guess;
 *  - cancellation stops and releases the MediaPlayer (`invokeOnCancellation`);
 *  - the temporary WAV is deleted in a `finally`, always;
 *  - completion is signalled through a [CompletableDeferred] instead of a plain `var`
 *    mutated from a MediaPlayer callback thread (which had no visibility guarantee);
 *  - the model list contains only models that actually support the AUDIO response
 *    modality. `gemini-2.0-flash` was tried first and cannot synthesise speech at
 *    all, so every request burned its budget on a guaranteed failure before reaching
 *    a real TTS model. Current TTS-capable models, newest first:
 *    `gemini-3.1-flash-tts-preview` (free tier), `gemini-2.5-flash-preview-tts`,
 *    `gemini-2.5-pro-preview-tts`.
 *  - the API key travels in the `x-goog-api-key` header rather than the URL query
 *    string, so it cannot land in proxy logs, crash reports or `toString()` output;
 *  - HTTP timeouts are realistic for speech synthesis instead of 2s/3s.
 *
 * Returns true only when the audio was synthesised AND actually played to completion,
 * so the caller's Android-TTS fallback fires exactly when it should and never twice.
 */
object GeminiVoicePlayer {

    /** Synthesis budget: network + model time, excluding playback. */
    private const val SYNTHESIS_TIMEOUT_MS = 12_000L

    /**
     * Playback ceiling. Normal completion is driven by the player callback; this only
     * bounds a stuck player so the speech queue can never wedge forever.
     */
    private const val PLAYBACK_TIMEOUT_MS = 90_000L

    /** TTS models, tried in order. All support `responseModalities: ["AUDIO"]`. */
    private val TTS_MODELS = listOf(
        "gemini-3.1-flash-tts-preview",
        "gemini-2.5-flash-preview-tts",
        "gemini-2.5-pro-preview-tts"
    )

    private const val GENERATE_CONTENT =
        "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent"

    /** Gemini TTS returns base64-encoded signed 16-bit PCM at 24 kHz mono. */
    private const val PCM_SAMPLE_RATE = 24_000

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var mediaPlayer: MediaPlayer? = null

    /**
     * Signals the in-flight playback. Held at object scope so [stop] can complete it
     * immediately -- otherwise a barge-in would have to wait out [PLAYBACK_TIMEOUT_MS]
     * before the speech queue could move on.
     */
    @Volatile
    private var playbackDone: CompletableDeferred<Boolean>? = null

    private val generation = AtomicInteger(0)

    val isPlaying: Boolean
        get() = try {
            mediaPlayer?.isPlaying == true
        } catch (_: Exception) {
            false
        }

    /** Aborts playback immediately. Bumps [generation] so in-flight work self-cancels. */
    fun stop() {
        generation.incrementAndGet()
        // Wake the awaiting coroutine first, then tear the player down.
        playbackDone?.complete(false)
        releasePlayer()
    }

    private fun releasePlayer() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        } catch (_: Exception) {
        }
        mediaPlayer = null
    }

    /**
     * Synthesises and plays [text]. Returns true only on full, uninterrupted playback.
     */
    suspend fun speak(context: Context, text: String, targetVoice: String? = null): Boolean =
        withContext(Dispatchers.IO) {
            if (text.isBlank()) return@withContext false
            val key = ApiConfig.currentGeminiKey
            if (key.isBlank()) return@withContext false

            // Barge-in: drop anything already playing before we start.
            stop()
            val myGen = generation.get()
            val voiceName = resolveVoice(targetVoice ?: ApiConfig.selectedVoiceId)

            // ── 1. Synthesis, bounded on its own ────────────────────────────────
            val wav: ByteArray? = withTimeoutOrNull(SYNTHESIS_TIMEOUT_MS) {
                synthesize(text, voiceName, key, myGen)
            }
            if (wav == null || myGen != generation.get()) return@withContext false

            // ── 2. Playback, bounded by the player's own completion callback ────
            playToCompletion(context, wav, myGen)
        }

    /** Maps a JARVIS voice preset onto a real Gemini prebuilt voice name. */
    private fun resolveVoice(selected: String): String = when (selected.lowercase()) {
        "charon", "rex", "deep", "male" -> "Charon"
        "fenrir", "smooth" -> "Fenrir"
        "kore", "calm" -> "Kore"
        "puck", "bright" -> "Puck"
        "aoede", "eve", "eva", "female" -> "Aoede"
        // Gemini ships 30 prebuilt voices; these are real ones worth exposing later.
        "zephyr" -> "Zephyr"
        "leda" -> "Leda"
        "orus" -> "Orus"
        "enceladus" -> "Enceladus"
        "sulafat" -> "Sulafat"
        else -> "Aoede"
    }

    /**
     * Tries each TTS model in order and returns a playable WAV, or null. A 429 rotates
     * the Gemini key pool once and retries the same model before moving on.
     */
    private fun synthesize(
        text: String,
        voiceName: String,
        initialKey: String,
        myGen: Int
    ): ByteArray? {
        var key = initialKey
        val payload = buildPayload(text, voiceName)

        for (model in TTS_MODELS) {
            if (myGen != generation.get()) return null

            var response = post(model, payload, key)
            if (response?.code == 429) {
                response.close()
                ApiConfig.markGeminiKeyRateLimited(key)
                val nextKey = ApiConfig.currentGeminiKey
                if (nextKey.isNotBlank() && nextKey != key) {
                    key = nextKey
                    response = post(model, payload, key)
                }
            }

            val audio = response?.use { resp ->
                if (!resp.isSuccessful) return@use null
                extractPcm(resp.body?.string() ?: return@use null)
            } ?: continue

            if (myGen != generation.get()) return null
            return ensureWav(audio, PCM_SAMPLE_RATE, channels = 1)
        }
        return null
    }

    private fun post(model: String, payload: String, key: String) = try {
        httpClient.newCall(
            Request.Builder()
                .url(GENERATE_CONTENT.format(model))
                .header("x-goog-api-key", key)
                .header("Content-Type", "application/json")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()
        ).execute()
    } catch (_: Exception) {
        null
    }

    private fun buildPayload(text: String, voiceName: String): String = JSONObject().apply {
        put(
            "contents", JSONArray().put(
                JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().put(JSONObject().put("text", "Speak this aloud: $text")))
                }
            )
        )
        put(
            "generationConfig", JSONObject().apply {
                put("responseModalities", JSONArray().put("AUDIO"))
                put(
                    "speechConfig", JSONObject().put(
                        "voiceConfig", JSONObject().put(
                            "prebuiltVoiceConfig", JSONObject().put("voiceName", voiceName)
                        )
                    )
                )
            }
        )
    }.toString()

    /** Pulls the base64 PCM blob out of a generateContent response. */
    private fun extractPcm(body: String): ByteArray? {
        val parts = JSONObject(body)
            .optJSONArray("candidates")?.optJSONObject(0)
            ?.optJSONObject("content")?.optJSONArray("parts") ?: return null

        for (i in 0 until parts.length()) {
            val inline = parts.optJSONObject(i)?.optJSONObject("inlineData") ?: continue
            val b64 = inline.optString("data")
            if (b64.isBlank()) continue
            val raw = try {
                android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
            } catch (_: Exception) {
                continue
            }
            // Reject stub/empty payloads so the caller falls through to the next model.
            if (raw.size > 256) return raw
        }
        return null
    }

    /**
     * Plays [wav] and suspends until it finishes. Always deletes the temporary file and
     * always releases the player, including on cancellation.
     */
    private suspend fun playToCompletion(context: Context, wav: ByteArray, myGen: Int): Boolean {
        var tempFile: File? = null
        val finished = CompletableDeferred<Boolean>()
        return try {
            tempFile = File.createTempFile("gemini_voice_", ".wav", context.cacheDir)
            tempFile.writeBytes(wav)
            if (myGen != generation.get()) return false

            playbackDone = finished
            val player = MediaPlayer()
            mediaPlayer = player

            player.setOnCompletionListener { finished.complete(true) }
            player.setOnErrorListener { _, _, _ -> finished.complete(false) }

            try {
                player.setDataSource(tempFile.absolutePath)
                player.prepare()
                if (myGen == generation.get()) player.start() else finished.complete(false)
            } catch (_: Exception) {
                finished.complete(false)
            }

            // Returns as soon as the player finishes, errors, or stop() is called.
            val outcome = withTimeoutOrNull(PLAYBACK_TIMEOUT_MS) { finished.await() } ?: false
            outcome && myGen == generation.get()
        } catch (_: Exception) {
            false
        } finally {
            if (playbackDone === finished) playbackDone = null
            if (myGen == generation.get()) releasePlayer()
            // deleteOnExit() never runs on Android -- remove the file explicitly.
            runCatching { tempFile?.delete() }
        }
    }

    /**
     * Wraps raw 16-bit PCM in a RIFF/WAVE container so [MediaPlayer] can decode it.
     * Returns [data] unchanged when it already carries a RIFF header.
     */
    private fun ensureWav(data: ByteArray, sampleRate: Int, channels: Int): ByteArray {
        if (data.size >= 4 &&
            data[0] == 'R'.code.toByte() && data[1] == 'I'.code.toByte() &&
            data[2] == 'F'.code.toByte() && data[3] == 'F'.code.toByte()
        ) {
            return data
        }

        val totalDataLen = data.size + 36
        val byteRate = sampleRate * channels * 2
        val header = ByteArray(44)

        fun le32(v: Int, at: Int) {
            header[at] = (v and 0xff).toByte()
            header[at + 1] = ((v shr 8) and 0xff).toByte()
            header[at + 2] = ((v shr 16) and 0xff).toByte()
            header[at + 3] = ((v shr 24) and 0xff).toByte()
        }

        fun le16(v: Int, at: Int) {
            header[at] = (v and 0xff).toByte()
            header[at + 1] = ((v shr 8) and 0xff).toByte()
        }

        "RIFF".forEachIndexed { i, c -> header[i] = c.code.toByte() }
        le32(totalDataLen, 4)
        "WAVE".forEachIndexed { i, c -> header[8 + i] = c.code.toByte() }
        "fmt ".forEachIndexed { i, c -> header[12 + i] = c.code.toByte() }
        le32(16, 16)                 // fmt chunk size
        le16(1, 20)                  // PCM
        le16(channels, 22)
        le32(sampleRate, 24)
        le32(byteRate, 28)
        le16(channels * 2, 32)       // block align
        le16(16, 34)                 // bits per sample
        "data".forEachIndexed { i, c -> header[36 + i] = c.code.toByte() }
        le32(data.size, 40)

        return header + data
    }
}
