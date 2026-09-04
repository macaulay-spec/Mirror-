package com.jarvis.app.memory

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {
    @Insert
    suspend fun insert(memory: MemoryEntity): Long

    @Delete
    suspend fun delete(memory: MemoryEntity)

    @Query("SELECT * FROM memories ORDER BY importance DESC, updatedAt DESC LIMIT 200")
    fun all(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE content LIKE :q ORDER BY importance DESC, updatedAt DESC")
    fun search(q: String): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories ORDER BY importance DESC, updatedAt DESC")
    suspend fun snapshot(): List<MemoryEntity>

    @Query("DELETE FROM memories")
    suspend fun clear()

    @Query("DELETE FROM memories WHERE content LIKE :q")
    suspend fun deleteWhere(q: String)
}

@Dao
interface ConversationDao {
    @Insert
    suspend fun insert(c: ConversationEntity)

    @Query("SELECT * FROM conversation WHERE sessionId = :sessionId ORDER BY createdAt ASC")
    suspend fun forSession(sessionId: String): List<ConversationEntity>

    @Query("SELECT * FROM conversation ORDER BY createdAt DESC LIMIT 40")
    suspend fun recent(): List<ConversationEntity>

    @Query("DELETE FROM conversation WHERE sessionId = :sessionId")
    suspend fun clearSession(sessionId: String)

    @Query("DELETE FROM conversation")
    suspend fun clear()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ChatSessionEntity)

    @Query("SELECT * FROM chat_sessions ORDER BY updatedAt DESC")
    suspend fun allSessions(): List<ChatSessionEntity>

    @Query("SELECT * FROM chat_sessions WHERE sessionId = :sessionId LIMIT 1")
    suspend fun getSession(sessionId: String): ChatSessionEntity?

    @Query("UPDATE chat_sessions SET title = :title, updatedAt = :updatedAt WHERE sessionId = :sessionId")
    suspend fun updateSessionTitle(sessionId: String, title: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM chat_sessions WHERE sessionId = :sessionId")
    suspend fun deleteSession(sessionId: String)
}
