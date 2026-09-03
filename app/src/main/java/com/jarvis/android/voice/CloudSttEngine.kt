package com.jarvis.android.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Base64
import com.jarvis.app.config.ApiConfig
import com.jarvis.app.voice.VoiceDiagnostics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * CloudSttEngine — cloud speech-to-text via the Vercel AI Gateway
 * (`xai/grok-stt`) through the Rork proxy.
 *
 * AUDIT ADDITION (2026-09-03): device SpeechRecognizer is OEM-dependent and
 * silently unavailable on many devices, which left those users with no voice
 * input at all. This engine records the microphone directly (with a simple
 * VAD so the Orb animates and recordings stay short) and transcribes in the
 * cloud, so STT works wherever the network does.
 */
object CloudSttEngine {

    private const val SAMPLE_RATE = 16_000
    private const val CHUNK_MS = 100L
    private const val MAX_DURATION_MS = 12_000
    private const val SILENCE_THRESHOLD = 1_200   // raw PCM amplitude 0..~32k
    private const val SILENCE_TAIL_MS = 1_400

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Record from the mic with simple voice-activity detection and transcribe
     * through the Gateway. Returns the transcript, or null when recording or
     * transcription failed. [onLevel] reports normalized mic amplitude (0..1)
     * so callers can drive the Orb's listening animation.
     */
    @SuppressLint("MissingPermission") // every caller checks RECORD_AUDIO first
    suspend fun listenAndTranscribe(onLevel: (Float) -> Unit = {}): String? =
        withContext(Dispatchers.IO) {
            val apiKey = ApiConfig.rorkApiKey
            if (apiKey.isBlank()) {
                VoiceDiagnostics.report("Cloud STT unavailable: no toolkit key configured")
                return@withContext null
            }

            val minBuf = AudioRecord.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBuf <= 0) {
                VoiceDiagnostics.report("Cloud STT unavailable: AudioRecord init failed")
                return@withContext null
            }

            val record = try {
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBuf * 4
                )
            } catch (e: Exception) {
                VoiceDiagnostics.report("Cloud STT: could not open microphone (${e.message})")
                return@withContext null
            }

            val chunk = ShortArray((SAMPLE_RATE * CHUNK_MS / 1000).toInt())
            val pcm = ByteArrayOutputStream()
            var speechStarted = false
            var silenceMs = 0L
            var elapsedMs = 0L

            try {
                record.startRecording()
                while (elapsedMs < MAX_DURATION_MS) {
                    val read = record.read(chunk, 0, chunk.size)
                    if (read <= 0) break
                    elapsedMs += CHUNK_MS

                    var sum = 0L
                    for (i in 0 until read) sum += abs(chunk[i].toInt())
                    val amplitude = sum / read
                    onLevel((amplitude / 6_000f).coerceIn(0f, 1f))

                    for (i in 0 until read) {
                        val v = chunk[i].toInt()
                        pcm.write(v and 0xFF)
                        pcm.write((v shr 8) and 0xFF)
                    }

                    if (amplitude > SILENCE_THRESHOLD) {
                        speechStarted = true
                        silenceMs = 0
                    } else if (speechStarted) {
                        silenceMs += CHUNK_MS
                        if (silenceMs >= SILENCE_TAIL_MS) break
                    }
                }
            } catch (e: Exception) {
                VoiceDiagnostics.report("Cloud STT recording failed: ${e.message}")
                return@withContext null
            } finally {
                try { record.stop() } catch (_: Exception) {}
                try { record.release() } catch (_: Exception) {}
            }

            if (!speechStarted) {
                VoiceDiagnostics.report("Cloud STT: no speech detected")
                return@withContext null
            }

            transcribe(pcm.toByteArray(), apiKey)
        }

    /** Send a WAV-encoded recording to the Gateway and return the transcript. */
    private fun transcribe(pcm: ByteArray, apiKey: String): String? {
        val b64 = Base64.encodeToString(wavBytes(pcm), Base64.NO_WRAP)
        val payload = JSONObject()
            .put("audio", b64)
            .put("mediaType", "audio/wav")

        val request = Request.Builder()
            .url("https://toolkit.rork.com/v2/vercel/v4/ai/transcription-model")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .header("ai-model-id", "xai/grok-stt")
            .header("ai-gateway-protocol-version", "0.0.1")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    VoiceDiagnostics.report("Cloud STT HTTP ${response.code}: ${body.take(120)}")
                    return null
                }
                JSONObject(body).optString("text").takeIf { it.isNotBlank() }
            }
        } catch (e: Exception) {
            VoiceDiagnostics.report("Cloud STT error: ${e.message}")
            null
        }
    }

    /** Wrap raw 16-bit mono PCM in a minimal RIFF/WAVE container. */
    private fun wavBytes(pcm: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(44 + pcm.size)
        fun le32(v: Int) {
            out.write(v and 0xFF); out.write((v shr 8) and 0xFF)
            out.write((v shr 16) and 0xFF); out.write((v shr 24) and 0xFF)
        }
        fun le16(v: Int) { out.write(v and 0xFF); out.write((v shr 8) and 0xFF) }

        out.write("RIFF".toByteArray()); le32(pcm.size + 36); out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray()); le32(16)
        le16(1); le16(1)                       // PCM, mono
        le32(SAMPLE_RATE); le32(SAMPLE_RATE * 2)
        le16(2); le16(16)                      // block align, bits per sample
        out.write("data".toByteArray()); le32(pcm.size)
        out.write(pcm)
        return out.toByteArray()
    }
}
