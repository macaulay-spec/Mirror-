package com.jarvis.agent.ai

import com.jarvis.app.assistant.GeminiService
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

data class ProviderKeyConfig(
    val provider: String,
    val apiKey: String,
    val model: String,
    var enabled: Boolean = true,
    var priority: Int = 1,
    var failureCount: Int = 0,
    var lastFailureTime: Long = 0L,
    var isHealthy: Boolean = true
)

class GeminiAIProvider : AIProvider {
    override val name: String = "gemini"
    private val geminiService = GeminiService()

    override fun getModels(): List<String> = listOf("gemini-2.5-flash", "gemini-1.5-flash", "gemini-1.5-pro")

    override suspend fun generate(
        apiKey: String,
        model: String,
        systemInstruction: String,
        history: List<Pair<String, String>>,
        userMessage: String
    ): Result<AIResponse> {
        val res = geminiService.generateChatResponse(
            systemInstruction = systemInstruction,
            history = history,
            userMessage = userMessage,
            model = model,
            apiKey = apiKey,
            temperature = 0.3f
        )
        if (res.isFailure && model != "gemini-1.5-flash") {
            val fallback = geminiService.generateChatResponse(
                systemInstruction = systemInstruction,
                history = history,
                userMessage = userMessage,
                model = "gemini-1.5-flash",
                apiKey = apiKey,
                temperature = 0.3f
            )
            if (fallback.isSuccess) {
                return fallback.map { AIResponse(content = it) }
            }
        }
        return res.map { AIResponse(content = it) }
    }

    override suspend fun stream(
        apiKey: String,
        model: String,
        systemInstruction: String,
        history: List<Pair<String, String>>,
        userMessage: String,
        onChunk: (String) -> Unit
    ): Result<AIResponse> {
        val gen = generate(apiKey, model, systemInstruction, history, userMessage)
        gen.onSuccess { onChunk(it.content) }
        return gen
    }
}

class OpenAiAIProvider : AIProvider {
    override val name: String = "openai"
    private val client = OkHttpClient.Builder().readTimeout(60, TimeUnit.SECONDS).build()

    override fun getModels(): List<String> = listOf("gpt-4o-mini", "gpt-4o")

    override suspend fun generate(
        apiKey: String,
        model: String,
        systemInstruction: String,
        history: List<Pair<String, String>>,
        userMessage: String
    ): Result<AIResponse> = withContext(Dispatchers.IO) {
        try {
            val messages = JSONArray()
            messages.put(JSONObject().put("role", "system").put("content", systemInstruction))
            history.forEach { (role, text) ->
                messages.put(JSONObject().put("role", if (role == "jarvis") "assistant" else "user").put("content", text))
            }
            messages.put(JSONObject().put("role", "user").put("content", userMessage))

            val body = JSONObject().put("model", model).put("messages", messages)
            val request = Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .header("Authorization", "Bearer $apiKey")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(Exception("Neural core status: ${resp.code}"))
                }
                val json = JSONObject(resp.body?.string() ?: "{}")
                val content = json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                Result.success(AIResponse(content = content))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun stream(
        apiKey: String,
        model: String,
        systemInstruction: String,
        history: List<Pair<String, String>>,
        userMessage: String,
        onChunk: (String) -> Unit
    ): Result<AIResponse> {
        val res = generate(apiKey, model, systemInstruction, history, userMessage)
        res.onSuccess { onChunk(it.content) }
        return res
    }
}

class AnthropicAIProvider : AIProvider {
    override val name: String = "anthropic"
    private val client = OkHttpClient.Builder().readTimeout(60, TimeUnit.SECONDS).build()

    override fun getModels(): List<String> = listOf("claude-3-5-sonnet-latest", "claude-3-haiku")

    override suspend fun generate(
        apiKey: String,
        model: String,
        systemInstruction: String,
        history: List<Pair<String, String>>,
        userMessage: String
    ): Result<AIResponse> = withContext(Dispatchers.IO) {
        try {
            val messages = JSONArray()
            history.forEach { (role, text) ->
                messages.put(JSONObject().put("role", if (role == "jarvis") "assistant" else "user").put("content", text))
            }
            messages.put(JSONObject().put("role", "user").put("content", userMessage))

            val body = JSONObject()
                .put("model", model)
                .put("system", systemInstruction)
                .put("max_tokens", 1024)
                .put("messages", messages)

            val request = Request.Builder()
                .url("https://api.anthropic.com/v1/messages")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(Exception("Neural core status: ${resp.code}"))
                }
                val json = JSONObject(resp.body?.string() ?: "{}")
                val content = json.getJSONArray("content").getJSONObject(0).getString("text")
                Result.success(AIResponse(content = content))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun stream(
        apiKey: String,
        model: String,
        systemInstruction: String,
        history: List<Pair<String, String>>,
        userMessage: String,
        onChunk: (String) -> Unit
    ): Result<AIResponse> {
        val res = generate(apiKey, model, systemInstruction, history, userMessage)
        res.onSuccess { onChunk(it.content) }
        return res
    }
}

class GroqAIProvider : AIProvider {
    override val name: String = "groq"
    private val client = OkHttpClient.Builder().readTimeout(60, TimeUnit.SECONDS).build()

    override fun getModels(): List<String> = listOf("llama-3.3-70b-versatile", "mixtral-8x7b-32768")

    override suspend fun generate(
        apiKey: String,
        model: String,
        systemInstruction: String,
        history: List<Pair<String, String>>,
        userMessage: String
    ): Result<AIResponse> = withContext(Dispatchers.IO) {
        try {
            val messages = JSONArray()
            messages.put(JSONObject().put("role", "system").put("content", systemInstruction))
            history.forEach { (role, text) ->
                messages.put(JSONObject().put("role", if (role == "jarvis") "assistant" else "user").put("content", text))
            }
            messages.put(JSONObject().put("role", "user").put("content", userMessage))

            val body = JSONObject().put("model", model).put("messages", messages)
            val request = Request.Builder()
                .url("https://api.groq.com/openai/v1/chat/completions")
                .header("Authorization", "Bearer $apiKey")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(Exception("Neural core status: ${resp.code}"))
                }
                val json = JSONObject(resp.body?.string() ?: "{}")
                val content = json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                Result.success(AIResponse(content = content))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun stream(
        apiKey: String,
        model: String,
        systemInstruction: String,
        history: List<Pair<String, String>>,
        userMessage: String,
        onChunk: (String) -> Unit
    ): Result<AIResponse> {
        val res = generate(apiKey, model, systemInstruction, history, userMessage)
        res.onSuccess { onChunk(it.content) }
        return res
    }
}

class OpenRouterAIProvider : AIProvider {
    override val name: String = "openrouter"

    override fun getModels(): List<String> = listOf("openai/gpt-4o-mini", "anthropic/claude-3.5-sonnet")

    override suspend fun generate(
        apiKey: String,
        model: String,
        systemInstruction: String,
        history: List<Pair<String, String>>,
        userMessage: String
    ): Result<AIResponse> = withContext(Dispatchers.IO) {
        try {
            val messages = JSONArray()
            messages.put(JSONObject().put("role", "system").put("content", systemInstruction))
            history.forEach { (role, text) ->
                messages.put(JSONObject().put("role", if (role == "jarvis") "assistant" else "user").put("content", text))
            }
            messages.put(JSONObject().put("role", "user").put("content", userMessage))

            val body = JSONObject().put("model", model).put("messages", messages)
            val client = OkHttpClient.Builder().readTimeout(60, TimeUnit.SECONDS).build()
            val request = Request.Builder()
                .url("https://openrouter.ai/api/v1/chat/completions")
                .header("Authorization", "Bearer $apiKey")
                .header("HTTP-Referer", "https://jarvis.android.app")
                .header("X-Title", "JARVIS Android Core")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(Exception("Neural core status: ${resp.code}"))
                }
                val json = JSONObject(resp.body?.string() ?: "{}")
                val content = json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                Result.success(AIResponse(content = content))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun stream(
        apiKey: String,
        model: String,
        systemInstruction: String,
        history: List<Pair<String, String>>,
        userMessage: String,
        onChunk: (String) -> Unit
    ): Result<AIResponse> {
        val res = generate(apiKey, model, systemInstruction, history, userMessage)
        res.onSuccess { onChunk(it.content) }
        return res
    }
}

class CerebrasAIProvider : AIProvider {
    override val name: String = "cerebras"
    private val client = OkHttpClient.Builder().readTimeout(30, TimeUnit.SECONDS).build()

    override fun getModels(): List<String> = listOf("llama3.1-70b", "llama3.1-8b")

    override suspend fun generate(
        apiKey: String,
        model: String,
        systemInstruction: String,
        history: List<Pair<String, String>>,
        userMessage: String
    ): Result<AIResponse> = withContext(Dispatchers.IO) {
        try {
            val messages = JSONArray()
            messages.put(JSONObject().put("role", "system").put("content", systemInstruction))
            history.forEach { (role, text) ->
                messages.put(JSONObject().put("role", if (role == "jarvis") "assistant" else "user").put("content", text))
            }
            messages.put(JSONObject().put("role", "user").put("content", userMessage))

            val body = JSONObject().put("model", model).put("messages", messages).put("temperature", 0.3)
            val request = Request.Builder()
                .url("https://api.cerebras.ai/v1/chat/completions")
                .header("Authorization", "Bearer $apiKey")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(Exception("Cerebras status: ${resp.code}"))
                }
                val json = JSONObject(resp.body?.string() ?: "{}")
                val content = json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                Result.success(AIResponse(content = content))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun stream(
        apiKey: String,
        model: String,
        systemInstruction: String,
        history: List<Pair<String, String>>,
        userMessage: String,
        onChunk: (String) -> Unit
    ): Result<AIResponse> {
        val res = generate(apiKey, model, systemInstruction, history, userMessage)
        res.onSuccess { onChunk(it.content) }
        return res
    }
}

class GrokAIProvider : AIProvider {
    override val name: String = "grok"
    private val client = OkHttpClient.Builder().readTimeout(45, TimeUnit.SECONDS).build()

    override fun getModels(): List<String> = listOf("grok-2", "grok-beta")

    override suspend fun generate(
        apiKey: String,
        model: String,
        systemInstruction: String,
        history: List<Pair<String, String>>,
        userMessage: String
    ): Result<AIResponse> = withContext(Dispatchers.IO) {
        try {
            val messages = JSONArray()
            messages.put(JSONObject().put("role", "system").put("content", systemInstruction))
            history.forEach { (role, text) ->
                messages.put(JSONObject().put("role", if (role == "jarvis") "assistant" else "user").put("content", text))
            }
            messages.put(JSONObject().put("role", "user").put("content", userMessage))

            val body = JSONObject().put("model", model).put("messages", messages)
            val request = Request.Builder()
                .url("https://api.x.ai/v1/chat/completions")
                .header("Authorization", "Bearer $apiKey")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(Exception("Grok status: ${resp.code}"))
                }
                val json = JSONObject(resp.body?.string() ?: "{}")
                val content = json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                Result.success(AIResponse(content = content))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun stream(
        apiKey: String,
        model: String,
        systemInstruction: String,
        history: List<Pair<String, String>>,
        userMessage: String,
        onChunk: (String) -> Unit
    ): Result<AIResponse> {
        val res = generate(apiKey, model, systemInstruction, history, userMessage)
        res.onSuccess { onChunk(it.content) }
        return res
    }
}

class MistralAIProvider : AIProvider {
    override val name: String = "mistral"
    private val client = OkHttpClient.Builder().readTimeout(45, TimeUnit.SECONDS).build()

    override fun getModels(): List<String> = listOf("mistral-small-latest", "mistral-large-latest")

    override suspend fun generate(
        apiKey: String,
        model: String,
        systemInstruction: String,
        history: List<Pair<String, String>>,
        userMessage: String
    ): Result<AIResponse> = withContext(Dispatchers.IO) {
        try {
            val messages = JSONArray()
            messages.put(JSONObject().put("role", "system").put("content", systemInstruction))
            history.forEach { (role, text) ->
                messages.put(JSONObject().put("role", if (role == "jarvis") "assistant" else "user").put("content", text))
            }
            messages.put(JSONObject().put("role", "user").put("content", userMessage))

            val body = JSONObject().put("model", model).put("messages", messages)
            val request = Request.Builder()
                .url("https://api.mistral.ai/v1/chat/completions")
                .header("Authorization", "Bearer $apiKey")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(Exception("Mistral status: ${resp.code}"))
                }
                val json = JSONObject(resp.body?.string() ?: "{}")
                val content = json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                Result.success(AIResponse(content = content))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun stream(
        apiKey: String,
        model: String,
        systemInstruction: String,
        history: List<Pair<String, String>>,
        userMessage: String,
        onChunk: (String) -> Unit
    ): Result<AIResponse> {
        val res = generate(apiKey, model, systemInstruction, history, userMessage)
        res.onSuccess { onChunk(it.content) }
        return res
    }
}

class CohereAIProvider : AIProvider {
    override val name: String = "cohere"
    private val client = OkHttpClient.Builder().readTimeout(45, TimeUnit.SECONDS).build()

    override fun getModels(): List<String> = listOf("command-r-plus", "command-r")

    override suspend fun generate(
        apiKey: String,
        model: String,
        systemInstruction: String,
        history: List<Pair<String, String>>,
        userMessage: String
    ): Result<AIResponse> = withContext(Dispatchers.IO) {
        try {
            val messages = JSONArray()
            messages.put(JSONObject().put("role", "system").put("content", systemInstruction))
            history.forEach { (role, text) ->
                messages.put(JSONObject().put("role", if (role == "jarvis") "assistant" else "user").put("content", text))
            }
            messages.put(JSONObject().put("role", "user").put("content", userMessage))

            val body = JSONObject().put("model", model).put("messages", messages)
            val request = Request.Builder()
                .url("https://api.cohere.com/v2/chat")
                .header("Authorization", "Bearer $apiKey")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(Exception("Cohere status: ${resp.code}"))
                }
                val json = JSONObject(resp.body?.string() ?: "{}")
                val content = json.optJSONObject("message")?.optJSONArray("content")?.optJSONObject(0)?.optString("text")
                    ?: json.optString("text", "")
                Result.success(AIResponse(content = content))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun stream(
        apiKey: String,
        model: String,
        systemInstruction: String,
        history: List<Pair<String, String>>,
        userMessage: String,
        onChunk: (String) -> Unit
    ): Result<AIResponse> {
        val res = generate(apiKey, model, systemInstruction, history, userMessage)
        res.onSuccess { onChunk(it.content) }
        return res
    }
}

class ProviderRouter {
    private val providers = mapOf<String, AIProvider>(
        "cerebras" to CerebrasAIProvider(),
        "openai" to OpenAiAIProvider(),
        "grok" to GrokAIProvider(),
        "mistral" to MistralAIProvider(),
        "cohere" to CohereAIProvider(),
        "gemini" to GeminiAIProvider(),
        "anthropic" to AnthropicAIProvider(),
        "groq" to GroqAIProvider(),
        "openrouter" to OpenRouterAIProvider()
    )

    private val keyConfigs = mutableListOf<ProviderKeyConfig>()

    private fun getSortedConfigs(): List<ProviderKeyConfig> {
        keyConfigs.clear()
        
        // Priority 1: User's custom key if configured
        if (ApiConfig.hasCustomKey) {
            val provider = ApiConfig.activeProvider
            val model = when (provider) {
                "cerebras" -> ApiConfig.CEREBRAS_MODEL
                "openai" -> ApiConfig.OPENAI_MODEL
                "grok" -> ApiConfig.GROK_MODEL
                "mistral" -> ApiConfig.MISTRAL_MODEL
                "cohere" -> ApiConfig.COHERE_MODEL
                "anthropic" -> ApiConfig.ANTHROPIC_MODEL
                "groq" -> ApiConfig.GROQ_MODEL
                "openrouter" -> ApiConfig.OPENROUTER_MODEL
                else -> ApiConfig.GEMINI_MODEL
            }
            keyConfigs.add(ProviderKeyConfig(provider, ApiConfig.activeApiKey, model, priority = 1))
        }

        // Priority 2: Built-in Cerebras (Ultra-fast voice inference)
        if (ApiConfig.BUNDLED_CEREBRAS_KEY.isNotBlank() && ApiConfig.activeProvider != "cerebras") {
            keyConfigs.add(ProviderKeyConfig("cerebras", ApiConfig.BUNDLED_CEREBRAS_KEY, ApiConfig.CEREBRAS_MODEL, priority = 2))
        }

        // Priority 3: Built-in OpenAI
        if (ApiConfig.BUNDLED_OPENAI_KEY.isNotBlank() && ApiConfig.activeProvider != "openai") {
            keyConfigs.add(ProviderKeyConfig("openai", ApiConfig.BUNDLED_OPENAI_KEY, ApiConfig.OPENAI_MODEL, priority = 3))
        }

        // Priority 4: Built-in Grok / xAI
        if (ApiConfig.BUNDLED_GROK_KEY.isNotBlank() && ApiConfig.activeProvider != "grok") {
            keyConfigs.add(ProviderKeyConfig("grok", ApiConfig.BUNDLED_GROK_KEY, ApiConfig.GROK_MODEL, priority = 4))
        }

        // Priority 5: Built-in Mistral
        if (ApiConfig.BUNDLED_MISTRAL_KEY.isNotBlank() && ApiConfig.activeProvider != "mistral") {
            keyConfigs.add(ProviderKeyConfig("mistral", ApiConfig.BUNDLED_MISTRAL_KEY, ApiConfig.MISTRAL_MODEL, priority = 5))
        }

        // Priority 6: Built-in Cohere
        if (ApiConfig.BUNDLED_COHERE_KEY.isNotBlank() && ApiConfig.activeProvider != "cohere") {
            keyConfigs.add(ProviderKeyConfig("cohere", ApiConfig.BUNDLED_COHERE_KEY, ApiConfig.COHERE_MODEL, priority = 6))
        }

        // Priority 7: Built-in Gemini
        if (ApiConfig.GEMINI_API_KEY.isNotBlank() && ApiConfig.activeProvider != "gemini") {
            keyConfigs.add(ProviderKeyConfig("gemini", ApiConfig.GEMINI_API_KEY, ApiConfig.GEMINI_MODEL, priority = 7))
        }

        return keyConfigs.filter { it.enabled && it.isHealthy }.sortedBy { it.priority }
    }

    suspend fun executeWithFallback(
        systemInstruction: String,
        history: List<Pair<String, String>>,
        userMessage: String
    ): Result<AIResponse> {
        val sortedConfigs = getSortedConfigs()
        if (sortedConfigs.isEmpty()) {
            return Result.success(AIResponse(content = "JARVIS local protocols active. Neural gateway is currently offline."))
        }

        var lastError: Throwable? = null
        for (config in sortedConfigs) {
            val provider = providers[config.provider] ?: continue
            val result = provider.generate(config.apiKey, config.model, systemInstruction, history, userMessage)
            if (result.isSuccess) {
                config.failureCount = 0
                config.isHealthy = true
                return result
            } else {
                val err = result.exceptionOrNull()
                lastError = err
                val isQuotaExhausted = err?.message?.contains("429") == true || err?.message?.contains("RESOURCE_EXHAUSTED") == true

                if (isQuotaExhausted) {
                    // Automatically rotate developer key pool if available
                    ApiConfig.rotateDeveloperKey()
                }

                config.failureCount++
                if (config.failureCount >= 3) {
                    config.isHealthy = false
                    config.lastFailureTime = System.currentTimeMillis()
                }
            }
        }

        return Result.failure(lastError ?: Exception("JARVIS neural matrix connection temporarily unavailable."))
    }

    /**
     * Pings every configured provider so the user can see, in seconds, which keys are
     * alive. Without this there was no way to tell a missing Gemini key from a dead
     * bundled one — the app just quietly answered from local rules instead.
     */
    suspend fun diagnostics(): List<ProviderStatus> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val configs = getSortedConfigs()
        if (configs.isEmpty()) {
            return@withContext listOf(
                ProviderStatus(
                    provider = "none",
                    keyPreview = "",
                    model = "",
                    ok = false,
                    latencyMs = 0,
                    message = "No AI key configured. Put GEMINI_API_KEY=... in local.properties and rebuild. " +
                        "Device control still works without it."
                )
            )
        }

        val out = ArrayList<ProviderStatus>()
        for (config in configs) {
            val provider = providers[config.provider]
            if (provider == null) {
                out.add(
                    ProviderStatus(config.provider, mask(config.apiKey), config.model, false, 0,
                        "No client for this provider.")
                )
                continue
            }
            val start = System.currentTimeMillis()
            val attempt = runCatching {
                provider.generate(
                    config.apiKey, config.model,
                    "You are a connectivity test.", emptyList(),
                    "Reply with the single word: online"
                )
            }
            val latency = System.currentTimeMillis() - start
            val result = attempt.getOrNull()
            val ok = result?.isSuccess == true
            val message = when {
                ok -> (result?.getOrNull()?.content ?: "responded").take(60).trim()
                result != null -> result.exceptionOrNull()?.localizedMessage ?: "failed"
                else -> attempt.exceptionOrNull()?.localizedMessage ?: "failed"
            }
            out.add(
                ProviderStatus(config.provider, mask(config.apiKey), config.model, ok, latency, message)
            )
        }
        out
    }

    data class ProviderStatus(
        val provider: String,
        val keyPreview: String,
        val model: String,
        val ok: Boolean,
        val latencyMs: Long,
        val message: String
    )

    private fun mask(key: String): String =
        if (key.length <= 10) "****" else key.take(6) + "..." + key.takeLast(4)

    suspend fun streamWithFallback(
        systemInstruction: String,
        history: List<Pair<String, String>>,
        userMessage: String,
        onChunk: (String) -> Unit
    ): Result<AIResponse> {
        val sortedConfigs = getSortedConfigs()
        if (sortedConfigs.isEmpty()) {
            val fallback = "JARVIS local streaming protocols active."
            onChunk(fallback)
            return Result.success(AIResponse(content = fallback))
        }

        var lastError: Throwable? = null
        for (config in sortedConfigs) {
            val provider = providers[config.provider] ?: continue
            val result = provider.stream(config.apiKey, config.model, systemInstruction, history, userMessage, onChunk)
            if (result.isSuccess) {
                config.failureCount = 0
                config.isHealthy = true
                return result
            } else {
                val err = result.exceptionOrNull()
                lastError = err
                if (err?.message?.contains("429") == true) {
                    ApiConfig.rotateDeveloperKey()
                }
                config.failureCount++
                if (config.failureCount >= 3) {
                    config.isHealthy = false
                    config.lastFailureTime = System.currentTimeMillis()
                }
            }
        }
        return Result.failure(lastError ?: Exception("JARVIS neural streaming temporarily unavailable."))
    }
}
