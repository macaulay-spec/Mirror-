package com.jarvis.app.memory

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
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

    @Query("SELECT * FROM conversation ORDER BY createdAt DESC LIMIT 40")
    suspend fun recent(): List<ConversationEntity>

    @Query("DELETE FROM conversation")
    suspend fun clear()
}
