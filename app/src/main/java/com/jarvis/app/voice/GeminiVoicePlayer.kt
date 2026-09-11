package com.jarvis.app.voice

import android.content.Context
import android.media.MediaPlayer
import com.jarvis.app.config.ApiConfig
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
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Gemini-powered Neural Text-to-Speech audio synthesizer & player.
 * Directly streams prebuilt voices (Aoede, Charon, Fenrir, Kore, Puck)
 * with robust WAV conversion for seamless Android MediaPlayer playback.
 */
object GeminiVoicePlayer {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private var mediaPlayer: MediaPlayer? = null
    private val generation = AtomicInteger(0)

    val isPlaying: Boolean
        get() = mediaPlayer?.isPlaying == true

    fun stop() {
        generation.incrementAndGet()
        try {
            mediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        } catch (_: Exception) {}
        mediaPlayer = null
    }

    suspend fun speak(context: Context, text: String, targetVoice: String? = null): Boolean = withContext(Dispatchers.IO) {
        val result = withTimeoutOrNull(20000L) {
            if (text.isBlank()) return@withTimeoutOrNull false
            var key = ApiConfig.currentGeminiKey
            if (key.isBlank()) return@withTimeoutOrNull false

            stop()
            val myGen = generation.get()

            // Map voice ID to recognized Gemini prebuilt voice name
            val voiceChoice = targetVoice ?: ApiConfig.selectedVoiceId
            val geminiVoiceName = when (voiceChoice.lowercase()) {
                "charon", "rex", "deep", "male" -> "Charon"
                "fenrir", "smooth" -> "Fenrir"
                "kore", "calm" -> "Kore"
                "puck", "bright" -> "Puck"
                "aoede", "eve", "eva", "female" -> "Aoede"
                else -> "Aoede"
            }

            val modelsToTry = listOf("gemini-2.5-flash", "gemini-2.0-flash")

            for (modelName in modelsToTry) {
                if (myGen != generation.get()) return@withTimeoutOrNull false
                try {
                    val payload = JSONObject().apply {
                        put("contents", JSONArray().put(
                            JSONObject().apply {
                                put("role", "user")
                                put("parts", JSONArray().put(JSONObject().put("text", "Speak this aloud: $text")))
                            }
                        ))
                        put("generationConfig", JSONObject().apply {
                            put("responseModalities", JSONArray().put("AUDIO"))
                            put("speechConfig", JSONObject().put("voiceConfig", JSONObject().put("prebuiltVoiceConfig", JSONObject().put("voiceName", geminiVoiceName))))
                        })
                    }

                    fun buildReq(k: String): Request {
                        val builder = Request.Builder()
                            .post(payload.toString().toRequestBody("application/json".toMediaType()))
                        if (k.startsWith("AIzaSy")) {
                            builder.url("https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$k")
                        } else {
                            builder.url("https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent")
                            if (k.isNotBlank()) {
                                builder.header("x-goog-api-key", k)
                            }
                        }
                        return builder.build()
                    }

                    var request = buildReq(key)
                    var response = httpClient.newCall(request).execute()
                    if (response.code == 429) {
                        ApiConfig.markGeminiKeyRateLimited(key)
                        val nextKey = ApiConfig.currentGeminiKey
                        if (nextKey.isNotBlank() && nextKey != key) {
                            key = nextKey
                            request = buildReq(key)
                            response = httpClient.newCall(request).execute()
                        }
                    }

                    if (!response.isSuccessful) {
                        continue // Try next model fallback
                    }

                    val body = response.body?.string() ?: continue
                    val obj = JSONObject(body)
                    val candidates = obj.optJSONArray("candidates") ?: continue
                    if (candidates.length() == 0) continue
                    val parts = candidates.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts") ?: continue

                    var audioB64: String? = null
                    for (i in 0 until parts.length()) {
                        val inlineData = parts.getJSONObject(i).optJSONObject("inlineData")
                        if (inlineData != null) {
                            audioB64 = inlineData.optString("data")
                            break
                        }
                    }
                    if (audioB64.isNullOrBlank()) continue

                    val rawAudio = android.util.Base64.decode(audioB64, android.util.Base64.DEFAULT)
                    if (rawAudio.size <= 256) continue

                    // Guarantee playback on Android MediaPlayer by framing with standard WAV header if raw PCM
                    val playableWav = ensureWav(rawAudio, sampleRate = 24000, channels = 1)

                    val tempFile = File.createTempFile("gemini_voice_", ".wav", context.cacheDir)
                    tempFile.deleteOnExit()
                    FileOutputStream(tempFile).use { it.write(playableWav) }

                    if (myGen != generation.get()) return@withTimeoutOrNull false

                    val player = MediaPlayer().apply {
                        setDataSource(tempFile.absolutePath)
                        prepare()
                        start()
                    }
                    mediaPlayer = player

                    var isDone = false
                    player.setOnCompletionListener {
                        runCatching { it.release() }
                        isDone = true
                    }
                    player.setOnErrorListener { mp, _, _ ->
                        runCatching { mp.release() }
                        isDone = true
                        true
                    }

                    while (!isDone && myGen == generation.get()) {
                        kotlinx.coroutines.delay(100)
                    }
                    return@withTimeoutOrNull true
                } catch (_: Exception) {
                    // Try next model fallback
                }
            }
            return@withTimeoutOrNull false
        }
        return@withContext (result == true)
    }

    /**
     * Converts raw 16-bit PCM bytes into a valid RIFF/WAVE file format
     * so that Android's MediaPlayer can decode and play it without error.
     */
    private fun ensureWav(data: ByteArray, sampleRate: Int = 24000, channels: Int = 1): ByteArray {
        // If already has RIFF header, return as-is
        if (data.size >= 4 && data[0] == 'R'.code.toByte() && data[1] == 'I'.code.toByte() && data[2] == 'F'.code.toByte() && data[3] == 'F'.code.toByte()) {
            return data
        }

        val totalDataLen = data.size + 36
        val byteRate = sampleRate * channels * 2
        val header = ByteArray(44)

        // RIFF header
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()

        // 'fmt ' chunk
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1 // PCM format
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = (channels * 2).toByte()
        header[33] = 0
        header[34] = 16 // 16-bit PCM
        header[35] = 0

        // 'data' chunk
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (data.size and 0xff).toByte()
        header[41] = ((data.size shr 8) and 0xff).toByte()
        header[42] = ((data.size shr 16) and 0xff).toByte()
        header[43] = ((data.size shr 24) and 0xff).toByte()

        val wavBytes = ByteArray(44 + data.size)
        System.arraycopy(header, 0, wavBytes, 0, 44)
        System.arraycopy(data, 0, wavBytes, 44, data.size)
        return wavBytes
    }
}
