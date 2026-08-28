package com.jarvis.app.memory

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * A person JARVIS knows about.
 *
 * This is the "personal context graph" from the plan. It is seeded automatically from the
 * phone's address book — the user never types a contact list. The only thing JARVIS asks
 * for is what it genuinely cannot infer: a nickname ("who is mumsi?") or a relationship
 * ("what is Amaka to you?") — and each is asked once, with a picker, then remembered.
 */
@Entity(tableName = "people")
data class PersonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val displayName: String,
    /** Android contact lookup key, so imported people can be re-synced. */
    val lookupKey: String = "",
    /** JSON: [{"number":"+234803...","type":"mobile"}] */
    val phoneNumbers: String = "[]",
    /** JSON: ["mumsi","mummy"] — learned, not typed in bulk. */
    val nicknames: String = "[]",
    /** mother, father, wife, husband, brother, sister, friend, boss, other */
    val relationship: String = "",
    /** sms | whatsapp | telegram | call */
    val preferredChannel: String = "",
    val notes: String = "",
    val lastContactedAt: Long = 0,
    val timesContacted: Int = 0
)

@Dao
interface PersonDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(person: PersonEntity): Long

    @Update
    suspend fun update(person: PersonEntity)

    @Query("SELECT * FROM people ORDER BY timesContacted DESC, displayName ASC")
    suspend fun all(): List<PersonEntity>

    @Query("SELECT * FROM people ORDER BY timesContacted DESC, displayName ASC")
    fun allFlow(): Flow<List<PersonEntity>>

    @Query("SELECT * FROM people WHERE relationship = :relationship LIMIT 5")
    suspend fun byRelationship(relationship: String): List<PersonEntity>

    @Query("DELETE FROM people WHERE lookupKey = :lookupKey")
    suspend fun deleteByLookupKey(lookupKey: String)

    @Query("DELETE FROM people")
    suspend fun clear()
}
