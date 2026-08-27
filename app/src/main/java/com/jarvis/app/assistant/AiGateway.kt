package com.jarvis.app.assistant

import com.jarvis.agent.ai.ProviderRouter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Optional cloud AI gateway.
 *
 * Uses ProviderRouter to seamlessly route across developer keys,
 * custom keys, and multi-AI providers (Gemini, OpenAI, Claude, Groq, OpenRouter).
 */
class AiGateway(
    private val router: ProviderRouter = ProviderRouter()
) {
    suspend fun chat(system: String, history: List<Pair<String, String>>, user: String): String? =
        withContext(Dispatchers.IO) {
            val res = router.executeWithFallback(
                systemInstruction = system,
                history = history,
                userMessage = user
            )
            res.getOrNull()?.content
        }
}
