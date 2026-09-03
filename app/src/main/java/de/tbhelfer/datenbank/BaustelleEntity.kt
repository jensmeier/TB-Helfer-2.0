package de.tbhelfer.datenbank

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "baustelle")
data class BaustelleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val baustellenname: String,
    val streckennummer: String? = null,
    val bahnhof: String? = null,
    val gleis: String? = null,
    val weiche: String? = null,
    val erstelltAmEpochMs: Long = System.currentTimeMillis()
)
