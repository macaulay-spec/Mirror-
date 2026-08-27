package com.jarvis.app.memory

import kotlinx.coroutines.flow.Flow

class MemoryRepository(private val db: AppDatabase) {
    private val memoryDao = db.memoryDao()
    private val conversationDao = db.conversationDao()

    fun all(): Flow<List<MemoryEntity>> = memoryDao.all()
    fun search(q: String): Flow<List<MemoryEntity>> = memoryDao.search("%$q%")

    suspend fun remember(content: String, type: String = "semantic", importance: Int = 60) {
        memoryDao.insert(MemoryEntity(content = content, type = type, importance = importance))
    }

    suspend fun forget(q: String) = memoryDao.deleteWhere("%$q%")
    suspend fun wipe() = memoryDao.clear()

    suspend fun recall(q: String): List<MemoryEntity> {
        val all = memoryDao.snapshot()
        val terms = q.lowercase().split(" ")
        return all.filter { m -> terms.any { it.length > 2 && m.content.lowercase().contains(it) } }
    }

    suspend fun addConversation(role: String, text: String) =
        conversationDao.insert(ConversationEntity(role = role, text = text))

    suspend fun recentConversation(): List<ConversationEntity> =
        conversationDao.recent().reversed()
}
