package de.tbhelfer.datenbank

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FotoDao {

    /** Für Suche (alle Baustellen). */
    @Query("SELECT * FROM foto ORDER BY erstelltAmEpochMs DESC")
    fun alleFotos(): Flow<List<FotoEntity>>

    /** Lightweight-Projektion (nur Pfade) für Cleanup/Orphan-Checks. */
    @Query("SELECT id, dateipfad FROM foto")
    suspend fun alleFotoPfade(): List<FotoPfad>

    /** Verhindert, dass eine gemeinsam referenzierte App-Datei zu früh gelöscht wird. */
    @Query("SELECT COUNT(*) FROM foto WHERE dateipfad = :dateipfad")
    suspend fun anzahlMitDateipfad(dateipfad: String): Int

    /** Detailansicht. */
    @Query("SELECT * FROM foto WHERE id = :id LIMIT 1")
    suspend fun fotoMitId(id: Long): FotoEntity?

    /** Mehrere Fotos (z.B. Multi-Select). */
    @Query("SELECT * FROM foto WHERE id IN (:ids)")
    suspend fun fotosMitIds(ids: List<Long>): List<FotoEntity>

    /** Nur DB löschen; der Repository-Layer räumt anschließend sichere App-Dateien auf. */
    @Query("DELETE FROM foto WHERE id IN (:ids)")
    suspend fun loeschenMitIds(ids: List<Long>): Int

    @Query("SELECT * FROM foto WHERE baustelleId = :baustelleId ORDER BY erstelltAmEpochMs DESC")
    fun fotosFuerBaustelle(baustelleId: Long): Flow<List<FotoEntity>>

    /** Einmaliger Abruf ohne Flow (z.B. Export/PDF/Listen-Init). */
    @Query("SELECT * FROM foto WHERE baustelleId = :baustelleId ORDER BY erstelltAmEpochMs DESC")
    suspend fun fotosFuerBaustelleOnce(baustelleId: Long): List<FotoEntity>

    @Query("SELECT * FROM foto WHERE baustelleId = :baustelleId AND baustellenKnotenId = :knotenId ORDER BY erstelltAmEpochMs DESC")
    fun fotosFuerUnterknoten(baustelleId: Long, knotenId: Long): Flow<List<FotoEntity>>

    @Query("SELECT * FROM foto WHERE baustelleId = :baustelleId AND baustellenKnotenId = :knotenId ORDER BY erstelltAmEpochMs DESC")
    suspend fun fotosFuerUnterknotenOnce(baustelleId: Long, knotenId: Long): List<FotoEntity>

    // Autovervollständigung
    @Query("SELECT DISTINCT firma FROM foto WHERE TRIM(firma) != '' ORDER BY firma COLLATE NOCASE")
    suspend fun alleFirmen(): List<String>

    @Query("SELECT DISTINCT kurztext FROM foto WHERE TRIM(kurztext) != '' ORDER BY kurztext COLLATE NOCASE")
    suspend fun alleKurztexte(): List<String>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun einfuegen(entity: FotoEntity): Long

    /** Notiz für mehrere Fotos setzen (z.B. nach Bearbeiten eines Foto-Eintrags im BTB). */
    @Query("UPDATE foto SET notiz = :notiz WHERE id IN (:ids)")
    suspend fun updateNotizMitIds(ids: List<Long>, notiz: String?): Int

    /** Echte Haupt-/Unterbaustellen-Zuordnung mehrerer Mangel-Fotos korrigieren. null = Hauptbaustelle. */
    @Query("UPDATE foto SET baustellenKnotenId = :knotenId WHERE id IN (:ids)")
    suspend fun updateBaustellenKnotenMitIds(ids: List<Long>, knotenId: Long?): Int

    /** Keep the physical image and stable id; edit text fields only. */
    @Query("UPDATE foto SET firma = :firma, kurztext = :kurztext, notiz = :notiz WHERE id = :id")
    suspend fun updateBeschriftung(id: Long, firma: String, kurztext: String, notiz: String?): Int

    @Delete
    suspend fun loeschen(entity: FotoEntity): Int
}
