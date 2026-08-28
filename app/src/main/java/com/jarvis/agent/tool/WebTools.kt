package com.jarvis.agent.tool

import android.content.Context
import com.jarvis.core.model.RiskLevel
import com.jarvis.core.model.ToolExecutionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object WebTools {
    private val client = OkHttpClient()
    private val backendUrl = com.jarvis.app.config.ApiConfig.JARVIS_BACKEND_URL

    suspend fun search(query: String): ToolExecutionResult = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().put("query", query)
            val request = Request.Builder()
                .url("$backendUrl/api/v1/web/search")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext ToolExecutionResult("web_search", false, null, "HTTP Error: ${response.code}")
                val json = JSONObject(response.body?.string() ?: "{}")
                if (json.optBoolean("success")) {
                    ToolExecutionResult("web_search", true, mapOf("results" to json.optJSONArray("results").toString()), verificationDetails = "Found results for $query")
                } else {
                    ToolExecutionResult("web_search", false, null, json.optString("error", "Unknown backend error"))
                }
            }
        } catch (e: Exception) {
            ToolExecutionResult("web_search", false, null, e.message)
        }
    }

    suspend fun open(url: String): ToolExecutionResult = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().put("url", url)
            val request = Request.Builder()
                .url("$backendUrl/api/v1/web/open")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext ToolExecutionResult("web_open", false, null, "HTTP Error: ${response.code}")
                val json = JSONObject(response.body?.string() ?: "{}")
                if (json.optBoolean("success")) {
                    ToolExecutionResult("web_open", true, mapOf("content" to json.optString("content")), verificationDetails = "Content extracted from $url")
                } else {
                    ToolExecutionResult("web_open", false, null, json.optString("error", "Unknown backend error"))
                }
            }
        } catch (e: Exception) {
            ToolExecutionResult("web_open", false, null, e.message)
        }
    }
    
    fun register(registry: ToolRegistry) {
        registry.register(
            ToolDefinition(
                id = "web_extract",
                name = "Web Extract / Open URL",
                description = "Extracts text content from a web page URL. Use this to read articles, docs, or web content. Pass 'url' parameter.",
                category = "WEB",
                riskLevel = RiskLevel.LEVEL_1
            ) { _, args ->
                val url = args["url"]?.toString() ?: ""
                if (url.isBlank()) ToolExecutionResult("web_extract", false, null, "URL required.")
                else open(url)
            }
        )
    }
}
