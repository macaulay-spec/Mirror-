package com.jarvis.app.memory

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

import com.jarvis.app.contextgraph.AppAliasEntity
import com.jarvis.app.contextgraph.ContextGraphDao
import com.jarvis.app.contextgraph.HabitEntity
import com.jarvis.app.contextgraph.PersonEntity
import com.jarvis.app.contextgraph.PlaceEntity

@Database(entities = [
    MemoryEntity::class, 
    ConversationEntity::class, 
    NotificationEntity::class,
    PersonEntity::class,
    PlaceEntity::class,
    AppAliasEntity::class,
    HabitEntity::class
], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao
    abstract fun conversationDao(): ConversationDao
    abstract fun notificationDao(): NotificationDao
    abstract fun contextGraphDao(): ContextGraphDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "jarvis.db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
    }
}
