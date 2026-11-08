@file:Suppress("DEPRECATION")

package de.tbhelfer.datenbank

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        BaustelleEntity::class,
        StreckenpunktEntity::class,
        BaustellenKnotenEntity::class,
        FotoEntity::class,
        TrassenAchseEntity::class,
        TrassenAchseElementEntity::class,
        TrassenSamplePointEntity::class,
        GvpPointEntity::class,
        AbsteckpunktEntity::class,
        TrassenImportStatusEntity::class,
        GraKmPunktEntity::class,
        TrassenAchseKorrekturEntity::class,
        TrassenKmAnkerEntity::class,
        TrassenManuellerAbschnittEntity::class,
        TrassenAbschnittKmPunktEntity::class,
        TrackSyncJournal::class
    ],
    version = 22,
    exportSchema = false
)
@TypeConverters(
    BaustellenKnotenTypKonverter::class,
    StreckenpunktTypKonverter::class,
    BildtypKonverter::class,
    IndikatorKonverter::class
)
abstract class TBDatenbank : RoomDatabase() {

    abstract fun baustelleDao(): BaustelleDao

    abstract fun streckenpunktDao(): StreckenpunktDao

    abstract fun baustellenKnotenDao(): BaustellenKnotenDao

    abstract fun fotoDao(): FotoDao

    abstract fun trackSyncJournalDao(): TrackSyncJournalDao

    abstract fun trassenDao(): TrassenDao

    companion object {
        // 20/21 were experimental schemas; never reuse those version numbers.
        // The established v19 database is extended without dropping any data.
        val MIGRATION_19_22 = object : androidx.room.migration.Migration(19, 22) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS track_sync_journal (owner TEXT NOT NULL, entryKey TEXT NOT NULL, payload TEXT NOT NULL, PRIMARY KEY(owner, entryKey))")
            }
        }


        private fun ensureBaustellenKnotenTable(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS baustellen_knoten (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    rootId INTEGER NOT NULL,
                    parentId INTEGER,
                    hauptbaustellenName TEXT NOT NULL,
                    knotenName TEXT,
                    altBaustelleId INTEGER NOT NULL,
                    typ TEXT NOT NULL,
                    streckenkennung TEXT,
                    kmVon REAL,
                    kmBis REAL,
                    bahnhofName TEXT,
                    gleisOderWeiche TEXT,
                    erstelltAmEpochMs INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_baustellen_knoten_rootId ON baustellen_knoten(rootId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_baustellen_knoten_parentId ON baustellen_knoten(parentId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_baustellen_knoten_hauptbaustellenName ON baustellen_knoten(hauptbaustellenName)")
            db.execSQL(
                """
                INSERT OR IGNORE INTO baustellen_knoten (
                    id, rootId, parentId, hauptbaustellenName, knotenName, altBaustelleId, typ,
                    streckenkennung, kmVon, kmBis, bahnhofName, gleisOderWeiche, erstelltAmEpochMs
                )
                SELECT
                    id,
                    id,
                    NULL,
                    baustellenname,
                    NULL,
                    id,
                    'HAUPT',
                    streckennummer,
                    NULL,
                    NULL,
                    bahnhof,
                    CASE
                        WHEN gleis IS NOT NULL AND TRIM(gleis) != '' THEN 'Gleis ' || gleis
                        WHEN weiche IS NOT NULL AND TRIM(weiche) != '' THEN 'Weiche ' || weiche
                        ELSE NULL
                    END,
                    erstelltAmEpochMs
                FROM baustelle
                """.trimIndent()
            )
        }

        // v6 -> v7: km-Spalte von NOT NULL auf NULLABLE (alt: km REAL NOT NULL DEFAULT 0)
        val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS foto_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        baustelleId INTEGER NOT NULL,
                        baustellenKnotenId INTEGER,
                        dateipfad TEXT NOT NULL,
                        gpsLat REAL NOT NULL,
                        gpsLon REAL NOT NULL,
                        gpsAltM REAL,
                        km REAL,
                        kmSuffix INTEGER NOT NULL,
                        kmManuell INTEGER NOT NULL,
                        achsabstandM REAL,
                        indikator TEXT,
                        firma TEXT NOT NULL,
                        bildtyp TEXT NOT NULL,
                        kurztext TEXT NOT NULL,
                        notiz TEXT,
                        objektKennung TEXT,
                        erstelltAmEpochMs INTEGER NOT NULL,
                        FOREIGN KEY(baustelleId) REFERENCES baustelle(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO foto_new (
                        id, baustelleId, baustellenKnotenId, dateipfad,
                        gpsLat, gpsLon, gpsAltM,
                        km, kmSuffix, kmManuell,
                        achsabstandM, indikator,
                        firma, bildtyp, kurztext, notiz, objektKennung, erstelltAmEpochMs
                    )
                    SELECT
                        id, baustelleId, baustellenKnotenId, dateipfad,
                        gpsLat, gpsLon, gpsAltM,
                        CASE WHEN km = 0 THEN NULL ELSE km END AS km,
                        kmSuffix, kmManuell,
                        achsabstandM, indikator,
                        firma, bildtyp, kurztext, notiz, objektKennung, erstelltAmEpochMs
                    FROM foto
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE foto")
                db.execSQL("ALTER TABLE foto_new RENAME TO foto")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_foto_baustelle_time ON foto(baustelleId, erstelltAmEpochMs)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_foto_baustelle_km ON foto(baustelleId, km)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_foto_baustelleId ON foto(baustelleId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_foto_baustellenKnotenId ON foto(baustellenKnotenId)")
            }
        }

        // v7 -> v8: FK auf baustellenKnotenId (SET_NULL) + Indikator als Enum (Column bleibt TEXT)
        val MIGRATION_7_8 = object : androidx.room.migration.Migration(7, 8) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                ensureBaustellenKnotenTable(db)
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS foto_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        baustelleId INTEGER NOT NULL,
                        baustellenKnotenId INTEGER,
                        dateipfad TEXT NOT NULL,
                        gpsLat REAL NOT NULL,
                        gpsLon REAL NOT NULL,
                        gpsAltM REAL,
                        km REAL,
                        kmSuffix INTEGER NOT NULL,
                        kmManuell INTEGER NOT NULL,
                        achsabstandM REAL,
                        indikator TEXT,
                        firma TEXT NOT NULL,
                        bildtyp TEXT NOT NULL,
                        kurztext TEXT NOT NULL,
                        notiz TEXT,
                        objektKennung TEXT,
                        erstelltAmEpochMs INTEGER NOT NULL,
                        FOREIGN KEY(baustelleId) REFERENCES baustelle(id) ON DELETE CASCADE,
                        FOREIGN KEY(baustellenKnotenId) REFERENCES baustellen_knoten(id) ON DELETE SET NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO foto_new (
                        id, baustelleId, baustellenKnotenId, dateipfad,
                        gpsLat, gpsLon, gpsAltM,
                        km, kmSuffix, kmManuell,
                        achsabstandM, indikator,
                        firma, bildtyp, kurztext, notiz, objektKennung, erstelltAmEpochMs
                    )
                    SELECT
                        id, baustelleId, baustellenKnotenId, dateipfad,
                        gpsLat, gpsLon, gpsAltM,
                        km, kmSuffix, kmManuell,
                        achsabstandM, indikator,
                        firma, bildtyp, kurztext, notiz, objektKennung, erstelltAmEpochMs
                    FROM foto
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE foto")
                db.execSQL("ALTER TABLE foto_new RENAME TO foto")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_foto_baustelle_time ON foto(baustelleId, erstelltAmEpochMs)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_foto_baustelle_km ON foto(baustelleId, km)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_foto_baustelleId ON foto(baustelleId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_foto_baustellenKnotenId ON foto(baustellenKnotenId)")
            }
        }



        // v8 -> v9: Streckenpunkte um Streckennummer/Bahnhof/Gleis/Weiche erweitern
        val MIGRATION_8_9 = object : androidx.room.migration.Migration(8, 9) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                ensureBaustellenKnotenTable(db)
                db.execSQL("ALTER TABLE streckenpunkt ADD COLUMN streckennummer TEXT")
                db.execSQL("ALTER TABLE streckenpunkt ADD COLUMN bahnhof TEXT")
                db.execSQL("ALTER TABLE streckenpunkt ADD COLUMN gleis TEXT")
                db.execSQL("ALTER TABLE streckenpunkt ADD COLUMN weiche TEXT")
            }
        }


        // v9 -> v10: optionaler Freitext-Typ für Streckenpunkte
        val MIGRATION_9_10 = object : androidx.room.migration.Migration(9, 10) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                ensureBaustellenKnotenTable(db)
                db.execSQL("ALTER TABLE streckenpunkt ADD COLUMN typFreitext TEXT")
            }
        }

        // v10 -> v11: fünf optionale frei benennbare Zusatzinfos pro Foto
        val MIGRATION_10_11 = object : androidx.room.migration.Migration(10, 11) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE foto ADD COLUMN info1Wert TEXT")
                db.execSQL("ALTER TABLE foto ADD COLUMN info2Wert TEXT")
                db.execSQL("ALTER TABLE foto ADD COLUMN info3Wert TEXT")
                db.execSQL("ALTER TABLE foto ADD COLUMN info4Wert TEXT")
                db.execSQL("ALTER TABLE foto ADD COLUMN info5Wert TEXT")
            }
        }

        // v11 -> v12: Grundlage für TB-Helfer 1.9 Vermessungsdaten / Trassenachsen
        val MIGRATION_11_12 = object : androidx.room.migration.Migration(11, 12) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS trassen_achse (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        baustelleId INTEGER,
                        name TEXT NOT NULL,
                        strecke TEXT,
                        gleis TEXT,
                        richtung TEXT,
                        achsTyp TEXT NOT NULL,
                        quelleDatei TEXT NOT NULL,
                        dateiSignatur TEXT,
                        koordinatensystem TEXT NOT NULL,
                        stationVon REAL,
                        stationBis REAL,
                        anzahlElemente INTEGER NOT NULL,
                        anzahlSamplepunkte INTEGER NOT NULL,
                        importStatus TEXT NOT NULL,
                        importiertAmEpochMs INTEGER NOT NULL,
                        notiz TEXT,
                        FOREIGN KEY(baustelleId) REFERENCES baustelle(id) ON DELETE SET NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_trassen_achse_baustelleId ON trassen_achse(baustelleId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_trassen_achse_strecke_gleis ON trassen_achse(strecke, gleis)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_trassen_achse_name ON trassen_achse(name)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_trassen_achse_quelle ON trassen_achse(quelleDatei)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS trassen_achse_element (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        achseId INTEGER NOT NULL,
                        elementIndex INTEGER NOT NULL,
                        stationStart REAL NOT NULL,
                        stationEnde REAL,
                        xDbRef REAL NOT NULL,
                        yDbRef REAL NOT NULL,
                        richtungDeg REAL,
                        elementTyp TEXT NOT NULL,
                        laengeM REAL NOT NULL,
                        radiusM REAL,
                        parameterVon REAL,
                        parameterBis REAL,
                        rawTyp INTEGER,
                        FOREIGN KEY(achseId) REFERENCES trassen_achse(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_trassen_achse_element_achseId ON trassen_achse_element(achseId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_trassen_element_achse_index ON trassen_achse_element(achseId, elementIndex)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_trassen_element_achse_station ON trassen_achse_element(achseId, stationStart)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS trassen_sample_point (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        achseId INTEGER NOT NULL,
                        station REAL NOT NULL,
                        kmMeter REAL,
                        xDbRef REAL NOT NULL,
                        yDbRef REAL NOT NULL,
                        lat REAL,
                        lon REAL,
                        hoeheM REAL,
                        richtungDeg REAL,
                        FOREIGN KEY(achseId) REFERENCES trassen_achse(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_trassen_sample_point_achseId ON trassen_sample_point(achseId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_trassen_sample_achse_station ON trassen_sample_point(achseId, station)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_trassen_sample_achse_km ON trassen_sample_point(achseId, kmMeter)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS gvp_point (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        baustelleId INTEGER,
                        strecke TEXT,
                        punktName TEXT NOT NULL,
                        kmMeter REAL,
                        xDbRef REAL NOT NULL,
                        yDbRef REAL NOT NULL,
                        hoeheM REAL,
                        typ TEXT,
                        klasse TEXT,
                        koordinatensystem TEXT NOT NULL,
                        quelleDatei TEXT NOT NULL,
                        rawLine TEXT,
                        importiertAmEpochMs INTEGER NOT NULL,
                        FOREIGN KEY(baustelleId) REFERENCES baustelle(id) ON DELETE SET NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_gvp_point_baustelleId ON gvp_point(baustelleId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_gvp_point_strecke_km ON gvp_point(strecke, kmMeter)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_gvp_point_punktName ON gvp_point(punktName)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS absteckpunkt (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        baustelleId INTEGER,
                        punktName TEXT NOT NULL,
                        strecke TEXT,
                        gleis TEXT,
                        kmMeter REAL,
                        xDbRef REAL NOT NULL,
                        yDbRef REAL NOT NULL,
                        hoeheM REAL,
                        typ TEXT,
                        quelleDatei TEXT NOT NULL,
                        rawLine TEXT,
                        importiertAmEpochMs INTEGER NOT NULL,
                        FOREIGN KEY(baustelleId) REFERENCES baustelle(id) ON DELETE SET NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_absteckpunkt_baustelleId ON absteckpunkt(baustelleId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_absteckpunkt_strecke_gleis ON absteckpunkt(strecke, gleis)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_absteckpunkt_punktName ON absteckpunkt(punktName)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS trassen_import_status (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        quelleDatei TEXT NOT NULL,
                        datenTyp TEXT NOT NULL,
                        status TEXT NOT NULL,
                        anzahlAchsen INTEGER NOT NULL,
                        anzahlElemente INTEGER NOT NULL,
                        anzahlSamplepunkte INTEGER NOT NULL,
                        anzahlGvpPunkte INTEGER NOT NULL,
                        anzahlAbsteckpunkte INTEGER NOT NULL,
                        warnungen INTEGER NOT NULL,
                        fehler INTEGER NOT NULL,
                        meldung TEXT,
                        importiertAmEpochMs INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_trassen_import_status_quelle ON trassen_import_status(quelleDatei)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_trassen_import_status_typ ON trassen_import_status(datenTyp)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_trassen_import_status_zeit ON trassen_import_status(importiertAmEpochMs)")
            }
        }


        // v12 -> v13: GRA-/kmLinie-Punkte fuer TB-Helfer 1.9 Patch 7
        val MIGRATION_12_13 = object : androidx.room.migration.Migration(12, 13) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS gra_km_punkt (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        baustelleId INTEGER,
                        quelleDatei TEXT NOT NULL,
                        punktIndex INTEGER NOT NULL,
                        strecke TEXT,
                        gleis TEXT,
                        richtung TEXT,
                        name TEXT,
                        kmMeter REAL NOT NULL,
                        hoeheM REAL,
                        radiusM REAL,
                        parameterM REAL,
                        punktCode INTEGER,
                        koordinatensystem TEXT NOT NULL,
                        rawOffset INTEGER,
                        importiertAmEpochMs INTEGER NOT NULL,
                        FOREIGN KEY(baustelleId) REFERENCES baustelle(id) ON DELETE SET NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_gra_km_punkt_baustelleId ON gra_km_punkt(baustelleId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_gra_km_strecke_gleis_km ON gra_km_punkt(strecke, gleis, kmMeter)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_gra_km_quelle ON gra_km_punkt(quelleDatei)")
            }
        }


        // v13 -> v14: DB_REF2016 Zone 4 nach WGS84/GPS vorbereiten
        val MIGRATION_13_14 = object : androidx.room.migration.Migration(13, 14) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE gvp_point ADD COLUMN lat REAL")
                db.execSQL("ALTER TABLE gvp_point ADD COLUMN lon REAL")
                db.execSQL("ALTER TABLE absteckpunkt ADD COLUMN lat REAL")
                db.execSQL("ALTER TABLE absteckpunkt ADD COLUMN lon REAL")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_trassen_sample_lat_lon ON trassen_sample_point(lat, lon)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_gvp_point_lat_lon ON gvp_point(lat, lon)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_absteckpunkt_lat_lon ON absteckpunkt(lat, lon)")
            }
        }




        // v14 -> v15: manuelle Achs-Korrekturen als eigene, reimportfeste Ebene
        val MIGRATION_14_15 = object : androidx.room.migration.Migration(14, 15) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS trassen_achse_korrektur (
                        achseName TEXT NOT NULL,
                        quelleDatei TEXT NOT NULL,
                        dateiSignatur TEXT,
                        originalAchsTyp TEXT,
                        originalStrecke TEXT,
                        originalGleis TEXT,
                        originalRichtung TEXT,
                        achsTypNeu TEXT NOT NULL,
                        streckeNeu TEXT,
                        gleisNeu TEXT,
                        richtungNeu TEXT,
                        erstelltAmEpochMs INTEGER NOT NULL,
                        aktualisiertAmEpochMs INTEGER NOT NULL,
                        notiz TEXT,
                        PRIMARY KEY(achseName, quelleDatei)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_trassen_korrektur_name ON trassen_achse_korrektur(achseName)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_trassen_korrektur_quelle ON trassen_achse_korrektur(quelleDatei)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_trassen_korrektur_ziel ON trassen_achse_korrektur(achsTypNeu, streckeNeu, gleisNeu)")
            }
        }


        // v15 -> v16: manuelle 2-Punkt-KM-Kalibrierung pro TRA-Achse
        val MIGRATION_15_16 = object : androidx.room.migration.Migration(15, 16) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Alte kmMeter-Werte aus frueheren Patches hatten keine Quellenkennzeichnung.
                // Sie bleiben erhalten, gelten aber erst nach Neuberechnung mit kmQuelle als belastbar.
                db.execSQL("ALTER TABLE trassen_sample_point ADD COLUMN kmQuelle TEXT")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS trassen_km_anker (
                        achseName TEXT NOT NULL,
                        quelleDatei TEXT NOT NULL,
                        punktNummer INTEGER NOT NULL,
                        aktuelleAchseId INTEGER,
                        dateiSignatur TEXT,
                        station REAL NOT NULL,
                        kmMeter REAL NOT NULL,
                        gpsLat REAL NOT NULL,
                        gpsLon REAL NOT NULL,
                        gpsAccuracyM REAL,
                        achsabstandM REAL,
                        erstelltAmEpochMs INTEGER NOT NULL,
                        aktualisiertAmEpochMs INTEGER NOT NULL,
                        PRIMARY KEY(achseName, quelleDatei, punktNummer)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_trassen_km_anker_achse ON trassen_km_anker(achseName, quelleDatei)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_trassen_km_anker_achse_id ON trassen_km_anker(aktuelleAchseId)")
            }
        }



        // v16 -> v17: Manuell festgelegte betriebliche Abschnitte auf unveraenderter TRA-Geometrie.
        val MIGRATION_16_17 = object : androidx.room.migration.Migration(16, 17) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS trassen_manueller_abschnitt (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        achseName TEXT NOT NULL,
                        quelleDatei TEXT NOT NULL,
                        aktuelleAchseId INTEGER,
                        dateiSignatur TEXT,
                        achsTyp TEXT NOT NULL,
                        bezeichnung TEXT NOT NULL,
                        strecke TEXT,
                        gleis TEXT,
                        richtung TEXT,
                        stationStart REAL NOT NULL,
                        stationEnde REAL,
                        kmPunkt1Meter REAL,
                        kmPunkt2Station REAL,
                        kmPunkt2Meter REAL,
                        startGpsLat REAL,
                        startGpsLon REAL,
                        startGpsAccuracyM REAL,
                        startGpsStreuungM REAL,
                        startAchsabstandM REAL,
                        punkt2GpsLat REAL,
                        punkt2GpsLon REAL,
                        punkt2GpsAccuracyM REAL,
                        punkt2GpsStreuungM REAL,
                        punkt2AchsabstandM REAL,
                        endeQuelle TEXT,
                        notiz TEXT,
                        erstelltAmEpochMs INTEGER NOT NULL,
                        aktualisiertAmEpochMs INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_trassen_manueller_abschnitt_achse ON trassen_manueller_abschnitt(achseName, quelleDatei)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_trassen_manueller_abschnitt_achse_id ON trassen_manueller_abschnitt(aktuelleAchseId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_trassen_manueller_abschnitt_ziel ON trassen_manueller_abschnitt(achsTyp, strecke, gleis)")
            }
        }

        // v17 -> v18: Flexible manuelle Abschnitte, dauerhafte Entwuerfe, mehrere KM-Anker und KM-Spruenge.
        val MIGRATION_17_18 = object : androidx.room.migration.Migration(17, 18) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS trassen_manueller_abschnitt_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        achseName TEXT,
                        quelleDatei TEXT,
                        aktuelleAchseId INTEGER,
                        dateiSignatur TEXT,
                        achsTyp TEXT NOT NULL,
                        bezeichnung TEXT NOT NULL,
                        strecke TEXT,
                        gleis TEXT,
                        richtung TEXT,
                        stationStart REAL,
                        stationEnde REAL,
                        startQuelle TEXT,
                        bekannteLaengeM REAL,
                        kmRichtung TEXT,
                        kmStationVorzeichen INTEGER,
                        kmAnkerStation REAL,
                        kmAnkerMeter REAL,
                        kmPunkt1Meter REAL,
                        kmPunkt2Station REAL,
                        kmPunkt2Meter REAL,
                        startGpsLat REAL,
                        startGpsLon REAL,
                        startGpsAccuracyM REAL,
                        startGpsStreuungM REAL,
                        startAchsabstandM REAL,
                        punkt2GpsLat REAL,
                        punkt2GpsLon REAL,
                        punkt2GpsAccuracyM REAL,
                        punkt2GpsStreuungM REAL,
                        punkt2AchsabstandM REAL,
                        endeQuelle TEXT,
                        bearbeitungsStatus TEXT NOT NULL,
                        notiz TEXT,
                        erstelltAmEpochMs INTEGER NOT NULL,
                        aktualisiertAmEpochMs INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO trassen_manueller_abschnitt_new (
                        id, achseName, quelleDatei, aktuelleAchseId, dateiSignatur,
                        achsTyp, bezeichnung, strecke, gleis, richtung,
                        stationStart, stationEnde, startQuelle, bekannteLaengeM,
                        kmRichtung, kmStationVorzeichen, kmAnkerStation, kmAnkerMeter,
                        kmPunkt1Meter, kmPunkt2Station, kmPunkt2Meter,
                        startGpsLat, startGpsLon, startGpsAccuracyM, startGpsStreuungM, startAchsabstandM,
                        punkt2GpsLat, punkt2GpsLon, punkt2GpsAccuracyM, punkt2GpsStreuungM, punkt2AchsabstandM,
                        endeQuelle, bearbeitungsStatus, notiz, erstelltAmEpochMs, aktualisiertAmEpochMs
                    )
                    SELECT
                        id, achseName, quelleDatei, aktuelleAchseId, dateiSignatur,
                        achsTyp, bezeichnung, strecke, gleis, richtung,
                        stationStart, stationEnde, 'GPS', NULL,
                        CASE
                            WHEN stationStart IS NOT NULL
                                 AND COALESCE(stationEnde, kmPunkt2Station) IS NOT NULL
                                 AND ABS(COALESCE(stationEnde, kmPunkt2Station) - stationStart) > 0.001
                                 AND kmPunkt1Meter IS NOT NULL
                                 AND kmPunkt2Station IS NOT NULL
                                 AND kmPunkt2Meter IS NOT NULL
                            THEN CASE
                                WHEN (
                                    CASE WHEN ((kmPunkt2Meter - kmPunkt1Meter) * (kmPunkt2Station - stationStart)) >= 0 THEN 1 ELSE -1 END
                                    *
                                    CASE WHEN COALESCE(stationEnde, kmPunkt2Station) > stationStart THEN 1 ELSE -1 END
                                ) > 0 THEN 'STEIGEND' ELSE 'FALLEND'
                            END
                            ELSE NULL
                        END,
                        CASE
                            WHEN stationStart IS NOT NULL
                                 AND kmPunkt1Meter IS NOT NULL
                                 AND kmPunkt2Station IS NOT NULL
                                 AND kmPunkt2Meter IS NOT NULL
                                 AND ABS(kmPunkt2Station - stationStart) > 0.001
                            THEN CASE WHEN ((kmPunkt2Meter - kmPunkt1Meter) * (kmPunkt2Station - stationStart)) >= 0 THEN 1 ELSE -1 END
                            ELSE NULL
                        END,
                        CASE WHEN kmPunkt1Meter IS NOT NULL THEN stationStart ELSE NULL END,
                        kmPunkt1Meter,
                        kmPunkt1Meter, kmPunkt2Station, kmPunkt2Meter,
                        startGpsLat, startGpsLon, startGpsAccuracyM, startGpsStreuungM, startAchsabstandM,
                        punkt2GpsLat, punkt2GpsLon, punkt2GpsAccuracyM, punkt2GpsStreuungM, punkt2AchsabstandM,
                        endeQuelle, CASE WHEN stationEnde IS NULL THEN 'OFFEN' ELSE 'FERTIG' END,
                        notiz, erstelltAmEpochMs, aktualisiertAmEpochMs
                    FROM trassen_manueller_abschnitt
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE trassen_manueller_abschnitt")
                db.execSQL("ALTER TABLE trassen_manueller_abschnitt_new RENAME TO trassen_manueller_abschnitt")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_trassen_manueller_abschnitt_achse ON trassen_manueller_abschnitt(achseName, quelleDatei)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_trassen_manueller_abschnitt_achse_id ON trassen_manueller_abschnitt(aktuelleAchseId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_trassen_manueller_abschnitt_ziel ON trassen_manueller_abschnitt(achsTyp, strecke, gleis)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS trassen_abschnitt_km_punkt (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        abschnittId INTEGER NOT NULL,
                        typ TEXT NOT NULL,
                        station REAL NOT NULL,
                        kmMeter REAL,
                        kmNachSprungMeter REAL,
                        gpsLat REAL,
                        gpsLon REAL,
                        gpsAccuracyM REAL,
                        gpsStreuungM REAL,
                        achsabstandM REAL,
                        notiz TEXT,
                        erstelltAmEpochMs INTEGER NOT NULL,
                        FOREIGN KEY(abschnittId) REFERENCES trassen_manueller_abschnitt(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_trassen_abschnitt_km_punkt_abschnitt ON trassen_abschnitt_km_punkt(abschnittId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_trassen_abschnitt_km_punkt_station ON trassen_abschnitt_km_punkt(abschnittId, station)")

                db.execSQL(
                    """
                    INSERT INTO trassen_abschnitt_km_punkt(abschnittId, typ, station, kmMeter, kmNachSprungMeter, erstelltAmEpochMs)
                    SELECT id, 'ANKER', stationStart, kmPunkt1Meter, NULL, aktualisiertAmEpochMs
                    FROM trassen_manueller_abschnitt
                    WHERE stationStart IS NOT NULL AND kmPunkt1Meter IS NOT NULL
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO trassen_abschnitt_km_punkt(abschnittId, typ, station, kmMeter, kmNachSprungMeter, erstelltAmEpochMs)
                    SELECT id, 'KONTROLLANKER', kmPunkt2Station, kmPunkt2Meter, NULL, aktualisiertAmEpochMs
                    FROM trassen_manueller_abschnitt
                    WHERE kmPunkt2Station IS NOT NULL AND kmPunkt2Meter IS NOT NULL
                    """.trimIndent()
                )
            }
        }


        // v18 -> v19: Foto-Bereich fuer Weichen- und DKW/Kreuzungsdokumentation.
        val MIGRATION_18_19 = object : androidx.room.migration.Migration(18, 19) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE foto ADD COLUMN objektBereich TEXT")
            }
        }

        /**
         * Kompatibilitaetsweg fuer die experimentellen Patch45ZW/ZX-Datenbanken.
         *
         * Diese Teststaende konnten die Room-Version bis 20/21 anheben. Patch45ZY
         * basiert wieder auf dem bestaetigten v19-Schema. Ein normales Downgrade
         * wuerde deshalb auf betroffenen Geraeten (z. B. Poco) bereits beim
         * Datenbank-Oeffnen abbrechen.
         *
         * Fuer die Rueckkehr auf v19 werden ausschliesslich Vermessungs-/TRA-Tabellen
         * neu aufgebaut. Baustellen, normale GPS-Streckenpunkte, Fotos und damit die
         * BTB-/Foto-Verknuepfungen werden nicht angefasst. Die Vermessungsdaten muessen
         * danach neu aus dem Streckendaten-ZIP importiert werden.
         */
        private fun trassenSchemaAufV19Zuruecksetzen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            val tabellen = linkedSetOf<String>()
            db.query("SELECT name FROM sqlite_master WHERE type='table'").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    val name = if (nameIndex >= 0) cursor.getString(nameIndex) else null
                    if (
                        !name.isNullOrBlank() &&
                        name.matches(Regex("[A-Za-z0-9_]+")) &&
                        (
                            name.startsWith("trassen_") ||
                                name == "gvp_point" ||
                                name == "absteckpunkt" ||
                                name == "gra_km_punkt"
                            )
                    ) {
                        tabellen += name
                    }
                }
            }

            // Fremdschluessel-Abhaengigkeiten dynamisch aufloesen, damit auch
            // experimentelle trassen_* Tabellen aus ZW/ZX zuerst als Kindtabellen
            // entfernt werden und keine FK-Verletzung entsteht.
            val kinderNachEltern = mutableMapOf<String, MutableSet<String>>()
            tabellen.forEach { kind ->
                db.query("PRAGMA foreign_key_list(`$kind`)").use { cursor ->
                    val tableIndex = cursor.getColumnIndex("table")
                    while (cursor.moveToNext()) {
                        val elternTabelle = if (tableIndex >= 0) cursor.getString(tableIndex) else null
                        if (!elternTabelle.isNullOrBlank() && elternTabelle in tabellen) {
                            kinderNachEltern.getOrPut(elternTabelle) { linkedSetOf() } += kind
                        }
                    }
                }
            }

            val geloescht = mutableSetOf<String>()
            val imPfad = mutableSetOf<String>()
            fun tabelleMitKindernLoeschen(tabelle: String) {
                if (tabelle in geloescht) return
                if (!imPfad.add(tabelle)) return
                kinderNachEltern[tabelle].orEmpty().sorted().forEach { kind ->
                    tabelleMitKindernLoeschen(kind)
                }
                imPfad.remove(tabelle)
                db.execSQL("DROP TABLE IF EXISTS `$tabelle`")
                geloescht += tabelle
            }
            tabellen.sorted().forEach { tabelleMitKindernLoeschen(it) }

            // Das bewaehrte v19-Trassenschema aus den vorhandenen Migrationen
            // reproduzieren. MIGRATION_18_19 betrifft nur die Foto-Tabelle und wird
            // hier absichtlich NICHT erneut ausgefuehrt.
            MIGRATION_11_12.migrate(db)
            MIGRATION_12_13.migrate(db)
            MIGRATION_13_14.migrate(db)
            MIGRATION_14_15.migrate(db)
            MIGRATION_15_16.migrate(db)
            MIGRATION_16_17.migrate(db)
            MIGRATION_17_18.migrate(db)

            // Marker fuer onOpen(): nur nach dieser Kompatibilitaetsmigration muessen
            // auch alte Streckendaten-Zuordnungen aus SharedPreferences weg.
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS tbhelfer_compat_reset_marker " +
                    "(id INTEGER PRIMARY KEY NOT NULL)"
            )
            db.execSQL("INSERT OR REPLACE INTO tbhelfer_compat_reset_marker(id) VALUES (1)")
        }

        val MIGRATION_20_19 = object : androidx.room.migration.Migration(20, 19) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                trassenSchemaAufV19Zuruecksetzen(db)
            }
        }

        val MIGRATION_21_19 = object : androidx.room.migration.Migration(21, 19) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                trassenSchemaAufV19Zuruecksetzen(db)
            }
        }

        private fun kompatibilitaetsResetNachMigration(
            context: Context,
            db: androidx.sqlite.db.SupportSQLiteDatabase
        ) {
            val markerVorhanden = db.query(
                "SELECT 1 FROM sqlite_master " +
                    "WHERE type='table' AND name='tbhelfer_compat_reset_marker' LIMIT 1"
            ).use { it.moveToFirst() }

            if (!markerVorhanden) return

            val appContext = context.applicationContext
            appContext.getSharedPreferences("tbhelfer_streckendaten_zuordnung", Context.MODE_PRIVATE)
                .edit().clear().commit()
            appContext.getSharedPreferences("tbhelfer_weichen_zuordnung", Context.MODE_PRIVATE)
                .edit().clear().commit()
            appContext.getSharedPreferences("tb_helfer_trassen_berechnung", Context.MODE_PRIVATE)
                .edit().clear().commit()

            db.execSQL("DROP TABLE IF EXISTS tbhelfer_compat_reset_marker")
        }

        fun erstellen(context: Context): TBDatenbank {
            return Room.databaseBuilder(
                context,
                TBDatenbank::class.java,
                de.tbhelfer.server.recovery.RecoveryWorkspace.databaseName("tbhelfer.db")
            )
                .addMigrations(
                    MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10,
                    MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14,
                    MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18,
                    MIGRATION_18_19, MIGRATION_19_22
                )
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        super.onOpen(db)
                        kompatibilitaetsResetNachMigration(context, db)
                    }
                })
                // Ältere Versionen (vor v6) können wir nicht sauber migrieren → dann neu erstellen.
                .fallbackToDestructiveMigrationFrom(1, 2, 3, 4, 5)
                .build()
        }
    }
}

