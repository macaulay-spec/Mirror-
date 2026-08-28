package com.jarvis.agent.ai

import com.jarvis.app.config.ApiConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ProviderRouter(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
) {
    data class ProviderStatus(
        val provider: String,
        val model: String,
        val ok: Boolean,
        val latencyMs: Long,
        val message: String
    )

    suspend fun diagnostics(): List<ProviderStatus> = withContext(Dispatchers.IO) {
        val results = mutableListOf<ProviderStatus>()

        // 1. Google Gemini
        val geminiKey = ApiConfig.GEMINI_API_KEY
        if (geminiKey.isNotBlank()) {
            val start = System.currentTimeMillis()
            try {
                val url = "https://generativelanguage.googleapis.com/v1beta/models/${ApiConfig.GEMINI_MODEL}:generateContent?key=$geminiKey"
                val body = JSONObject()
                    .put(
                        "contents",
                        JSONArray().put(
                            JSONObject().put(
                                "parts",
                                JSONArray().put(JSONObject().put("text", "ping"))
                            )
                        )
                    )
                    .toString()
                    .toRequestBody("application/json".toMediaType())
                val req = Request.Builder().url(url).post(body).build()
                client.newCall(req).execute().use { resp ->
                    val latency = System.currentTimeMillis() - start
                    if (resp.isSuccessful) {
                        results.add(
                            ProviderStatus("Google Gemini", ApiConfig.GEMINI_MODEL, true, latency, "Online & responsive")
                        )
                    } else {
                        val bodyText = resp.body?.string() ?: ""
                        results.add(
                            ProviderStatus("Google Gemini", ApiConfig.GEMINI_MODEL, false, latency, "HTTP ${resp.code}: $bodyText")
                        )
                    }
                }
            } catch (e: Exception) {
                results.add(
                    ProviderStatus("Google Gemini", ApiConfig.GEMINI_MODEL, false, System.currentTimeMillis() - start, e.message ?: "Connection error")
                )
            }
        } else {
            results.add(
                ProviderStatus("Google Gemini", ApiConfig.GEMINI_MODEL, false, 0, "No API key configured (add GEMINI_API_KEY to local.properties)")
            )
        }

        // 2. Custom Provider / Backend
        if (ApiConfig.hasCustomKey) {
            results.add(
                ProviderStatus(ApiConfig.getProviderLabel(), "Custom Model", true, 0, "Custom API key configured")
            )
        }

        results
    }
}
