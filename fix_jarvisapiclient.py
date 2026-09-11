import re

with open("app/src/main/java/com/jarvis/app/assistant/JarvisApiClient.kt", "r") as f:
    content = f.read()

# Replace executeNVIDIA with a proper executeOpenAIFormat
replacement = """    private fun executeOpenAIFormat(
        apiKey: String,
        provider: String,
        model: String,
        systemPrompt: String,
        history: List<Pair<String, String>>,
        userMessage: String,
        allowTools: Boolean = true
    ): Result<AiResponse> {
        val endpoint = when {
            provider == "grok" || provider == "xai" -> "https://api.x.ai/v1/chat/completions"
            provider == "openai" -> "https://api.openai.com/v1/chat/completions"
            provider == "groq" -> "https://api.groq.com/openai/v1/chat/completions"
            provider == "openrouter" -> "https://openrouter.ai/api/v1/chat/completions"
            provider == "cerebras" -> "https://api.cerebras.ai/v1/chat/completions"
            provider == "mistral" -> "https://api.mistral.ai/v1/chat/completions"
            else -> "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions"
        }
        
        val requestModel = when {
            provider == "grok" || provider == "xai" -> "grok-beta"
            provider == "groq" -> "llama3-70b-8192"
            provider == "openrouter" -> "mistralai/mistral-7b-instruct:free"
            provider == "cerebras" -> "llama3.1-70b"
            else -> model
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
            .put("model", requestModel)
            .put("messages", messages)

        if (allowTools) {
            val tools = ToolSchema.forOpenAI()
            if (tools.length() > 0) {
                payload.put("tools", tools)
                payload.put("tool_choice", "auto")
            }
        }

        val requestBuilder = Request.Builder()
            .post(payload.toString().toRequestBody("application/json".toMediaType()))

        if (apiKey.startsWith("AIzaSy")) {
            requestBuilder.url("$endpoint?key=$apiKey")
        } else {
            requestBuilder.url(endpoint)
            if (apiKey.isNotBlank()) {
                requestBuilder.header("Authorization", "Bearer $apiKey")
            }
        }
"""

start_idx = content.find("private fun executeNVIDIA(")
end_idx = content.find("        val request = requestBuilder.build()", start_idx)

if start_idx != -1 and end_idx != -1:
    content = content[:start_idx] + replacement + content[end_idx:]

content = content.replace("executeNVIDIA(currentApiKey", "executeOpenAIFormat(currentApiKey")

with open("app/src/main/java/com/jarvis/app/assistant/JarvisApiClient.kt", "w") as f:
    f.write(content)
print("done")
