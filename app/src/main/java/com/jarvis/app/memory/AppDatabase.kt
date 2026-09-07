package com.jarvis.app.memory

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

import com.jarvis.app.contextgraph.AppAliasEntity
import com.jarvis.app.contextgraph.ContextGraphDao
import com.jarvis.app.contextgraph.HabitEntity
import com.jarvis.app.contextgraph.PlaceEntity

/**
 * JARVIS's persistent memory: facts, conversations, sessions, notifications, people,
 * places, app aliases and habits.
 *
 * ## Migration policy (fixed 2026-09-07 — was P0-D)
 *
 * This database used to be built with a blanket `fallbackToDestructiveMigration()` and
 * `exportSchema = false`. The combination meant that **every schema bump silently
 * deleted all of the user's memories, conversations and learned habits** — the data
 * JARVIS is supposed to accumulate — and because no schema was ever exported there was
 * no history to write a migration against, so the wipe was permanent and invisible.
 *
 * Now:
 *
 * - `exportSchema = true` with `room.schemaLocation` (see `app/build.gradle.kts`), so
 *   every version's schema is written to `app/schemas/` and committed. That history is
 *   what makes real migrations — and `MigrationTestHelper` tests — possible.
 * - There is **no blanket destructive fallback**. A version bump without a migration
 *   now throws `IllegalStateException` instead of quietly erasing the user's life. The
 *   CI job `room-schema-guard` exists to stop that shipping.
 * - Destruction is scoped to versions 1-4 only, via
 *   `fallbackToDestructiveMigrationFrom`. Those schemas were never exported and are
 *   unrecoverable, so a device upgrading from them has no migration path that could
 *   exist. This is a deliberate, one-time, documented loss — not a default.
 * - Downgrades (a dev reinstalling an older build) are also destructive, so a
 *   downgrade never corrupts a newer schema.
 *
 * See [Migrations] for how to add the next one.
 */
@Database(
    entities = [
        MemoryEntity::class,
        ConversationEntity::class,
        ChatSessionEntity::class,
        NotificationEntity::class,
        PersonEntity::class,
        PlaceEntity::class,
        AppAliasEntity::class,
        HabitEntity::class
    ],
    version = 5,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao
    abstract fun conversationDao(): ConversationDao
    abstract fun notificationDao(): NotificationDao
    abstract fun contextGraphDao(): ContextGraphDao
    abstract fun personDao(): PersonDao

    companion object {
        private const val TAG = "AppDatabase"
        private const val DB_NAME = "jarvis.db"

        /**
         * The lowest schema version whose JSON was ever exported. Anything below this
         * has no recoverable history, which is why destruction is scoped to it.
         *
         * Must equal the `version` in the `@Database` annotation above until the first
         * real migration lands; the CI `room-schema-guard` job checks that.
         */
        const val EXPORTED_BASELINE = 5

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: build(context.applicationContext).also { INSTANCE = it }
            }

        private fun build(appContext: Context): AppDatabase =
            Room.databaseBuilder(appContext, AppDatabase::class.java, DB_NAME)
                .addMigrations(*Migrations.ALL)
                // Versions 1-4: schemas were never exported, so no migration can exist.
                // Scoped on purpose — this is NOT a general "wipe when unsure" escape hatch.
                .fallbackToDestructiveMigrationFrom(true, 1, 2, 3, 4)
                .fallbackToDestructiveMigrationOnDowngrade(true)
                .addCallback(object : Callback() {
                    override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
                        // Should only ever fire for the scoped 1-4 path above. If this
                        // appears on a 5+ upgrade, a migration is missing and user data
                        // was just destroyed — make that impossible to miss in logcat.
                        Log.e(
                            TAG,
                            "Destructive migration ran on jarvis.db (version ${db.version}). " +
                                "If this was not a pre-v5 install, a Migration is missing and " +
                                "user memories were deleted. See Migrations.kt."
                        )
                    }

                    override fun onOpen(db: SupportSQLiteDatabase) {
                        Log.i(TAG, "jarvis.db opened at schema version ${db.version}")
                    }
                })
                .build()
    }
}
