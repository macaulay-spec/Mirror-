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
}
