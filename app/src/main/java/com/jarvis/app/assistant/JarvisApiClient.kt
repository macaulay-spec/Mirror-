package com.jarvis.app.assistant

import com.jarvis.agent.ai.ToolSchema
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

data class ToolCallRequest(
    val toolName: String,
    val arguments: Map<String, Any?>,
    val callId: String? = null
)

data class AiResponse(
    val message: String?,
    val toolCalls: List<ToolCallRequest> = emptyList()
)

class JarvisApiClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()
) {
    suspend fun chat(
        systemPrompt: String,
        history: List<Pair<String, String>>,
        userMessage: String,
        provider: String = ApiConfig.activeProvider,
        model: String = when (provider) {
            "openai" -> ApiConfig.OPENAI_MODEL
            "groq" -> ApiConfig.GROQ_MODEL
            "cerebras" -> ApiConfig.CEREBRAS_MODEL
            "mistral" -> ApiConfig.MISTRAL_MODEL
            "cohere" -> ApiConfig.COHERE_MODEL
            "openrouter" -> ApiConfig.OPENROUTER_MODEL
            else -> ApiConfig.GEMINI_MODEL
        }
    ): Result<AiResponse> = withContext(Dispatchers.IO) {
        val apiKey = ApiConfig.activeApiKey
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                Exception("Gemini API key is not configured. Please set your API key in Access Control settings.")
            )
        }

        try {
            if (provider == "gemini") {
                executeGemini(apiKey, model, systemPrompt, history, userMessage)
            } else {
                executeOpenAICompatible(apiKey, provider, model, systemPrompt, history, userMessage)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun executeGemini(
        apiKey: String,
        model: String,
        systemPrompt: String,
        history: List<Pair<String, String>>,
        userMessage: String
    ): Result<AiResponse> {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

        val contents = JSONArray()
        for ((role, text) in history) {
            if (text.isBlank()) continue
            val geminiRole = if (role == "jarvis" || role == "model" || role == "assistant") "model" else "user"
            contents.put(
                JSONObject()
                    .put("role", geminiRole)
                    .put("parts", JSONArray().put(JSONObject().put("text", text)))
            )
        }
        contents.put(
            JSONObject()
                .put("role", "user")
                .put("parts", JSONArray().put(JSONObject().put("text", userMessage)))
        )

        val payload = JSONObject()
            .put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt))))
            .put("contents", contents)
            .put("tools", ToolSchema.forGemini())

        val request = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return client.newCall(request).execute().use { response ->
            val bodyString = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                // If custom key failed with 401/400 and we have a built-in BuildConfig key, attempt fallback
                if ((response.code == 401 || response.code == 400) && apiKey != ApiConfig.GEMINI_API_KEY && ApiConfig.GEMINI_API_KEY.isNotBlank()) {
                    return executeGemini(ApiConfig.GEMINI_API_KEY, model, systemPrompt, history, userMessage)
                }
                val userFriendlyMessage = when (response.code) {
                    401 -> "Gemini API key is unauthorized or invalid (HTTP 401). Please check your Gemini API key in Settings."
                    429 -> "Gemini API rate limit reached. Please wait a moment and try again."
                    else -> "Gemini API error (HTTP ${response.code}): ${bodyString.take(150)}"
                }
                return@use Result.failure(Exception(userFriendlyMessage))
            }

            val json = JSONObject(bodyString)
            val candidates = json.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                return@use Result.success(AiResponse(message = "I am standing by."))
            }

            val firstCandidate = candidates.getJSONObject(0)
            val content = firstCandidate.optJSONObject("content")
            val parts = content?.optJSONArray("parts")

            val toolCalls = mutableListOf<ToolCallRequest>()
            val textBuilder = StringBuilder()

            if (parts != null) {
                for (i in 0 until parts.length()) {
                    val part = parts.getJSONObject(i)
                    if (part.has("text")) {
                        textBuilder.append(part.getString("text"))
                    }
                    if (part.has("functionCall")) {
                        val fn = part.getJSONObject("functionCall")
                        val fnName = fn.getString("name")
                        val fnArgsObj = fn.optJSONObject("args") ?: JSONObject()
                        val argsMap = mutableMapOf<String, Any?>()
                        val keys = fnArgsObj.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            argsMap[k] = fnArgsObj.get(k)
                        }
                        toolCalls.add(ToolCallRequest(toolName = fnName, arguments = argsMap))
                    }
                }
            }

            val text = textBuilder.toString().trim().takeIf { it.isNotBlank() }
            Result.success(AiResponse(message = text, toolCalls = toolCalls))
        }
    }

    private fun executeOpenAICompatible(
        apiKey: String,
        provider: String,
        model: String,
        systemPrompt: String,
        history: List<Pair<String, String>>,
        userMessage: String
    ): Result<AiResponse> {
        val endpoint = when (provider) {
            "groq" -> "https://api.groq.com/openai/v1/chat/completions"
            "cerebras" -> "https://api.cerebras.ai/v1/chat/completions"
            "openrouter" -> "https://openrouter.ai/api/v1/chat/completions"
            "mistral" -> "https://api.mistral.ai/v1/chat/completions"
            else -> "https://api.openai.com/v1/chat/completions"
        }

        val messages = JSONArray()
        messages.put(JSONObject().put("role", "system").put("content", systemPrompt))
        for ((role, text) in history) {
            if (text.isBlank()) continue
            val openAiRole = if (role == "jarvis" || role == "assistant" || role == "model") "assistant" else "user"
            messages.put(JSONObject().put("role", openAiRole).put("content", text))
        }
        messages.put(JSONObject().put("role", "user").put("content", userMessage))

        val payload = JSONObject()
            .put("model", model)
            .put("messages", messages)
            .put("tools", ToolSchema.forOpenAI())

        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer $apiKey")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return client.newCall(request).execute().use { response ->
            val bodyString = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return@use Result.failure(Exception("$provider API error (HTTP ${response.code}): $bodyString"))
            }

            val json = JSONObject(bodyString)
            val choices = json.optJSONArray("choices")
            if (choices == null || choices.length() == 0) {
                return@use Result.success(AiResponse(message = "I am standing by."))
            }

            val choice = choices.getJSONObject(0)
            val message = choice.optJSONObject("message")
            val content = message?.optString("content")?.takeIf { it.isNotBlank() }
            val toolCallsArray = message?.optJSONArray("tool_calls")

            val toolCalls = mutableListOf<ToolCallRequest>()
            if (toolCallsArray != null) {
                for (i in 0 until toolCallsArray.length()) {
                    val tc = toolCallsArray.getJSONObject(i)
                    val fn = tc.optJSONObject("function") ?: continue
                    val fnName = fn.getString("name")
                    val fnArgsStr = fn.optString("arguments", "{}")
                    val fnArgsObj = runCatching { JSONObject(fnArgsStr) }.getOrDefault(JSONObject())
                    val argsMap = mutableMapOf<String, Any?>()
                    val keys = fnArgsObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        argsMap[k] = fnArgsObj.get(k)
                    }
                    toolCalls.add(ToolCallRequest(toolName = fnName, arguments = argsMap, callId = tc.optString("id")))
                }
            }

            Result.success(AiResponse(message = content, toolCalls = toolCalls))
        }
    }
}
