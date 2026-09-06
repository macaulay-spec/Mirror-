package com.jarvis.app.tools

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
 * JARVIS Multimodal Vision & Image Analysis Engine.
 * Powered by Google Gemini Vision with automatic key failover and local heuristic fallback.
 */
object ImageAnalyzer {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    data class Analysis(
        val width: Int,
        val height: Int,
        val dominantColors: List<Pair<String, Int>>,
        val aiDescription: String? = null
    )

    suspend fun analyzeWithAI(bitmap: Bitmap, prompt: String = "Describe what you see in this image or phone screen in detail. If text or UI elements are visible, extract the key information."): String =
        withContext(Dispatchers.IO) {
            // Compress bitmap to JPEG Base64
            val base64Image = runCatching {
                val outputStream = ByteArrayOutputStream()
                val scaled = if (bitmap.width > 1280 || bitmap.height > 1280) {
                    val ratio = 1280f / maxOf(bitmap.width, bitmap.height)
                    Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true)
                } else bitmap
                scaled.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
                Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
            }.getOrNull()

            val geminiKey = ApiConfig.currentGeminiKey
            if (base64Image != null && geminiKey.isNotBlank()) {
                val visionResult = callGeminiVision(base64Image, prompt, geminiKey)
                if (!visionResult.isNullOrBlank()) return@withContext visionResult
            }

            // Fallback to local heuristic
            val localAnalysis = analyze(bitmap)
            describe(localAnalysis)
        }

    private fun callGeminiVision(base64Image: String, prompt: String, initialKey: String): String? {
        var key = initialKey
        for (attempt in 0..1) {
            try {
                val parts = JSONArray().apply {
                    put(JSONObject().apply {
                        put("text", prompt)
                    })
                    put(JSONObject().apply {
                        put("inlineData", JSONObject().apply {
                            put("mimeType", "image/jpeg")
                            put("data", base64Image)
                        })
                    })
                }
                val content = JSONObject().apply {
                    put("role", "user")
                    put("parts", parts)
                }
                val payload = JSONObject().apply {
                    put("contents", JSONArray().put(content))
                    put("generationConfig", JSONObject().apply {
                        put("temperature", 0.4)
                        put("maxOutputTokens", 1024)
                    })
                }

                val request = Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$key")
                    .header("Content-Type", "application/json")
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val response = httpClient.newCall(request).execute()
                if (response.code == 429) {
                    ApiConfig.markGeminiKeyRateLimited(key)
                    val next = ApiConfig.currentGeminiKey
                    if (next.isNotBlank() && next != key) {
                        key = next
                        continue
                    }
                }
                if (!response.isSuccessful) return null
                val bodyStr = response.body?.string() ?: return null
                val respJson = JSONObject(bodyStr)
                val candidates = respJson.optJSONArray("candidates") ?: return null
                if (candidates.length() > 0) {
                    val candidateParts = candidates.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts")
                    if (candidateParts != null && candidateParts.length() > 0) {
                        val text = candidateParts.getJSONObject(0).optString("text")
                        if (text.isNotBlank()) return text
                    }
                }
            } catch (_: Exception) {
            }
        }
        return null
    }

    fun analyze(bitmap: Bitmap): Analysis? {
        if (bitmap.width <= 0 || bitmap.height <= 0) return null
        val colors = HashMap<Int, Long>()
        val step = maxOf(1, (bitmap.width * bitmap.height / 8000))
        var count = 0
        for (y in 0 until bitmap.height step step) {
            for (x in 0 until bitmap.width step step) {
                val c = bitmap.getPixel(x, y)
                val q = 0xFF000000.toInt() or ((c and 0xFF0000) shr 16 and 0xF8 shl 16) or
                    ((c and 0xFF00) shr 8 and 0xF8 shl 8) or ((c and 0xFF) and 0xF8)
                colors[q] = (colors[q] ?: 0L) + 1
                count++
            }
        }
        val top = colors.entries.sortedByDescending { it.value }.take(3)
        val list = top.map { (color, freq) ->
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF
            val pct = (freq * 100 / maxOf(1, count))
            val hexc = "#" + String.format("%02X%02X%02X", r, g, b)
            hexc to pct.toInt()
        }
        return Analysis(bitmap.width, bitmap.height, list)
    }

    fun describe(analysis: Analysis?): String {
        if (analysis == null) return "I couldn't read that image."
        if (!analysis.aiDescription.isNullOrBlank()) return analysis.aiDescription
        val colorsText = analysis.dominantColors.joinToString(", ") { "${it.first} (~${it.second}%)" }
        return "Image captured (${analysis.width}×${analysis.height} px). Dominant palette: $colorsText."
    }
}
