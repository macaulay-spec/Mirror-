package com.jarvis.agent.ai

import android.content.Context
import com.jarvis.agent.memory.JarvisMemoryManager
import com.jarvis.agent.tool.ToolRegistry
import com.jarvis.core.model.JarvisVisualState
import com.jarvis.core.model.RiskLevel
import com.jarvis.core.model.ToolExecutionRequest
import com.jarvis.core.model.ToolExecutionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Runs the multi-step loop between the model and the tool registry.
 *
 * Two ways a model can ask for an action:
 *  1. **Native function calling** — Gemini and OpenAI now receive the real ToolRegistry
 *     schema and return a structured call. This is reliable, and it means any registered
 *     tool is reachable, including ones added later.
 *  2. **JSON in prose** — the legacy path, kept for providers without tool support.
 *
 * Anything risky that the model picks (a call, a message) is sent back as a confirmation
 * request instead of being executed silently.
 */
class AgentExecutor(
    private val context: Context,
    private val providerRouter: ProviderRouter,
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

        val history = initialHistory.toMutableList()
        var currentInput = userMessage
        var stepCount = 0
        val maxSteps = 6

        while (stepCount < maxSteps) {

            // ---- 1. Native function calling -------------------------------------------
            val toolResponse = providerRouter.executeWithTools(systemPrompt, history, currentInput)
            val response = toolResponse.getOrNull()

            if (response != null && response.toolCalls.isNotEmpty()) {
                val summaries = ArrayList<String>()
                for (call in response.toolCalls) {
                    val toolId = call["name"]?.toString() ?: continue
                    @Suppress("UNCHECKED_CAST")
                    val args = (call["arguments"] as? Map<String, Any?>) ?: emptyMap()

                    val confirmation = buildRequest(toolId, args)
                    if (confirmation != null) {
                        // Risky action chosen by the model — ask before doing it.
                        return@withContext JarvisEngineResult(
                            reply = "Shall I ${describe(toolId, args)}?",
                            state = JarvisVisualState.IDLE,
                            confirmRequest = confirmation
                        )
                    }

                    onStateChange?.invoke(JarvisVisualState.EXECUTING)
                    val result = ToolRegistry.execute(context, requestFor(toolId, args))
                    memoryManager.recordToolExecution(toolId, args, result)
                    summaries.add(
                        "Tool '$toolId' -> " +
                            if (result.success) result.verificationDetails ?: "done"
                            else result.error ?: "failed"
                    )
                    stepCount++
                }

                if (response.content.isNotBlank()) {
                    history.add("jarvis" to response.content)
                }
                currentInput = summaries.joinToString("\n")
                history.add("user" to currentInput)
                onStateChange?.invoke(JarvisVisualState.THINKING)
                continue
            }

            // ---- 2. No tool call: JSON-in-prose, or a plain reply ----------------------
            val text = response?.content
            if (!text.isNullOrBlank()) {
                val legacy = parseJsonToolCall(text)
                if (legacy != null) {
                    val (toolId, args, expected) = legacy
                    val confirmation = buildRequest(toolId, args)
                    if (confirmation != null) {
                        return@withContext JarvisEngineResult(
                            reply = "Shall I ${describe(toolId, args)}?",
                            state = JarvisVisualState.IDLE,
                            confirmRequest = confirmation
                        )
                    }
                    onStateChange?.invoke(JarvisVisualState.EXECUTING)
                    val result = ToolRegistry.execute(context, requestFor(toolId, args))
                    memoryManager.recordToolExecution(toolId, args, result)
                    stepCount++
                    currentInput = "Tool '$toolId' result: " +
                        if (result.success) result.verificationDetails ?: "done"
                        else result.error ?: "failed"
                    history.add("jarvis" to text)
                    history.add("user" to currentInput)
                    onStateChange?.invoke(JarvisVisualState.THINKING)
                    continue
                }

                val finalReply = ReplySanitizer.sanitize(text)
                onChunk?.invoke(finalReply)
                return@withContext JarvisEngineResult(finalReply, JarvisVisualState.SUCCESS)
            }

            // ---- 3. Providers without tool support -------------------------------------
            val fallback = providerRouter.executeWithFallback(systemPrompt, history, currentInput)
            if (fallback.isFailure) {
                val cleanError = ReplySanitizer.sanitize(
                    fallback.exceptionOrNull()?.message ?: "I couldn't reach the cloud right now."
                )
                return@withContext JarvisEngineResult(cleanError, JarvisVisualState.ERROR)
            }

            val aiText = fallback.getOrNull()?.content ?: ""
            history.add("jarvis" to aiText)

            val legacy = parseJsonToolCall(aiText)
            if (legacy != null) {
                val (toolId, args, _) = legacy
                val confirmation = buildRequest(toolId, args)
                if (confirmation != null) {
                    return@withContext JarvisEngineResult(
                        reply = "Shall I ${describe(toolId, args)}?",
                        state = JarvisVisualState.IDLE,
                        confirmRequest = confirmation
                    )
                }
                onStateChange?.invoke(JarvisVisualState.EXECUTING)
                val result = ToolRegistry.execute(context, requestFor(toolId, args))
                memoryManager.recordToolExecution(toolId, args, result)
                stepCount++
                currentInput = "Tool '$toolId' result: " +
                    if (result.success) result.verificationDetails ?: "done"
                    else result.error ?: "failed"
                history.add("user" to currentInput)
                onStateChange?.invoke(JarvisVisualState.THINKING)
                continue
            }

            val finalReply = ReplySanitizer.sanitize(aiText)
            onChunk?.invoke(finalReply)
            return@withContext JarvisEngineResult(finalReply, JarvisVisualState.SUCCESS)
        }

        JarvisEngineResult("Task finished.", JarvisVisualState.SUCCESS)
    }

    // ------------------------------------------------------------------- helpers

    private data class LegacyCall(
        val toolId: String,
        val args: Map<String, Any?>,
        val expected: String
    )

    private fun parseJsonToolCall(text: String): LegacyCall? {
        if (!text.contains("{") || !text.contains("}")) return null
        return try {
            val jsonStr = text.substring(text.indexOf('{'), text.lastIndexOf('}') + 1)
            val json = JSONObject(jsonStr)
            if (json.optString("action") != "tool_call") return null
            val toolId = json.optString("tool")
            if (toolId.isBlank()) return null
            val argsObj = json.optJSONObject("arguments") ?: JSONObject()
            val args = LinkedHashMap<String, Any?>()
            val keys = argsObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                args[key] = argsObj.opt(key)
            }
            LegacyCall(toolId, args, json.optString("expectedResult"))
        } catch (_: Exception) {
            null
        }
    }

    private fun requestFor(toolId: String, args: Map<String, Any?>): ToolExecutionRequest {
        val tool = ToolRegistry.getTool(toolId)
        val risk = tool?.riskLevel ?: RiskLevel.LEVEL_1
        return ToolExecutionRequest(
            toolId = toolId,
            name = tool?.name ?: toolId,
            arguments = args,
            riskLevel = risk,
            requiresConfirmation = risk >= RiskLevel.LEVEL_2
        )
    }

    /** Returns a request only when the action is risky enough to need confirming. */
    private fun buildRequest(toolId: String, args: Map<String, Any?>): ToolExecutionRequest? {
        val request = requestFor(toolId, args)
        return if (request.riskLevel >= RiskLevel.LEVEL_2) request else null
    }

    private fun describe(toolId: String, args: Map<String, Any?>): String {
        val target = args["contact"]?.toString()
            ?: args["app"]?.toString()
            ?: args["query"]?.toString()
            ?: args["title"]?.toString()
        return when (toolId) {
            "call_contact" -> "call $target"
            "send_sms", "communication_send", "reply_to_notification", "reply_notification" ->
                "send that message to $target"
            "calendar_create" -> "create the event \"$target\""
            else -> ToolRegistry.getTool(toolId)?.name?.lowercase() ?: toolId
        }
    }

    private fun summarize(result: ToolExecutionResult): String =
        if (result.success) result.verificationDetails ?: "done" else result.error ?: "failed"
}
