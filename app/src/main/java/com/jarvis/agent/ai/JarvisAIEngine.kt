package com.jarvis.agent.ai

import android.content.Context
import com.jarvis.android.accessibility.JarvisAccessibilityService
import com.jarvis.agent.memory.JarvisMemoryManager
import com.jarvis.agent.tool.ToolRegistry
import com.jarvis.core.model.JarvisVisualState
import com.jarvis.core.model.RiskLevel
import com.jarvis.core.model.ToolExecutionRequest
import com.jarvis.core.model.ToolExecutionResult
import com.jarvis.app.assistant.JarvisApiClient
import com.jarvis.agent.ai.plan.AgentStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class JarvisEngineResult(
    val reply: String,
    val state: JarvisVisualState = JarvisVisualState.SUCCESS,
    val toolResult: ToolExecutionResult? = null,
    // ADDED: lets AgentExecutor hand a high-risk, model-issued tool call back up
    // to AssistantOrchestrator for confirmation instead of firing it unconditionally.
    val pendingConfirmation: ToolExecutionRequest? = null
)

/**
 * JarvisAIEngine  the LLM reasoning core.
 *
 * Architecture:
 *   BEFORE: 200-line deterministic router intercepted most commands before the AI
 *           saw them, defeating function-calling entirely.
 *   AFTER:  Four truly zero-latency hardware commands (battery, time, flashlight)
 *           are fast-pathed. Everything else goes through the LLM with real
 *           function declarations so the model decides what tool to call.
 *
 * Tool IDs used in the fast-path match the IDs registered in ToolRegistry:
 *   device_battery   registered as "device_battery"
 *   device_time      registered as "device_time"
 *   device_flashlight  registered as "device_flashlight"
 *
 * NEW: Task execution tracking for TaskExecutionScreen
 * - Passes step update callbacks to AgentExecutor
 * - Tracks multi-step execution progress
 */
class JarvisAIEngine(private val context: Context) {
    private val apiClient = JarvisApiClient()
    val memoryManager = JarvisMemoryManager(context)
    private val agentExecutor = AgentExecutor(context, apiClient, memoryManager)

    val systemPrompt: String
        get() {
            val session = memoryManager.getSessionContext()
            val name = com.jarvis.app.config.ApiConfig.userName
            val tone = com.jarvis.app.config.ApiConfig.personalityTone
            val toneInstruction = when (tone) {
                "conversational" -> "Speak in a friendly, natural conversational tone."
                "executive"      -> "Speak in a concise, executive briefing style — bullet points when appropriate."
                else             -> "Speak in the calm, precise style of the JARVIS AI from Iron Man — British, direct, never verbose."
            }

            val a11y = com.jarvis.android.accessibility.JarvisAccessibilityService.instance
            val a11yStatus = if (a11y != null) {
                val pkg = a11y.currentPackageName ?: "unknown"
                val screenTexts = a11y.findTextOnScreen().take(25)
                val textSummary = if (screenTexts.isNotEmpty()) screenTexts.joinToString(" | ") else "No text found"
                """
                [REAL-TIME SCREEN ACCESSIBILITY: ACTIVE]
                - Foreground App: $pkg
                - Currently Visible Screen Text: $textSummary
                - YOU HAVE DIRECT SIGHT OF THIS SCREEN. When asked about what is on screen or whether you see a text/button, reference this exact ground truth.
                - You can interact with any on-screen element using click_element(text="...") or tap(x, y).
                """.trimIndent()
            } else {
                """
                [SCREEN ACCESSIBILITY: DISABLED]
                - Jarvis Accessibility Service is NOT currently enabled in Android Settings.
                - You CANNOT see what is on screen or tap UI elements.
                - If the user asks you to see the screen, tap a button, or asks if you see text, DO NOT GUESS OR LIE. Explicitly inform the user: "My Accessibility Service is currently disabled in Android settings. Please enable Jarvis in Accessibility settings so I can see and interact with your screen."
                """.trimIndent()
            }

            return """
You are JARVIS, an elite, highly intelligent, and proactive autonomous AI operating layer for Android.
Your primary user is $name.

$toneInstruction

Current device context:
- Active package/app: ${session.currentApp}
- Ongoing background task: ${session.currentTask}
- Last executed action: ${session.lastAction} (Result: ${session.lastActionResult})

$a11yStatus

CRITICAL INSTRUCTIONS:
1. CAPABILITY: You are deeply integrated into the Android system. You can interact with UI elements, read screen contents, toggle hardware states, send messages, fetch data from the web, and synthesize memories.
2. PROACTIVITY & MEMORY: Utilize your long-term memory graph to remember preferences, people, and context. If asked about previous events, check your context graph.
3. REAL SCREEN GROUNDING: Never hallucinate seeing on-screen buttons or text that are not listed in your active screen context. If accessibility is disabled, tell the user to enable it.
4. TOOL CALLING: You have native function-calling abilities. ONLY call a tool if it is absolutely necessary to fulfill the user's specific request. If the user is just saying hello or making general conversation, DO NOT call any tools. Just reply directly.
5. PROBLEM SOLVING: Think step-by-step for complex requests. If a tool fails, dynamically adapt and try an alternative approach.
6. SAFETY: For irreversible or high-risk actions (sending emails, making calls, modifying critical settings), prompt the user for confirmation.
7. RESPONSE REQUIREMENT: After executing a tool or receiving a tool's result, you MUST provide a final conversational response to the user summarizing the outcome. NEVER leave your final message empty.
8. AESTHETICS: Never expose internal technical details, raw JSON, tool names, or stack traces to the user. Your output must always be refined, natural, and helpful.
            """.trimIndent()
        }

    suspend fun processCommand(
        rawInput: String,
        sessionId: String = "default",
        onChunk: ((String) -> Unit)? = null,
        onStepUpdate: ((List<AgentStep>, Int) -> Unit)? = null,
        onComplete: ((String, Boolean) -> Unit)? = null
    ): JarvisEngineResult =
        withContext(Dispatchers.Default) {
            val input = rawInput.trim()
            if (input.isBlank()) return@withContext JarvisEngineResult("Please go ahead.")

            // Update context from accessibility service
            val activeApp = JarvisAccessibilityService.instance?.currentPackageName ?: "unknown"
            memoryManager.updateSessionContext(app = activeApp, task = "processing", action = null, result = null, details = null)
            memoryManager.addConversation("user", input, sessionId)

            val isChat = isConversational(input)

            // Retrieve memory context only if not a pure greeting/chat
            val relevantMemories = if (isChat) emptyList() else memoryManager.recallRelevant(input)
            val recentConv = memoryManager.conversationForSession(sessionId).takeLast(10)
            val history = recentConv.map { it.role to it.text }.toMutableList()
            // Inject real-time screen inspection into history
            val a11y = com.jarvis.android.accessibility.JarvisAccessibilityService.instance
            val screenContext = if (a11y != null) {
                val pkg = a11y.currentPackageName ?: "unknown"
                val texts = a11y.findTextOnScreen().take(25)
                val textSummary = if (texts.isNotEmpty()) texts.joinToString(" | ") else "No text detected"
                "[Active Screen: app=$pkg, visible_texts=[$textSummary]]"
            } else {
                "[Active Screen: Accessibility Service DISABLED in system settings. Cannot read screen or click elements.]"
            }
            history.add(0, "system" to screenContext)

            if (relevantMemories.isNotEmpty()) {
                val memSnippet = "Relevant memories:\n" + relevantMemories.joinToString("\n") { "- ${it.content}" }
                history.add(1, "system" to memSnippet)
            }

            // Fast-path: only truly zero-latency hardware commands skip the LLM.
            // Tool IDs match ToolRegistry registrations exactly.
            val fastResult = fastPath(input)
            if (fastResult != null) {
                val reply = ReplySanitizer.sanitize(
                    if (fastResult.success) fastResult.verificationDetails ?: "Done."
                    else fastResult.error ?: "That didn't work."
                )
                memoryManager.addConversation("jarvis", reply, sessionId)
                return@withContext JarvisEngineResult(
                    reply = reply,
                    state = if (fastResult.success) JarvisVisualState.SUCCESS else JarvisVisualState.ERROR,
                    toolResult = fastResult
                )
            }

            // All other commands — LLM with real function calling
            // For pure conversational queries, disable tools for instant sub-second response
            val agentResult = agentExecutor.executeTask(
                systemPrompt = systemPrompt,
                initialHistory = history,
                userMessage = input,
                allowTools = !isChat,
                onChunk = onChunk,
                onStepUpdate = onStepUpdate,
                onComplete = onComplete
            )

            // Minimal offline fallback when the AI key is invalid or network is down
            val finalResult = if (agentResult.state == JarvisVisualState.ERROR) {
                if (!com.jarvis.app.config.ApiConfig.hasAI) {
                    JarvisEngineResult(reply = localFallback(input), state = JarvisVisualState.SUCCESS)
                } else {
                    agentResult.copy(reply = ReplySanitizer.sanitize(agentResult.reply))
                }
            } else {
                agentResult.copy(reply = ReplySanitizer.sanitize(agentResult.reply))
            }

            memoryManager.addConversation("jarvis", finalResult.reply, sessionId)
            finalResult
        }

    private fun isConversational(input: String): Boolean {
        val lower = input.lowercase().trim()
        if (lower.contains("screen") || lower.contains("button") || lower.contains("tap") ||
            lower.contains("click") || lower.contains("press") || lower.contains("read") ||
            lower.contains("see") || lower.contains("look") || lower.contains("type") ||
            lower.contains("scroll") || lower.contains("open") || lower.contains("launch") ||
            lower.contains("send") || lower.contains("message") || lower.contains("call") ||
            lower.contains("whatsapp") || lower.contains("setting") || lower.contains("turn") ||
            lower.contains("enable") || lower.contains("disable") || lower.contains("find")
        ) return false

        val clean = lower.replace(Regex("[?!.,]"), "")
        val conversationalPhrases = setOf(
            "hi", "hello", "hey", "howdy", "sup", "yo", "good morning", "good evening", "good afternoon",
            "how are you", "how are you doing", "how r u", "how is it going", "what's up", "whats up",
            "who are you", "what is your name", "tell me about yourself", "what can you do", "help",
            "thank you", "thanks", "ok", "okay", "cool", "nice", "awesome", "great", "good job",
            "tell me a joke", "what is the meaning of life", "goodnight", "bye", "see you"
        )
        if (clean in conversationalPhrases) return true
        if (clean.startsWith("hi ") || clean.startsWith("hello ") || clean.startsWith("hey ")) return true
        return false
    }

    /**
     * Fast-path handles only the four commands where sub-100ms matters.
     * Uses the same tool IDs that ToolRegistry registers  verified against ToolRegistry.kt.
     */
    private suspend fun fastPath(input: String): ToolExecutionResult? {
        val lower = input.lowercase().trim()
        return when {
            lower == "battery" || lower == "battery level" || lower.startsWith("how much battery") ->
                ToolRegistry.execute(context, ToolExecutionRequest("device_battery", "Battery", emptyMap(), RiskLevel.LEVEL_0))
            lower == "time" || lower == "what time" || lower == "what time is it" || lower == "what's the time" ->
                ToolRegistry.execute(context, ToolExecutionRequest("device_time", "Time", emptyMap(), RiskLevel.LEVEL_0))
            lower.contains("flashlight on") || lower.contains("torch on") ||
            lower.contains("turn on flashlight") || lower.contains("turn on torch") ->
                ToolRegistry.execute(context, ToolExecutionRequest("device_flashlight", "Flashlight On", mapOf("enabled" to true), RiskLevel.LEVEL_0))
            lower.contains("flashlight off") || lower.contains("torch off") ||
            lower.contains("turn off flashlight") || lower.contains("turn off torch") ->
                ToolRegistry.execute(context, ToolExecutionRequest("device_flashlight", "Flashlight Off", mapOf("enabled" to false), RiskLevel.LEVEL_0))
            else -> null
        }
    }

    /** Minimal offline fallback when the AI key is invalid or network is down. */
    private fun localFallback(input: String): String {
        val lower = input.lowercase().trim()
        val name = com.jarvis.app.config.ApiConfig.userName
        return when {
            lower.contains("who are you") || lower.contains("what is your name") ->
                "I'm JARVIS, your personal AI assistant, $name. I'm running on local protocols  my AI connection needs a valid key."
            lower.contains("hello") || lower.contains("hi") || lower.contains("hey") ->
                "Hello $name. I'm here  though my AI reasoning is offline. Check Settings  Access Control to verify your key."
            lower.contains("how are you") ->
                "Local systems are nominal, $name. My cloud reasoning is unavailable right now."
            lower.contains("thank") ->
                "Always at your service, $name."
            lower.contains("what can you do") || lower.contains("help") || lower.contains("capabilities") ->
                "I can control your device, open apps, read notifications, answer questions and much more  once my AI key is configured. Go to Settings  Access Control."
            else ->
                "I'm operating on local protocols only, $name. Add a valid xAI or Gemini key in Settings  Access Control to restore full intelligence."
        }
    }
}
