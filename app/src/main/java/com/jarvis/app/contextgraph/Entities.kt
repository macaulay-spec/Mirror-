package com.jarvis.app.contextgraph

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "places")
data class PlaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val address: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val nicknames: String = "[]"
)

@Entity(tableName = "app_aliases")
data class AppAliasEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val defaultLabel: String,
    val nicknames: String = "[]"
)

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pattern: String,
    val action: String,
    val description: String = ""
)
