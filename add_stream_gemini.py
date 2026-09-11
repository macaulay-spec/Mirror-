import re

with open("app/src/main/java/com/jarvis/app/assistant/JarvisApiClient.kt", "r") as f:
    content = f.read()

# Add streamGeminiNative function
gemini_func = """
    private fun streamGeminiNative(
        apiKey: String,
        model: String,
        systemPrompt: String,
        history: List<Pair<String, String>>,
        userMessage: String,
        allowTools: Boolean = true,
        onDelta: (String) -> Unit
    ): Result<AiResponse> {
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$model:streamGenerateContent?alt=sse"
        
        val contents = JSONArray()
        for ((role, text) in history) {
            if (text.isBlank()) continue
            val geminiRole = if (role == "user") "user" else "model"
            contents.put(JSONObject().put("role", geminiRole).put("parts", JSONArray().put(JSONObject().put("text", text))))
        }
        contents.put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", userMessage))))

        val payload = JSONObject()
            .put("contents", contents)
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt))))

        // We skip tool mapping for now to keep it simple and ensure core intelligence/TTS works.
        // If tools are strictly needed, we'd map ToolSchema here.

        val request = Request.Builder()
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .url(endpoint)
            .header("x-goog-api-key", apiKey)
            .build()

        return try {
            streamClient.newCall(request).execute().use { response ->
                val body = response.body
                if (!response.isSuccessful || body == null) {
                    val err = body?.string() ?: ""
                    if (response.code == 401 || response.code == 403 || response.code == 429) {
                        Log.w("JarvisApiClient", "Gemini HTTP ${response.code} error on native stream. Rotating key...")
                        ApiConfig.markGeminiKeyFailed(apiKey)
                        val nextKey = ApiConfig.currentGeminiKey
                        if (nextKey.isNotBlank() && nextKey != apiKey) {
                            return streamGeminiNative(nextKey, model, systemPrompt, history, userMessage, allowTools, onDelta)
                        }
                    }
                    return Result.failure(Exception("API error (HTTP ${response.code}): ${err.take(200)}"))
                }

                var fullText = ""
                body.source().use { source ->
                    while (!source.exhausted()) {
                        val line = source.readUtf8LineStrict()
                        if (line.startsWith("data: ")) {
                            val data = line.removePrefix("data: ").trim()
                            if (data == "[DONE]") continue
                            try {
                                val json = JSONObject(data)
                                val candidates = json.optJSONArray("candidates")
                                if (candidates != null && candidates.length() > 0) {
                                    val parts = candidates.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts")
                                    if (parts != null && parts.length() > 0) {
                                        val text = parts.getJSONObject(0).optString("text", "")
                                        if (text.isNotEmpty()) {
                                            fullText += text
                                            onDelta(text)
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("JarvisApiClient", "JSON parse error on chunk: $data", e)
                            }
                        }
                    }
                }
                Result.success(AiResponse(text = fullText, toolCalls = null))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
"""

# Insert the function before streamNVIDIA
content = content.replace('    private fun streamNVIDIA(', gemini_func + '\n    private fun streamNVIDIA(')

# Reroute streamChat
content = content.replace(
    '''        val requestModel = if (model.startsWith("gemini")) model else ApiConfig.GEMINI_FLASH_MODEL
''',
    '''        val requestModel = if (model.startsWith("gemini")) model else ApiConfig.GEMINI_FLASH_MODEL
        if (provider.startsWith("gemini")) {
            return streamGeminiNative(apiKey, requestModel, systemPrompt, history, userMessage, allowTools, onDelta)
        }
'''
)

with open("app/src/main/java/com/jarvis/app/assistant/JarvisApiClient.kt", "w") as f:
    f.write(content)

print("done")
