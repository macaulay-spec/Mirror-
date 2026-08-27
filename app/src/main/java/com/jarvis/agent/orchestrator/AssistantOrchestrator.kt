package com.jarvis.agent.orchestrator

import android.content.Context
import com.jarvis.agent.ai.JarvisAIEngine
import com.jarvis.agent.tool.ToolRegistry
import com.jarvis.android.voice.JarvisVoiceEngine
import com.jarvis.app.assistant.GeminiService
import com.jarvis.app.config.ApiConfig
import com.jarvis.app.memory.AppDatabase
import com.jarvis.app.memory.MemoryEntity
import com.jarvis.core.model.AssistantMessage
import com.jarvis.core.model.JarvisVisualState
import com.jarvis.core.model.MessageRole
import com.jarvis.core.model.RiskLevel
import com.jarvis.core.model.ToolExecutionRequest
import com.jarvis.core.model.ToolExecutionResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * AssistantOrchestrator: The central AI reasoning and execution engine backed by JarvisAIEngine.
 * Pipeline: USER INPUT -> JARVIS AI ENGINE -> PROVIDER ROUTER -> TOOL EXECUTION -> VERIFICATION -> MEMORY -> RESPONSE
 */
class AssistantOrchestrator(
    private val context: Context,
    private val geminiService: GeminiService,
    private val database: AppDatabase,
    var voiceEngine: JarvisVoiceEngine? = null
) {
    private val aiEngine = JarvisAIEngine(context)

    private val _visualState = MutableStateFlow(JarvisVisualState.IDLE)
    val visualState: StateFlow<JarvisVisualState> = _visualState.asStateFlow()

    private val _messages = MutableStateFlow<List<AssistantMessage>>(emptyList())
    val messages: StateFlow<List<AssistantMessage>> = _messages.asStateFlow()

    private val _pendingConfirmation = MutableStateFlow<ToolExecutionRequest?>(null)
    val pendingConfirmation: StateFlow<ToolExecutionRequest?> = _pendingConfirmation.asStateFlow()

    fun setVisualState(state: JarvisVisualState) {
        _visualState.value = state
    }

    fun postSystemMessage(text: String) {
        _messages.value = _messages.value + AssistantMessage(
            role = MessageRole.SYSTEM,
            text = text
        )
    }

    suspend fun submitUserInput(userInput: String) {
        processUserCommand(userInput)
    }

    fun confirmToolExecution(request: ToolExecutionRequest) {
        CoroutineScope(Dispatchers.Main).launch {
            _pendingConfirmation.value = null
            _visualState.value = JarvisVisualState.EXECUTING
            val result = ToolRegistry.execute(context, request)
            handleExecutionResult(result)
        }
    }

    fun rejectToolExecution() {
        _pendingConfirmation.value = null
        _visualState.value = JarvisVisualState.IDLE
        postSystemMessage("Execution canceled by user protocol.")
    }

    suspend fun clearHistory() {
        _messages.value = emptyList()
        database.memoryDao().clear()
        database.conversationDao().clear()
        aiEngine.memoryManager.clearAllMemories()
        aiEngine.memoryManager.resetSession()
        _visualState.value = JarvisVisualState.IDLE
    }

    suspend fun processUserCommand(userInput: String) {
        if (userInput.isBlank()) return

        // 1. Add User Message
        val userMsg = AssistantMessage(role = MessageRole.USER, text = userInput)
        _messages.value = _messages.value + userMsg

        _visualState.value = JarvisVisualState.THINKING

        try {
            val engineResult = aiEngine.processCommand(userInput)
            val jarvisMsg = AssistantMessage(role = MessageRole.JARVIS, text = engineResult.reply)
            _messages.value = _messages.value + jarvisMsg
            _visualState.value = engineResult.state
            speakIfPossible(engineResult.reply)
            delay(400)
            _visualState.value = JarvisVisualState.IDLE
        } catch (e: Exception) {
            _visualState.value = JarvisVisualState.ERROR
            val errorMsg = AssistantMessage(
                role = MessageRole.JARVIS,
                text = "Neural sync failed: ${e.localizedMessage ?: "Unknown error"}"
            )
            _messages.value = _messages.value + errorMsg
            _visualState.value = JarvisVisualState.IDLE
        }
    }

    private suspend fun checkDeterministicRouting(input: String): ToolExecutionResult? {
        val lower = input.lowercase().trim()
        return when {
            lower.contains("battery") -> {
                ToolRegistry.execute(context, ToolExecutionRequest("device_battery", "Get Battery", emptyMap(), RiskLevel.LEVEL_0))
            }
            lower.startsWith("time") || lower.startsWith("what time") -> {
                ToolRegistry.execute(context, ToolExecutionRequest("device_time", "Get Time", emptyMap(), RiskLevel.LEVEL_0))
            }
            lower.startsWith("open chrome") -> {
                ToolRegistry.execute(context, ToolExecutionRequest("app_launch", "Open Chrome", mapOf("app_name" to "chrome"), RiskLevel.LEVEL_1))
            }
            lower.startsWith("open whatsapp") -> {
                ToolRegistry.execute(context, ToolExecutionRequest("app_launch", "Open WhatsApp", mapOf("app_name" to "whatsapp"), RiskLevel.LEVEL_1))
            }
            lower.startsWith("search for ") -> {
                val q = lower.removePrefix("search for ").trim()
                ToolRegistry.execute(context, ToolExecutionRequest("web_search", "Web Search", mapOf("query" to q), RiskLevel.LEVEL_1))
            }
            lower.contains("read screen") || lower.contains("what's on screen") || lower.contains("screen text") -> {
                ToolRegistry.execute(context, ToolExecutionRequest("screen_read", "Read Screen Structure", emptyMap(), RiskLevel.LEVEL_0))
            }
            lower.startsWith("find ") -> {
                val query = lower.removePrefix("find ").trim()
                ToolRegistry.execute(context, ToolExecutionRequest("find_text", "Find Text", mapOf("query" to query), RiskLevel.LEVEL_0))
            }
            lower.startsWith("click ") -> {
                val target = input.substring(6).trim()
                ToolRegistry.execute(context, ToolExecutionRequest("click_element", "Click Element", mapOf("target" to target), RiskLevel.LEVEL_1))
            }
            lower.startsWith("type ") -> {
                val text = input.substring(5).trim()
                ToolRegistry.execute(context, ToolExecutionRequest("type_text", "Type Text", mapOf("text" to text, "marker" to ""), RiskLevel.LEVEL_1))
            }
            lower.contains("scroll down") || lower.contains("scroll forward") -> {
                ToolRegistry.execute(context, ToolExecutionRequest("scroll", "Scroll Screen", mapOf("direction" to "forward"), RiskLevel.LEVEL_1))
            }
            lower.contains("scroll up") || lower.contains("scroll backward") -> {
                ToolRegistry.execute(context, ToolExecutionRequest("scroll", "Scroll Screen", mapOf("direction" to "backward"), RiskLevel.LEVEL_1))
            }
            lower == "back" || lower == "press back" -> {
                ToolRegistry.execute(context, ToolExecutionRequest("press_back", "Press Back", emptyMap(), RiskLevel.LEVEL_1))
            }
            lower == "home" || lower == "press home" -> {
                ToolRegistry.execute(context, ToolExecutionRequest("press_home", "Press Home", emptyMap(), RiskLevel.LEVEL_1))
            }
            lower == "recents" || lower == "recent apps" -> {
                ToolRegistry.execute(context, ToolExecutionRequest("open_recents", "Open Recents", emptyMap(), RiskLevel.LEVEL_1))
            }
            lower.startsWith("remember ") -> {
                val toRemember = input.substring(9).trim()
                database.memoryDao().insert(MemoryEntity(content = toRemember, type = "fact"))
                ToolExecutionResult(
                    toolId = "memory_save",
                    success = true,
                    data = mapOf("saved" to toRemember),
                    verificationDetails = "I will remember that: \"$toRemember\""
                )
            }
            else -> null
        }
    }

    private suspend fun parseAndExecuteModelResponse(response: String) {
        // Check if response contains a tool action JSON block
        if (response.contains("\"tool\":") || response.contains("```json")) {
            _visualState.value = JarvisVisualState.EXECUTING
            val reply = AssistantMessage(role = MessageRole.JARVIS, text = response)
            _messages.value = _messages.value + reply
            _visualState.value = JarvisVisualState.SUCCESS
            _visualState.value = JarvisVisualState.IDLE
        } else {
            _visualState.value = JarvisVisualState.SPEAKING
            _messages.value = _messages.value + AssistantMessage(role = MessageRole.JARVIS, text = response)
            speakIfPossible(response)
            _visualState.value = JarvisVisualState.IDLE
        }
    }

    private fun speakIfPossible(text: String) {
        try {
            voiceEngine?.speak(text)
        } catch (_: Exception) { }
    }

    private fun handleExecutionResult(result: ToolExecutionResult) {
        _visualState.value = if (result.success) JarvisVisualState.SUCCESS else JarvisVisualState.ERROR
        val text = result.verificationDetails ?: result.error ?: "Operation executed."
        _messages.value = _messages.value + AssistantMessage(
            role = MessageRole.JARVIS,
            text = text,
            toolResult = result
        )
        speakIfPossible(text)
        _visualState.value = JarvisVisualState.IDLE
    }

    private fun buildSystemContext(): String {
        return """
            You are JARVIS, a personal AI operating layer for Android.
            You are direct, precise, concise, and focused on executing actions via Accessibility and device tools.
            Available tools:
            - device_battery: check battery percentage
            - device_time: check current system time
            - app_launch: launch installed apps (Chrome, WhatsApp, Camera, Settings, Calendar)
            - web_search: search query on the web
            - screen_read: read structured screen elements and visible text
            - find_text: search for specific text on screen
            - click_element: click an interactive UI element by text or description
            - type_text: enter text into editable input fields
            - scroll: scroll forward or backward
            - tap: tap at specific coordinates
            - swipe: perform gesture swipe
            - press_back: press system back button
            - press_home: press system home button
            - open_recents: open recent apps overview
        """.trimIndent()
    }

    fun emergencyStop() {
        voiceEngine?.stopSpeaking()
        voiceEngine?.stopListening()
        _visualState.value = JarvisVisualState.IDLE
        _pendingConfirmation.value = null
        _messages.value = _messages.value + AssistantMessage(
            role = MessageRole.SYSTEM,
            text = "EMERGENCY STOP TRIGGERED: Active tasks halted."
        )
    }
}
