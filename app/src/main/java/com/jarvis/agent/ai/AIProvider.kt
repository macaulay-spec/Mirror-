package com.jarvis.agent.ai

data class AIResponse(
    val content: String,
    val toolCalls: List<Map<String, Any>> = emptyList(),
    val success: Boolean = true,
    val error: String? = null
)

interface AIProvider {
    val name: String
    fun supportsTools(): Boolean = true
    fun supportsVision(): Boolean = true
    fun supportsText(): Boolean = true
    fun getModels(): List<String>

    suspend fun generate(
        apiKey: String,
        model: String,
        systemInstruction: String,
        history: List<Pair<String, String>>,
        userMessage: String
    ): Result<AIResponse>

    suspend fun stream(
        apiKey: String,
        model: String,
        systemInstruction: String,
        history: List<Pair<String, String>>,
        userMessage: String,
        onChunk: (String) -> Unit
    ): Result<AIResponse>

    /**
     * Function calling, where the provider supports it.
     *
     * Default implementation just ignores the tool schemas, so the eight providers without
     * native tool support keep working unchanged. Gemini and OpenAI override this.
     */
    suspend fun generateWithTools(
        apiKey: String,
        model: String,
        systemInstruction: String,
        history: List<Pair<String, String>>,
        userMessage: String,
        tools: org.json.JSONArray
    ): Result<AIResponse> = generate(apiKey, model, systemInstruction, history, userMessage)
}
