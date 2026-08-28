package com.jarvis.agent.orchestrator

import android.content.Context
import com.jarvis.agent.ai.JarvisAIEngine
import com.jarvis.agent.tool.ToolRegistry
import com.jarvis.android.voice.JarvisVoiceEngine
import com.jarvis.app.config.ApiConfig
import com.jarvis.app.dialogue.DialogueManager
import com.jarvis.app.dialogue.DialogueResult
import com.jarvis.app.memory.AppDatabase
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
 * AssistantOrchestrator: The central AI reasoning and execution engine.
 * Pipeline: USER INPUT -> LOCAL DIALOGUE MANAGER -> LOCAL TOOLS / LLM FALLBACK -> VERIFICATION -> MEMORY -> TTS RESPONSE
 */
class AssistantOrchestrator(
    private val context: Context,
    private val database: AppDatabase,
    var voiceEngine: JarvisVoiceEngine? = null
) {
    private val aiEngine = JarvisAIEngine(context)
    private val dialogueManager = DialogueManager(context, database.contextGraphDao())

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
            // Check Local Dialogue Manager first for deterministic, fast, private slot/intent resolution
            when (val result = dialogueManager.handle(userInput)) {
                is DialogueResult.Reply -> {
                    val jarvisMsg = AssistantMessage(role = MessageRole.JARVIS, text = result.message)
                    _messages.value = _messages.value + jarvisMsg
                    _visualState.value = JarvisVisualState.SUCCESS
                    speakIfPossible(result.message)
                    delay(400)
                    _visualState.value = JarvisVisualState.IDLE
                }
                is DialogueResult.Ask -> {
                    val jarvisMsg = AssistantMessage(role = MessageRole.JARVIS, text = result.question)
                    _messages.value = _messages.value + jarvisMsg
                    _visualState.value = JarvisVisualState.LISTENING
                    speakIfPossible(result.question)
                }
                is DialogueResult.Confirm -> {
                    val req = ToolExecutionRequest(
                        toolId = result.tool,
                        name = result.tool.replace('_', ' ').replaceFirstChar { it.uppercase() },
                        arguments = result.arguments,
                        riskLevel = if (result.risk >= 2) RiskLevel.LEVEL_2 else RiskLevel.LEVEL_1,
                        requiresConfirmation = true
                    )
                    _pendingConfirmation.value = req
                    val confirmMsg = AssistantMessage(
                        role = MessageRole.JARVIS,
                        text = result.prompt,
                        toolCall = req
                    )
                    _messages.value = _messages.value + confirmMsg
                    _visualState.value = JarvisVisualState.IDLE
                    speakIfPossible(result.prompt)
                }
                is DialogueResult.ToolCall -> {
                    if (result.tool == "llm_fallback") {
                        val rawUtterance = (result.arguments["utterance"] as? String) ?: userInput
                        val engineResult = aiEngine.processCommand(rawUtterance)
                        val jarvisMsg = AssistantMessage(
                            role = MessageRole.JARVIS,
                            text = engineResult.reply,
                            toolResult = engineResult.toolResult
                        )
                        _messages.value = _messages.value + jarvisMsg
                        _visualState.value = engineResult.state
                        speakIfPossible(engineResult.reply)
                        delay(300)
                        _visualState.value = JarvisVisualState.IDLE
                    } else {
                        // Direct native tool execution
                        _visualState.value = JarvisVisualState.EXECUTING
                        val req = ToolExecutionRequest(
                            toolId = result.tool,
                            name = result.tool.replace('_', ' ').replaceFirstChar { it.uppercase() },
                            arguments = result.arguments,
                            riskLevel = RiskLevel.LEVEL_1,
                            requiresConfirmation = false
                        )
                        val toolExecResult = ToolRegistry.execute(context, req)
                        handleExecutionResult(toolExecResult)
                    }
                }
            }
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
