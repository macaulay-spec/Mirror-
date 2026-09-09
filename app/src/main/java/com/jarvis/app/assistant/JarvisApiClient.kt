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
 * JARVIS AI Client — Gemini-first with automatic provider fallback.
 *
 * Requests go straight to Google's OpenAI-compatible Generative Language endpoint using the
 * key from BuildConfig. Both the streaming and blocking paths walk
 * ApiConfig.PROVIDER_FALLBACK_CHAIN, so Gemini quota exhaustion degrades to NVIDIA (whose
 * key is compiled in) instead of silencing JARVIS.
 *
 * CHANGED during the sync: the Convex backend-proxy path is gone with the rest of that
 * integration (owner decision — Convex is not used). Every call takes the direct path, which
 * is what happened in practice anyway: BackendConfig.isBackendReady was permanently false
 * because no WORKER_URL was ever configured, so chatViaProxy was unreachable dead code that
 * still had to compile.
 *
 * Also removed: the Convex-backed saveVoicePreferences / loadVoicePreferences pair and
 * fetchElevenLabsVoices, plus the VoicePreferences and ElevenLabsVoice data classes. All
 * four had zero callers — voice selection is persisted locally by
 * ApiConfig.saveVoicePreferences(context, engineType, voiceId) into SharedPreferences, which
 * is what SetupScreen, VoiceRoomScreen and VoiceSettingsManager actually call.
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
            // Was a per-provider `when` that had no openai arm, so a user-supplied OpenAI
            // key fell through to activeApiKey and could be paired with the wrong endpoint.
            // keyFor is the single definition of "which key authorises this provider".
            val currentApiKey = ApiConfig.keyFor(providerToTry)

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

    // Real-time streaming (SSE) with Google Gemini AI engine
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
        // CHANGED during the sync, and this was the load-bearing one. Both of these lines
        // were hardcoded to Gemini while `provider` and `model` were still accepted as
        // parameters and then ignored:
        //
        //     val endpoint = "https://generativelanguage.googleapis.com/v1beta/openai/..."
        //     val requestModel = if (model.startsWith("gemini")) model else GEMINI_FLASH_MODEL
        //
        // So chatDirect's provider loop walked the chain, selected an NVIDIA key for
        // "nvidia_super", and posted it to Google's endpoint -- a guaranteed 401. The
        // fallback existed only in the sense that it was tried; it could never succeed.
        // Endpoint and model now follow the provider, and the Gemini path is unchanged:
        // endpointFor("gemini_flash") is that same URL and resolveModel returns the same id.
        val endpoint = ApiConfig.endpointFor(provider)
        val requestModel = model.ifBlank { ApiConfig.resolveModel(provider) }

        val messages = JSONArray()
        messages.put(JSONObject().put("role", "system").put("content", systemPrompt))
        for ((role, text) in history) {
            if (text.isBlank()) continue
            val openAiRole = if (role == "jarvis" || role == "assistant" || role == "model") "assistant" else "user"
            messages.put(JSONObject().put("role", openAiRole).put("content", text))
        }
        messages.put(JSONObject().put("role", "user").put("content", userMessage))

        val payload = JSONObject()
            .put("model", requestModel)
            .put("messages", messages)
            .put("stream", true)

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
                    if (provider.startsWith("gemini") || model.startsWith("gemini")) {
                        if (response.code == 401 || response.code == 403 || response.code == 429) {
                            Log.w("JarvisApiClient", "Gemini HTTP ${response.code} error on key. Rotating key in pool...")
                            ApiConfig.markGeminiKeyFailed(apiKey)
                            val nextKey = ApiConfig.currentGeminiKey
                            if (nextKey.isNotBlank() && nextKey != apiKey) {
                                return streamNVIDIA(nextKey, provider, model, systemPrompt, history, userMessage, allowTools, onDelta)
                            }
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

    // Direct Path (development/testing only) with automatic multi-provider fallback
    private fun chatDirect(
        systemPrompt: String,
        history: List<Pair<String, String>>,
        userMessage: String,
        provider: String,
        model: String,
        allowTools: Boolean = true
    ): Result<AiResponse> {
        var currentProvider: String? = provider
        var lastResult: Result<AiResponse> = Result.failure(Exception("No AI provider available"))

        while (currentProvider != null) {
            val currentModel = ApiConfig.resolveModel(currentProvider)
            val currentApiKey = ApiConfig.keyFor(currentProvider)

            if (currentApiKey.isNotBlank()) {
                val res = try {
                    executeNVIDIA(currentApiKey, currentProvider, currentModel, systemPrompt, history, userMessage, allowTools)
                } catch (e: Exception) {
                    Result.failure(e)
                }
                if (res.isSuccess) {
                    return res
                }
                lastResult = res
            }

            currentProvider = ApiConfig.getNextProvider(currentProvider)
        }

        return lastResult
    }

    // Direct execution via Google Gemini AI engine
    private fun executeNVIDIA(
        apiKey: String,
        provider: String,
        model: String,
        systemPrompt: String,
        history: List<Pair<String, String>>,
        userMessage: String,
        allowTools: Boolean = true
    ): Result<AiResponse> {
        // CHANGED during the sync, and this was the load-bearing one. Both of these lines
        // were hardcoded to Gemini while `provider` and `model` were still accepted as
        // parameters and then ignored:
        //
        //     val endpoint = "https://generativelanguage.googleapis.com/v1beta/openai/..."
        //     val requestModel = if (model.startsWith("gemini")) model else GEMINI_FLASH_MODEL
        //
        // So chatDirect's provider loop walked the chain, selected an NVIDIA key for
        // "nvidia_super", and posted it to Google's endpoint -- a guaranteed 401. The
        // fallback existed only in the sense that it was tried; it could never succeed.
        // Endpoint and model now follow the provider, and the Gemini path is unchanged:
        // endpointFor("gemini_flash") is that same URL and resolveModel returns the same id.
        val endpoint = ApiConfig.endpointFor(provider)
        val requestModel = model.ifBlank { ApiConfig.resolveModel(provider) }

        val messages = JSONArray()
        messages.put(JSONObject().put("role", "system").put("content", systemPrompt))
        for ((role, text) in history) {
            if (text.isBlank()) continue
            val openAiRole = if (role == "jarvis" || role == "assistant" || role == "model") "assistant" else "user"
            messages.put(JSONObject().put("role", openAiRole).put("content", text))
        }
        messages.put(JSONObject().put("role", "user").put("content", userMessage))

        val payload = JSONObject()
            .put("model", requestModel)
            .put("messages", messages)

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
                if (provider.startsWith("gemini") || model.startsWith("gemini")) {
                    if (response.code == 401 || response.code == 403 || response.code == 429) {
                        Log.w("JarvisApiClient", "Gemini HTTP ${response.code} error. Marking key as failed...")
                        ApiConfig.markGeminiKeyFailed(apiKey)
                        val nextKey = ApiConfig.currentGeminiKey
                        if (nextKey.isNotBlank() && nextKey != apiKey) {
                            return executeNVIDIA(nextKey, provider, model, systemPrompt, history, userMessage, allowTools)
                        }
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
