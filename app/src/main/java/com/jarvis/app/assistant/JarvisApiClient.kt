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
import java.io.IOException
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
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build(),
    private val streamClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()
) {
    private fun getEndpoint(provider: String): String = when {
        provider.startsWith("nvidia") -> "https://integrate.api.nvidia.com/v1/chat/completions"
        provider == "grok" || provider == "xai" -> "https://api.x.ai/v1/chat/completions"
        provider == "openai" -> "https://api.openai.com/v1/chat/completions"
        provider == "groq" -> "https://api.groq.com/openai/v1/chat/completions"
        provider == "openrouter" -> "https://openrouter.ai/api/v1/chat/completions"
        provider == "cerebras" -> "https://api.cerebras.ai/v1/chat/completions"
        provider == "mistral" -> "https://api.mistral.ai/v1/chat/completions"
        else -> "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions"
    }

    private fun getRequestModel(provider: String, baseModel: String): String = when {
        provider.startsWith("nvidia") -> "nvidia/nemotron-3-super-120b-a12b"
        provider == "grok" || provider == "xai" -> "grok-3-mini"
        provider == "groq" -> "llama3-70b-8192"
        provider == "openrouter" -> "mistralai/mistral-7b-instruct:free"
        provider == "cerebras" -> "llama3.1-70b"
        provider.startsWith("gemini") -> if (baseModel.startsWith("gemini")) baseModel else ApiConfig.GEMINI_FLASH_MODEL
        else -> baseModel
    }

    private fun buildMessages(systemPrompt: String, history: List<Pair<String, String>>, userMessage: String): JSONArray {
        val messages = JSONArray()
        messages.put(JSONObject().put("role", "system").put("content", systemPrompt))
        for ((role, text) in history) {
            if (text.isBlank()) continue
            val openAiRole = if (role == "jarvis" || role == "assistant" || role == "model") "assistant" else "user"
            messages.put(JSONObject().put("role", openAiRole).put("content", text))
        }
        messages.put(JSONObject().put("role", "user").put("content", userMessage))
        return messages
    }

    private fun buildRequest(apiKey: String, endpoint: String, payload: JSONObject): Request {
        val requestBuilder = Request.Builder()
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            
        // For Google API keys (AIza...), we MUST send it as Bearer for the OpenAI endpoint!
        // The compat endpoint now perfectly works with standard Authorization header.
        requestBuilder.url(endpoint)
        if (apiKey.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer $apiKey")
        }
        return requestBuilder.build()
    }

    private fun handleRateLimit(responseCode: Int, apiKey: String, provider: String): Boolean {
        if (provider.startsWith("gemini") && (responseCode == 401 || responseCode == 403 || responseCode == 429)) {
            Log.w("JarvisApiClient", "Gemini HTTP $responseCode error. Rotating key...")
            ApiConfig.markGeminiKeyFailed(apiKey)
            return true
        }
        return false
    }

    private fun queryDavidCyrilAi(provider: String, userMessage: String, systemPrompt: String): Result<AiResponse> {
        val path = when (provider) {
            "claude_opus" -> "ai/claude-opus-4.6"
            "grok_fast" -> "ai/grok-4.1-fast"
            "deepseek_thinking" -> "ai/deepseek-v3.2-thinking"
            "gpt4o" -> "ai/gpt-4o"
            else -> "ai/claude-opus-4.6"
        }
        return try {
            val fullPrompt = if (systemPrompt.isNotBlank()) "$systemPrompt\n\nUser: $userMessage" else userMessage
            val encoded = java.net.URLEncoder.encode(fullPrompt, "UTF-8")
            val url = "https://apis.davidcyril.name.ng/$path?prompt=$encoded"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android; JARVIS AI)")
                .build()
            client.newCall(req).execute().use { response ->
                if (!response.isSuccessful) return Result.failure(IOException("HTTP ${response.code}"))
                val body = response.body?.string() ?: return Result.failure(IOException("Empty response"))
                val json = JSONObject(body)
                val answer = json.optString("data", json.optString("response", ""))
                if (answer.isNotBlank()) {
                    Result.success(AiResponse(message = answer))
                } else {
                    Result.failure(Exception("Blank response from $path"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun chat(
        systemPrompt: String,
        history: List<Pair<String, String>>,
        userMessage: String,
        provider: String = ApiConfig.activeProvider,
        model: String = ApiConfig.resolveModel(provider),
        allowTools: Boolean = true
    ): Result<AiResponse> = withContext(Dispatchers.IO) {
        var currentProvider: String? = provider
        var lastResult: Result<AiResponse> = Result.failure(Exception("No providers available"))

        while (currentProvider != null) {
            if (currentProvider in listOf("claude_opus", "grok_fast", "deepseek_thinking", "gpt4o")) {
                val keylessRes = queryDavidCyrilAi(currentProvider, userMessage, systemPrompt)
                if (keylessRes.isSuccess) return@withContext keylessRes
            } else {
                val currentModel = getRequestModel(currentProvider, model)
                val currentApiKey = when {
                    currentProvider.startsWith("gemini") -> ApiConfig.currentGeminiKey
                    currentProvider.startsWith("nvidia") -> ApiConfig.NVIDIA_API_KEY
                    else -> ApiConfig.activeApiKey
                }
                if (currentApiKey.isNotBlank()) {
                    lastResult = chatDirect(currentApiKey, currentProvider, currentModel, systemPrompt, history, userMessage, allowTools)
                    if (lastResult.isSuccess) {
                        return@withContext lastResult
                    }
                }
            }
            currentProvider = ApiConfig.getNextProvider(currentProvider)
        }
        return@withContext lastResult
    }

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
        val track: (String) -> Unit = { d -> emitted = true; onDelta(d) }

        suspend fun fallbackBlocking(): Result<AiResponse> {
            val blocked = chat(systemPrompt, history, userMessage, provider, model, allowTools)
            blocked.getOrNull()?.message?.takeIf { it.isNotBlank() }?.let(track)
            return blocked
        }

        suspend fun tryStreamWithProvider(providerToTry: String): Result<AiResponse> {
            if (providerToTry in listOf("claude_opus", "grok_fast", "deepseek_thinking", "gpt4o")) {
                val res = queryDavidCyrilAi(providerToTry, userMessage, systemPrompt)
                res.getOrNull()?.message?.let { msg ->
                    val words = msg.split(" ")
                    for (chunk in words.chunked(3)) {
                        val part = chunk.joinToString(" ") + " "
                        track(part)
                    }
                }
                return res
            }

            val currentModel = getRequestModel(providerToTry, model)
            val currentApiKey = when {
                providerToTry.startsWith("gemini") -> ApiConfig.currentGeminiKey
                providerToTry.startsWith("nvidia") -> ApiConfig.NVIDIA_API_KEY
                else -> ApiConfig.activeApiKey
            }
            if (currentApiKey.isBlank()) return Result.failure(Exception("No API key for $providerToTry"))
            
            if (providerToTry == "anthropic") {
                return fallbackBlocking()
            }
            
            return streamDirect(currentApiKey, providerToTry, currentModel, systemPrompt, history, userMessage, allowTools, track)
        }

        var currentProvider: String? = provider
        var lastResult: Result<AiResponse> = Result.failure(Exception("No providers available"))

        while (currentProvider != null) {
            lastResult = tryStreamWithProvider(currentProvider)
            if (lastResult.isSuccess || emitted) {
                return@withContext lastResult
            }
            currentProvider = ApiConfig.getNextProvider(currentProvider)
        }
        if (!emitted) fallbackBlocking() else lastResult
    }

    private fun chatDirect(
        apiKey: String,
        provider: String,
        model: String,
        systemPrompt: String,
        history: List<Pair<String, String>>,
        userMessage: String,
        allowTools: Boolean
    ): Result<AiResponse> {
        val endpoint = getEndpoint(provider)
        val requestModel = getRequestModel(provider, model)
        val messages = buildMessages(systemPrompt, history, userMessage)

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

        val request = buildRequest(apiKey, endpoint, payload)

        return try {
            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    if (handleRateLimit(response.code, apiKey, provider)) {
                        val nextKey = ApiConfig.currentGeminiKey
                        if (nextKey.isNotBlank() && nextKey != apiKey) {
                            return chatDirect(nextKey, provider, model, systemPrompt, history, userMessage, allowTools)
                        }
                    }
                    return Result.failure(Exception("API Error HTTP ${response.code}: ${bodyString.take(200)}"))
                }
                
                val obj = JSONObject(bodyString)
                val choices = obj.optJSONArray("choices")
                if (choices == null || choices.length() == 0) {
                    return Result.failure(Exception("Empty choices in response"))
                }
                
                val msg = choices.getJSONObject(0).optJSONObject("message")
                val content = msg?.optString("content")?.takeIf { it.isNotBlank() }
                val parsedTools = mutableListOf<ToolCallRequest>()
                
                msg?.optJSONArray("tool_calls")?.let { tcs ->
                    for (i in 0 until tcs.length()) {
                        val tc = tcs.getJSONObject(i)
                        val fn = tc.optJSONObject("function") ?: continue
                        val id = tc.optString("id").takeIf { it.isNotBlank() }
                        val name = fn.optString("name")
                        val argsStr = fn.optString("arguments")
                        
                        val argsMap = mutableMapOf<String, Any?>()
                        if (argsStr.isNotBlank()) {
                            runCatching {
                                val jsonArgs = JSONObject(argsStr)
                                jsonArgs.keys().forEach { k -> argsMap[k] = jsonArgs.get(k) }
                            }
                        }
                        parsedTools.add(ToolCallRequest(name, argsMap, id))
                    }
                }
                Result.success(AiResponse(content, parsedTools))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun streamDirect(
        apiKey: String,
        provider: String,
        model: String,
        systemPrompt: String,
        history: List<Pair<String, String>>,
        userMessage: String,
        allowTools: Boolean,
        onDelta: (String) -> Unit
    ): Result<AiResponse> {
        val endpoint = getEndpoint(provider)
        val requestModel = getRequestModel(provider, model)
        val messages = buildMessages(systemPrompt, history, userMessage)

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

        val request = buildRequest(apiKey, endpoint, payload)

        return try {
            streamClient.newCall(request).execute().use { response ->
                val body = response.body
                if (!response.isSuccessful || body == null) {
                    val err = body?.string() ?: ""
                    if (handleRateLimit(response.code, apiKey, provider)) {
                        val nextKey = ApiConfig.currentGeminiKey
                        if (nextKey.isNotBlank() && nextKey != apiKey) {
                            return streamDirect(nextKey, provider, model, systemPrompt, history, userMessage, allowTools, onDelta)
                        }
                    }
                    return Result.failure(Exception("Stream API error (HTTP ${response.code}): ${err.take(200)}"))
                }

                val text = java.lang.StringBuilder()
                val toolIds = mutableMapOf<Int, String>()
                val toolNames = mutableMapOf<Int, String>()
                val toolArgs = mutableMapOf<Int, java.lang.StringBuilder>()

                body.source().use { source ->
                    while (!source.exhausted()) {
                        val line = source.readUtf8LineStrict()
                        if (!line.startsWith("data: ")) continue
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
                                        toolArgs.getOrPut(idx) { java.lang.StringBuilder() }.append(frag)
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
            }
        } catch (e: Exception) {
            Result.failure(Exception("Stream failed: ${e.localizedMessage}"))
        }
    }


}