package com.jarvis.agent.memory

import android.content.Context
import com.jarvis.android.accessibility.JarvisAccessibilityService
import com.jarvis.app.memory.AppDatabase
import com.jarvis.app.memory.ConversationEntity
import com.jarvis.app.memory.MemoryEntity
import com.jarvis.app.memory.MemoryRepository
import com.jarvis.core.model.ToolExecutionResult
import kotlinx.coroutines.flow.Flow
import java.util.Locale

data class SessionContext(
    var currentApp: String = "unknown",
    var currentTask: String = "idle",
    var lastAction: String = "none",
    var lastActionResult: Boolean = true,
    var lastActionDetails: String = ""
)

class JarvisMemoryManager(context: Context) {
    private val repository = MemoryRepository(AppDatabase.get(context))
    private val sessionContext = SessionContext()
    private val actionHistory = mutableListOf<String>()
    private var memoryEnabled: Boolean = true

    fun setMemoryEnabled(enabled: Boolean) {
        memoryEnabled = enabled
    }

    fun isMemoryEnabled(): Boolean = memoryEnabled

    fun updateSessionContext(app: String?, task: String?, action: String?, result: Boolean?, details: String?) {
        if (!app.isNullOrBlank()) sessionContext.currentApp = app
        if (!task.isNullOrBlank()) sessionContext.currentTask = task
        if (!action.isNullOrBlank()) {
            sessionContext.lastAction = action
            actionHistory.add("Executed $action")
            if (actionHistory.size > 20) actionHistory.removeAt(0)
        }
        if (result != null) sessionContext.lastActionResult = result
        if (!details.isNullOrBlank()) sessionContext.lastActionDetails = details
    }

    fun getSessionContext(): SessionContext = sessionContext
    fun getActionHistory(): List<String> = actionHistory.toList()

    suspend fun recordToolExecution(toolId: String, args: Map<String, Any?>, result: ToolExecutionResult) {
        val details = if (result.success) result.verificationDetails ?: "Success" else result.error ?: "Failure"
        updateSessionContext(
            app = null,
            task = "Tool $toolId",
            action = toolId,
            result = result.success,
            details = details
        )
    }

    suspend fun addConversation(role: String, text: String, sessionId: String = "default") {
        if (!memoryEnabled) return
        repository.addConversation(role, text, sessionId)

        // Only explicitly flagged memory statements are saved to long-term memory
        if (role == "user") {
            val lower = text.lowercase().trimStart()
            val explicitRemember = lower.startsWith("remember") || lower.startsWith("keep in mind")
            if (explicitRemember && !containsSensitive(lower)) {
                val clean = text.trim()
                    .removePrefix("Remember that ").removePrefix("remember that ")
                    .removePrefix("Remember ").removePrefix("remember ")
                    .removePrefix("Keep in mind ").removePrefix("keep in mind ")
                    .trim()
                if (clean.isNotBlank()) {
                    repository.remember(clean, type = "preference", importance = 80)
                }
            }
        }
    }

    suspend fun recentConversation(): List<ConversationEntity> {
        return if (memoryEnabled) repository.recentConversation() else emptyList()
    }

    suspend fun conversationForSession(sessionId: String): List<ConversationEntity> {
        return repository.conversationForSession(sessionId)
    }

    suspend fun allSessions(): List<com.jarvis.app.memory.ChatSessionEntity> {
        return repository.allSessions()
    }

    suspend fun saveSession(session: com.jarvis.app.memory.ChatSessionEntity) {
        repository.saveSession(session)
    }

    suspend fun updateSessionTitle(sessionId: String, title: String) {
        repository.updateSessionTitle(sessionId, title)
    }

    suspend fun deleteSession(sessionId: String) {
        repository.deleteSession(sessionId)
    }

    suspend fun recallRelevant(query: String): List<MemoryEntity> {
        if (!memoryEnabled) return emptyList()
        return repository.recall(query)
    }

    fun allMemories(): Flow<List<MemoryEntity>> = repository.all()

    suspend fun deleteMemory(memory: MemoryEntity) {
        repository.forget(memory.content)
    }

    suspend fun clearAllMemories() {
        repository.wipe()
    }

    suspend fun resetSession() {
        sessionContext.currentApp = "unknown"
        sessionContext.currentTask = "idle"
        sessionContext.lastAction = "none"
        sessionContext.lastActionResult = true
        sessionContext.lastActionDetails = ""
        actionHistory.clear()
    }

    private fun containsSensitive(lower: String): Boolean {
        return lower.contains("password") || lower.contains("token") || lower.contains("apikey") ||
                lower.contains("api key") || lower.contains("secret") || lower.contains("credit card") ||
                lower.contains("pin") || lower.contains("ssn") || lower.contains("bank")
    }
}
