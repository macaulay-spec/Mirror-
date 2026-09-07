package com.jarvis.app.memory

import kotlinx.coroutines.flow.Flow

class MemoryRepository(private val db: AppDatabase) {
    private val memoryDao = db.memoryDao()
    private val conversationDao = db.conversationDao()

    fun all(): Flow<List<MemoryEntity>> = memoryDao.all()
    fun latest(): Flow<MemoryEntity?> = memoryDao.latest()
    fun search(q: String): Flow<List<MemoryEntity>> = memoryDao.search("%$q%")

    suspend fun remember(content: String, type: String = "semantic", importance: Int = 60) {
        // FIX (production repair): repeated saves of the same fact used to pile
        // up as duplicates and pollute recall. Skip an exact-content match.
        val duplicate = memoryDao.snapshot().any { it.content.equals(content.trim(), ignoreCase = true) }
        if (duplicate) return
        memoryDao.insert(MemoryEntity(content = content.trim(), type = type, importance = importance))
    }

    suspend fun forget(q: String) = memoryDao.deleteWhere("%$q%")
    suspend fun delete(memory: MemoryEntity) = memoryDao.delete(memory)
    suspend fun wipe() = memoryDao.clear()

    /**
     * Enhanced recall: Handles both direct term matching and general memory inquiries
     * ("who am I", "what do you remember", "tell me what you know", "preferences").
     * Surfaces top relevant memories so JARVIS always has context.
     */
    suspend fun recall(q: String): List<MemoryEntity> {
        val all = memoryDao.snapshot()
        if (all.isEmpty()) return emptyList()

        val lowerQ = q.lowercase()
        val isGeneralMemoryQuery = lowerQ.contains("remember") || lowerQ.contains("memor") ||
            lowerQ.contains("who am i") || lowerQ.contains("know about me") ||
            lowerQ.contains("preference") || lowerQ.contains("my name") ||
            lowerQ.contains("tell me about") || lowerQ.contains("profile")

        if (isGeneralMemoryQuery) {
            return all.sortedWith(
                compareByDescending<MemoryEntity> { it.importance }
                    .thenByDescending { it.updatedAt }
            ).take(10)
        }

        val terms = lowerQ.split(Regex("\\W+")).filter { it.length > 2 }
        if (terms.isEmpty()) {
            return all.sortedByDescending { it.importance }.take(4)
        }

        val scored = all
            .map { memory -> memory to terms.count { memory.content.lowercase().contains(it) } }
            .filter { (_, hits) -> hits > 0 }
            .sortedWith(
                compareByDescending<Pair<MemoryEntity, Int>> { it.second }
                    .thenByDescending { it.first.importance }
                    .thenByDescending { it.first.updatedAt }
            )
            .take(8)
            .map { it.first }

        return if (scored.isNotEmpty()) scored else all.sortedByDescending { it.importance }.take(3)
    }

    suspend fun addConversation(role: String, text: String, sessionId: String = "default") =
        conversationDao.insert(ConversationEntity(sessionId = sessionId, role = role, text = text))

    suspend fun recentConversation(): List<ConversationEntity> =
        conversationDao.recent().reversed()

    suspend fun conversationForSession(sessionId: String): List<ConversationEntity> =
        conversationDao.forSession(sessionId)

    suspend fun allSessions(): List<ChatSessionEntity> =
        conversationDao.allSessions()

    suspend fun saveSession(session: ChatSessionEntity) =
        conversationDao.insertSession(session)

    suspend fun updateSessionTitle(sessionId: String, title: String) =
        conversationDao.updateSessionTitle(sessionId, title)

    suspend fun deleteSession(sessionId: String) {
        conversationDao.deleteSession(sessionId)
        conversationDao.clearSession(sessionId)
    }
}
