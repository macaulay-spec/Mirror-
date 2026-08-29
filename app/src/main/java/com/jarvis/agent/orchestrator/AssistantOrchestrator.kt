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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * AssistantOrchestrator — the single entry point for all user input.
 *
 * Pipeline:
 *   USER INPUT
 *     → DialogueManager  (fast local intents: flash, volume, open app, etc.)
 *       → Reply immediately if handled
 *       → Ask/disambiguate if needed
 *       → Confirm before risky actions
 *       → Falls through to LLM if no local handler
 *     → JarvisAIEngine  (Grok/Gemini with real function calling)
 *     → ToolRegistry    (executes the chosen tool)
 *     → VoiceEngine.speak()  (speaks the reply)
 *     → VoiceBus.setEngineState()  (drives the Orb)
 */
class AssistantOrchestrator(
    private val context: Context,
    private val database: AppDatabase,
    var voiceEngine: JarvisVoiceEngine? = null
) {
    private val aiEngine = JarvisAIEngine(context)
    private val dialogueManager = DialogueManager(context, database.contextGraphDao())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _visualState = MutableStateFlow(JarvisVisualState.IDLE)
    val visualState: StateFlow<JarvisVisualState> = _visualState.asStateFlow()

    private val _messages = MutableStateFlow<List<AssistantMessage>>(emptyList())
    val messages: StateFlow<List<AssistantMessage>> = _messages.asStateFlow()

    private val _pendingConfirmation = MutableStateFlow<ToolExecutionRequest?>(null)
    val pendingConfirmation: StateFlow<ToolExecutionRequest?> = _pendingConfirmation.asStateFlow()

    // ── Public API ────────────────────────────────────────────────────────

    fun setVisualState(state: JarvisVisualState) {
        _visualState.value = state
        com.jarvis.app.voice.VoiceBus.setEngineState(state)
    }

    fun postSystemMessage(text: String) {
        addMessage(AssistantMessage(role = MessageRole.SYSTEM, text = text))
    }

    suspend fun submitUserInput(userInput: String) = processUserCommand(userInput)

    fun confirmToolExecution(request: ToolExecutionRequest) {
        scope.launch {
            _pendingConfirmation.value = null
            setVisualState(JarvisVisualState.EXECUTING)
            val result = ToolRegistry.execute(context, request)
            handleExecutionResult(result)
        }
    }

    fun rejectToolExecution() {
        _pendingConfirmation.value = null
        setVisualState(JarvisVisualState.IDLE)
        val msg = "Understood. Action cancelled."
        addMessage(AssistantMessage(role = MessageRole.JARVIS, text = msg))
        speak(msg)
    }

    suspend fun clearHistory() {
        _messages.value = emptyList()
        _pendingConfirmation.value = null
        runCatching { database.memoryDao().clear() }
        runCatching { database.conversationDao().clear() }
        runCatching { aiEngine.memoryManager.clearAllMemories() }
        runCatching { aiEngine.memoryManager.resetSession() }
        setVisualState(JarvisVisualState.IDLE)
    }

    fun emergencyStop() {
        voiceEngine?.stopSpeaking()
        voiceEngine?.stopListening()
        setVisualState(JarvisVisualState.IDLE)
        _pendingConfirmation.value = null
        addMessage(AssistantMessage(role = MessageRole.SYSTEM, text = "All active tasks halted."))
    }

    // ── Core pipeline ─────────────────────────────────────────────────────

    private suspend fun processUserCommand(userInput: String) {
        if (userInput.isBlank()) return

        addMessage(AssistantMessage(role = MessageRole.USER, text = userInput))
        setVisualState(JarvisVisualState.THINKING)

        try {
            when (val dialogueResult = dialogueManager.handle(userInput)) {

                is DialogueResult.Reply -> {
                    // Dialogue handled it locally — fast path
                    respondWith(dialogueResult.message)
                }

                is DialogueResult.Ask -> {
                    // Needs clarification before proceeding
                    addMessage(AssistantMessage(role = MessageRole.JARVIS, text = dialogueResult.question))
                    setVisualState(JarvisVisualState.LISTENING)
                    speak(dialogueResult.question)
                }

                is DialogueResult.Confirm -> {
                    // High-risk action — surface confirmation card to the user
                    val req = ToolExecutionRequest(
                        toolId = dialogueResult.tool,
                        name = dialogueResult.tool.replace('_', ' ').replaceFirstChar { it.uppercase() },
                        arguments = dialogueResult.arguments,
                        riskLevel = if (dialogueResult.risk >= 2) RiskLevel.LEVEL_2 else RiskLevel.LEVEL_1,
                        requiresConfirmation = true
                    )
                    _pendingConfirmation.value = req
                    addMessage(AssistantMessage(role = MessageRole.JARVIS, text = dialogueResult.prompt, toolCall = req))
                    setVisualState(JarvisVisualState.IDLE)
                    speak(dialogueResult.prompt)
                }

                is DialogueResult.ToolCall -> {
                    if (dialogueResult.tool == "llm_fallback") {
                        // Dialogue couldn't handle it — send to LLM with full function calling
                        val utterance = (dialogueResult.arguments["utterance"] as? String) ?: userInput
                        val engineResult = aiEngine.processCommand(utterance)
                        addMessage(AssistantMessage(
                            role = MessageRole.JARVIS,
                            text = engineResult.reply,
                            toolResult = engineResult.toolResult
                        ))
                        setVisualState(engineResult.state)
                        speak(engineResult.reply)
                        delay(300)
                        setVisualState(JarvisVisualState.IDLE)
                    } else {
                        // Dialogue resolved to a direct tool call
                        setVisualState(JarvisVisualState.EXECUTING)
                        val req = ToolExecutionRequest(
                            toolId = dialogueResult.tool,
                            name = dialogueResult.tool.replace('_', ' ').replaceFirstChar { it.uppercase() },
                            arguments = dialogueResult.arguments,
                            riskLevel = RiskLevel.LEVEL_1,
                            requiresConfirmation = false
                        )
                        handleExecutionResult(ToolRegistry.execute(context, req))
                    }
                }
            }
        } catch (e: Exception) {
            val errorText = "Something went wrong: ${e.localizedMessage ?: "unknown error"}"
            addMessage(AssistantMessage(role = MessageRole.JARVIS, text = errorText))
            setVisualState(JarvisVisualState.ERROR)
            delay(300)
            setVisualState(JarvisVisualState.IDLE)
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun respondWith(text: String) {
        val clean = com.jarvis.agent.ai.ReplySanitizer.sanitize(text)
        addMessage(AssistantMessage(role = MessageRole.JARVIS, text = clean))
        setVisualState(JarvisVisualState.SUCCESS)
        speak(clean)
        scope.launch {
            delay(400)
            setVisualState(JarvisVisualState.IDLE)
        }
    }

    private fun handleExecutionResult(result: ToolExecutionResult) {
        val text = com.jarvis.agent.ai.ReplySanitizer.sanitize(
            result.verificationDetails ?: result.error ?: "Done."
        )
        setVisualState(if (result.success) JarvisVisualState.SUCCESS else JarvisVisualState.ERROR)
        addMessage(AssistantMessage(role = MessageRole.JARVIS, text = text, toolResult = result))
        speak(text)
        scope.launch {
            delay(500)
            setVisualState(JarvisVisualState.IDLE)
        }
    }

    private fun speak(text: String) {
        if (text.isBlank()) return
        try {
            voiceEngine?.speak(text)
        } catch (_: Exception) {}
    }

    private fun addMessage(msg: AssistantMessage) {
        _messages.value = _messages.value + msg
    }
}
