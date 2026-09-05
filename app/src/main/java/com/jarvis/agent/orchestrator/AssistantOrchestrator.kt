package com.jarvis.agent.orchestrator

import android.content.Context
import com.jarvis.agent.ai.AgentExecutor
import com.jarvis.agent.ai.JarvisAIEngine
import com.jarvis.agent.ai.plan.AgentStep
import com.jarvis.agent.dialogue.DialogueManager
import com.jarvis.agent.nlu.IntentRouter
import com.jarvis.agent.tool.ToolRegistry
import com.jarvis.android.voice.JarvisVoiceEngine
import com.jarvis.app.config.ApiConfig
import com.jarvis.app.memory.AppDatabase
import com.jarvis.core.model.AssistantMessage
import com.jarvis.core.model.JarvisVisualState
import com.jarvis.core.model.MessageRole
import com.jarvis.core.model.RiskLevel
import com.jarvis.core.model.ToolExecutionRequest
import com.jarvis.core.model.ToolExecutionResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * AssistantOrchestrator  the single entry point for all user input.
 *
 * Pipeline:
 *   USER INPUT
 *      DialogueManager  (fast local intents + slot filling + confirmation)
 *      JarvisAIEngine   (Grok/Gemini with real function calling)
 *      ToolRegistry     (executes the chosen tool)
 *      VoiceEngine.speak()  (speaks the reply)
 *
 * NEW: Task execution tracking
 * - Exposes current task execution state for TaskExecutionScreen
 * - Tracks multi-step execution progress
 * - Connects AgentExecutor step updates to UI
 */
class AssistantOrchestrator(
    private val context: Context,
    private val database: AppDatabase,
    var voiceEngine: JarvisVoiceEngine? = null
) {
    private val aiEngine = JarvisAIEngine(context)
    private val dialogueManager = DialogueManager(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // ── Turn generation (master spec §80, L-003/L-004) ──
    // Every turn owns a generation id. New input or a stop bumps it; every
    // async callback (stream chunk, tool result, TTS queue) must verify it is
    // still current before touching UI, memory or speech.
    @Volatile
    private var turnGeneration = 0L
    private var currentTurnJob: Job? = null

    private val _visualState = MutableStateFlow(JarvisVisualState.IDLE)
    val visualState: StateFlow<JarvisVisualState> = _visualState.asStateFlow()

    private val _messages = MutableStateFlow<List<AssistantMessage>>(emptyList())
    val messages: StateFlow<List<AssistantMessage>> = _messages.asStateFlow()

    private val _pendingConfirmation = MutableStateFlow<ToolExecutionRequest?>(null)
    val pendingConfirmation: StateFlow<ToolExecutionRequest?> = _pendingConfirmation.asStateFlow()

    // Task execution state for TaskExecutionScreen
    private val _currentTaskDescription = MutableStateFlow<String?>(null)
    val currentTaskDescription: StateFlow<String?> = _currentTaskDescription.asStateFlow()

    private val _currentSteps = MutableStateFlow<List<AgentStep>>(emptyList())
    val currentSteps: StateFlow<List<AgentStep>> = _currentSteps.asStateFlow()

    private val _currentStepIndex = MutableStateFlow<Int?>(null)
    val currentStepIndex: StateFlow<Int?> = _currentStepIndex.asStateFlow()

    private val _isTaskExecuting = MutableStateFlow<Boolean>(false)
    val isTaskExecuting: StateFlow<Boolean> = _isTaskExecuting.asStateFlow()

    private val _taskFinalResult = MutableStateFlow<String?>(null)
    val taskFinalResult: StateFlow<String?> = _taskFinalResult.asStateFlow()

    private val _currentSessionId = MutableStateFlow<String>(java.util.UUID.randomUUID().toString())
    val currentSessionId: StateFlow<String> = _currentSessionId.asStateFlow()

    private val _sessions = MutableStateFlow<List<com.jarvis.app.memory.ChatSessionEntity>>(emptyList())
    val sessions: StateFlow<List<com.jarvis.app.memory.ChatSessionEntity>> = _sessions.asStateFlow()

    init {
        loadSessions()
    }

    fun loadSessions() {
        scope.launch(Dispatchers.IO) {
            val list = aiEngine.memoryManager.allSessions()
            _sessions.value = list
            if (list.isNotEmpty() && _messages.value.isEmpty()) {
                val latest = list.first()
                _currentSessionId.value = latest.sessionId
                loadMessagesForSession(latest.sessionId)
            } else if (list.isEmpty()) {
                startNewChat()
            }
        }
    }

    fun startNewChat() {
        val newId = java.util.UUID.randomUUID().toString()
        _currentSessionId.value = newId
        _messages.value = emptyList()
        scope.launch(Dispatchers.IO) {
            val session = com.jarvis.app.memory.ChatSessionEntity(
                sessionId = newId,
                title = "New Chat",
                createdAt = System.currentTimeMillis()
            )
            aiEngine.memoryManager.saveSession(session)
            _sessions.value = aiEngine.memoryManager.allSessions()
        }
    }

    fun switchSession(sessionId: String) {
        _currentSessionId.value = sessionId
        scope.launch(Dispatchers.IO) {
            loadMessagesForSession(sessionId)
        }
    }

    private suspend fun loadMessagesForSession(sessionId: String) {
        val entities = aiEngine.memoryManager.conversationForSession(sessionId)
        val assistantMessages = entities.map { entity ->
            val role = when (entity.role.lowercase()) {
                "user" -> MessageRole.USER
                "system" -> MessageRole.SYSTEM
                else -> MessageRole.JARVIS
            }
            AssistantMessage(role = role, text = entity.text, timestamp = entity.createdAt)
        }
        _messages.value = assistantMessages
    }

    fun deleteSession(sessionId: String) {
        scope.launch(Dispatchers.IO) {
            aiEngine.memoryManager.deleteSession(sessionId)
            val updated = aiEngine.memoryManager.allSessions()
            _sessions.value = updated
            if (_currentSessionId.value == sessionId) {
                if (updated.isNotEmpty()) {
                    switchSession(updated.first().sessionId)
                } else {
                    startNewChat()
                }
            }
        }
    }

    //  Public API 

    fun setVisualState(state: JarvisVisualState) {
        _visualState.value = state
        com.jarvis.app.voice.VoiceBus.setEngineState(state)
    }

    fun postSystemMessage(text: String) {
        addMessage(AssistantMessage(role = MessageRole.SYSTEM, text = text))
    }

    /** True while a user turn (AI stream, tool loop) is still running. */
    val isTurnActive: Boolean
        get() = currentTurnJob?.isActive == true

    /**
     * §80 / L-003: global cancellation. Stops the AI stream, the tool loop,
     * queued speech, and clears dialogue state. Late callbacks for the old
     * generation are ignored (L-004).
     */
    fun cancelActive() {
        turnGeneration++
        currentTurnJob?.cancel()
        currentTurnJob = null
        voiceEngine?.stopSpeaking()
        _pendingConfirmation.value = null
        dialogueManager.cancel()
        com.jarvis.app.tools.MessagingAutomation.clearPending()
        resetTaskExecution()
    }

    /**
     * §80: "Stop." / "don't do that" while JARVIS is busy cancels the active
     * response/task instead of reaching the brain. Any other input barges in:
     * current speech stops and the in-flight turn is superseded (V-011).
     */
    fun submitUserInput(userInput: String) {
        val text = userInput.trim()
        if (text.isEmpty()) return

        if (IntentRouter.isCancel(text) && (isTurnActive || voiceEngine?.isSpeaking == true)) {
            cancelActive()
            addMessage(AssistantMessage(role = MessageRole.JARVIS, text = "Stopped."))
            speak("Stopped.")
            return
        }

        // Barge-in only when something is actually running. With a confirmation
        // card pending (turn finished), the input must reach DialogueManager
        // intact so "yes" / "don't do that" / corrections resolve there.
        if (isTurnActive || voiceEngine?.isSpeaking == true) {
            cancelActive()
        }
        currentTurnJob = scope.launch { processUserCommand(text) }
    }

    fun confirmToolExecution(request: ToolExecutionRequest) {
        val generation = ++turnGeneration
        currentTurnJob = scope.launch {
            _pendingConfirmation.value = null
            setVisualState(JarvisVisualState.EXECUTING)
            val result = ToolRegistry.execute(context, request)
            if (generation != turnGeneration) return@launch
            handleExecutionResult(result)
        }
    }

    fun rejectToolExecution() {
        _pendingConfirmation.value = null
        dialogueManager.cancel()
        com.jarvis.app.tools.MessagingAutomation.clearPending()
        setVisualState(JarvisVisualState.IDLE)
        val msg = "Understood. Action cancelled."
        addMessage(AssistantMessage(role = MessageRole.JARVIS, text = msg))
        speak(msg)
    }

    suspend fun clearHistory() {
        _messages.value = emptyList()
        _pendingConfirmation.value = null
        dialogueManager.cancel()
        runCatching { database.memoryDao().clear() }
        runCatching { database.conversationDao().clear() }
        runCatching { aiEngine.memoryManager.clearAllMemories() }
        runCatching { aiEngine.memoryManager.resetSession() }
        setVisualState(JarvisVisualState.IDLE)
        resetTaskExecution()
    }

    fun emergencyStop() {
        cancelActive()
        voiceEngine?.stopListening()
        setVisualState(JarvisVisualState.IDLE)
        addMessage(AssistantMessage(role = MessageRole.SYSTEM, text = "All active tasks halted."))
    }

    /**
     * Resets task execution state.
     */
    private fun resetTaskExecution() {
        _currentTaskDescription.value = null
        _currentSteps.value = emptyList()
        _currentStepIndex.value = null
        _isTaskExecuting.value = false
        _taskFinalResult.value = null
    }

    //  Core pipeline 

    private suspend fun processUserCommand(userInput: String) {
        if (userInput.isBlank()) return

        val generation = ++turnGeneration
        fun isCurrent(): Boolean = generation == turnGeneration

        addMessage(AssistantMessage(role = MessageRole.USER, text = userInput))
        setVisualState(JarvisVisualState.THINKING)

        // Barge-in (V-011): cut off any speech still playing from the previous turn.
        voiceEngine?.stopSpeaking()

        val activeSessionId = _currentSessionId.value
        scope.launch(Dispatchers.IO) {
            val cur = _sessions.value.find { it.sessionId == activeSessionId }
            if (cur == null || cur.title == "New Chat") {
                val title = userInput.take(35).replace("\n", " ").trim()
                aiEngine.memoryManager.updateSessionTitle(activeSessionId, title)
                _sessions.value = aiEngine.memoryManager.allSessions()
            }
        }

        try {
            val turnResult = dialogueManager.handle(userInput)
            if (!isCurrent()) return

            if (turnResult.handled) {
                // Dialogue manager handled it (local intent, slot filling, confirmation)
                turnResult.spoken?.let { text ->
                    val clean = com.jarvis.agent.ai.ReplySanitizer.sanitize(text)
                    addMessage(AssistantMessage(role = MessageRole.JARVIS, text = clean))
                    speak(clean)
                }

                // High-risk action needs UI confirmation
                turnResult.confirmRequest?.let { req ->
                    _pendingConfirmation.value = req
                    setVisualState(JarvisVisualState.IDLE)
                    val prompt = describeRequest(req)
                    addMessage(AssistantMessage(role = MessageRole.JARVIS, text = prompt, toolCall = req))
                    speak(prompt)
                    return
                }

                // Tool was executed directly
                turnResult.toolResult?.let { result ->
                    handleExecutionResult(result)
                    return
                }

                // Just spoken a reply, settle to idle
                setVisualState(JarvisVisualState.SUCCESS)
                delay(400)
                setVisualState(JarvisVisualState.IDLE)
            } else {
                // Dialogue couldn't handle it  send to LLM with full function calling
                // Track task execution for UI
                _currentTaskDescription.value = userInput
                _isTaskExecuting.value = true
                resetTaskExecution()

                // ── Real-time streaming ──────────────────────────────────────
                // Text deltas update the JARVIS chat bubble live, and every
                // completed sentence is spoken immediately (queued), so the
                // reply is heard while the rest is still being generated.
                val streamBuf = StringBuilder()
                var spokenUpTo = 0
                var streamMsgIndex: Int? = null
                var streamedAny = false

                val engineResult = aiEngine.processCommand(
                    userInput,
                    sessionId = activeSessionId,
                    onChunk = { chunk ->
                        if (chunk.isNotEmpty() && isCurrent()) {
                        streamedAny = true
                        streamBuf.append(chunk)

                        // Update (or create) the streaming chat bubble.
                        val live = streamBuf.toString()
                        val idx = streamMsgIndex
                        if (idx == null) {
                            streamMsgIndex = _messages.value.size
                            addMessage(AssistantMessage(role = MessageRole.JARVIS, text = live))
                        } else {
                            val list = _messages.value.toMutableList()
                            if (idx < list.size) {
                                list[idx] = list[idx].copy(text = live)
                                _messages.value = list
                            }
                        }

                        // Speak each sentence as soon as it completes.
                        while (spokenUpTo < streamBuf.length) {
                            var end = -1
                            for (i in spokenUpTo until streamBuf.length) {
                                val c = streamBuf[i]
                                if (c == '.' || c == '!' || c == '?' || c == '\n' || c == '…') { end = i; break }
                            }
                            if (end < 0) break
                            val sentence = streamBuf.substring(spokenUpTo, end + 1).trim()
                            if (sentence.isNotBlank()) voiceEngine?.speakQueued(sentence)
                            spokenUpTo = end + 1
                        }
                        }
                    },
                    onStepUpdate = { steps, stepIndex ->
                        if (generation == turnGeneration) {
                            _currentSteps.value = steps
                            _currentStepIndex.value = stepIndex
                        }
                    },
                    onComplete = { result, success ->
                        if (generation == turnGeneration) {
                            _taskFinalResult.value = result
                            _isTaskExecuting.value = false
                            if (success) {
                                _currentStepIndex.value = _currentSteps.value.lastIndex
                            }
                        }
                    }
                )

                if (!isCurrent()) return
                val finalText = com.jarvis.agent.ai.ReplySanitizer.sanitize(engineResult.reply)
                if (streamedAny) {
                    // Replace the streamed bubble with the authoritative final
                    // text (sanitizer may have trimmed things).
                    val idx = streamMsgIndex
                    if (idx != null && idx < _messages.value.size) {
                        val list = _messages.value.toMutableList()
                        list[idx] = list[idx].copy(
                            text = finalText,
                            toolResult = engineResult.toolResult,
                            toolCall = engineResult.pendingConfirmation
                        )
                        _messages.value = list
                    }
                    // Speak whatever tail never formed a full sentence.
                    if (spokenUpTo < streamBuf.length) {
                        val tail = streamBuf.substring(spokenUpTo).trim()
                        if (tail.isNotBlank()) voiceEngine?.speakQueued(tail)
                    }
                } else {
                    addMessage(AssistantMessage(
                        role = MessageRole.JARVIS,
                        text = finalText,
                        toolResult = engineResult.toolResult,
                        toolCall = engineResult.pendingConfirmation
                    ))
                    speak(finalText)
                }

                if (engineResult.pendingConfirmation != null) {
                    _pendingConfirmation.value = engineResult.pendingConfirmation
                    setVisualState(JarvisVisualState.IDLE)
                } else {
                    setVisualState(engineResult.state)
                    delay(300)
                    setVisualState(JarvisVisualState.IDLE)
                }
            }
        } catch (e: Exception) {
            if (generation != turnGeneration) return  // turn was cancelled — no stale error message
            val errorText = "Something went wrong: ${e.localizedMessage ?: "unknown error"}"
            addMessage(AssistantMessage(role = MessageRole.JARVIS, text = errorText))
            _taskFinalResult.value = errorText
            _isTaskExecuting.value = false
            setVisualState(JarvisVisualState.ERROR)
            delay(300)
            setVisualState(JarvisVisualState.IDLE)
        }
    }

    //  Helpers 

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

    private fun describeRequest(request: ToolExecutionRequest): String {
        val who = request.arguments["contact"]?.toString()
            ?: request.arguments["app"]?.toString()
            ?: request.arguments["package"]?.toString()
        return when (request.toolId) {
            "call_contact" -> "Call $who. Shall I?"
            "send_sms" -> "Send to $who: \"${request.arguments["message"]}\". Shall I?"
            else -> "${request.name}  shall I go ahead?"
        }
    }
}
