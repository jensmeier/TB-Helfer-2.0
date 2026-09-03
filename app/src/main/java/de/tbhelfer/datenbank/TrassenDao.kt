package de.tbhelfer.datenbank

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TrassenDao {

    @Query("SELECT * FROM trassen_achse ORDER BY strecke, gleis, name")
    fun achsenFlow(): Flow<List<TrassenAchseEntity>>

    @Query("SELECT * FROM trassen_import_status ORDER BY importiertAmEpochMs DESC")
    fun importStatusFlow(): Flow<List<TrassenImportStatusEntity>>

    @Query("SELECT COUNT(*) FROM trassen_achse")
    suspend fun anzahlAchsen(): Int

    @Query("SELECT COUNT(*) FROM trassen_achse_element")
    suspend fun anzahlAchselemente(): Int

    @Query("SELECT COUNT(*) FROM trassen_sample_point")
    suspend fun anzahlSamplepunkte(): Int

    @Query("SELECT COUNT(*) FROM gvp_point")
    suspend fun anzahlGvpPunkte(): Int

    @Query("SELECT COUNT(*) FROM absteckpunkt")
    suspend fun anzahlAbsteckpunkte(): Int

    @Query("SELECT COUNT(*) FROM gra_km_punkt")
    suspend fun anzahlGraPunkte(): Int

    @Query("SELECT COUNT(*) FROM trassen_sample_point WHERE lat IS NOT NULL AND lon IS NOT NULL")
    suspend fun anzahlSamplepunkteMitGps(): Int

    @Query("SELECT COUNT(*) FROM trassen_sample_point WHERE kmMeter IS NOT NULL")
    suspend fun anzahlSamplepunkteMitKm(): Int

    @Query("SELECT COUNT(*) FROM trassen_sample_point sp INNER JOIN trassen_achse a ON sp.achseId = a.id WHERE a.achsTyp = 'GLEIS'")
    suspend fun anzahlGleisSamplepunkte(): Int

    @Query("SELECT COUNT(*) FROM trassen_sample_point sp INNER JOIN trassen_achse a ON sp.achseId = a.id WHERE a.achsTyp = 'GLEIS' AND sp.kmMeter IS NOT NULL")
    suspend fun anzahlGleisSamplepunkteMitKm(): Int

    @Query("SELECT COUNT(*) FROM trassen_sample_point sp INNER JOIN trassen_achse a ON sp.achseId = a.id WHERE a.achsTyp = 'GLEIS' AND sp.lat IS NOT NULL AND sp.lon IS NOT NULL")
    suspend fun anzahlGleisSamplepunkteMitGps(): Int

    @Query("SELECT COUNT(*) FROM gvp_point WHERE lat IS NOT NULL AND lon IS NOT NULL")
    suspend fun anzahlGvpPunkteMitGps(): Int

    @Query("SELECT COUNT(*) FROM absteckpunkt WHERE lat IS NOT NULL AND lon IS NOT NULL")
    suspend fun anzahlAbsteckpunkteMitGps(): Int

    @Query("SELECT COUNT(*) FROM trassen_import_status")
    suspend fun anzahlImportStatus(): Int

    @Query("SELECT COUNT(*) FROM trassen_achse_korrektur")
    suspend fun anzahlAchsKorrekturen(): Int

    @Query("SELECT COUNT(*) FROM trassen_km_anker")
    suspend fun anzahlKmAnker(): Int

    @Query("SELECT COUNT(*) FROM trassen_manueller_abschnitt")
    suspend fun anzahlManuelleAbschnitte(): Int

    @Query("SELECT * FROM trassen_manueller_abschnitt ORDER BY aktualisiertAmEpochMs DESC, bezeichnung")
    suspend fun alleManuellenAbschnitte(): List<TrassenManuellerAbschnittEntity>

    @Query("SELECT * FROM trassen_manueller_abschnitt WHERE id = :id LIMIT 1")
    suspend fun manuellerAbschnittMitId(id: Long): TrassenManuellerAbschnittEntity?

    @Query("SELECT * FROM trassen_manueller_abschnitt WHERE achseName = :achseName AND quelleDatei = :quelleDatei ORDER BY stationStart")
    suspend fun manuelleAbschnitteFuerAchse(achseName: String, quelleDatei: String): List<TrassenManuellerAbschnittEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun manuellenAbschnittErsetzen(entity: TrassenManuellerAbschnittEntity): Long

    /** Patch45ZW: Neue Abschnitte einfuegen, bestehende per UPDATE aendern.
     *  Dadurch bleiben verknuepfte Kontrollanker/KM-Spruenge erhalten; REPLACE
     *  koennte wegen ON DELETE CASCADE Kinddatensaetze entfernen. */
    @Insert
    suspend fun manuellenAbschnittEinfuegen(entity: TrassenManuellerAbschnittEntity): Long

    @Update
    suspend fun manuellenAbschnittAktualisieren(entity: TrassenManuellerAbschnittEntity): Int

    @Query("UPDATE trassen_manueller_abschnitt SET aktuelleAchseId = :achseId WHERE achseName = :achseName AND quelleDatei = :quelleDatei")
    suspend fun manuelleAbschnitteMitAchseVerknuepfen(achseName: String, quelleDatei: String, achseId: Long): Int

    @Query("DELETE FROM trassen_manueller_abschnitt WHERE id = :id")
    suspend fun manuellenAbschnittLoeschen(id: Long): Int

    @Query("SELECT * FROM trassen_abschnitt_km_punkt WHERE abschnittId = :abschnittId ORDER BY station, erstelltAmEpochMs")
    suspend fun abschnittKmPunkte(abschnittId: Long): List<TrassenAbschnittKmPunktEntity>

    @Query("SELECT * FROM trassen_abschnitt_km_punkt ORDER BY abschnittId, station, erstelltAmEpochMs")
    suspend fun alleAbschnittKmPunkte(): List<TrassenAbschnittKmPunktEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun abschnittKmPunktErsetzen(entity: TrassenAbschnittKmPunktEntity): Long

    @Query("DELETE FROM trassen_abschnitt_km_punkt WHERE id = :id")
    suspend fun abschnittKmPunktLoeschen(id: Long): Int

    @Query("DELETE FROM trassen_abschnitt_km_punkt WHERE abschnittId = :abschnittId")
    suspend fun abschnittKmPunkteLoeschen(abschnittId: Long): Int

    @Query("DELETE FROM trassen_abschnitt_km_punkt WHERE abschnittId = :abschnittId AND typ = :typ")
    suspend fun abschnittKmPunkteNachTypLoeschen(abschnittId: Long, typ: String): Int

    @Query("SELECT * FROM trassen_km_anker ORDER BY achseName, quelleDatei, punktNummer")
    suspend fun alleKmAnker(): List<TrassenKmAnkerEntity>

    @Query("SELECT * FROM trassen_km_anker WHERE achseName = :achseName AND quelleDatei = :quelleDatei ORDER BY punktNummer")
    suspend fun kmAnkerFuerAchse(achseName: String, quelleDatei: String): List<TrassenKmAnkerEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun kmAnkerErsetzen(entity: TrassenKmAnkerEntity): Long

    @Query("DELETE FROM trassen_km_anker WHERE achseName = :achseName AND quelleDatei = :quelleDatei")
    suspend fun kmAnkerFuerAchseLoeschen(achseName: String, quelleDatei: String): Int

    @Query("UPDATE trassen_sample_point SET kmMeter = NULL, kmQuelle = NULL WHERE achseId = :achseId AND kmQuelle = 'MANUELL_2PUNKT'")
    suspend fun manuelleKmWerteFuerAchseLoeschen(achseId: Long): Int

    @Query("UPDATE trassen_km_anker SET aktuelleAchseId = :achseId WHERE achseName = :achseName AND quelleDatei = :quelleDatei")
    suspend fun kmAnkerMitAchseVerknuepfen(achseName: String, quelleDatei: String, achseId: Long): Int

    @Query("SELECT * FROM trassen_achse_korrektur ORDER BY aktualisiertAmEpochMs DESC, achseName")
    suspend fun alleAchsKorrekturen(): List<TrassenAchseKorrekturEntity>

    @Query("SELECT * FROM trassen_achse_korrektur WHERE achseName = :achseName AND quelleDatei = :quelleDatei LIMIT 1")
    suspend fun achsKorrekturFuerAchse(achseName: String, quelleDatei: String): TrassenAchseKorrekturEntity?

    @Query("DELETE FROM trassen_achse_korrektur WHERE achseName = :achseName AND quelleDatei = :quelleDatei")
    suspend fun achsKorrekturLoeschen(achseName: String, quelleDatei: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun achsKorrekturErsetzen(entity: TrassenAchseKorrekturEntity): Long

    @Query("SELECT * FROM trassen_import_status ORDER BY importiertAmEpochMs DESC LIMIT :limit")
    suspend fun letzteImportStatus(limit: Int = 10): List<TrassenImportStatusEntity>

    @Query("SELECT * FROM trassen_achse ORDER BY achsTyp, strecke, gleis, name")
    suspend fun alleAchsen(): List<TrassenAchseEntity>

    @Query("SELECT * FROM trassen_achse WHERE id = :achseId LIMIT 1")
    suspend fun achseMitId(achseId: Long): TrassenAchseEntity?

    @Query("SELECT strecke, COUNT(*) AS anzahl, MIN(kmMeter) AS kmVon, MAX(kmMeter) AS kmBis FROM gvp_point GROUP BY strecke ORDER BY strecke")
    suspend fun gvpUebersicht(): List<GvpUebersichtRow>

    @Query("SELECT quelleDatei, gleis, COUNT(*) AS anzahl, MIN(punktName) AS punktVon, MAX(punktName) AS punktBis FROM absteckpunkt GROUP BY quelleDatei, gleis ORDER BY quelleDatei, gleis")
    suspend fun absteckpunktUebersicht(): List<AbsteckpunktUebersichtRow>

    @Query("SELECT quelleDatei, strecke, gleis, COUNT(*) AS anzahl, MIN(kmMeter) AS kmVon, MAX(kmMeter) AS kmBis, MIN(hoeheM) AS hoeheMin, MAX(hoeheM) AS hoeheMax FROM gra_km_punkt GROUP BY quelleDatei, strecke, gleis ORDER BY quelleDatei, strecke, gleis")
    suspend fun graUebersicht(): List<GraUebersichtRow>

    @Query("SELECT * FROM trassen_sample_point")
    suspend fun alleSamplepunkte(): List<TrassenSamplePointEntity>

    @Query("SELECT * FROM gra_km_punkt ORDER BY strecke, gleis, kmMeter")
    suspend fun alleGraPunkte(): List<GraKmPunktEntity>

    @Query("SELECT * FROM trassen_sample_point WHERE lat IS NOT NULL AND lon IS NOT NULL ORDER BY achseId, station")
    suspend fun alleSamplepunkteMitGps(): List<TrassenSamplePointEntity>

    @Query("SELECT * FROM gvp_point")
    suspend fun alleGvpPunkte(): List<GvpPointEntity>

    @Query("SELECT * FROM absteckpunkt")
    suspend fun alleAbsteckpunkte(): List<AbsteckpunktEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun achseEinfuegen(entity: TrassenAchseEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun achseElementeEinfuegen(entities: List<TrassenAchseElementEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun samplePunkteEinfuegen(entities: List<TrassenSamplePointEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun samplePunkteErsetzen(entities: List<TrassenSamplePointEntity>): List<Long>

    @Query("SELECT * FROM trassen_sample_point WHERE achseId = :achseId ORDER BY station")
    suspend fun samplepunkteFuerAchse(achseId: Long): List<TrassenSamplePointEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun gvpPunkteEinfuegen(entities: List<GvpPointEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun absteckpunkteEinfuegen(entities: List<AbsteckpunktEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun graPunkteEinfuegen(entities: List<GraKmPunktEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun importStatusEinfuegen(entity: TrassenImportStatusEntity): Long

    @Query("UPDATE trassen_sample_point SET lat = :lat, lon = :lon WHERE id = :id")
    suspend fun sampleGpsAktualisieren(id: Long, lat: Double, lon: Double): Int

    @Query("UPDATE gvp_point SET lat = :lat, lon = :lon WHERE id = :id")
    suspend fun gvpGpsAktualisieren(id: Long, lat: Double, lon: Double): Int

    @Query("UPDATE absteckpunkt SET lat = :lat, lon = :lon WHERE id = :id")
    suspend fun absteckpunktGpsAktualisieren(id: Long, lat: Double, lon: Double): Int

    @Query("""
        UPDATE trassen_achse
        SET achsTyp = :achsTyp,
            strecke = :strecke,
            gleis = :gleis,
            richtung = :richtung,
            notiz = :notiz
        WHERE id = :achseId
    """)
    suspend fun achseMetadatenAktualisieren(
        achseId: Long,
        achsTyp: String,
        strecke: String?,
        gleis: String?,
        richtung: String?,
        notiz: String?
    ): Int

    @Query("DELETE FROM trassen_achse WHERE id = :achseId")
    suspend fun achseLoeschen(achseId: Long): Int

    @Query("DELETE FROM trassen_achse WHERE quelleDatei = :quelleDatei")
    suspend fun achsenMitQuelleLoeschen(quelleDatei: String): Int

    @Query("DELETE FROM trassen_import_status")
    suspend fun importStatusLeeren(): Int

    @Query("DELETE FROM trassen_achse")
    suspend fun achsenLeeren(): Int

    @Query("DELETE FROM gvp_point WHERE quelleDatei = :quelleDatei")
    suspend fun gvpMitQuelleLoeschen(quelleDatei: String): Int

    @Query("DELETE FROM gvp_point")
    suspend fun gvpLeeren(): Int

    @Query("DELETE FROM absteckpunkt WHERE quelleDatei = :quelleDatei")
    suspend fun absteckpunkteMitQuelleLoeschen(quelleDatei: String): Int

    @Query("DELETE FROM absteckpunkt")
    suspend fun absteckpunkteLeeren(): Int

    @Query("DELETE FROM gra_km_punkt WHERE quelleDatei = :quelleDatei")
    suspend fun graMitQuelleLoeschen(quelleDatei: String): Int

    @Query("DELETE FROM gra_km_punkt")
    suspend fun graLeeren(): Int

    @Query("DELETE FROM trassen_km_anker")
    suspend fun kmAnkerLeeren(): Int

    @Query("DELETE FROM trassen_achse_korrektur")
    suspend fun achsKorrekturenLeeren(): Int

    @Query("DELETE FROM trassen_manueller_abschnitt")
    suspend fun manuelleAbschnitteLeeren(): Int

}


data class GvpUebersichtRow(
    val strecke: String?,
    val anzahl: Int,
    val kmVon: Double?,
    val kmBis: Double?
)


data class AbsteckpunktUebersichtRow(
    val quelleDatei: String,
    val gleis: String?,
    val anzahl: Int,
    val punktVon: String?,
    val punktBis: String?
)


data class GraUebersichtRow(
    val quelleDatei: String,
    val strecke: String?,
    val gleis: String?,
    val anzahl: Int,
    val kmVon: Double?,
    val kmBis: Double?,
    val hoeheMin: Double?,
    val hoeheMax: Double?
)
