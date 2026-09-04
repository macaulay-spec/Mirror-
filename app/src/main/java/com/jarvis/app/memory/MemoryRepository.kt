package com.jarvis.app.memory

import kotlinx.coroutines.flow.Flow

class MemoryRepository(private val db: AppDatabase) {
    private val memoryDao = db.memoryDao()
    private val conversationDao = db.conversationDao()

    fun all(): Flow<List<MemoryEntity>> = memoryDao.all()
    fun search(q: String): Flow<List<MemoryEntity>> = memoryDao.search("%$q%")

    suspend fun remember(content: String, type: String = "semantic", importance: Int = 60) {
        // FIX (production repair): repeated saves of the same fact used to pile
        // up as duplicates and pollute recall. Skip an exact-content match.
        val duplicate = memoryDao.snapshot().any { it.content.equals(content.trim(), ignoreCase = true) }
        if (duplicate) return
        memoryDao.insert(MemoryEntity(content = content.trim(), type = type, importance = importance))
    }

    suspend fun forget(q: String) = memoryDao.deleteWhere("%$q%")
    suspend fun wipe() = memoryDao.clear()

    suspend fun recall(q: String): List<MemoryEntity> {
        val all = memoryDao.snapshot()
        val terms = q.lowercase().split(" ")
        return all.filter { m -> terms.any { it.length > 2 && m.content.lowercase().contains(it) } }
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
