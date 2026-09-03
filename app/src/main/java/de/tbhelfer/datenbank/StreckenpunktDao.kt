package de.tbhelfer.datenbank

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface StreckenpunktDao {

    @Query("SELECT * FROM streckenpunkt")
    fun allePunkte(): Flow<List<StreckenpunktEntity>>

    @Query("SELECT * FROM streckenpunkt WHERE baustelleId = :baustelleId ORDER BY km ASC")
    fun punkteFuerBaustelle(baustelleId: Long): Flow<List<StreckenpunktEntity>>

    @Query("SELECT * FROM streckenpunkt")
    suspend fun ladeAlleOnce(): List<StreckenpunktEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun einfuegen(entity: StreckenpunktEntity): Long

    @Query("SELECT COUNT(*) FROM streckenpunkt WHERE streckennummer = :streckenkennung")
    suspend fun anzahlFuerStrecke(streckenkennung: String): Int

    @Query("SELECT COUNT(*) FROM streckenpunkt WHERE bahnhof = :bahnhof")
    suspend fun anzahlFuerBahnhof(bahnhof: String): Int

    @Query("SELECT COUNT(*) FROM streckenpunkt WHERE bahnhof = :bahnhof AND gleis = :gleis")
    suspend fun anzahlFuerBahnhofGleis(bahnhof: String, gleis: String): Int

    @Query("SELECT COUNT(*) FROM streckenpunkt WHERE bahnhof = :bahnhof AND weiche = :weiche")
    suspend fun anzahlFuerBahnhofWeiche(bahnhof: String, weiche: String): Int

    @Update
    suspend fun aktualisieren(entity: StreckenpunktEntity): Int

    /** Rückgabewert: Anzahl gelöschter Zeilen (vermeidet KSP/Room Probleme mit 'void'). */
    @Delete
    suspend fun loeschen(entity: StreckenpunktEntity): Int

    @Query("DELETE FROM streckenpunkt WHERE baustelleId = :baustelleId")
    suspend fun loeschenFuerBaustelle(baustelleId: Long): Int
}
