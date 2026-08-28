package com.jarvis.app.contextgraph

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ContextGraphDao {
    // --- People ---
    @Query("SELECT * FROM people")
    fun getAllPeople(): Flow<List<PersonEntity>>

    @Query("SELECT * FROM people WHERE name = :name LIMIT 1")
    suspend fun getPersonByName(name: String): PersonEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPerson(person: PersonEntity): Long

    @Update
    suspend fun updatePerson(person: PersonEntity)

    @Delete
    suspend fun deletePerson(person: PersonEntity)

    // --- Places ---
    @Query("SELECT * FROM places")
    fun getAllPlaces(): Flow<List<PlaceEntity>>

    @Query("SELECT * FROM places WHERE label = :label LIMIT 1")
    suspend fun getPlaceByLabel(label: String): PlaceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlace(place: PlaceEntity): Long

    @Update
    suspend fun updatePlace(place: PlaceEntity)

    // --- App Aliases ---
    @Query("SELECT * FROM app_aliases")
    fun getAllAppAliases(): Flow<List<AppAliasEntity>>

    @Query("SELECT * FROM app_aliases")
    suspend fun getAllAppAliasesSync(): List<AppAliasEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAppAlias(appAlias: AppAliasEntity): Long

    // --- Habits ---
    @Query("SELECT * FROM habits")
    fun getAllHabits(): Flow<List<HabitEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHabit(habit: HabitEntity): Long
}
