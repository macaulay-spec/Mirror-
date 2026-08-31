package com.jarvis.app.assistant

import com.jarvis.agent.ai.ToolSchema
import com.jarvis.app.config.ApiConfig
import com.jarvis.app.config.BackendConfig
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

/**
 * JARVIS AI Client — routes requests through the backend proxy.
 *
 * When BackendConfig.USE_BACKEND is true (default):
 *   All API calls go through the Cloudflare Worker proxy.
 *   API keys are stored server-side — the app never sees them.
 *
 * When BackendConfig.USE_BACKEND is false:
 *   Direct API calls (for development/testing without a backend).
 *   API keys must be in local.properties → BuildConfig.
 *
 * Supported providers:
 *   xai, gemini, openai, groq, cerebras, openrouter, mistral, anthropic
 */
class JarvisApiClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
) {
    suspend fun chat(
        systemPrompt: String,
        history: List<Pair<String, String>>,
        userMessage: String,
        provider: String = ApiConfig.activeProvider,
        model: String = resolveModel(provider)
    ): Result<AiResponse> = withContext(Dispatchers.IO) {
        if (BackendConfig.USE_BACKEND) {
            chatViaProxy(systemPrompt, history, userMessage, provider, model)
        } else {
            chatDirect(systemPrompt, history, userMessage, provider, model)
        }
    }

    // ─── Backend Proxy Path (production) ───────────────────────────────

    private suspend fun chatViaProxy(
        systemPrompt: String,
        history: List<Pair<String, String>>,
        userMessage: String,
        provider: String,
        model: String
    ): Result<AiResponse> = withContext(Dispatchers.IO) {
        try {
            val messagesArray = JSONArray()
            for ((role, text) in history) {
                if (text.isBlank()) continue
                messagesArray.put(JSONObject().put("role", role).put("content", text))
            }
            messagesArray.put(JSONObject().put("role", "user").put("content", userMessage))

            val payload = JSONObject()
                .put("provider", provider)
                .put("model", model)
                .put("systemPrompt", systemPrompt)
                .put("messages", messagesArray)

            // Add tool schemas if available
            val tools = ToolSchema.forOpenAI()
            if (tools.length() > 0) {
                payload.put("tools", tools)
            }

            val request = Request.Builder()
                .url("${BackendConfig.WORKER_URL}${BackendConfig.LLM_CHAT_ENDPOINT}")
                .header("Content-Type", "application/json")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val msg = when (response.code) {
                        503 -> "AI service not configured on the backend. Check deployment."
                        429 -> "Rate limit reached. Please wait a moment and try again."
                        else -> "Backend error (HTTP ${response.code}): ${bodyString.take(200)}"
                    }
                    return@use Result.failure(Exception(msg))
                }

                val json = JSONObject(bodyString)
                val message = json.optString("message").takeIf { it.isNotBlank() }
                val toolCallsArray = json.optJSONArray("toolCalls")

                val toolCalls = mutableListOf<ToolCallRequest>()
                if (toolCallsArray != null) {
                    for (i in 0 until toolCallsArray.length()) {
                        val tc = toolCallsArray.getJSONObject(i)
                        val argsMap = mutableMapOf<String, Any?>()
                        tc.optJSONObject("arguments")?.let { args ->
                            args.keys().forEach { k -> argsMap[k] = args.get(k) }
                        }
                        toolCalls.add(ToolCallRequest(tc.getString("toolName"), argsMap))
                    }
                }

                Result.success(AiResponse(message = message, toolCalls = toolCalls))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Backend connection failed: ${e.localizedMessage}"))
        }
    }

    // ─── Direct Path (development/testing only) ────────────────────────

    private fun chatDirect(
        systemPrompt: String,
        history: List<Pair<String, String>>,
        userMessage: String,
        provider: String,
        model: String
    ): Result<AiResponse> {
        val apiKey = ApiConfig.activeApiKey
        if (apiKey.isBlank()) {
            return Result.failure(
                Exception("No AI key configured. Add your xAI or Gemini key in Settings → Access Control.")
            )
        }

        return try {
            when (provider) {
                "gemini" -> executeGemini(apiKey, model, systemPrompt, history, userMessage)
                "anthropic" -> executeAnthropic(apiKey, model, systemPrompt, history, userMessage)
                else -> executeOpenAICompatible(apiKey, provider, model, systemPrompt, history, userMessage)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ─── xAI Grok + OpenAI-compatible providers ────────────────────────

    private fun executeOpenAICompatible(
        apiKey: String,
        provider: String,
        model: String,
        systemPrompt: String,
        history: List<Pair<String, String>>,
        userMessage: String
    ): Result<AiResponse> {
        val endpoint = when (provider) {
            "xai"        -> "https://api.x.ai/v1/chat/completions"
            "groq"       -> "https://api.groq.com/openai/v1/chat/completions"
            "cerebras"   -> "https://api.cerebras.ai/v1/chat/completions"
            "openrouter" -> "https://openrouter.ai/api/v1/chat/completions"
            "mistral"    -> "https://api.mistral.ai/v1/chat/completions"
            else         -> "https://api.openai.com/v1/chat/completions"
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

        val tools = ToolSchema.forOpenAI()
        if (tools.length() > 0) {
            payload.put("tools", tools)
            payload.put("tool_choice", "auto")
        }

        val requestBuilder = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer $apiKey")

        if (provider == "openrouter") {
            requestBuilder.header("HTTP-Referer", "https://github.com/macaulay-spec/Mirror-")
            requestBuilder.header("X-Title", "JARVIS")
        }

        val request = requestBuilder
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return client.newCall(request).execute().use { response ->
            val bodyString = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                val msg = when (response.code) {
                    401 -> "$provider API key is invalid or expired (HTTP 401). Check Settings → Access Control."
                    429 -> "$provider rate limit reached. Please wait a moment and try again."
                    else -> "$provider API error (HTTP ${response.code}): ${bodyString.take(200)}"
                }
                return@use Result.failure(Exception(msg))
            }

            val json = JSONObject(bodyString)
            val choices = json.optJSONArray("choices")
            if (choices == null || choices.length() == 0) {
                return@use Result.success(AiResponse(message = "Standing by."))
            }

            val choice = choices.getJSONObject(0)
            val message = choice.optJSONObject("message")
            val content = message?.optString("content")?.takeIf { it.isNotBlank() }
            val toolCallsArray = message?.optJSONArray("tool_calls")

            val toolCalls = parseOpenAIToolCalls(toolCallsArray)
            Result.success(AiResponse(message = content, toolCalls = toolCalls))
        }
    }

    // ─── Google Gemini (native format) ─────────────────────────────────

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
                val msg = when (response.code) {
                    401 -> "Gemini key is invalid (HTTP 401). Check Settings → Access Control."
                    429 -> "Gemini rate limit reached. Try again in a moment."
                    else -> "Gemini error (HTTP ${response.code}): ${bodyString.take(200)}"
                }
                return@use Result.failure(Exception(msg))
            }

            val json = JSONObject(bodyString)
            val candidates = json.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                return@use Result.success(AiResponse(message = "Standing by."))
            }

            val content = candidates.getJSONObject(0).optJSONObject("content")
            val parts = content?.optJSONArray("parts")

            val toolCalls = mutableListOf<ToolCallRequest>()
            val textBuilder = StringBuilder()

            if (parts != null) {
                for (i in 0 until parts.length()) {
                    val part = parts.getJSONObject(i)
                    part.optString("text").takeIf { it.isNotBlank() }?.let { textBuilder.append(it) }
                    part.optJSONObject("functionCall")?.let { fn ->
                        val args = mutableMapOf<String, Any?>()
                        val fnArgs = fn.optJSONObject("args") ?: JSONObject()
                        fnArgs.keys().forEach { k -> args[k] = fnArgs.get(k) }
                        toolCalls.add(ToolCallRequest(fn.getString("name"), args))
                    }
                }
            }

            Result.success(AiResponse(message = textBuilder.toString().trim().takeIf { it.isNotBlank() }, toolCalls = toolCalls))
        }
    }

    // ─── Anthropic Claude (unique format) ─────────────────────────────

    private fun executeAnthropic(
        apiKey: String,
        model: String,
        systemPrompt: String,
        history: List<Pair<String, String>>,
        userMessage: String
    ): Result<AiResponse> {
        val messages = JSONArray()
        for ((role, text) in history) {
            if (text.isBlank()) continue
            val claudeRole = if (role == "jarvis" || role == "assistant") "assistant" else "user"
            messages.put(JSONObject().put("role", claudeRole).put("content", text))
        }
        messages.put(JSONObject().put("role", "user").put("content", userMessage))

        val payload = JSONObject()
            .put("model", model)
            .put("max_tokens", 4096)
            .put("system", systemPrompt)
            .put("messages", messages)

        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return client.newCall(request).execute().use { response ->
            val bodyString = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return@use Result.failure(Exception("Anthropic error (HTTP ${response.code}): ${bodyString.take(200)}"))
            }
            val json = JSONObject(bodyString)
            val content = json.optJSONArray("content")
            val text = if (content != null && content.length() > 0)
                content.getJSONObject(0).optString("text").takeIf { it.isNotBlank() }
            else null
            Result.success(AiResponse(message = text))
        }
    }

    // ─── Helpers ───────────────────────────────────────────────────────

    private fun parseOpenAIToolCalls(toolCallsArray: JSONArray?): List<ToolCallRequest> {
        if (toolCallsArray == null) return emptyList()
        val result = mutableListOf<ToolCallRequest>()
        for (i in 0 until toolCallsArray.length()) {
            val tc = toolCallsArray.getJSONObject(i)
            val fn = tc.optJSONObject("function") ?: continue
            val fnName = fn.getString("name")
            val fnArgsStr = fn.optString("arguments", "{}")
            val fnArgsObj = runCatching { JSONObject(fnArgsStr) }.getOrDefault(JSONObject())
            val argsMap = mutableMapOf<String, Any?>()
            fnArgsObj.keys().forEach { k -> argsMap[k] = fnArgsObj.get(k) }
            result.add(ToolCallRequest(fnName, argsMap, tc.optString("id")))
        }
        return result
    }

    companion object {
        fun resolveModel(provider: String): String = when (provider) {
            "xai"        -> ApiConfig.XAI_MODEL
            "gemini"     -> ApiConfig.GEMINI_MODEL
            "openai"     -> ApiConfig.OPENAI_MODEL
            "groq"       -> ApiConfig.GROQ_MODEL
            "cerebras"   -> ApiConfig.CEREBRAS_MODEL
            "mistral"    -> ApiConfig.MISTRAL_MODEL
            "openrouter" -> ApiConfig.OPENROUTER_MODEL
            "anthropic"  -> ApiConfig.ANTHROPIC_MODEL
            else         -> ApiConfig.XAI_MODEL
        }
    }
}
