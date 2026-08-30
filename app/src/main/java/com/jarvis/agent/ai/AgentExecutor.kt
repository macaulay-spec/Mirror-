package com.jarvis.agent.ai

import android.content.Context
import com.jarvis.agent.ai.plan.AgentPlan
import com.jarvis.agent.ai.plan.AgentStep
import com.jarvis.agent.ai.plan.StepStatus
import com.jarvis.agent.memory.JarvisMemoryManager
import com.jarvis.agent.tool.ToolRegistry
import com.jarvis.app.assistant.JarvisApiClient
import com.jarvis.core.model.JarvisVisualState
import com.jarvis.core.model.RiskLevel
import com.jarvis.core.model.ToolExecutionRequest
import kotlinx.coroutines.Dispatchers
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
 */
class AgentExecutor(
    private val context: Context,
    private val apiClient: JarvisApiClient,
    private val memoryManager: JarvisMemoryManager
) {
    companion object {
        private const val MAX_STEPS = 4
    }

    suspend fun executeTask(
        systemPrompt: String,
        initialHistory: List<Pair<String, String>>,
        userMessage: String,
        onStateChange: ((JarvisVisualState) -> Unit)? = null,
        onChunk: ((String) -> Unit)? = null
    ): JarvisEngineResult = withContext(Dispatchers.Default) {
        onStateChange?.invoke(JarvisVisualState.THINKING)

        val plan = AgentPlan(goal = userMessage)
        val history = initialHistory.toMutableList()
        var currentInput = userMessage
        var lastToolVerification: String? = null

        for (stepCount in 0 until MAX_STEPS) {
            val aiResult = apiClient.chat(systemPrompt, history, currentInput)
            if (aiResult.isFailure) {
                val detail = aiResult.exceptionOrNull()?.message?.takeIf { it.isNotBlank() }
                return@withContext JarvisEngineResult(
                    reply = detail ?: "I am operating under local core protocols. How may I assist you?",
                    state = JarvisVisualState.ERROR
                )
            }

            val aiResponse = aiResult.getOrNull() ?: break

            if (aiResponse.toolCalls.isEmpty()) {
                // No tool calls this round -- the model considers the goal done.
                val replyText = aiResponse.message?.takeIf { it.isNotBlank() }
                    ?: lastToolVerification
                    ?: "Task completed."
                val cleanReply = ReplySanitizer.sanitize(replyText)
                history.add("jarvis" to cleanReply)
                return@withContext JarvisEngineResult(reply = cleanReply, state = JarvisVisualState.SUCCESS)
            }

            // The model explained itself before/while acting -- surface that
            // immediately instead of only revealing it once the whole
            // multi-step turn finishes. This is the "explain what it's doing"
            // half of the vision; verification below is the other half.
            aiResponse.message?.takeIf { it.isNotBlank() }?.let {
                onChunk?.invoke(ReplySanitizer.sanitize(it))
            }

            // Gate the WHOLE round on risk before executing anything in it --
            // safer than discovering a high-risk call partway through a batch
            // after lower-risk ones already fired.
            val riskyCall = aiResponse.toolCalls.firstOrNull { call ->
                (ToolRegistry.getTool(call.toolName)?.riskLevel ?: RiskLevel.LEVEL_1) >= RiskLevel.LEVEL_2
            }
            if (riskyCall != null) {
                val riskLevel = ToolRegistry.getTool(riskyCall.toolName)?.riskLevel ?: RiskLevel.LEVEL_2
                val pendingReq = ToolExecutionRequest(riskyCall.toolName, riskyCall.toolName, riskyCall.arguments, riskLevel)
                val argsText = riskyCall.arguments.entries.joinToString { "${it.key}: ${it.value}" }
                val askText = "I'd like to ${riskyCall.toolName.replace('_', ' ')}" +
                    (if (argsText.isNotBlank()) " ($argsText)" else "") + ". Go ahead?"
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
            }

            val allSummary = executionSummaries.joinToString(". ")
            // Always hand results back to the model instead of guessing here
            // whether the goal is finished -- the model saw what it asked for
            // and is in the best position to decide if another step is needed.
            currentInput = "Tool execution results: $allSummary"
            history.add("user" to currentInput)
            onStateChange?.invoke(JarvisVisualState.THINKING)
        }

        val fallback = ReplySanitizer.sanitize(lastToolVerification ?: "Task finished.")
        JarvisEngineResult(fallback, JarvisVisualState.SUCCESS)
    }
}
