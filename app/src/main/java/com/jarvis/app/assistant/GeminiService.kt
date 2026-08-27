package com.jarvis.app.assistant

import android.graphics.Bitmap
import android.util.Base64
import com.jarvis.app.config.ApiConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * Service layer interfacing with Google Gemini API using OkHttp.
 *
 * Provides:
 *  - Conversational AI responses with multi-turn context and system instructions.
 *  - Voice & audio query processing (audio understanding and speech transcription).
 *  - Multimodal image + voice/text prompt analysis.
 *  - Streaming conversational token generation.
 */
class GeminiService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
) {

    companion object {
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    /**
     * Generates a conversational AI response for multi-turn chat with optional system instruction.
     */
    suspend fun generateChatResponse(
        systemInstruction: String? = null,
        history: List<Pair<String, String>> = emptyList(),
        userMessage: String,
        temperature: Float = 0.7f,
        model: String = ApiConfig.GEMINI_MODEL,
        apiKey: String = ApiConfig.GEMINI_API_KEY
    ): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Gemini API key is not configured."))
        }

        try {
            val contents = JSONArray()
            history.forEach { (role, text) ->
                val apiRole = if (role.equals("jarvis", ignoreCase = true) || role.equals("model", ignoreCase = true)) "model" else "user"
                contents.put(
                    JSONObject().apply {
                        put("role", apiRole)
                        put("parts", JSONArray().put(JSONObject().put("text", text)))
                    }
                )
            }
            // Append current user message
            contents.put(
                JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().put(JSONObject().put("text", userMessage)))
                }
            )

            val payload = JSONObject().apply {
                put("contents", contents)
                systemInstruction?.takeIf { it.isNotBlank() }?.let { sys ->
                    put("systemInstruction", JSONObject().apply {
                        put("parts", JSONArray().put(JSONObject().put("text", sys)))
                    })
                }
                put("generationConfig", JSONObject().apply {
                    put("temperature", temperature.toDouble())
                    put("topP", 0.95)
                })
            }

            val request = Request.Builder()
                .url("$BASE_URL/$model:generateContent?key=$apiKey")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val errorMsg = parseErrorMessage(bodyString, response.code)
                    return@withContext Result.failure(RuntimeException(errorMsg))
                }
                val text = extractFirstCandidateText(bodyString)
                    ?: return@withContext Result.failure(RuntimeException("No valid text response from Gemini."))
                Result.success(text)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Processes voice/audio data (e.g. recorded PCM / WAV / MP3 / AAC bytes)
     * and generates an understanding / transcription or conversational reply.
     */
    suspend fun processVoiceAudio(
        audioBytes: ByteArray,
        mimeType: String = "audio/mp3",
        prompt: String = "Transcribe the user's speech accurately, understand their intent, and respond helpfully.",
        systemInstruction: String? = null,
        model: String = ApiConfig.GEMINI_MODEL,
        apiKey: String = ApiConfig.GEMINI_API_KEY
    ): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Gemini API key is not configured."))
        }

        try {
            val base64Audio = Base64.encodeToString(audioBytes, Base64.NO_WRAP)
            val parts = JSONArray().apply {
                put(
                    JSONObject().apply {
                        put("inlineData", JSONObject().apply {
                            put("mimeType", mimeType)
                            put("data", base64Audio)
                        })
                    }
                )
                put(JSONObject().put("text", prompt))
            }

            val contents = JSONArray().put(JSONObject().put("parts", parts))
            val payload = JSONObject().apply {
                put("contents", contents)
                systemInstruction?.takeIf { it.isNotBlank() }?.let { sys ->
                    put("systemInstruction", JSONObject().apply {
                        put("parts", JSONArray().put(JSONObject().put("text", sys)))
                    })
                }
            }

            val request = Request.Builder()
                .url("$BASE_URL/$model:generateContent?key=$apiKey")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val errorMsg = parseErrorMessage(bodyString, response.code)
                    return@withContext Result.failure(RuntimeException(errorMsg))
                }
                val text = extractFirstCandidateText(bodyString)
                    ?: return@withContext Result.failure(RuntimeException("No response returned for audio."))
                Result.success(text)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Processes an image along with a user prompt (multimodal vision analysis).
     */
    suspend fun processImage(
        bitmap: Bitmap,
        prompt: String,
        systemInstruction: String? = null,
        model: String = ApiConfig.GEMINI_MODEL,
        apiKey: String = ApiConfig.GEMINI_API_KEY
    ): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Gemini API key is not configured."))
        }

        try {
            val bos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, bos)
            val base64Image = Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)

            val parts = JSONArray().apply {
                put(
                    JSONObject().apply {
                        put("inlineData", JSONObject().apply {
                            put("mimeType", "image/jpeg")
                            put("data", base64Image)
                        })
                    }
                )
                put(JSONObject().put("text", prompt))
            }

            val contents = JSONArray().put(JSONObject().put("parts", parts))
            val payload = JSONObject().apply {
                put("contents", contents)
                systemInstruction?.takeIf { it.isNotBlank() }?.let { sys ->
                    put("systemInstruction", JSONObject().apply {
                        put("parts", JSONArray().put(JSONObject().put("text", sys)))
                    })
                }
            }

            val request = Request.Builder()
                .url("$BASE_URL/$model:generateContent?key=$apiKey")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val errorMsg = parseErrorMessage(bodyString, response.code)
                    return@withContext Result.failure(RuntimeException(errorMsg))
                }
                val text = extractFirstCandidateText(bodyString)
                    ?: return@withContext Result.failure(RuntimeException("No response returned for image."))
                Result.success(text)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Streams conversational responses chunk by chunk using OkHttp.
     */
    suspend fun streamChatResponse(
        systemInstruction: String? = null,
        userMessage: String,
        onChunk: (String) -> Unit,
        model: String = ApiConfig.GEMINI_MODEL,
        apiKey: String = ApiConfig.GEMINI_API_KEY
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Gemini API key is not configured."))
        }

        try {
            val contents = JSONArray().put(
                JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().put(JSONObject().put("text", userMessage)))
                }
            )

            val payload = JSONObject().apply {
                put("contents", contents)
                systemInstruction?.takeIf { it.isNotBlank() }?.let { sys ->
                    put("systemInstruction", JSONObject().apply {
                        put("parts", JSONArray().put(JSONObject().put("text", sys)))
                    })
                }
            }

            val request = Request.Builder()
                .url("$BASE_URL/$model:streamGenerateContent?key=$apiKey")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorMsg = parseErrorMessage(response.body?.string() ?: "", response.code)
                    return@withContext Result.failure(RuntimeException(errorMsg))
                }

                val source = response.body?.byteStream() ?: return@withContext Result.failure(RuntimeException("Empty response body"))
                source.bufferedReader().useLines { lines ->
                    for (line in lines) {
                        val clean = line.trim()
                        if (clean.isBlank() || clean.startsWith("[") || clean.startsWith("]") || clean == ",") continue
                        val jsonText = if (clean.endsWith(",")) clean.dropLast(1) else clean
                        try {
                            val chunkObj = JSONObject(jsonText)
                            val chunkText = extractFirstCandidateText(chunkObj.toString())
                            if (!chunkText.isNullOrBlank()) {
                                onChunk(chunkText)
                            }
                        } catch (_: Exception) {
                            // Ignored partial formatting lines
                        }
                    }
                }
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractFirstCandidateText(jsonString: String): String? {
        return try {
            val root = JSONObject(jsonString)
            val candidates = root.optJSONArray("candidates") ?: return null
            if (candidates.length() == 0) return null
            val firstCandidate = candidates.getJSONObject(0)
            val content = firstCandidate.optJSONObject("content") ?: return null
            val parts = content.optJSONArray("parts") ?: return null
            val sb = StringBuilder()
            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                if (part.has("text")) {
                    sb.append(part.getString("text"))
                }
            }
            sb.toString().takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseErrorMessage(jsonString: String, statusCode: Int): String {
        return try {
            val root = JSONObject(jsonString)
            val error = root.optJSONObject("error")
            val message = error?.optString("message") ?: ""
            if (statusCode == 429 || message.contains("quota", ignoreCase = true) || message.contains("RESOURCE_EXHAUSTED", ignoreCase = true)) {
                "JARVIS neural bandwidth limit reached (Code 429). Switching to alternate core."
            } else if (statusCode == 401 || statusCode == 403) {
                "JARVIS neural authentication key invalid or expired (Code $statusCode)."
            } else if (message.isNotBlank()) {
                "JARVIS neural core error ($statusCode): $message"
            } else {
                "JARVIS neural core status: HTTP $statusCode"
            }
        } catch (_: Exception) {
            "JARVIS neural core communication anomaly (Code $statusCode)"
        }
    }
}
