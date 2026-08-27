package com.jarvis.app.memory

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
    val type: String,          // semantic / episodic / preference / person
    val importance: Int = 50,  // 0-100
    val source: String = "user",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val expiresAt: Long? = null
)

@Entity(tableName = "conversation")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String,
    val text: String,
    val createdAt: Long = System.currentTimeMillis()
)
