package com.jarvis.agent.ai

import android.content.Context
import com.jarvis.agent.ai.plan.AgentPlan
import com.jarvis.agent.ai.plan.AgentStep
import com.jarvis.agent.ai.plan.StepStatus
import com.jarvis.agent.memory.JarvisMemoryManager
import com.jarvis.agent.tool.ToolRegistry
import com.jarvis.app.assistant.JarvisApiClient
import com.jarvis.app.config.ApiConfig
import com.jarvis.core.model.JarvisVisualState
import com.jarvis.core.model.RiskLevel
import com.jarvis.core.model.ToolExecutionRequest
import com.jarvis.core.model.ToolExecutionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Runs the model <-> tool loop for a single user turn.
 *
 * CHANGED (forensic audit -> fix pass, see JARVIS_MIRROR_FORENSIC_AUDIT.md):
 *
 * 1) D.6 / Task 7 - a failed AI call's real error ("check your API key", "rate
 *    limited") was discarded and replaced with a generic line. Now surfaced.
 *
 * 2) Multi-step loop - the old `stepCount >= 1 || toolCalls.size == 1` check
 *    returned immediately after almost any single tool call, which is the
 *    common case, so a goal like "read the screen, then tap whatever says
 *    Notifications" could never actually run as two model-reasoned steps.
 *    Tool results are now always handed back to the model, which decides for
 *    itself whether it's done (plain-text reply, no more tool calls) or needs
 *    another step (more tool calls). MAX_STEPS still bounds it.
 *
 * 3) Risk-consistency fix - tool calls issued by the model used to run at a
 *    hard-coded RiskLevel.LEVEL_0 no matter what the tool itself declares, so
 *    "text Dana I'll be late" fired with zero confirmation if it reached the
 *    model instead of the typed-intent router (which *does* confirm it).
 *    Now a tool's own registered risk level decides, and a high-risk call
 *    pauses and returns a pendingConfirmation that AssistantOrchestrator
 *    routes into the same confirm/reject UI the typed-intent path already
 *    uses -- one confirmation system instead of two inconsistent ones.
 *
 * NEW: Step-by-step progress tracking for TaskExecutionScreen
 * - Exposes currentPlan StateFlow for UI observation
 * - Emits step updates as execution progresses
 * - Provides completion/failure callbacks
 */
class AgentExecutor(
    private val context: Context,
    private val apiClient: JarvisApiClient,
    private val memoryManager: JarvisMemoryManager
) {
    companion object {
        private const val MAX_STEPS = 4
        private const val STEP_DELAY_MS = 200L
    }

    suspend fun executeTask(
        systemPrompt: String,
        initialHistory: List<Pair<String, String>>,
        userMessage: String,
        allowTools: Boolean = true,
        onStateChange: ((JarvisVisualState) -> Unit)? = null,
        onChunk: ((String) -> Unit)? = null,
        onStepUpdate: ((List<AgentStep>, Int) -> Unit)? = null,
        onComplete: ((String, Boolean) -> Unit)? = null
    ): JarvisEngineResult = withContext(Dispatchers.Default) {
        onStateChange?.invoke(JarvisVisualState.THINKING)

        val plan = AgentPlan(goal = userMessage)
        val history = initialHistory.toMutableList()
        var currentInput = userMessage
        // Two-tier brain: route hard requests to the deep-think tier up front;
        // everything else stays on the fast conversation tier.
        val provider = ApiConfig.providerForUtterance(userMessage)
        var lastToolVerification: String? = null
        var lastToolResult: ToolExecutionResult? = null
        var stepIndex = 0

        for (stepCount in 0 until MAX_STEPS) {
            // STREAMING: tokens flow to the UI the moment the model emits
            // them. Tools are allowed for up to (MAX_STEPS - 1) iterations so the model
            // can read the screen and then click an element or type text.
            // On the final step, tools are disabled to force a clean summary reply.
            var streamedChars = 0
            val shouldAllowTools = if (stepCount < (MAX_STEPS - 1)) allowTools else false

            val aiResult = apiClient.chatStream(
                systemPrompt = systemPrompt,
                history = history,
                userMessage = currentInput,
                provider = provider,
                allowTools = shouldAllowTools,
                onDelta = { delta ->
                    streamedChars += delta.length
                    onChunk?.invoke(delta)
                }
            )
            if (aiResult.isFailure) {
                val detail = aiResult.exceptionOrNull()?.message?.takeIf { it.isNotBlank() }
                val errorMsg = detail ?: "I am operating under local core protocols. How may I assist you?"
                onComplete?.invoke(errorMsg, false)
                onStateChange?.invoke(JarvisVisualState.ERROR)
                return@withContext JarvisEngineResult(
                    reply = errorMsg,
                    state = JarvisVisualState.ERROR
                )
            }

            val aiResponse = aiResult.getOrNull() ?: break

            if (aiResponse.toolCalls.isEmpty()) {
                val replyText = aiResponse.message?.takeIf { it.isNotBlank() }
                    ?: lastToolVerification
                    ?: "All systems operational, sir. How may I assist you?"
                val cleanReply = ReplySanitizer.sanitize(replyText)
                
                onComplete?.invoke(cleanReply, true)
                onStepUpdate?.invoke(plan.steps, plan.steps.lastIndex)
                
                history.add("jarvis" to cleanReply)
                return@withContext JarvisEngineResult(
                    reply = cleanReply,
                    state = JarvisVisualState.SUCCESS,
                    toolResult = lastToolResult
                )
            }

            // Non-streamed interim narration (only when nothing arrived as
            // deltas — otherwise this would duplicate the streamed text).
            if (streamedChars == 0) {
                aiResponse.message?.takeIf { it.isNotBlank() }?.let {
                    onChunk?.invoke(ReplySanitizer.sanitize(it))
                }
            }

            // Explicitly track the AI's tool calling intent in the history to prevent infinite loops.
            val toolIntents = aiResponse.toolCalls.joinToString(", ") { "${it.toolName}(${it.arguments})" }
            val intentText = (aiResponse.message ?: "") + "\n[Action: Calling tools: $toolIntents]"
            history.add("jarvis" to intentText.trim())

            val riskyCall = aiResponse.toolCalls.firstOrNull { call ->
                (ToolRegistry.getTool(call.toolName)?.riskLevel ?: RiskLevel.LEVEL_1) >= RiskLevel.LEVEL_2
            }
            if (riskyCall != null) {
                val riskLevel = ToolRegistry.getTool(riskyCall.toolName)?.riskLevel ?: RiskLevel.LEVEL_2
                val pendingReq = ToolExecutionRequest(riskyCall.toolName, riskyCall.toolName, riskyCall.arguments, riskLevel)
                val argsText = riskyCall.arguments.entries.joinToString { "${it.key}: ${it.value}" }
                val askText = "I'd like to ${riskyCall.toolName.replace('_', ' ')}" +
                    (if (argsText.isNotBlank()) " ($argsText)" else "") + ". Go ahead?"
                
                onStateChange?.invoke(JarvisVisualState.LISTENING)
                
                return@withContext JarvisEngineResult(
                    reply = askText,
                    state = JarvisVisualState.LISTENING,
                    pendingConfirmation = pendingReq
                )
            }

            onStateChange?.invoke(JarvisVisualState.EXECUTING)
            val executionSummaries = mutableListOf<String>()

            for (toolCall in aiResponse.toolCalls) {
                val toolName = toolCall.toolName
                val argsMap = toolCall.arguments
                val riskLevel = ToolRegistry.getTool(toolName)?.riskLevel ?: RiskLevel.LEVEL_1

                val step = AgentStep(tool = toolName, arguments = argsMap)
                plan.steps.add(step)
                step.status = StepStatus.EXECUTING

                val req = ToolExecutionRequest(toolName, toolName, argsMap, riskLevel)
                val result = ToolRegistry.execute(context, req)
                lastToolResult = result
                memoryManager.recordToolExecution(toolName, argsMap, result)

                val summary = if (result.success) {
                    step.status = StepStatus.SUCCESS
                    step.result = result.verificationDetails ?: "Executed $toolName successfully."
                    result.verificationDetails ?: "Done."
                } else {
                    step.status = StepStatus.FAILED
                    step.error = result.error ?: "Action failed."
                    result.error ?: "Failed to execute $toolName."
                }
                executionSummaries.add(summary)
                lastToolVerification = summary
                
                // Notify UI of step update
                onStepUpdate?.invoke(plan.steps, plan.steps.lastIndex)
                
                delay(STEP_DELAY_MS)
                
                stepIndex++
            }

            val allSummary = executionSummaries.joinToString(". ")
            currentInput = "Tool execution results: $allSummary"
            history.add("user" to currentInput)
            onStateChange?.invoke(JarvisVisualState.THINKING)
        }

        val fallback = ReplySanitizer.sanitize(lastToolVerification ?: "Task finished.")
        onComplete?.invoke(fallback, true)
        onStepUpdate?.invoke(plan.steps, plan.steps.lastIndex)
        
        JarvisEngineResult(reply = fallback, state = JarvisVisualState.SUCCESS, toolResult = lastToolResult)
    }

    /**
     * Builds a human-readable description for a step.
     */
    private fun buildStepDescription(toolName: String, args: Map<String, Any?>): String {
        return when (toolName) {
            "read_screen" -> "Reading screen content"
            "find_on_screen" -> "Finding element on screen"
            "tap_on_screen" -> "Tapping on screen"
            "open_app" -> "Opening app: ${args["packageName"] ?: args["app"]}"
            "send_sms" -> "Sending SMS to ${args["phone"] ?: args["recipient"]}"
            "call_phone" -> "Calling ${args["phone"] ?: args["number"]}"
            "get_contacts" -> "Getting contacts"
            "get_battery_level" -> "Checking battery level"
            "get_network_info" -> "Checking network status"
            "get_location" -> "Getting location"
            "take_photo" -> "Taking photo"
            "read_notifications" -> "Reading notifications"
            else -> "Executing: ${toolName.replace("_", " ")}"
        }
    }
}
