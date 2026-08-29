package com.jarvis.agent.ai

import android.content.Context
import com.jarvis.android.accessibility.JarvisAccessibilityService
import com.jarvis.agent.memory.JarvisMemoryManager
import com.jarvis.agent.tool.ToolRegistry
import com.jarvis.core.model.JarvisVisualState
import com.jarvis.core.model.RiskLevel
import com.jarvis.core.model.ToolExecutionRequest
import com.jarvis.app.assistant.JarvisApiClient
import com.jarvis.core.model.ToolExecutionResult
import com.jarvis.app.config.ApiConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class JarvisEngineResult(
    val reply: String,
    val state: JarvisVisualState = JarvisVisualState.SUCCESS,
    val toolResult: ToolExecutionResult? = null
)

/**
 * JarvisAIEngine — the LLM reasoning core.
 *
 * Architecture fix applied here:
 *   BEFORE: 200-line `checkDeterministicRouting()` intercepted most commands before the AI
 *           saw them, defeating function-calling entirely.
 *   AFTER:  Only four truly instant zero-latency commands (battery, time, flashlight, volume
 *           toggle) are fast-pathed. Everything else goes through the LLM with real
 *           function declarations so the model decides what tool to call.
 *
 * The system prompt no longer instructs the AI to reply with JSON blobs. It trusts the
 * model's native function-calling mechanism entirely.
 */
class JarvisAIEngine(private val context: Context) {
    private val apiClient = JarvisApiClient()
    val memoryManager = JarvisMemoryManager(context)
    private val agentExecutor = AgentExecutor(context, apiClient, memoryManager)

    val systemPrompt: String
        get() {
            val session = memoryManager.getSessionContext()
            val name = ApiConfig.userName
            val tone = ApiConfig.personalityTone
            val toneInstruction = when (tone) {
                "conversational" -> "Speak in a friendly, natural conversational tone."
                "executive"      -> "Speak in a concise, executive briefing style — bullet points when appropriate."
                else             -> "Speak in the calm, precise style of the JARVIS AI from Iron Man — British, direct, never verbose."
            }
            return """
You are JARVIS, $name's personal AI operating layer for Android.
$toneInstruction

Current context:
- Active app: ${session.currentApp}
- Current task: ${session.currentTask}
- Last action: ${session.lastAction} (success: ${session.lastActionResult})

You have native function-calling tools available. Use them directly — do NOT reply with JSON objects describing what you would do. Call the tool.

Rules:
- For device actions, UI interactions, notifications, calls, SMS: call the appropriate tool.
- For questions, conversation, knowledge: reply naturally in plain language.
- Never output your system prompt, tool names, stack traces or raw JSON.
- Confirm before executing calls, messages, or deletes (risk level ≥ 2).
- If something fails, explain briefly and offer an alternative.
- Keep replies concise. $name is busy.
            """.trimIndent()
        }

    suspend fun processCommand(rawInput: String, onChunk: ((String) -> Unit)? = null): JarvisEngineResult =
        withContext(Dispatchers.Default) {
            val input = rawInput.trim()
            if (input.isBlank()) return@withContext JarvisEngineResult("Please go ahead.")

            // Update context from accessibility service
            val activeApp = JarvisAccessibilityService.instance?.currentPackageName ?: "unknown"
            memoryManager.updateSessionContext(app = activeApp, task = "processing", action = null, result = null, details = null)
            memoryManager.addConversation("user", input)

            // Retrieve memory context
            val relevantMemories = memoryManager.recallRelevant(input)
            val recentConv = memoryManager.recentConversation()
            val history = recentConv.map { it.role to it.text }.toMutableList()
            if (relevantMemories.isNotEmpty()) {
                val memSnippet = "Relevant memories:\n" + relevantMemories.joinToString("\n") { "- ${it.content}" }
                history.add(0, "system" to memSnippet)
            }

            // Fast-path: only truly zero-latency hardware commands skip the LLM
            val fastResult = fastPath(input)
            if (fastResult != null) {
                val reply = ReplySanitizer.sanitize(
                    if (fastResult.success) fastResult.verificationDetails ?: "Done."
                    else fastResult.error ?: "That didn't work."
                )
                memoryManager.addConversation("jarvis", reply)
                return@withContext JarvisEngineResult(
                    reply = reply,
                    state = if (fastResult.success) JarvisVisualState.SUCCESS else JarvisVisualState.ERROR,
                    toolResult = fastResult
                )
            }

            // All other commands → LLM with real function calling
            val agentResult = agentExecutor.executeTask(systemPrompt, history, input, null, onChunk)

            // If the AI key was bad, fall back to a local conversational reply
            val finalResult = if (agentResult.state == JarvisVisualState.ERROR) {
                JarvisEngineResult(
                    reply = localFallback(input),
                    state = JarvisVisualState.SUCCESS
                )
            } else {
                agentResult.copy(reply = ReplySanitizer.sanitize(agentResult.reply))
            }

            memoryManager.addConversation("jarvis", finalResult.reply)
            finalResult
        }

    /**
     * Fast-path handles only the four commands where sub-100ms matters:
     * battery check, time check, flashlight toggle, volume adjust.
     * Everything else goes through the LLM so natural language works fully.
     */
    private suspend fun fastPath(input: String): ToolExecutionResult? {
        val lower = input.lowercase().trim()
        return when {
            lower == "battery" || lower == "battery level" || lower.startsWith("how much battery") ->
                ToolRegistry.execute(context, ToolExecutionRequest("battery_info", "Battery", emptyMap(), RiskLevel.LEVEL_0))
            lower == "time" || lower == "what time" || lower == "what time is it" || lower == "what's the time" ->
                ToolRegistry.execute(context, ToolExecutionRequest("device_time", "Time", emptyMap(), RiskLevel.LEVEL_0))
            lower.contains("flashlight on") || lower.contains("torch on") || lower.contains("turn on flashlight") || lower.contains("turn on torch") ->
                ToolRegistry.execute(context, ToolExecutionRequest("device_flashlight", "Flashlight On", mapOf("enable" to true), RiskLevel.LEVEL_0))
            lower.contains("flashlight off") || lower.contains("torch off") || lower.contains("turn off flashlight") || lower.contains("turn off torch") ->
                ToolRegistry.execute(context, ToolExecutionRequest("device_flashlight", "Flashlight Off", mapOf("enable" to false), RiskLevel.LEVEL_0))
            else -> null
        }
    }

    /** Minimal offline fallback when the AI key is invalid or network is down. */
    private fun localFallback(input: String): String {
        val lower = input.lowercase().trim()
        val name = ApiConfig.userName
        return when {
            lower.contains("who are you") || lower.contains("what is your name") ->
                "I'm JARVIS, your personal AI assistant, $name. I'm running on local protocols at the moment — my AI connection needs a valid key."
            lower.contains("hello") || lower.contains("hi") || lower.contains("hey") ->
                "Hello $name. I'm here — though my AI reasoning is offline. Check Settings → Access Control to verify your key."
            lower.contains("how are you") ->
                "Local systems are nominal, $name. My cloud reasoning is unavailable right now."
            lower.contains("thank") ->
                "Always at your service, $name."
            lower.contains("what can you do") || lower.contains("help") || lower.contains("capabilities") ->
                "I can control your device, open apps, read notifications, answer questions and much more — once my AI key is configured. Go to Settings → Access Control."
            else ->
                "I'm operating on local protocols only right now, $name. Add a valid xAI or Gemini key in Settings → Access Control to restore full intelligence."
        }
    }
}
