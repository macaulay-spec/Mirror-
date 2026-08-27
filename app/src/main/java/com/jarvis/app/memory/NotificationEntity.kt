package com.jarvis.app.memory

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val fullContent: String = "",
    val sbnKey: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val hasReplyAction: Boolean = false,
    val isRead: Boolean = false
)

@Dao
interface NotificationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(notification: NotificationEntity): Long

    @Query("SELECT * FROM notifications ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentFlow(limit: Int = 50): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 30): List<NotificationEntity>

    @Query("SELECT * FROM notifications WHERE packageName = :packageName OR appLabel LIKE :appName ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getByApp(packageName: String, appName: String, limit: Int = 20): List<NotificationEntity>

    @Query("SELECT * FROM notifications WHERE isRead = 0 ORDER BY timestamp DESC")
    suspend fun getUnread(): List<NotificationEntity>

    @Query("UPDATE notifications SET isRead = 1 WHERE id = :id")
    suspend fun markAsRead(id: Long)

    @Query("DELETE FROM notifications WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM notifications")
    suspend fun clearAll()
}
