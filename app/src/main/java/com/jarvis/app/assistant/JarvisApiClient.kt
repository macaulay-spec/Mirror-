package com.jarvis.app.assistant

import android.util.Log
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
 * JARVIS AI Client — NVIDIA-focused with automatic provider fallback.
 *
 * When BackendConfig.isBackendReady:
 *   All API calls go through Convex HTTP actions (API keys server-side).
 *
 * When not using backend:
 *   Direct API calls to NVIDIA's OpenAI-compatible endpoint with keys from BuildConfig.
 *   Automatic fallback through the NVIDIA provider chain on failures.
 *
 * Provider fallback chain (per NVIDIA_MULTI_MODEL_PROMPT.md):
 *   1. NVIDIA GLM-5.2 (primary)
 *   2. NVIDIA Nemotron-3-Super
 *   3. NVIDIA Mistral Nemotron
 *   4. NVIDIA Llama-4 Maverick
 */
class JarvisApiClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build(),
    private val streamClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .build()
) {
    suspend fun chat(
        systemPrompt: String,
        history: List<Pair<String, String>>,
        userMessage: String,
        provider: String = ApiConfig.activeProvider,
        model: String = ApiConfig.resolveModel(provider)
    ): Result<AiResponse> = withContext(Dispatchers.IO) {
        if (BackendConfig.isBackendReady) {
            chatViaProxy(systemPrompt, history, userMessage, provider, model)
        } else if (BackendConfig.USE_BACKEND && !BackendConfig.isWorkerUrlConfigured) {
            Result.failure(IllegalStateException(
                "Backend mode is enabled but BackendConfig.WORKER_URL is not configured. " +
                "Deploy Convex and set the URL, or set USE_BACKEND=false."
            ))
        } else {
            chatDirect(systemPrompt, history, userMessage, provider, model)
        }
    }

    // Real-time streaming (SSE) with automatic provider fallback
    suspend fun chatStream(
        systemPrompt: String,
        history: List<Pair<String, String>>,
        userMessage: String,
        provider: String = ApiConfig.activeProvider,
        model: String = ApiConfig.resolveModel(provider),
        onDelta: (String) -> Unit
    ): Result<AiResponse> = withContext(Dispatchers.IO) {
        var emitted = false
        var triedProviders = mutableListOf<String>()
        val track: (String) -> Unit = { d -> emitted = true; onDelta(d) }

        suspend fun fallbackBlocking(): Result<AiResponse> {
            val blocked = chat(systemPrompt, history, userMessage, provider, model)
            blocked.getOrNull()?.message?.takeIf { it.isNotBlank() }?.let(track)
            return blocked
        }

        suspend fun tryStreamWithProvider(providerToTry: String): Result<AiResponse> {
            val currentModel = ApiConfig.resolveModel(providerToTry)
            val currentApiKey = when (providerToTry) {
                in listOf("nvidia_glm", "nvidia_nemotron", "nvidia_mistral", "nvidia_llama") ->
                    ApiConfig.NVIDIA_API_KEY
                else -> ApiConfig.activeApiKey
            }

            if (currentApiKey.isBlank()) {
                return Result.failure(Exception("No API key for $providerToTry"))
            }

            triedProviders.add(providerToTry)

            if (BackendConfig.isBackendReady || providerToTry == "anthropic") {
                return fallbackBlocking()
            }

            val streamed = streamNVIDIA(
                currentApiKey, providerToTry, currentModel,
                systemPrompt, history, userMessage, track
            )

            return streamed
        }

        // Try providers in fallback chain order
        var currentProvider = provider
        var lastResult: Result<AiResponse> = Result.failure(Exception("No providers available"))

        while (currentProvider != null) {
            lastResult = tryStreamWithProvider(currentProvider)
            if (lastResult.isSuccess) {
                Log.i("JarvisApiClient", "Provider $currentProvider succeeded")
                return@withContext lastResult
            }

            Log.w("JarvisApiClient", "Provider $currentProvider failed, trying next in chain")
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
        onDelta: (String) -> Unit
    ): Result<AiResponse> {
        val endpoint = "${ApiConfig.NVIDIA_BASE_URL}/chat/completions"

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

        val tools = ToolSchema.forOpenAI()
        if (tools.length() > 0) {
            payload.put("tools", tools)
            payload.put("tool_choice", "auto")
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
                    val err = response.body?.string()?.take(200) ?: ""
                    return Result.failure(Exception("NVIDIA stream error (HTTP ${response.code}): $err"))
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

    // Backend Proxy Path (Convex)
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
                        503 -> "AI service not configured on the backend. Check Convex deployment."
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

    // Save Voice Preferences via Convex
    suspend fun saveVoicePreferences(
        userId: String,
        voiceId: String,
        voiceName: String,
        engineType: String = "elevenlabs",
        stability: Float = 0.5f,
        similarityBoost: Float = 0.75f,
        style: Float = 0f
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject()
                .put("userId", userId)
                .put("voiceId", voiceId)
                .put("voiceName", voiceName)
                .put("engineType", engineType)
                .put("stability", stability.toDouble())
                .put("similarityBoost", similarityBoost.toDouble())
                .put("style", style.toDouble())

            val request = Request.Builder()
                .url("${BackendConfig.WORKER_URL}${BackendConfig.PREFERENCES_ENDPOINT}")
                .header("Content-Type", "application/json")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(true)
                } else {
                    val body = response.body?.string() ?: ""
                    Result.failure(Exception("Failed to save preferences: ${body.take(200)}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("Preferences save failed: ${e.localizedMessage}"))
        }
    }

    // Load Voice Preferences via Convex
    suspend fun loadVoicePreferences(userId: String): Result<VoicePreferences?> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("${BackendConfig.WORKER_URL}${BackendConfig.PREFERENCES_ENDPOINT}?userId=$userId")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use Result.success(null)
                }

                val body = response.body?.string() ?: "{}"
                val json = JSONObject(body)
                val voice = json.optJSONObject("voice")

                if (voice == null) {
                    Result.success(null)
                } else {
                    Result.success(
                        VoicePreferences(
                            voiceId = voice.optString("voiceId", "JBFqnCBsd6RMkjVDRZzb"),
                            voiceName = voice.optString("voiceName", "George"),
                            engineType = voice.optString("engineType", "elevenlabs"),
                            stability = voice.optDouble("stability", 0.5).toFloat(),
                            similarityBoost = voice.optDouble("similarityBoost", 0.75).toFloat(),
                            style = voice.optDouble("style", 0.0).toFloat()
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("Preferences load failed: ${e.localizedMessage}"))
        }
    }

    // Fetch Available ElevenLabs Voices
    suspend fun fetchElevenLabsVoices(): Result<List<ElevenLabsVoice>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("${BackendConfig.WORKER_URL}${BackendConfig.TTS_VOICES_ENDPOINT}")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: "{}"
                if (!response.isSuccessful) {
                    return@use Result.failure(Exception("Failed to fetch voices: ${body.take(200)}"))
                }

                val json = JSONObject(body)
                val voicesArray = json.optJSONArray("voices") ?: return@use Result.success(emptyList())

                val voices = mutableListOf<ElevenLabsVoice>()
                for (i in 0 until voicesArray.length()) {
                    val v = voicesArray.getJSONObject(i)
                    voices.add(
                        ElevenLabsVoice(
                            voiceId = v.optString("voiceId"),
                            name = v.optString("name"),
                            category = v.optString("category"),
                            description = v.optString("description"),
                            previewUrl = v.optString("previewUrl")
                        )
                    )
                }
                Result.success(voices)
            }
        } catch (e: Exception) {
            Result.failure(Exception("Voice fetch failed: ${e.localizedMessage}"))
        }
    }

    // Direct Path (development/testing only)
    private fun chatDirect(
        systemPrompt: String,
        history: List<Pair<String, String>>,
        userMessage: String,
        provider: String,
        model: String
    ): Result<AiResponse> {
        val apiKey = when (provider) {
            in listOf("nvidia_glm", "nvidia_nemotron", "nvidia_mistral", "nvidia_llama") ->
                ApiConfig.NVIDIA_API_KEY
            else -> ApiConfig.activeApiKey
        }

        if (apiKey.isBlank()) {
            return Result.failure(
                Exception("No AI key configured for $provider. Add your NVIDIA key in Settings.")
            )
        }

        return try {
            when (provider) {
                else -> executeNVIDIA(apiKey, provider, model, systemPrompt, history, userMessage)
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
        userMessage: String
    ): Result<AiResponse> {
        val endpoint = "${ApiConfig.NVIDIA_BASE_URL}/chat/completions"

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

        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer $apiKey")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return client.newCall(request).execute().use { response ->
            val bodyString = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                val msg = when (response.code) {
                    401 -> "NVIDIA API key is invalid or expired (HTTP 401). Check Settings."
                    429 -> "NVIDIA rate limit reached. Please wait a moment and try again."
                    else -> "NVIDIA API error (HTTP ${response.code}): ${bodyString.take(200)}"
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

/** Data classes for voice preferences and ElevenLabs voices. */
data class VoicePreferences(
    val voiceId: String,
    val voiceName: String,
    val engineType: String,
    val stability: Float,
    val similarityBoost: Float,
    val style: Float
)

data class ElevenLabsVoice(
    val voiceId: String,
    val name: String,
    val category: String,
    val description: String,
    val previewUrl: String
)
