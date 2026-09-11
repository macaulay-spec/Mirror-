import re

with open("app/src/main/java/com/jarvis/app/assistant/JarvisApiClient.kt", "r") as f:
    content = f.read()

chat_func = """    suspend fun chat(
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
            currentProvider = ApiConfig.getNextProvider(currentProvider)
        }
        return@withContext lastResult
    }"""

content = re.sub(
    r"    suspend fun chat\([\s\S]*?\): Result<AiResponse> = withContext\(Dispatchers\.IO\) \{\n        chatDirect\(systemPrompt, history, userMessage, provider, model, allowTools\)\n    \}",
    chat_func,
    content
)

with open("app/src/main/java/com/jarvis/app/assistant/JarvisApiClient.kt", "w") as f:
    f.write(content)

print("done")
