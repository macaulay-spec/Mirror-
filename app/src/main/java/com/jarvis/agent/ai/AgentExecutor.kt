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

class AgentExecutor(
    private val context: Context,
    private val apiClient: JarvisApiClient,
    private val memoryManager: JarvisMemoryManager
) {
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

        val maxSteps = 4
        var stepCount = 0
        var lastToolVerification: String? = null

        while (stepCount < maxSteps) {
            val aiResult = apiClient.chat(systemPrompt, history, currentInput)
            if (aiResult.isFailure) {
                return@withContext JarvisEngineResult(
                    reply = "I am operating under local core protocols. How may I assist you?",
                    state = JarvisVisualState.ERROR
                )
            }

            val aiResponse = aiResult.getOrNull() ?: break

            // 1. If tools were called by the AI model
            if (aiResponse.toolCalls.isNotEmpty()) {
                onStateChange?.invoke(JarvisVisualState.EXECUTING)

                val executionSummaries = mutableListOf<String>()
                for (toolCall in aiResponse.toolCalls) {
                    val toolName = toolCall.toolName
                    val argsMap = toolCall.arguments

                    val step = AgentStep(tool = toolName, arguments = argsMap)
                    plan.steps.add(step)
                    step.status = StepStatus.EXECUTING

                    val req = ToolExecutionRequest(toolName, toolName, argsMap, RiskLevel.LEVEL_0)
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

                // If the model also supplied an explicit response text alongside the tool call, use it
                if (!aiResponse.message.isNullOrBlank()) {
                    val msg = ReplySanitizer.sanitize(aiResponse.message)
                    history.add("jarvis" to msg)
                    return@withContext JarvisEngineResult(
                        reply = "$msg\n$allSummary".trim(),
                        state = JarvisVisualState.SUCCESS
                    )
                }

                // If only 1 step or autonomous action completed, return the verification directly
                if (stepCount >= 1 || aiResponse.toolCalls.size == 1) {
                    val finalReply = ReplySanitizer.sanitize(allSummary)
                    history.add("jarvis" to finalReply)
                    return@withContext JarvisEngineResult(
                        reply = finalReply,
                        state = JarvisVisualState.SUCCESS
                    )
                }

                // Feed back into AI for follow-up reasoning if needed
                currentInput = "Tool execution results: $allSummary"
                history.add("user" to currentInput)
                stepCount++
                onStateChange?.invoke(JarvisVisualState.THINKING)
                continue
            }

            // 2. Direct conversational or synthesized response
            val replyText = aiResponse.message?.takeIf { it.isNotBlank() }
                ?: lastToolVerification
                ?: "Task completed."

            val cleanReply = ReplySanitizer.sanitize(replyText)
            history.add("jarvis" to cleanReply)
            return@withContext JarvisEngineResult(
                reply = cleanReply,
                state = JarvisVisualState.SUCCESS
            )
        }

        val fallback = ReplySanitizer.sanitize(lastToolVerification ?: "Task finished.")
        return@withContext JarvisEngineResult(fallback, JarvisVisualState.SUCCESS)
    }
}
