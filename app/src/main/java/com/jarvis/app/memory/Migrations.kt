package com.jarvis.app.memory

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Every schema migration JARVIS has ever shipped.
 *
 * ## Why this file exists
 *
 * Before 2026-09-07 `AppDatabase` was built with `fallbackToDestructiveMigration()` and
 * `exportSchema = false`. Bumping `version` therefore **deleted the user's memories,
 * conversations, people, places and habits** without a word. Versions 1-4 are gone for
 * good because no schema was ever written down to migrate from; version 5 is the
 * baseline, and from here on every change preserves data.
 *
 * ## Adding a migration (v5 -> v6 example)
 *
 * 1. Change the entity/DAO.
 * 2. Bump `version = 5` to `version = 6` in the `@Database` annotation on
 *    [AppDatabase].
 * 3. Add the migration here and register it in [ALL]:
 *
 *    ```kotlin
 *    val MIGRATION_5_6 = object : Migration(5, 6) {
 *        override fun migrate(db: SupportSQLiteDatabase) {
 *            db.execSQL("ALTER TABLE memories ADD COLUMN importance INTEGER NOT NULL DEFAULT 0")
 *        }
 *    }
 *    ```
 *
 * 4. Build once. KSP writes `app/schemas/com.jarvis.app.memory.AppDatabase/6.json`
 *    (because `exportSchema = true` and `room.schemaLocation` is set in
 *    `app/build.gradle.kts`). **Commit that file.** It is the history future migrations
 *    are written against, and it is what a `MigrationTestHelper` test needs.
 *
 * 5. The CI job `room-schema-guard` fails the build if the version in the annotation has
 *    no committed schema JSON, or if there is no migration path from [BASELINE_VERSION]
 *    to it. That is deliberate: a forgotten migration must be caught before it reaches a
 *    phone, where Room will now throw `IllegalStateException` instead of wiping data.
 *
 * ### Additive changes: prefer Room auto-migration
 *
 * For purely additive changes (new column with a default, new table, new index) you can
 * let Room write the SQL by declaring it on the annotation instead:
 *
 * ```kotlin
 * @Database(..., version = 6, exportSchema = true,
 *     autoMigrations = [AutoMigration(from = 5, to = 6)])
 * ```
 *
 * This needs both `5.json` and `6.json` committed under `app/schemas/`, which is another
 * reason step 4 is not optional. Auto-migration cannot handle renames or type changes —
 * for those, write it by hand here and supply an `AutoMigrationSpec` if a table or column
 * was renamed.
 */
object Migrations {

    /**
     * The first schema version with an exported JSON history. Mirrors
     * [AppDatabase.EXPORTED_BASELINE]; the CI guard checks they agree with the `version`
     * in the `@Database` annotation.
     */
    const val BASELINE_VERSION = 5

    /**
     * Registered with `addMigrations(*ALL)`.
     *
     * Empty today: version 5 is the baseline, so there is nothing to migrate *to* yet.
     * It is not a placeholder — the moment `version` moves past 5 this array must be
     * non-empty, and CI enforces that.
     */
    val ALL: Array<Migration> = arrayOf()

    /**
     * Convenience for the most common migration shape: adding a column.
     *
     * Kept here so hand-written migrations stay short and consistent, and so the SQL is
     * always `NOT NULL DEFAULT`-safe — an `ALTER TABLE ADD COLUMN` without a default
     * fails outright on a table that already has rows.
     *
     * Usage, for a 5 -> 6 bump:
     * ```kotlin
     * val ALL: Array<Migration> = arrayOf(
     *     addColumn(from = 5, to = 6, table = "memories",
     *               column = "importance", sqlType = "INTEGER", defaultValue = "0")
     * )
     * ```
     *
     * @param defaultValue a raw SQL literal, so strings must be quoted by the caller
     *   (`"'unknown'"`, not `"unknown"`).
     */
    fun addColumn(
        from: Int,
        to: Int,
        table: String,
        column: String,
        sqlType: String,
        defaultValue: String
    ): Migration {
        require(to == from + 1) {
            "addColumn builds single-step migrations; got $from -> $to. Chain one per version."
        }
        return object : Migration(from, to) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE $table ADD COLUMN $column $sqlType NOT NULL DEFAULT $defaultValue"
                )
            }
        }
    }
}
