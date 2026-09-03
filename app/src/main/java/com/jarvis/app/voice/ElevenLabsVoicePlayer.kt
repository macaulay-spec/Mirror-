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

    private var mediaPlayer: MediaPlayer? = null

    @Volatile
    private var playbackDone: CompletableDeferred<Boolean>? = null

    /** Incremented on every new play/stop; stale completions are ignored. */
    private val generation = AtomicInteger(0)

    private data class Endpoint(
        val label: String,
        val url: (String) -> String,
        val auth: (Request.Builder) -> Request.Builder
    )

    private fun endpoints(): List<Endpoint> = buildList {
        add(
            Endpoint(
                label = "proxy",
                url = { voiceId -> "https://toolkit.rork.com/v2/elevenlabs/v1/text-to-speech/$voiceId" },
                auth = { builder -> builder.header("Authorization", "Bearer ${ApiConfig.rorkApiKey}") }
            )
        )
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

            val myGen = generation.incrementAndGet()
            stop()  // barge-in: stop any current playback before starting

            for (endpoint in endpoints()) {
                for (vid in voiceOrder(voiceId)) {
                    if (myGen != generation.get()) return@withContext true  // superseded by a newer utterance
                    val outcome = tryPlay(context, text, vid, endpoint, myGen)
                    if (outcome != null) {
                        if (outcome) VoiceDiagnostics.success("ElevenLabs ${endpoint.label} voice: $vid")
                        return@withContext outcome
                    }
                }
            }

            VoiceDiagnostics.report("ElevenLabs: all endpoints and voices failed — falling back to Android TTS")
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
                    VoiceDiagnostics.report("ElevenLabs ${endpoint.label} HTTP ${response.code} for voice $voiceId")
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
                setOnErrorListener { _, what, extra ->
                    VoiceDiagnostics.report("MediaPlayer error: $what/$extra")
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
