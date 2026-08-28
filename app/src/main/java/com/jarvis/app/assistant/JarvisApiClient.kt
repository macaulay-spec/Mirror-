package com.jarvis.app.assistant

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class JarvisApiClient(
    private val backendUrl: String = com.jarvis.app.config.ApiConfig.JARVIS_BACKEND_URL,
    private val client: OkHttpClient = OkHttpClient.Builder().readTimeout(60, TimeUnit.SECONDS).build()
) {
    suspend fun chat(
        system: String,
        history: List<Pair<String, String>>,
        user: String,
        provider: String = "gemini",
        model: String = "gemini-2.5-flash"
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val messagesArray = JSONArray()
            history.forEach { (role, text) ->
                messagesArray.put(JSONObject().put("role", if (role == "jarvis") "assistant" else "user").put("content", text))
            }
            messagesArray.put(JSONObject().put("role", "user").put("content", user))

            val payload = JSONObject()
                .put("system", system)
                .put("messages", messagesArray)
                .put("provider", provider)
                .put("model", model)

            val request = Request.Builder()
                .url("\$backendUrl/api/v1/ai/chat")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Backend error: \${response.code}"))
                }
                val json = JSONObject(response.body?.string() ?: "{}")
                if (json.optBoolean("success")) {
                    Result.success(json.optString("message", ""))
                } else {
                    Result.failure(Exception(json.optString("error", "Unknown error")))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
