package com.jarvis.app.assistant

import android.util.Log
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

/**
 * JARVIS AI Client — NVIDIA NIM and Google Gemini over one OpenAI-compatible
 * transport, with automatic provider fallback.
 *
 * CHANGED (owner decision, 2026-09-07): the Convex backend proxy has been removed
 * entirely. It was never deployed — `USE_BACKEND` was false and `WORKER_URL` was
 * still the `https://YOUR_DEPLOYMENT.convex.site` placeholder — so `chatViaProxy`
 * and the three preference/voice endpoints were unreachable code that would have
 * failed with a DNS error against a host that does not exist. All inference is now
 * direct to the provider.
 *
 * Fallback chain (see ApiConfig.PROVIDER_FALLBACK_CHAIN):
 *   gemini_flash -> gemini_pro -> gemini_lite -> nvidia_super -> nvidia_nano -> nvidia_ultra
 */
class JarvisApiClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build(),
    private val streamClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()
) {
    suspend fun chat(
        systemPrompt: String,
        history: List<Pair<String, String>>,
        userMessage: String,
        provider: String = ApiConfig.activeProvider,
        model: String = ApiConfig.resolveModel(provider),
        allowTools: Boolean = true
    ): Result<AiResponse> = withContext(Dispatchers.IO) {
        chatDirect(systemPrompt, history, userMessage, provider, model, allowTools)
    }

    // Real-time streaming (SSE) with automatic provider fallback
    suspend fun chatStream(
        systemPrompt: String,
        history: List<Pair<String, String>>,
        userMessage: String,
        provider: String = ApiConfig.activeProvider,
        model: String = ApiConfig.resolveModel(provider),
        allowTools: Boolean = true,
        onDelta: (String) -> Unit
    ): Result<AiResponse> = withContext(Dispatchers.IO) {
        var emitted = false
        var triedProviders = mutableListOf<String>()
        val track: (String) -> Unit = { d -> emitted = true; onDelta(d) }

        suspend fun fallbackBlocking(): Result<AiResponse> {
            val blocked = chat(systemPrompt, history, userMessage, provider, model, allowTools)
            blocked.getOrNull()?.message?.takeIf { it.isNotBlank() }?.let(track)
            return blocked
        }

        suspend fun tryStreamWithProvider(providerToTry: String): Result<AiResponse> {
            val currentModel = ApiConfig.resolveModel(providerToTry)
            val currentApiKey = when {
                providerToTry.startsWith("gemini") -> ApiConfig.currentGeminiKey
                providerToTry.startsWith("nvidia") -> ApiConfig.NVIDIA_API_KEY
                else -> ApiConfig.activeApiKey
            }

            if (currentApiKey.isBlank()) {
                return Result.failure(Exception("No API key for $providerToTry"))
            }

            triedProviders.add(providerToTry)

            val streamed = streamNVIDIA(
                currentApiKey, providerToTry, currentModel,
                systemPrompt, history, userMessage, allowTools, track
            )

            return streamed
        }

        // Try providers in fallback chain order
        var currentProvider: String? = provider
        var lastResult: Result<AiResponse> = Result.failure(Exception("No providers available"))

        while (currentProvider != null) {
            lastResult = tryStreamWithProvider(currentProvider)
            if (lastResult.isSuccess || emitted) {
                Log.i("JarvisApiClient", "Provider $currentProvider finished (emitted=$emitted)")
                return@withContext lastResult
            }

            Log.w("JarvisApiClient", "Provider $currentProvider failed without emitting, trying next")
            currentProvider = ApiConfig.getNextProvider(currentProvider)
        }

        // If we get here, all providers failed
        if (!emitted) fallbackBlocking() else lastResult
    }

    // NVIDIA streaming (OpenAI-compatible endpoint)
    private fun streamNVIDIA(
        apiKey: String,
        provider: String,
        model: String,
        systemPrompt: String,
        history: List<Pair<String, String>>,
        userMessage: String,
        allowTools: Boolean = true,
        onDelta: (String) -> Unit
    ): Result<AiResponse> {
        val endpoint = if (model.startsWith("gemini")) "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions" else "${ApiConfig.NVIDIA_BASE_URL}/chat/completions"

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
            .put("stream", true)
        applyInferenceControls(payload, provider, model)

        if (allowTools) {
            val tools = ToolSchema.forOpenAI()
            if (tools.length() > 0) {
                payload.put("tools", tools)
                payload.put("tool_choice", "auto")
            }
        }

        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer $apiKey")
            .header("Accept", "text/event-stream")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return try {
            streamClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    if (response.code == 429 && (provider == "gemini_flash" || model.startsWith("gemini"))) {
                        Log.w("JarvisApiClient", "Gemini 429 quota reached. Rotating key in pool...")
                        ApiConfig.markGeminiKeyRateLimited(apiKey)
                        val nextKey = ApiConfig.currentGeminiKey
                        if (nextKey.isNotBlank() && nextKey != apiKey) {
                            return streamNVIDIA(nextKey, provider, model, systemPrompt, history, userMessage, allowTools, onDelta)
                        }
                    }
                    val err = response.body?.string()?.take(200) ?: ""
                    return Result.failure(Exception("AI stream error (HTTP ${response.code}): $err"))
                }
                val source = response.body?.source()
                    ?: return Result.failure(Exception("NVIDIA stream returned an empty body"))

                val text = StringBuilder()
                val toolNames = HashMap<Int, String>()
                val toolArgs = HashMap<Int, StringBuilder>()
                val toolIds = HashMap<Int, String>()

                while (true) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val data = line.substring(5).trim()
                    if (data.isEmpty()) continue
                    if (data == "[DONE]") break
                    val obj = try { JSONObject(data) } catch (_: Exception) { continue }
                    val choices = obj.optJSONArray("choices") ?: continue
                    if (choices.length() == 0) continue
                    val delta = choices.getJSONObject(0).optJSONObject("delta") ?: continue

                    delta.optString("content").takeIf { it.isNotEmpty() }?.let {
                        text.append(it)
                        onDelta(it)
                    }

                    delta.optJSONArray("tool_calls")?.let { tcs ->
                        for (i in 0 until tcs.length()) {
                            val tc = tcs.getJSONObject(i)
                            val idx = tc.optInt("index", 0)
                            tc.optString("id").takeIf { it.isNotBlank() }?.let { toolIds[idx] = it }
                            tc.optJSONObject("function")?.let { fn ->
                                fn.optString("name").takeIf { it.isNotBlank() }?.let { toolNames[idx] = it }
                                fn.optString("arguments").takeIf { it.isNotEmpty() }?.let { frag ->
                                    toolArgs.getOrPut(idx) { StringBuilder() }.append(frag)
                                }
                            }
                        }
                    }
                }

                val toolCalls = toolNames.keys.sorted().map { idx ->
                    val argsStr = toolArgs[idx]?.toString() ?: "{}"
                    val argsObj = runCatching { JSONObject(argsStr) }.getOrDefault(JSONObject())
                    val argsMap = mutableMapOf<String, Any?>()
                    argsObj.keys().forEach { k -> argsMap[k] = argsObj.get(k) }
                    ToolCallRequest(toolNames[idx] ?: "unknown", argsMap, toolIds[idx])
                }

                Result.success(
                    AiResponse(
                        message = text.toString().trim().takeIf { it.isNotBlank() },
                        toolCalls = toolCalls
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(Exception("NVIDIA stream failed: ${e.localizedMessage}"))
        }
    }

    // Direct Path (development/testing only)
    private fun chatDirect(
        systemPrompt: String,
        history: List<Pair<String, String>>,
        userMessage: String,
        provider: String,
        model: String,
        allowTools: Boolean = true
    ): Result<AiResponse> {
        val apiKey = when {
            provider.startsWith("gemini") -> ApiConfig.currentGeminiKey
            provider.startsWith("nvidia") -> ApiConfig.NVIDIA_API_KEY
            else -> ApiConfig.activeApiKey
        }

        if (apiKey.isBlank()) {
            return Result.failure(
                Exception("No AI key configured for $provider. Please configure keys in Settings.")
            )
        }

        return try {
            when (provider) {
                else -> executeNVIDIA(apiKey, provider, model, systemPrompt, history, userMessage, allowTools)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // NVIDIA execution (OpenAI-compatible)
    private fun executeNVIDIA(
        apiKey: String,
        provider: String,
        model: String,
        systemPrompt: String,
        history: List<Pair<String, String>>,
        userMessage: String,
        allowTools: Boolean = true
    ): Result<AiResponse> {
        val endpoint = if (model.startsWith("gemini")) "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions" else "${ApiConfig.NVIDIA_BASE_URL}/chat/completions"

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
        applyInferenceControls(payload, provider, model)

        if (allowTools) {
            val tools = ToolSchema.forOpenAI()
            if (tools.length() > 0) {
                payload.put("tools", tools)
                payload.put("tool_choice", "auto")
            }
        }

        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer $apiKey")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return client.newCall(request).execute().use { response ->
            val bodyString = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                if (response.code == 429 && (provider == "gemini_flash" || model.startsWith("gemini"))) {
                    Log.w("JarvisApiClient", "Gemini 429 quota reached. Rotating key in pool...")
                    ApiConfig.markGeminiKeyRateLimited(apiKey)
                    val nextKey = ApiConfig.currentGeminiKey
                    if (nextKey.isNotBlank() && nextKey != apiKey) {
                        return executeNVIDIA(nextKey, provider, model, systemPrompt, history, userMessage, allowTools)
                    }
                }
                val msg = when (response.code) {
                    401 -> "API key is invalid or expired (HTTP 401). Check Settings."
                    429 -> "API rate limit / quota reached. Rotating or wait a moment."
                    else -> "API error (HTTP ${response.code}): ${bodyString.take(200)}"
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

    /**
     * Sampling and reasoning controls (audit P0-6 / plan section 4.4).
     *
     * Requests previously carried only model / messages / stream / tools, so every
     * call inherited the provider's server-side defaults. Two of those defaults are
     * actively harmful for a voice assistant:
     *
     *  - Nemotron 3 defaults to reasoning mode ON. Independent measurements put
     *    Nemotron 3 Super around 16 seconds per turn with reasoning enabled.
     *    `chat_template_kwargs.enable_thinking` is now sent explicitly: OFF for the
     *    conversational tier, ON only for the deep tier. It is gated on the model
     *    because the Gemini OpenAI-compatibility endpoint does not accept
     *    `chat_template_kwargs` and would reject the whole request.
     *  - No `max_tokens` meant the model could emit up to its full output budget for
     *    a reply that is about to be read aloud. The cap bounds latency and cost; it
     *    is deliberately generous enough that a spoken reply plus streamed tool-call
     *    arguments cannot be truncated into invalid JSON.
     */
    private fun applyInferenceControls(payload: JSONObject, provider: String, model: String) {
        val deep = ApiConfig.isDeepTier(provider)
        payload.put("temperature", if (deep) 0.4 else 0.7)
        payload.put("max_tokens", if (deep) 4096 else 1024)
        if (ApiConfig.supportsReasoningToggle(model)) {
            payload.put("chat_template_kwargs", JSONObject().put("enable_thinking", deep))
        }
    }

    // Helpers
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
        fun resolveModel(provider: String): String = ApiConfig.resolveModel(provider)
    }
}
