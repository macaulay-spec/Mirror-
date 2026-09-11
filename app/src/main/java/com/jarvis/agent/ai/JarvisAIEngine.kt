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
                val nodes = a11y.getStructuredScreenData().take(20)
                val textSummary = if (nodes.isNotEmpty()) {
                    nodes.joinToString("\n                ") { n -> 
                        val txt = n["text"] as? String ?: ""
                        val desc = n["contentDescription"] as? String ?: ""
                        val clickable = n["clickable"] as? Boolean ?: false
                        val cx = n["centerX"] as? Float ?: 0f
                        val cy = n["centerY"] as? Float ?: 0f
                        "- ${if (txt.isNotBlank()) "Text: '$txt'" else "Desc: '$desc'"} [Clickable: $clickable, coords: ($cx, $cy)]"
                    }
                } else "No readable elements found"
                """
                [REAL-TIME SCREEN ACCESSIBILITY: ACTIVE]
                - Foreground App: $pkg
                - Currently Visible Screen Text: $textSummary
                - YOU HAVE DIRECT SIGHT OF THIS SCREEN. When asked about what is on screen or whether you see a text/button, reference this exact ground truth.
                - You can interact with any on-screen element using click_element(text="...") or tap(x, y).
                """.trimIndent()
            } else {
                """
                [SCREEN ACCESSIBILITY: UNAVAILABLE THIS SESSION]
                - The Jarvis Accessibility Service is not enabled, so screen reading and UI tapping are unavailable right now.
                - This is background capability info ONLY — it is NOT a topic of conversation.
                - NEVER bring this up on its own. Do not mention it for greetings, small talk, questions, memories, or anything that does not need the screen. Respond normally to those.
                - Mention it ONLY if the user's current request explicitly requires seeing or touching the screen (e.g. "read my screen", "tap the button"), and then in one short natural sentence at the END of your reply.
                - Never open a reply with it. Never repeat it every turn.
                """.trimIndent()
            }

            // Wave A brain-context fix: the model previously had NO idea what
            // time it was, so "tomorrow 8am" / "in 20 minutes" were guessed.
            val timeFmt = java.text.SimpleDateFormat("EEEE, d MMMM yyyy 'at' h:mm a", java.util.Locale.US)
            val now = java.util.Calendar.getInstance().time
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager
            val batteryPct = batteryManager?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            val isCharging = try { batteryManager?.isCharging == true } catch (_: Exception) { false }
            val batteryLine = if (batteryPct > 0)
                "- Battery: $batteryPct percent${if (isCharging) " (charging)" else ""}."
            else ""

            return """
You are JARVIS, an elite, highly intelligent, and proactive autonomous AI operating layer for Android.
Your primary user is $name.

$toneInstruction

CURRENT TIME (authoritative): ${timeFmt.format(now)} ${java.util.TimeZone.getDefault().id}
Resolve every relative time ("tomorrow", "tonight", "in 20 minutes") against this. Never ask the user what time or day it is.

Current device context:
$batteryLine
- Active package/app: ${session.currentApp}
- Ongoing background task: ${session.currentTask}
- Last executed action: ${session.lastAction} (Result: ${session.lastActionResult})

$a11yStatus

CRITICAL INSTRUCTIONS:
1. CORE MISSION: You are an autonomous agent deeply integrated into the Android system. You must THINK step-by-step and ACT.
2. REACT LOOP: When the user asks you to interact with the screen or an app:
   - Step 1: Use `screen_read` to analyze the UI structure.
   - Step 2: Identify the exact `centerX` and `centerY` of the target element.
   - Step 3: Use the `tap` tool with those exact `x` and `y` coordinates to click it.
   - Step 4: If you need to type, use `tap` to focus the field, then use `type_text` or `adb_shell` if necessary.
   - Step 5: NEVER guess coordinates. ALWAYS `screen_read` first to verify the screen state before proceeding.
3. PROACTIVITY & MEMORY: Utilize your long-term memory graph to remember preferences, people, and context.
4. REAL SCREEN GROUNDING: Never hallucinate seeing on-screen buttons or text that are not listed in your active screen context. 
5. PROBLEM SOLVING: Think step-by-step for complex requests. If a tool fails, dynamically adapt and try an alternative approach.
6. TOOL CALLING: You have native function-calling abilities. ONLY call a tool if it is absolutely necessary to fulfill the user's specific request.
7. RESPONSE REQUIREMENT: After executing a tool, you MUST provide a final conversational response to the user summarizing the outcome.
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

            // A-002 fix: every utterance gets the same treatment — memory recall
            // and tool access. The model decides conversational vs. action (its
            // system prompt already forbids tool calls for pure chat); no keyword
            // buckets deciding what the user "meant" before the AI sees it.
            val relevantMemories = memoryManager.recallRelevant(input)
            val recentConv = memoryManager.conversationForSession(sessionId).takeLast(10)
            val history = recentConv.map { it.role to it.text }.toMutableList()
            // Inject real-time screen inspection into history
            val a11y = com.jarvis.android.accessibility.JarvisAccessibilityService.instance
            val screenContext = if (a11y != null) {
                val pkg = a11y.currentPackageName ?: "unknown"
                val nodes = a11y.getStructuredScreenData().take(25)
                val textSummary = if (nodes.isNotEmpty()) {
                    nodes.joinToString("\n") { n ->
                        val txt = n["text"] as? String ?: ""
                        val desc = n["contentDescription"] as? String ?: ""
                        val cx = n["centerX"] as? Float ?: 0f
                        val cy = n["centerY"] as? Float ?: 0f
                        "- [\"$txt\" | \"$desc\"] at ($cx, $cy)"
                    }
                } else "No readable elements found"
                "[Active Screen: app=$pkg\nVisible Elements (x, y):\n$textSummary]"
            } else {
                // Quiet capability note.
                "[Background note: screen reading is unavailable this session. Ignore this unless the request needs the screen — do not mention it unprompted.]"
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

            // All other commands — LLM with real function calling.
            // The model, not a keyword list, decides whether tools are needed.
            val agentResult = agentExecutor.executeTask(
                systemPrompt = systemPrompt,
                initialHistory = history,
                userMessage = input,
                allowTools = true,
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

    /**
     * Fast-path handles zero-latency device commands directly on-device
     * (apps, volume, flashlight, battery, WhatsApp quick actions).
     */
    private suspend fun fastPath(input: String): ToolExecutionResult? {
        val lower = input.lowercase().trim()

        // 1. Hardware & Sensors
        if (lower == "battery" || lower == "battery level" || lower.startsWith("how much battery")) {
            return ToolRegistry.execute(context, ToolExecutionRequest("device_battery", "Battery", emptyMap(), RiskLevel.LEVEL_0))
        }
        if (lower == "time" || lower == "what time" || lower == "what time is it" || lower == "what's the time") {
            return ToolRegistry.execute(context, ToolExecutionRequest("device_time", "Time", emptyMap(), RiskLevel.LEVEL_0))
        }
        if (lower.contains("flashlight on") || lower.contains("torch on") ||
            lower.contains("turn on flashlight") || lower.contains("turn on torch")) {
            return ToolRegistry.execute(context, ToolExecutionRequest("device_flashlight", "Flashlight On", mapOf("enabled" to true), RiskLevel.LEVEL_0))
        }
        if (lower.contains("flashlight off") || lower.contains("torch off") ||
            lower.contains("turn off flashlight") || lower.contains("turn off torch")) {
            return ToolRegistry.execute(context, ToolExecutionRequest("device_flashlight", "Flashlight Off", mapOf("enabled" to false), RiskLevel.LEVEL_0))
        }

        // 2. Volume Controls
        if (lower.contains("volume up") || lower.contains("increase volume") || lower.contains("louder")) {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
            audioManager?.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_RAISE, android.media.AudioManager.FLAG_SHOW_UI)
            return ToolExecutionResult(toolId = "volume", success = true, data = null, verificationDetails = "Increased volume.")
        }
        if (lower.contains("volume down") || lower.contains("decrease volume") || lower.contains("lower volume") || lower.contains("quieter")) {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
            audioManager?.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_LOWER, android.media.AudioManager.FLAG_SHOW_UI)
            return ToolExecutionResult(toolId = "volume", success = true, data = null, verificationDetails = "Decreased volume.")
        }

        // 3. WhatsApp Smart Messaging with Settle & Act
        if (lower.contains("whatsapp") && (lower.contains("send") || lower.contains("message") || lower.contains("tell") || lower.contains("tap on"))) {
            var targetRecipient = ""
            var msgText = ""

            val tapPattern = Regex("""tap on (?:name )?([a-zA-Z0-9_\- ]+?) (?:and|to) send (?:a message )?(.*)""", RegexOption.IGNORE_CASE)
            val matchTap = tapPattern.find(input)
            if (matchTap != null) {
                targetRecipient = matchTap.groupValues[1].trim()
                msgText = matchTap.groupValues[2].trim()
            } else {
                val sendToPattern = Regex("""send (?:a )?message to ([a-zA-Z0-9_\- ]+?) on whatsapp(?: saying|:)? (.*)""", RegexOption.IGNORE_CASE)
                val matchSend = sendToPattern.find(input)
                if (matchSend != null) {
                    targetRecipient = matchSend.groupValues[1].trim()
                    msgText = matchSend.groupValues[2].trim()
                }
            }

            if (msgText.isNotBlank()) {
                return ToolRegistry.execute(
                    context,
                    ToolExecutionRequest(
                        "send_whatsapp",
                        "Send WhatsApp Message",
                        mapOf("recipient" to targetRecipient, "message" to msgText),
                        RiskLevel.LEVEL_0
                    )
                )
            }
        }

        // 4. Instant App Launch (sub-100ms)
        if (lower.startsWith("open ") || lower.startsWith("launch ")) {
            val rawApp = lower.removePrefix("open ").removePrefix("launch ").trim()
            val cleanApp = rawApp.removePrefix("the ").removeSuffix(" app").trim()
            if (cleanApp.isNotBlank() && cleanApp.length < 30 && !cleanApp.contains(" and ") && !cleanApp.contains(" then ")) {
                return ToolRegistry.execute(
                    context,
                    ToolExecutionRequest("open_app", "Open Installed App", mapOf("app" to cleanApp), RiskLevel.LEVEL_0)
                )
            }
        }

        return null
    }

    /** Minimal offline fallback when the AI key is invalid or network is down. */
    private fun localFallback(input: String): String {
        val lower = input.lowercase().trim()
        val name = com.jarvis.app.config.ApiConfig.userName
        return when {
            lower.contains("who are you") || lower.contains("what is your name") ->
                "I am JARVIS, your personal AI assistant, $name. How may I assist you today?"
            lower.contains("hello") || lower.contains("hi") || lower.contains("hey") ->
                "Hello $name. Systems are operational and ready for your commands."
            lower.contains("how are you") ->
                "All systems nominal, $name. How can I help you right now?"
            lower.contains("thank") ->
                "Always at your service, $name."
            lower.contains("what can you do") || lower.contains("help") || lower.contains("capabilities") ->
                "I can control your device, launch apps, send WhatsApp messages, check battery status, manage tasks, and perform intelligent actions for you."
            else ->
                "I am at your service, $name. How can I assist you with your device or tasks?"
        }
    }
}
