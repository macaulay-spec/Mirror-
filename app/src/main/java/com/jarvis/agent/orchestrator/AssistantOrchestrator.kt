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
        aiEngine.dialogueManager.noteConfirmed()
        CoroutineScope(Dispatchers.Main).launch {
            _pendingConfirmation.value = null
            _visualState.value = JarvisVisualState.EXECUTING
            val result = ToolRegistry.execute(context, request)
            handleExecutionResult(result)
        }
    }

    fun rejectToolExecution() {
        aiEngine.dialogueManager.cancel()
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

            // A risky action (call, message, payment) is parked until the user confirms.
            // Attaching it to the message is what makes the CONFIRM card appear in the UI —
            // before this it was the one piece of the safety design that never fired.
            val confirmation = engineResult.confirmRequest
            _pendingConfirmation.value = confirmation

            val jarvisMsg = AssistantMessage(
                role = MessageRole.JARVIS,
                text = engineResult.reply,
                toolCall = confirmation,
                toolResult = engineResult.toolResult
            )
            _messages.value = _messages.value + jarvisMsg
            _visualState.value = engineResult.state
            speakIfPossible(engineResult.reply)
            delay(300)
            _visualState.value = JarvisVisualState.IDLE
        } catch (e: Exception) {
            _visualState.value = JarvisVisualState.ERROR
            val errorMsg = AssistantMessage(
                role = MessageRole.JARVIS,
                text = "Subsystem error: ${e.localizedMessage ?: "Operation failed"}"
            )
            _messages.value = _messages.value + errorMsg
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
        val text = com.jarvis.agent.ai.ReplySanitizer.sanitize(result.verificationDetails ?: result.error ?: "Operation executed.")
        _messages.value = _messages.value + AssistantMessage(
            role = MessageRole.JARVIS,
            text = text,
            toolResult = result
        )
        speakIfPossible(text)
        _visualState.value = JarvisVisualState.IDLE
    }

    fun emergencyStop() {
        aiEngine.dialogueManager.cancel()
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
