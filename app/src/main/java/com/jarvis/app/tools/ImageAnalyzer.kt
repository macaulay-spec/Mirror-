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
 * Supports Llama 3.2 11B/90B Vision Instruct with local heuristic fallback.
 */
object ImageAnalyzer {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    data class Analysis(
        val width: Int,
        val height: Int,
        val dominantColors: List<Pair<String, Int>>,
        val aiDescription: String? = null
    )

    suspend fun analyzeWithAI(bitmap: Bitmap, prompt: String = "Describe what you see in this image in detail."): String =
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

            if (base64Image != null && ApiConfig.NVIDIA_API_KEY.isNotBlank()) {
                val visionResult = callNvidiaVision(base64Image, prompt)
                if (!visionResult.isNullOrBlank()) return@withContext visionResult
            }

            // Fallback to local heuristic
            val localAnalysis = analyze(bitmap)
            describe(localAnalysis)
        }

    private fun callNvidiaVision(base64Image: String, prompt: String): String? {
        return try {
            val json = JSONObject().apply {
                put("model", "meta/llama-3.2-11b-vision-instruct")
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", JSONArray().apply {
                            put(JSONObject().apply {
                                put("type", "text")
                                put("text", prompt)
                            })
                            put(JSONObject().apply {
                                put("type", "image_url")
                                put("image_url", JSONObject().apply {
                                    put("url", "data:image/jpeg;base64,$base64Image")
                                })
                            })
                        })
                    })
                })
                put("max_tokens", 512)
            }

            val request = Request.Builder()
                .url("${ApiConfig.NVIDIA_BASE_URL}/chat/completions")
                .header("Authorization", "Bearer ${ApiConfig.NVIDIA_API_KEY}")
                .header("Content-Type", "application/json")
                .post(json.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return null
            val bodyStr = response.body?.string() ?: return null
            val respJson = JSONObject(bodyStr)
            val choices = respJson.optJSONArray("choices") ?: return null
            if (choices.length() > 0) {
                choices.getJSONObject(0).optJSONObject("message")?.optString("content")
            } else null
        } catch (_: Exception) {
            null
        }
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
