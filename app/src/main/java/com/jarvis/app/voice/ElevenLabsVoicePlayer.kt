package com.jarvis.app.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.jarvis.app.config.ApiConfig
import com.jarvis.core.model.JarvisVisualState
import kotlinx.coroutines.CompletableDeferred
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
import java.util.concurrent.atomic.AtomicInteger

/**
 * ElevenLabsVoicePlayer — HD TTS with a resilient endpoint + voice chain.
 *
 * Endpoint chain (tried in order):
 *   1. Rork proxy (`toolkit.rork.com/v2/elevenlabs/...`) — zero setup, key held
 *      server-side and injected at build time.
 *   2. Direct api.elevenlabs.io — only when the user supplied their own key.
 *
 * Voice chain: the configured voice first, then JarvisVoice's British
 * candidates, so a per-account missing voice ID can never leave JARVIS silent.
 *
 * [speak] suspends until playback completes (or returns false if synthesis
 * failed, telling the caller to fall back to Android TTS). [stop] is safe to
 * call at any time and acts as barge-in: the in-flight [speak] unblocks and
 * any queued sentence is abandoned by the caller.
 */
object ElevenLabsVoicePlayer {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Gateway speech voice for the JARVIS persona. xAI voices: eve, ara, rex,
     * sal, leo — rex is the deep male pick for the British-assistant persona.
     */
    private const val GATEWAY_VOICE = "rex"

    /**
     * Cloud-only voice chain (2026-09-05): the managed gateway IS the voice of
     * JARVIS. ElevenLabs is disabled entirely — its key expired and the owner
     * decided the assistant must always speak with the cloud voice, never
     * ElevenLabs. Kept as a flag so the code path is documented, not deleted.
     */
    private const val ELEVENLABS_ENABLED = false

    private var mediaPlayer: MediaPlayer? = null

    @Volatile
    private var playbackDone: CompletableDeferred<Boolean>? = null

    /** Cooldown timestamp if ElevenLabs returned quota/auth error (401/402/403). */
    @Volatile
    private var quotaExceededUntil: Long = 0L

    /** Cooldown timestamp if the Rork gateway returned auth/quota errors. */
    @Volatile
    private var gatewayCooldownUntil: Long = 0L

    fun resetCooldown() {
        quotaExceededUntil = 0L
        gatewayCooldownUntil = 0L
    }

    /** Incremented on every new play/stop; stale completions are ignored. */
    private val generation = AtomicInteger(0)

    private data class Endpoint(
        val label: String,
        val url: (String) -> String,
        val auth: (Request.Builder) -> Request.Builder
    )

    private fun endpoints(): List<Endpoint> = buildList {
        if (ApiConfig.ELEVENLABS_API_KEY.isNotBlank()) {
            add(
                Endpoint(
                    label = "direct",
                    url = { voiceId -> "https://api.elevenlabs.io/v1/text-to-speech/$voiceId" },
                    auth = { builder -> builder.header("xi-api-key", ApiConfig.ELEVENLABS_API_KEY) }
                )
            )
        }
    }

    private fun voiceOrder(configured: String): List<String> = buildList {
        add(configured)
        com.jarvis.app.voice.JarvisVoice.BRITISH_CANDIDATES.forEach { candidate ->
            if (candidate.id != configured && !contains(candidate.id)) add(candidate.id)
        }
    }

    /**
     * Speak [text] and suspend until playback finishes.
     * Returns true when audio played (or was barged in mid-playback),
     * false when synthesis failed on every endpoint/voice — the caller
     * should then fall back to Android TTS.
     */
    suspend fun speak(context: Context, text: String, voiceId: String = ApiConfig.selectedVoiceId): Boolean =
        withContext(Dispatchers.IO) {
            if (text.isBlank()) return@withContext false

            if (System.currentTimeMillis() < quotaExceededUntil) {
                // Cooldown active after quota error; fall back directly to device TTS without delay
                return@withContext false
            }

            // Barge-in FIRST: stop() bumps the generation counter, so myGen must
            // be captured AFTER it. The old order (capture, then stop) made the
            // staleness check below fire on every call and silently skipped the
            // entire cloud chain — JARVIS never spoke a word.
            stop()
            val myGen = generation.incrementAndGet()

            // PRIMARY VOICE (2026-09-05): the Rork managed gateway — xAI's 'rex',
            // a deep British male. Zero user setup: the key is injected at build
            // time. Tried FIRST so JARVIS's signature voice is the default, not
            // the last resort before the robot voice.
            if (myGen != generation.get()) return@withContext true
            val gatewayOutcome = tryGatewaySpeech(context, text, myGen)
            if (gatewayOutcome == true) return@withContext true

            // ElevenLabs branch disabled (see ELEVENLABS_ENABLED) — cloud TTS only.
            if (ELEVENLABS_ENABLED) for (endpoint in endpoints()) {
                for (vid in voiceOrder(voiceId)) {
                    if (System.currentTimeMillis() < quotaExceededUntil) break
                    if (myGen != generation.get()) return@withContext true  // superseded by a newer utterance
                    val outcome = tryPlay(context, text, vid, endpoint, myGen)
                    if (outcome != null) {
                        if (outcome) VoiceDiagnostics.success("ElevenLabs ${endpoint.label} voice: $vid")
                        return@withContext outcome
                    }
                }
            }

            VoiceDiagnostics.report("Voice chain exhausted — falling back to Android TTS")
            return@withContext false
        }

    /** Stop current playback immediately (barge-in). Safe from any thread. */
    fun stop() {
        generation.incrementAndGet()
        try {
            mediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        } catch (_: Exception) {}
        mediaPlayer = null
        // Unblock a waiting speak() — treat as played so the caller does not
        // fall back to TTS over the top of a user-initiated interruption.
        playbackDone?.complete(true)
        playbackDone = null
    }

    val isPlaying: Boolean
        get() = try { mediaPlayer?.isPlaying == true } catch (_: Exception) { false }

    /**
     * Download + play one utterance. Returns null when the request failed and
     * the next endpoint/voice should be tried; otherwise the playback outcome.
     */
    private suspend fun tryPlay(
        context: Context,
        text: String,
        voiceId: String,
        endpoint: Endpoint,
        myGen: Int
    ): Boolean? = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject()
                .put("text", text)
                .put("model_id", JarvisVoice.MODEL)
                .put("voice_settings", JSONObject().apply {
                    put("stability", JarvisVoice.STABILITY)
                    put("similarity_boost", JarvisVoice.SIMILARITY_BOOST)
                    put("style", JarvisVoice.STYLE)
                    put("use_speaker_boost", JarvisVoice.SPEAKER_BOOST)
                })

            val requestBuilder = endpoint.auth(
                Request.Builder()
                    .url(endpoint.url(voiceId))
                    .header("Content-Type", "application/json")
                    .header("Accept", "audio/mpeg")
            )

            val request = requestBuilder
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val code = response.code
                    VoiceDiagnostics.report("ElevenLabs ${endpoint.label} HTTP $code for voice $voiceId")
                    if (code in 401..403) {
                        quotaExceededUntil = System.currentTimeMillis() + 60_000L
                        VoiceDiagnostics.report("ElevenLabs quota/auth error ($code). Instant fallback to device voice.")
                    }
                    return@use null
                }

                val body = response.body ?: return@use null
                val tempFile = File.createTempFile("jarvis_tts_", ".mp3", context.cacheDir)
                tempFile.deleteOnExit()

                FileOutputStream(tempFile).use { fos ->
                    body.byteStream().use { input -> input.copyTo(fos) }
                }
                if (tempFile.length() <= 512) {
                    tempFile.delete()
                    return@use null  // empty/corrupt
                }

                startPlayback(tempFile, myGen)
            }
        } catch (e: Exception) {
            VoiceDiagnostics.report("ElevenLabs ${endpoint.label} error for $voiceId: ${e.message}")
            null
        }
    }

    /**
     * Vercel AI Gateway speech via the Rork proxy. Unlike ElevenLabs this
     * returns JSON with base64 audio. Returns null when the request failed
     * and the caller should fall back further; otherwise the playback outcome.
     */
    private data class GatewayVoice(val modelId: String, val voice: String)

    /**
     * Managed gateway voices, tried in order. 'rex' is the JARVIS persona
     * (deep, British, male); openai/tts-1 'onyx' is the deep-male fallback
     * from the legacy TTS voice set.
     */
    private val OPENAI_VOICES = setOf("onyx", "nova", "alloy", "echo", "fable", "shimmer")

    /**
     * Managed gateway voices: the user's selected cloud voice first, then the
     * JARVIS persona (rex), then the deep-male fallback — so a missing voice
     * can never leave JARVIS silent.
     */
    private fun gatewayVoices(): List<GatewayVoice> {
        val selected = ApiConfig.selectedVoiceId
        val model = if (selected.lowercase() in OPENAI_VOICES) "openai/tts-1" else "xai/grok-tts"
        return listOf(
            GatewayVoice(model, selected),
            GatewayVoice("xai/grok-tts", GATEWAY_VOICE),
            GatewayVoice("openai/tts-1", "onyx")
        ).distinctBy { "${it.modelId}:${it.voice}" }
    }

    /**
     * Vercel AI Gateway speech via the Rork Toolkit proxy. Returns JSON with
     * base64 audio (not streamed binary). Returns null when every attempt
     * failed and the caller should fall back further; otherwise the playback
     * outcome. Auth-cooldowns after 401/402/403 so a missing key never delays
     * the next utterance.
     */
    private suspend fun tryGatewaySpeech(
        context: Context,
        text: String,
        myGen: Int
    ): Boolean? = withContext(Dispatchers.IO) {
        val key = ApiConfig.TOOLKIT_SECRET_KEY
        if (key.isBlank()) return@withContext null  // gateway not provisioned in this build
        if (System.currentTimeMillis() < gatewayCooldownUntil) return@withContext null

        for (gv in gatewayVoices()) {
            if (myGen != generation.get()) return@withContext true  // superseded
            try {
                val payload = JSONObject()
                    .put("text", text)
                    .put("voice", gv.voice)
                    .put("outputFormat", "mp3")

                val request = Request.Builder()
                    .url("${ApiConfig.TOOLKIT_URL}/v2/vercel/v4/ai/speech-model")
                    .header("Authorization", "Bearer $key")
                    .header("Content-Type", "application/json")
                    .header("ai-model-id", gv.modelId)
                    .header("ai-gateway-protocol-version", "0.0.1")
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val outcome = httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        VoiceDiagnostics.report("Gateway speech ${gv.modelId} HTTP ${response.code}")
                        if (response.code in 401..403) {
                            // 5 minute cooldown: a dead/absent key must not add
                            // latency to every utterance.
                            gatewayCooldownUntil = System.currentTimeMillis() + 300_000L
                        }
                        return@use null
                    }

                    val body = response.body?.string() ?: return@use null
                    val audioB64 = JSONObject(body).optString("audio")
                    if (audioB64.isBlank()) return@use null

                    val audio = try {
                        android.util.Base64.decode(audioB64, android.util.Base64.DEFAULT)
                    } catch (_: Exception) {
                        return@use null
                    }
                    if (audio.size <= 512) return@use null  // empty/corrupt

                    val tempFile = File.createTempFile("jarvis_tts_", ".mp3", context.cacheDir)
                    tempFile.deleteOnExit()
                    FileOutputStream(tempFile).use { it.write(audio) }

                    startPlayback(tempFile, myGen)
                }

                if (outcome != null) {
                    if (outcome) VoiceDiagnostics.success("Cloud speech ${gv.modelId} voice ${gv.voice}")
                    return@withContext outcome
                }
            } catch (e: Exception) {
                VoiceDiagnostics.report("Gateway speech ${gv.modelId} error: ${e.message}")
            }
        }
        null
    }

    private suspend fun startPlayback(tempFile: File, myGen: Int): Boolean? =
        withContext(Dispatchers.Main) {
            if (myGen != generation.get()) {
                tempFile.delete()
                return@withContext true  // superseded by a newer utterance
            }

            val done = CompletableDeferred<Boolean>()
            playbackDone = done
            try { mediaPlayer?.release() } catch (_: Exception) {}

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .build()
                )
                setDataSource(tempFile.absolutePath)
                setOnCompletionListener {
                    try { it.release(); tempFile.delete() } catch (_: Exception) {}
                    done.complete(true)
                    if (myGen == generation.get()) {
                        VoiceBus.setEngineState(JarvisVisualState.IDLE)
                    }
                }
                setOnErrorListener { mp, what, extra ->
                    VoiceDiagnostics.report("MediaPlayer error: $what/$extra")
                    // Release the dead player and clean the temp file — leaving
                    // either behind stalls every later playback attempt.
                    try { mp.release() } catch (_: Exception) {}
                    if (mediaPlayer === mp) mediaPlayer = null
                    tempFile.delete()
                    done.complete(false)
                    true
                }
                try {
                    prepare()
                    start()
                } catch (e: Exception) {
                    VoiceDiagnostics.report("MediaPlayer prepare failed: ${e.message}")
                    try { release() } catch (_: Exception) {}
                    mediaPlayer = null
                    tempFile.delete()
                    return@withContext null
                }
            }

            VoiceBus.setEngineState(JarvisVisualState.SPEAKING)
            done.await()
        }
}
