package com.jarvis.agent.ai

import android.content.Context
import com.jarvis.agent.ai.plan.AgentPlan
import com.jarvis.agent.ai.plan.AgentStep
import com.jarvis.agent.ai.plan.PlanStatus
import com.jarvis.agent.ai.plan.StepStatus
import com.jarvis.agent.memory.JarvisMemoryManager
import com.jarvis.agent.tool.ToolRegistry
import com.jarvis.core.model.JarvisVisualState
import com.jarvis.core.model.RiskLevel
import com.jarvis.core.model.ToolExecutionRequest
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AgentExecutor(
    private val context: Context,
    private val apiClient: com.jarvis.app.assistant.JarvisApiClient,
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
        
        val maxSteps = 5
        var stepCount = 0
        
        while (stepCount < maxSteps) {
            val aiResult = apiClient.chat(systemPrompt, history, currentInput)
            if (aiResult.isFailure) {
                val cleanError = ReplySanitizer.sanitize(aiResult.exceptionOrNull()?.message ?: "I couldn't reach the cloud right now.")
                return@withContext JarvisEngineResult(
                    reply = cleanError,
                    state = JarvisVisualState.ERROR
                )
            }
            
            val aiText = aiResult.getOrNull() ?: ""
            history.add("jarvis" to aiText)
            
            // Try to parse JSON from aiText
            val jsonText = aiText.substringAfter("{").substringBeforeLast("}") 
            val jsonStr = if (aiText.contains("{") && aiText.contains("}")) "{$jsonText}" else aiText
            
            try {
                val json = JSONObject(jsonStr)
                if (json.optString("action") == "tool_call") {
                    val toolName = json.optString("tool")
                    val argsObj = json.optJSONObject("arguments") ?: JSONObject()
                    val expectedResult = json.optString("expectedResult")
                    
                    val argsMap = mutableMapOf<String, Any>()
                    val keys = argsObj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        argsMap[key] = argsObj.get(key)
                    }
                    
                    val step = AgentStep(tool = toolName, arguments = argsMap, expectedResult = expectedResult)
                    plan.steps.add(step)
                    step.status = StepStatus.EXECUTING
                    onStateChange?.invoke(JarvisVisualState.EXECUTING)
                    
                    val req = ToolExecutionRequest(toolName, toolName, argsMap, RiskLevel.LEVEL_0)
                    val result = ToolRegistry.execute(context, req)
                    
                    memoryManager.recordToolExecution(toolName, argsMap, result)
                    
                    if (result.success) {
                        step.status = StepStatus.SUCCESS
                        step.result = result.verificationDetails ?: "Done."
                    } else {
                        step.status = StepStatus.FAILED
                        step.error = result.error ?: "Action failed."
                    }
                    
                    // Feed back into AI
                    currentInput = "Tool '$toolName' result: ${if (result.success) step.result else step.error}"
                    history.add("user" to currentInput)
                    stepCount++
                    onStateChange?.invoke(JarvisVisualState.THINKING)
                    continue
                } else if (json.optString("action") == "reply") {
                    val msg = ReplySanitizer.sanitize(json.optString("message"))
                    return@withContext JarvisEngineResult(msg, JarvisVisualState.SUCCESS)
                } else {
                    val msg = ReplySanitizer.sanitize(aiText)
                    return@withContext JarvisEngineResult(msg, JarvisVisualState.SUCCESS)
                }
            } catch (e: Exception) {
                // Not JSON, standard reply
                val msg = ReplySanitizer.sanitize(aiText)
                return@withContext JarvisEngineResult(msg, JarvisVisualState.SUCCESS)
            }
        }
        
        return@withContext JarvisEngineResult("Task finished.", JarvisVisualState.SUCCESS)
    }
}
