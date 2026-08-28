package com.jarvis.app.contextgraph

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters

@Entity(tableName = "places")
data class PlaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String, // e.g. "home", "work", "gym"
    val address: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null
)

@Entity(tableName = "app_aliases")
@TypeConverters(StringListConverter::class)
data class AppAliasEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val defaultLabel: String,
    val nicknames: List<String> = emptyList() // e.g. "the bank app"
)

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val description: String,
    val frequency: Int = 0,
    val timeOfDay: String? = null
)

class StringListConverter {
    @TypeConverter
    fun fromList(list: List<String>): String = list.joinToString("|")

    @TypeConverter
    fun toList(string: String): List<String> = if (string.isEmpty()) emptyList() else string.split("|")
}

