package de.tbhelfer.datenbank

import de.tbhelfer.vermessung.AbsteckCsvParseResult
import de.tbhelfer.vermessung.GvpParseResult
import de.tbhelfer.vermessung.KorParseResult
import de.tbhelfer.vermessung.DbRefKoordinatenwandler
import de.tbhelfer.vermessung.GraParseResult
import de.tbhelfer.vermessung.TraParseResult
import de.tbhelfer.vermessung.TrassenNaheBerechner
import de.tbhelfer.vermessung.TrassenKmStatusLogik
import de.tbhelfer.vermessung.ManuelleAbschnittKmLogik
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.util.Locale

class TrassenRepository(private val dao: TrassenDao) {

    fun achsenFlow(): Flow<List<TrassenAchseEntity>> = dao.achsenFlow().flowOn(Dispatchers.IO)

    fun importStatusFlow(): Flow<List<TrassenImportStatusEntity>> = dao.importStatusFlow().flowOn(Dispatchers.IO)

    suspend fun ladeUebersicht(): TrassenUebersicht = withContext(Dispatchers.IO) {
        val achsenAlle = dao.alleAchsen()
        val sampleAlle = dao.alleSamplepunkte()
        val ankerAlle = dao.alleKmAnker()
        val sampleNachAchse = sampleAlle.groupBy { it.achseId }
        val ankerNachAchse = ankerAlle.groupBy { it.achseName to it.quelleDatei }
        val kmBewertungen = achsenAlle.associate { achse ->
            val bewertung = TrassenKmStatusLogik.bewerte(
                punkte = sampleNachAchse[achse.id].orEmpty().map { TrassenKmStatusLogik.Punkt(it.station, it.kmMeter.takeIf { _ -> it.kmQuelle != null }) },
                manuelleAnker = ankerNachAchse[achse.name to achse.quelleDatei].orEmpty()
                    .map { TrassenKmStatusLogik.Anker(it.punktNummer, it.station, it.kmMeter) }
            )
            achse.id to bewertung
        }

        TrassenUebersicht(
            anzahlAchsen = achsenAlle.size,
            anzahlAchselemente = dao.anzahlAchselemente(),
            anzahlSamplepunkte = sampleAlle.size,
            anzahlGvpPunkte = dao.anzahlGvpPunkte(),
            anzahlAbsteckpunkte = dao.anzahlAbsteckpunkte(),
            anzahlGraPunkte = dao.anzahlGraPunkte(),
            anzahlSamplepunkteMitGps = dao.anzahlSamplepunkteMitGps(),
            anzahlSamplepunkteMitKm = dao.anzahlSamplepunkteMitKm(),
            anzahlGleisSamplepunkte = dao.anzahlGleisSamplepunkte(),
            anzahlGleisSamplepunkteMitGps = dao.anzahlGleisSamplepunkteMitGps(),
            anzahlGleisSamplepunkteMitKm = dao.anzahlGleisSamplepunkteMitKm(),
            anzahlGvpPunkteMitGps = dao.anzahlGvpPunkteMitGps(),
            anzahlAbsteckpunkteMitGps = dao.anzahlAbsteckpunkteMitGps(),
            anzahlImportStatus = dao.anzahlImportStatus(),
            anzahlAchsKorrekturen = dao.anzahlAchsKorrekturen(),
            anzahlKmAnker = ankerAlle.size,
            kmAutomatischAchsen = kmBewertungen.values.count { it.status == TrassenKmStatusLogik.STATUS_AUTOMATISCH },
            kmManuellAchsen = kmBewertungen.values.count { it.status == TrassenKmStatusLogik.STATUS_MANUELL },
            kmFehltAchsen = kmBewertungen.values.count { it.status == TrassenKmStatusLogik.STATUS_FEHLT },
            kmUnplausibelAchsen = kmBewertungen.values.count { it.status == TrassenKmStatusLogik.STATUS_UNPLAUSIBEL },
            letzteStatus = dao.letzteImportStatus(10),
            achsen = achsenAlle,
            achsKorrekturen = dao.alleAchsKorrekturen(),
            gvpGruppen = dao.gvpUebersicht(),
            absteckpunktGruppen = dao.absteckpunktUebersicht(),
            graGruppen = dao.graUebersicht()
        )
    }

    suspend fun importStatusSpeichern(entity: TrassenImportStatusEntity): Long =
        withContext(Dispatchers.IO) { dao.importStatusEinfuegen(entity) }

    suspend fun alleKmAnker(): List<TrassenKmAnkerEntity> = withContext(Dispatchers.IO) {
        dao.alleKmAnker()
    }

    suspend fun speichereManuellenKmAnker(
        achseId: Long,
        punktNummer: Int,
        station: Double,
        kmMeter: Double,
        gpsLat: Double,
        gpsLon: Double,
        gpsAccuracyM: Float?,
        achsabstandM: Double?
    ): ManuelleKmKalibrierungErgebnis = withContext(Dispatchers.IO) {
        require(punktNummer in 1..2) { "Punktnummer muss 1 oder 2 sein." }
        require(station.isFinite() && kmMeter.isFinite()) { "Station und km muessen gueltig sein." }
        val achse = dao.achseMitId(achseId) ?: error("Achse nicht gefunden: $achseId")

        dao.kmAnkerErsetzen(
            TrassenKmAnkerEntity(
                achseName = achse.name,
                quelleDatei = achse.quelleDatei,
                punktNummer = punktNummer,
                aktuelleAchseId = achse.id,
                dateiSignatur = achse.dateiSignatur,
                station = station,
                kmMeter = kmMeter,
                gpsLat = gpsLat,
                gpsLon = gpsLon,
                gpsAccuracyM = gpsAccuracyM,
                achsabstandM = achsabstandM
            )
        )
        manuelleKmKalibrierungFuerAchseAnwenden(achse)
    }

    suspend fun loescheManuelleKmKalibrierung(achseId: Long): Int = withContext(Dispatchers.IO) {
        val achse = dao.achseMitId(achseId) ?: return@withContext 0
        val anker = dao.kmAnkerFuerAchseLoeschen(achse.name, achse.quelleDatei)
        val kmWerte = dao.manuelleKmWerteFuerAchseLoeschen(achse.id)
        anker + kmWerte
    }

    suspend fun manuelleKmKalibrierungenAnwenden(): Int = withContext(Dispatchers.IO) {
        val achsen = dao.alleAchsen()
        var angewendet = 0
        achsen.forEach { achse ->
            val ergebnis = manuelleKmKalibrierungFuerAchseAnwenden(achse)
            if (ergebnis.angewendet) angewendet++
        }
        angewendet
    }

    private suspend fun manuelleKmKalibrierungFuerAchseAnwenden(
        achse: TrassenAchseEntity
    ): ManuelleKmKalibrierungErgebnis {
        dao.kmAnkerMitAchseVerknuepfen(achse.name, achse.quelleDatei, achse.id)
        val anker = dao.kmAnkerFuerAchse(achse.name, achse.quelleDatei)
            .sortedBy { it.punktNummer }
        if (anker.size < 2) {
            return ManuelleKmKalibrierungErgebnis(
                angewendet = false,
                status = TrassenKmStatusLogik.STATUS_FEHLT,
                ankerAnzahl = anker.size,
                hinweis = if (anker.size == 1) "Punkt 1 gespeichert. Fuer die Berechnung fehlt Punkt 2." else "Keine manuellen KM-Anker."
            )
        }

        val a = TrassenKmStatusLogik.Anker(anker[0].punktNummer, anker[0].station, anker[0].kmMeter)
        val b = TrassenKmStatusLogik.Anker(anker[1].punktNummer, anker[1].station, anker[1].kmMeter)
        val kal = TrassenKmStatusLogik.kalibrierungAus(a, b)
        if (!kal.plausibel) {
            return ManuelleKmKalibrierungErgebnis(
                angewendet = false,
                status = TrassenKmStatusLogik.STATUS_FEHLT,
                ankerAnzahl = anker.size,
                stationAbstandM = kal.stationAbstandM,
                kmAbstandM = kal.kmAbstandM,
                steigend = kal.steigend,
                hinweis = kal.hinweis
            )
        }

        val samples = dao.samplepunkteFuerAchse(achse.id)
        if (samples.size < 2) {
            return ManuelleKmKalibrierungErgebnis(
                angewendet = false,
                status = TrassenKmStatusLogik.STATUS_FEHLT,
                ankerAnzahl = anker.size,
                hinweis = "Achse hat zu wenige berechnete Punkte."
            )
        }
        dao.samplePunkteErsetzen(samples.map { sample ->
            sample.copy(kmMeter = kal.kmFuerStation(sample.station), kmQuelle = "MANUELL_2PUNKT")
        })

        return ManuelleKmKalibrierungErgebnis(
            angewendet = true,
            status = TrassenKmStatusLogik.STATUS_MANUELL,
            ankerAnzahl = anker.size,
            stationAbstandM = kal.stationAbstandM,
            kmAbstandM = kal.kmAbstandM,
            steigend = kal.steigend,
            hinweis = null
        )
    }

    suspend fun traImportSpeichern(result: TraParseResult): Long = withContext(Dispatchers.IO) {
        // Gleiche Quelldatei ersetzt den alten Import. Elemente/Samplepunkte werden per CASCADE entfernt.
        dao.achsenMitQuelleLoeschen(result.achse.quelleDatei)

        val achseId = dao.achseEinfuegen(result.achse)

        // Patch40: gespeicherte manuelle Korrektur nach Re-Import wieder anwenden.
        dao.achsKorrekturFuerAchse(result.achse.name, result.achse.quelleDatei)?.let { korrektur ->
            dao.achseMetadatenAktualisieren(
                achseId = achseId,
                achsTyp = korrektur.achsTypNeu,
                strecke = korrektur.streckeNeu,
                gleis = korrektur.gleisNeu,
                richtung = korrektur.richtungNeu,
                notiz = "manuelle Achs-Korrektur automatisch angewendet"
            )
        }

        val elemente = result.elemente.map { vorlage ->
            TrassenAchseElementEntity(
                achseId = achseId,
                elementIndex = vorlage.elementIndex,
                stationStart = vorlage.stationStart,
                stationEnde = vorlage.stationEnde,
                xDbRef = vorlage.xDbRef,
                yDbRef = vorlage.yDbRef,
                richtungDeg = vorlage.richtungDeg,
                elementTyp = vorlage.elementTyp,
                laengeM = vorlage.laengeM,
                radiusM = vorlage.radiusM,
                parameterVon = vorlage.parameterVon,
                parameterBis = vorlage.parameterBis,
                rawTyp = vorlage.rawTyp
            )
        }
        if (elemente.isNotEmpty()) {
            dao.achseElementeEinfuegen(elemente)
        }

        val samples = result.samples.map { vorlage ->
            val gps = DbRefKoordinatenwandler.dbRef2016Zone4ZuWgs84OrNull(vorlage.xDbRef, vorlage.yDbRef)
            TrassenSamplePointEntity(
                achseId = achseId,
                station = vorlage.station,
                xDbRef = vorlage.xDbRef,
                yDbRef = vorlage.yDbRef,
                lat = gps?.lat,
                lon = gps?.lon,
                richtungDeg = vorlage.richtungDeg
            )
        }
        if (samples.isNotEmpty()) {
            dao.samplePunkteEinfuegen(samples)
        }

        // Patch43: vorhandene manuelle KM-Anker ueberleben einen Re-Import derselben Achse.
        dao.kmAnkerMitAchseVerknuepfen(result.achse.name, result.achse.quelleDatei, achseId)
        dao.manuelleAbschnitteMitAchseVerknuepfen(result.achse.name, result.achse.quelleDatei, achseId)
        val neuImportierteAchse = result.achse.copy(id = achseId)
        manuelleKmKalibrierungFuerAchseAnwenden(neuImportierteAchse)

        dao.importStatusEinfuegen(
            TrassenImportStatusEntity(
                quelleDatei = result.achse.quelleDatei,
                datenTyp = "TRA",
                status = TrassenAchseEntity.STATUS_IMPORTIERT,
                anzahlAchsen = 1,
                anzahlElemente = elemente.size,
                anzahlSamplepunkte = samples.size,
                warnungen = result.warnungen.size,
                meldung = result.achse.notiz
            )
        )

        achseId
    }


    suspend fun gvpImportSpeichern(dateiname: String, result: GvpParseResult): Int = withContext(Dispatchers.IO) {
        // Gleiche Quelldatei ersetzt den alten GVP-Import.
        dao.gvpMitQuelleLoeschen(dateiname)

        if (result.punkte.isNotEmpty()) {
            dao.gvpPunkteEinfuegen(result.punkte)
        }

        dao.importStatusEinfuegen(
            TrassenImportStatusEntity(
                quelleDatei = dateiname,
                datenTyp = "GVP",
                status = TrassenAchseEntity.STATUS_IMPORTIERT,
                anzahlGvpPunkte = result.punkte.size,
                warnungen = result.warnungen.size,
                meldung = "GVP/Festpunkte importiert"
            )
        )

        result.punkte.size
    }


    suspend fun graImportSpeichern(dateiname: String, result: GraParseResult): Int = withContext(Dispatchers.IO) {
        // Gleiche Quelldatei ersetzt den alten GRA-Import.
        dao.graMitQuelleLoeschen(dateiname)

        if (result.punkte.isNotEmpty()) {
            dao.graPunkteEinfuegen(result.punkte)
        }

        dao.importStatusEinfuegen(
            TrassenImportStatusEntity(
                quelleDatei = dateiname,
                datenTyp = "GRA",
                status = TrassenAchseEntity.STATUS_IMPORTIERT,
                anzahlSamplepunkte = result.punkte.size,
                warnungen = result.warnungen.size,
                meldung = "GRA/kmLinie importiert"
            )
        )

        result.punkte.size
    }

    suspend fun korImportSpeichern(dateiname: String, result: KorParseResult): Int = withContext(Dispatchers.IO) {
        // Gleiche Quelldatei ersetzt den alten KOR-Import.
        dao.absteckpunkteMitQuelleLoeschen(dateiname)

        if (result.punkte.isNotEmpty()) {
            dao.absteckpunkteEinfuegen(result.punkte)
        }

        dao.importStatusEinfuegen(
            TrassenImportStatusEntity(
                quelleDatei = dateiname,
                datenTyp = "KOR",
                status = TrassenAchseEntity.STATUS_IMPORTIERT,
                anzahlAbsteckpunkte = result.punkte.size,
                warnungen = result.warnungen.size,
                meldung = "Absteckpunkte importiert"
            )
        )

        result.punkte.size
    }


    suspend fun csvAbsteckpunkteImportSpeichern(dateiname: String, result: AbsteckCsvParseResult): Int = withContext(Dispatchers.IO) {
        // Gleiche Quelldatei ersetzt den alten CSV-Absteckpunktimport.
        dao.absteckpunkteMitQuelleLoeschen(dateiname)

        if (result.punkte.isNotEmpty()) {
            dao.absteckpunkteEinfuegen(result.punkte)
        }

        dao.importStatusEinfuegen(
            TrassenImportStatusEntity(
                quelleDatei = dateiname,
                datenTyp = "CSV",
                status = TrassenAchseEntity.STATUS_IMPORTIERT,
                anzahlAbsteckpunkte = result.punkte.size,
                warnungen = result.warnungen.size,
                meldung = "Absteckpunkte aus CSV importiert"
            )
        )

        result.punkte.size
    }



    suspend fun graMitAchspunktenVerknuepfen(): GraVerknuepfungErgebnis = withContext(Dispatchers.IO) {
        val graPunkte = dao.alleGraPunkte()
        val achsen = dao.alleAchsen()
        val samplesNachAchse = dao.alleSamplepunkte().groupBy { it.achseId }

        if (graPunkte.isEmpty() || achsen.isEmpty() || samplesNachAchse.isEmpty()) {
            val ergebnis = GraVerknuepfungErgebnis(achsenVerknuepft = 0, samplepunkteVerknuepft = 0, graGruppenVerwendet = 0)
            dao.importStatusEinfuegen(
                TrassenImportStatusEntity(
                    quelleDatei = "GRA/kmLinien",
                    datenTyp = "KM",
                    status = TrassenAchseEntity.STATUS_FEHLER,
                    meldung = "Keine passenden GRA-/Achsdaten zum Verknuepfen gefunden"
                )
            )
            return@withContext ergebnis
        }

        val graGruppen = graPunkte
            .groupBy { gra ->
                GraSchluessel(
                    strecke = gra.strecke?.trim(),
                    gleis = gra.gleis?.trim(),
                    name = gra.name?.trim(),
                    istKmQuelle = gra.quelleDatei.istExpliziteKmGra()
                )
            }
            .mapValues { (_, liste) -> liste.sortedBy { it.punktIndex } }

        var achsenVerknuepft = 0
        var samplepunkteVerknuepft = 0
        val verwendeteGruppen = mutableSetOf<GraSchluessel>()

        achsen.forEach { achse ->
            val samples = samplesNachAchse[achse.id].orEmpty().sortedBy { it.station }
            if (samples.isEmpty()) return@forEach

            // Explizite KM-GRA und normale Hoehen-GRA werden bewusst getrennt.
            // Sonst koennten z. B. Harburgs ..._O00.GRA und ..._O00_km5300.GRA
            // in einer Gruppe landen und falsche Min/Max-Werte erzeugen.
            val kmGraEintrag = passendeGraGruppe(achse, graGruppen, nurKmQuelle = true)
            val hoehenGraEintrag = passendeGraGruppe(achse, graGruppen, nurKmQuelle = false)
            val kmGraListe = kmGraEintrag?.value.orEmpty()
            val hoehenGraListe = hoehenGraEintrag?.value.orEmpty()
            if (kmGraListe.size < 2 && hoehenGraListe.size < 2) return@forEach

            val stationVon = achse.stationVon ?: samples.first().station
            val stationBis = achse.stationBis ?: samples.last().station
            val stationSpanne = stationBis - stationVon
            if (stationSpanne <= 0.001) return@forEach

            val hatExpliziteKm = kmGraListe.size >= 2 &&
                kotlin.math.abs(kmGraListe.last().kmMeter - kmGraListe.first().kmMeter) > 0.001
            val graVon = kmGraListe.firstOrNull()?.kmMeter
            val graBis = kmGraListe.lastOrNull()?.kmMeter

            val aktualisierteSamples = samples.map { sample ->
                val hoehe = if (hoehenGraListe.size >= 2) {
                    hoeheInterpoliert(hoehenGraListe, sample.station)
                } else null

                if (hatExpliziteKm && graVon != null && graBis != null) {
                    val t = ((sample.station - stationVon) / stationSpanne).coerceIn(0.0, 1.0)
                    val kmWert = graVon + (graBis - graVon) * t
                    sample.copy(
                        kmMeter = kmWert,
                        kmQuelle = "GRA_KM",
                        hoeheM = hoehe ?: sample.hoeheM
                    )
                } else {
                    sample.copy(
                        // Bestehende KM aus GVP/KV/manueller Quelle nicht ueberschreiben.
                        hoeheM = hoehe ?: sample.hoeheM
                    )
                }
            }

            dao.samplePunkteErsetzen(aktualisierteSamples)
            achsenVerknuepft++
            samplepunkteVerknuepft += aktualisierteSamples.size
            kmGraEintrag?.let { verwendeteGruppen += it.key }
            hoehenGraEintrag?.let { verwendeteGruppen += it.key }
        }

        val gvpKalibrierung = kmMitGvpFestpunktenKalibrieren(achsen, samplesNachAchse)

        // Manuelle 2-Punkt-Kalibrierungen haben als bewusste Nutzerentscheidung Vorrang
        // und werden nach automatischer GRA/GVP-Verknuepfung erneut angewendet.
        val manuellAngewendet = manuelleKmKalibrierungenAnwenden()

        val ergebnis = GraVerknuepfungErgebnis(
            achsenVerknuepft = achsenVerknuepft + gvpKalibrierung.achsenKalibriert,
            samplepunkteVerknuepft = samplepunkteVerknuepft + gvpKalibrierung.samplepunkteKalibriert,
            graGruppenVerwendet = verwendeteGruppen.size,
            gvpAchsenKalibriert = gvpKalibrierung.achsenKalibriert,
            gvpSamplepunkteKalibriert = gvpKalibrierung.samplepunkteKalibriert
        )

        val meldung = buildString {
            append("GRA/kmLinien mit Achspunkten verknuepft: ")
            append(achsenVerknuepft)
            append(" Achse(n), ")
            append(samplepunkteVerknuepft)
            append(" Punkt(e)")
            if (gvpKalibrierung.samplepunkteKalibriert > 0) {
                append("; GVP-KM-Kalibrierung: ")
                append(gvpKalibrierung.achsenKalibriert)
                append(" Achse(n), ")
                append(gvpKalibrierung.samplepunkteKalibriert)
                append(" Punkt(e)")
            }
            if (manuellAngewendet > 0) {
                append("; manuell 2-Punkt: ")
                append(manuellAngewendet)
                append(" Achse(n)")
            }
        }

        dao.importStatusEinfuegen(
            TrassenImportStatusEntity(
                quelleDatei = "GRA/kmLinien + GVP",
                datenTyp = "KM",
                status = if (ergebnis.samplepunkteVerknuepft > 0) TrassenAchseEntity.STATUS_IMPORTIERT else TrassenAchseEntity.STATUS_FEHLER,
                anzahlSamplepunkte = ergebnis.samplepunkteVerknuepft,
                meldung = meldung
            )
        )

        ergebnis
    }


    private suspend fun kmMitGvpFestpunktenKalibrieren(
        achsen: List<TrassenAchseEntity>,
        samplesNachAchse: Map<Long, List<TrassenSamplePointEntity>>
    ): GvpKmKalibrierungErgebnis {
        val gvpNachStrecke = dao.alleGvpPunkte()
            .filter { it.kmMeter != null && !it.strecke.isNullOrBlank() }
            .groupBy { it.strecke!!.trim() }

        if (gvpNachStrecke.isEmpty()) return GvpKmKalibrierungErgebnis()

        var achsenKalibriert = 0
        var samplepunkteKalibriert = 0

        achsen.forEach { achse ->
            val strecke = achse.strecke?.trim().orEmpty()
            if (strecke.isBlank()) return@forEach
            if (achse.achsTyp != TrassenAchseEntity.ACHSTYP_GLEIS &&
                achse.achsTyp != TrassenAchseEntity.ACHSTYP_STRECKE
            ) return@forEach

            val gvpListe = gvpNachStrecke[strecke].orEmpty()
            if (gvpListe.size < 4) return@forEach

            val samples = samplesNachAchse[achse.id].orEmpty().sortedBy { it.station }
            if (samples.size < 4) return@forEach

            val offset = gvpKmOffsetRobust(samples, gvpListe) ?: return@forEach
            val aktualisierteSamples = samples.map { sample ->
                sample.copy(kmMeter = sample.station + offset, kmQuelle = "GVP")
            }

            dao.samplePunkteErsetzen(aktualisierteSamples)
            achsenKalibriert++
            samplepunkteKalibriert += aktualisierteSamples.size
        }

        return GvpKmKalibrierungErgebnis(
            achsenKalibriert = achsenKalibriert,
            samplepunkteKalibriert = samplepunkteKalibriert
        )
    }

    private fun gvpKmOffsetRobust(
        samples: List<TrassenSamplePointEntity>,
        gvpListe: List<GvpPointEntity>
    ): Double? {
        val offsets = gvpListe.mapNotNull { gvp ->
            val km = gvp.kmMeter ?: return@mapNotNull null
            val naechster = samples.minByOrNull { sample ->
                val dx = sample.xDbRef - gvp.xDbRef
                val dy = sample.yDbRef - gvp.yDbRef
                dx * dx + dy * dy
            } ?: return@mapNotNull null

            val dx = naechster.xDbRef - gvp.xDbRef
            val dy = naechster.yDbRef - gvp.yDbRef
            val abstand = kotlin.math.sqrt(dx * dx + dy * dy)

            // GVP-Punkte liegen als Festpunkte oft seitlich neben der Gleisachse.
            // Darum ist die Toleranz bewusst groesser als beim GPS-Live-Test.
            if (abstand > 120.0) return@mapNotNull null
            km - naechster.station
        }.sorted()

        if (offsets.size < 4) return null

        val median = median(offsets)
        val robusteOffsets = offsets.filter { kotlin.math.abs(it - median) <= 80.0 }
        if (robusteOffsets.size < 4) return null
        return median(robusteOffsets.sorted())
    }

    private fun median(sortierteWerte: List<Double>): Double {
        val n = sortierteWerte.size
        if (n == 0) return Double.NaN
        val mitte = n / 2
        return if (n % 2 == 1) {
            sortierteWerte[mitte]
        } else {
            (sortierteWerte[mitte - 1] + sortierteWerte[mitte]) / 2.0
        }
    }

    private data class GvpKmKalibrierungErgebnis(
        val achsenKalibriert: Int = 0,
        val samplepunkteKalibriert: Int = 0
    )

    private fun passendeGraGruppe(
        achse: TrassenAchseEntity,
        graGruppen: Map<GraSchluessel, List<GraKmPunktEntity>>,
        nurKmQuelle: Boolean? = null
    ): Map.Entry<GraSchluessel, List<GraKmPunktEntity>>? {
        val achsStrecke = achse.strecke?.trim()
        val achsGleis = achse.gleis?.trim()
        val achsName = achse.name.trim().lowercase(Locale.GERMANY)

        val kandidaten = graGruppen.entries.filter { (key, _) ->
            nurKmQuelle == null || key.istKmQuelle == nurKmQuelle
        }

        kandidaten.firstOrNull { (key, _) ->
            !achsStrecke.isNullOrBlank() &&
                textGleich(key.strecke, achsStrecke) &&
                textGleich(key.gleis, achsGleis)
        }?.let { return it }

        kandidaten.firstOrNull { (key, _) ->
            achsStrecke.isNullOrBlank() &&
                !achsGleis.isNullOrBlank() &&
                key.strecke.isNullOrBlank() &&
                textGleich(key.gleis, achsGleis)
        }?.let { return it }

        return kandidaten.firstOrNull { (key, _) ->
            val graName = key.name?.lowercase(Locale.GERMANY).orEmpty()
            graName.isNotBlank() && (achsName == graName || achsName.contains(graName) || graName.contains(achsName))
        }
    }

    private fun textGleich(a: String?, b: String?): Boolean =
        !a.isNullOrBlank() && !b.isNullOrBlank() && a.equals(b, ignoreCase = true)

    private fun hoeheInterpoliert(graListe: List<GraKmPunktEntity>, graWert: Double): Double? {
        val mitHoehe = graListe.filter { it.hoeheM != null }.sortedBy { it.kmMeter }
        if (mitHoehe.isEmpty()) return null
        if (graWert <= mitHoehe.first().kmMeter) return mitHoehe.first().hoeheM
        if (graWert >= mitHoehe.last().kmMeter) return mitHoehe.last().hoeheM

        val rechtsIndex = mitHoehe.indexOfFirst { it.kmMeter >= graWert }
        if (rechtsIndex <= 0) return mitHoehe.first().hoeheM
        val links = mitHoehe[rechtsIndex - 1]
        val rechts = mitHoehe[rechtsIndex]
        val linksHoehe = links.hoeheM ?: return rechts.hoeheM
        val rechtsHoehe = rechts.hoeheM ?: return links.hoeheM
        val spanne = rechts.kmMeter - links.kmMeter
        if (kotlin.math.abs(spanne) <= 0.001) return linksHoehe
        val t = ((graWert - links.kmMeter) / spanne).coerceIn(0.0, 1.0)
        return linksHoehe + (rechtsHoehe - linksHoehe) * t
    }

    private fun String.istExpliziteKmGra(): Boolean {
        val name = substringAfterLast('/').substringAfterLast('\\').uppercase(Locale.GERMANY)
        return Regex("(?:^|[_-])KM\\d{4}(?:[_\\.-]|$)").containsMatchIn(name) ||
            name.contains("KMLINIE") ||
            name.contains("KM_LINIE")
    }

    private data class GraSchluessel(
        val strecke: String?,
        val gleis: String?,
        val name: String?,
        val istKmQuelle: Boolean
    )



    suspend fun trassenNaheTestMitErstemAchspunkt(): TrassenNaheBerechner.Ergebnis? = withContext(Dispatchers.IO) {
        val achsen = dao.alleAchsen().map { achse ->
            TrassenNaheBerechner.AchsInfo(
                achseId = achse.id,
                name = achse.name,
                achsTyp = achse.achsTyp,
                strecke = achse.strecke,
                gleis = achse.gleis,
                richtung = achse.richtung
            )
        }
        val samplepunkte = dao.alleSamplepunkteMitGps().mapNotNull { punkt ->
            val lat = punkt.lat ?: return@mapNotNull null
            val lon = punkt.lon ?: return@mapNotNull null
            TrassenNaheBerechner.Punkt(
                id = punkt.id,
                achseId = punkt.achseId,
                station = punkt.station,
                kmMeter = punkt.kmMeter,
                lat = lat,
                lon = lon,
                hoeheM = punkt.hoeheM,
                richtungDeg = punkt.richtungDeg
            )
        }
        val testPunkt = samplepunkte.firstOrNull() ?: return@withContext null
        TrassenNaheBerechner.findeNaechsteAchse(
            lat = testPunkt.lat,
            lon = testPunkt.lon,
            achsen = achsen,
            punkte = samplepunkte,
            maxAbstandM = 250.0
        )
    }



    suspend fun findeNaechsteTrassenKandidaten(
        lat: Double,
        lon: Double,
        maxAbstandM: Double = 250.0,
        limit: Int = 6,
        gleisVorrang: Boolean = false
    ): List<TrassenNaheBerechner.Ergebnis> = withContext(Dispatchers.IO) {
        val achsen = dao.alleAchsen().map { achse ->
            TrassenNaheBerechner.AchsInfo(
                achseId = achse.id,
                name = achse.name,
                achsTyp = achse.achsTyp,
                strecke = achse.strecke,
                gleis = achse.gleis,
                richtung = achse.richtung
            )
        }
        val samplepunkte = dao.alleSamplepunkteMitGps().mapNotNull { punkt ->
            val pLat = punkt.lat ?: return@mapNotNull null
            val pLon = punkt.lon ?: return@mapNotNull null
            TrassenNaheBerechner.Punkt(
                id = punkt.id,
                achseId = punkt.achseId,
                station = punkt.station,
                kmMeter = punkt.kmMeter,
                lat = pLat,
                lon = pLon,
                hoeheM = punkt.hoeheM,
                richtungDeg = punkt.richtungDeg
            )
        }
        val kandidaten = TrassenNaheBerechner.findeNaechsteAchsen(
            lat = lat,
            lon = lon,
            achsen = achsen,
            punkte = samplepunkte,
            maxAbstandM = maxAbstandM,
            limit = limit,
            gleisVorrang = gleisVorrang
        )
        val mitGvp = kandidatenMitGvpKmVerbessern(kandidaten)
        kandidatenMitManuellenAbschnittenVerbessern(mitGvp)
    }

    suspend fun findeNaechsteTrasse(lat: Double, lon: Double, maxAbstandM: Double = 250.0): TrassenNaheBerechner.Ergebnis? = withContext(Dispatchers.IO) {
        val achsen = dao.alleAchsen().map { achse ->
            TrassenNaheBerechner.AchsInfo(
                achseId = achse.id,
                name = achse.name,
                achsTyp = achse.achsTyp,
                strecke = achse.strecke,
                gleis = achse.gleis,
                richtung = achse.richtung
            )
        }
        val samplepunkte = dao.alleSamplepunkteMitGps().mapNotNull { punkt ->
            val pLat = punkt.lat ?: return@mapNotNull null
            val pLon = punkt.lon ?: return@mapNotNull null
            TrassenNaheBerechner.Punkt(
                id = punkt.id,
                achseId = punkt.achseId,
                station = punkt.station,
                kmMeter = punkt.kmMeter,
                lat = pLat,
                lon = pLon,
                hoeheM = punkt.hoeheM,
                richtungDeg = punkt.richtungDeg
            )
        }
        val kandidat = TrassenNaheBerechner.findeNaechsteAchse(
            lat = lat,
            lon = lon,
            achsen = achsen,
            punkte = samplepunkte,
            maxAbstandM = maxAbstandM
        )
        kandidat?.let {
            val mitGvp = kandidatenMitGvpKmVerbessern(listOf(it))
            kandidatenMitManuellenAbschnittenVerbessern(mitGvp).firstOrNull()
        }
    }

    /**
     * Patch45 NEU: Legt betriebliche, manuell festgelegte Abschnitte ueber die unveraenderte
     * TRA-Rohachse. Ohne zwei gesetzte Grenzen gilt ein betrieblicher Abschnitt vorlaeufig
     * auf der gesamten fest gewaehlten Roh-TRA; mit zwei Grenzen nur innerhalb dieses Intervalls.
     */
    private suspend fun kandidatenMitManuellenAbschnittenVerbessern(
        kandidaten: List<TrassenNaheBerechner.Ergebnis>
    ): List<TrassenNaheBerechner.Ergebnis> {
        if (kandidaten.isEmpty()) return kandidaten
        val abschnitte = dao.alleManuellenAbschnitte()
            .filter { it.aktuelleAchseId != null }
            .sortedByDescending { it.aktualisiertAmEpochMs }
        if (abschnitte.isEmpty()) return kandidaten
        val kmPunkteNachAbschnitt = dao.alleAbschnittKmPunkte().groupBy { it.abschnittId }

        return kandidaten.map { kandidat ->
            val abschnitt = abschnitte.firstOrNull { a ->
                if (a.aktuelleAchseId != kandidat.achseId) return@firstOrNull false
                val grenze1 = a.stationStart
                val grenze2 = a.stationEnde
                if (grenze1 == null || grenze2 == null) {
                    // Offene Entwuerfe bleiben gespeichert, duerfen aber nicht die gesamte Roh-TRA
                    // als fachlich gueltigen, manuell kilometrierten Abschnitt ueberlagern.
                    false
                } else {
                    kandidat.station >= minOf(grenze1, grenze2) - 0.01 &&
                        kandidat.station <= maxOf(grenze1, grenze2) + 0.01
                }
            } ?: return@map kandidat

            val kmErgebnis = ManuelleAbschnittKmLogik.kmFuerStation(
                abschnitt = abschnitt,
                station = kandidat.station,
                punkte = kmPunkteNachAbschnitt[abschnitt.id].orEmpty()
            )
            kandidat.copy(
                achseName = abschnitt.bezeichnung,
                achsTyp = abschnitt.achsTyp,
                strecke = abschnitt.strecke,
                gleis = abschnitt.gleis,
                richtung = abschnitt.richtung,
                kmMeter = when {
                    kmErgebnis.kmMeter != null -> kmErgebnis.kmMeter
                    kmErgebnis.quelle == "MANUELL_ABSCHNITT_UNPLAUSIBEL" -> null
                    else -> kandidat.kmMeter
                },
                kmQuelle = when {
                    kmErgebnis.kmMeter != null -> kmErgebnis.quelle
                    kmErgebnis.quelle == "MANUELL_ABSCHNITT_UNPLAUSIBEL" -> kmErgebnis.quelle
                    else -> kandidat.kmQuelle
                }
            )
        }
    }

    private suspend fun kandidatenMitGvpKmVerbessern(
        kandidaten: List<TrassenNaheBerechner.Ergebnis>
    ): List<TrassenNaheBerechner.Ergebnis> {
        if (kandidaten.isEmpty()) return kandidaten

        val gvpAlle = dao.alleGvpPunkte()
            .filter { punkt ->
                punkt.kmMeter != null &&
                    !punkt.strecke.isNullOrBlank()
            }
        if (gvpAlle.isEmpty()) return kandidaten

        val samplepunkteNachAchse = dao.alleSamplepunkte().groupBy { it.achseId }

        return kandidaten.map { kandidat ->
            val diagnose = gvpKmDiagnose(kandidat, gvpAlle, samplepunkteNachAchse[kandidat.achseId].orEmpty())
            val gvpKm = diagnose?.kmMeter
            if (gvpKm != null) {
                kandidat.copy(
                    kmOriginalMeter = kandidat.kmMeter,
                    kmMeter = gvpKm,
                    kmQuelle = diagnose.quelle
                )
            } else {
                kandidat
            }
        }
    }

    /**
     * Patch 20: Lokale KM-Plausibilisierung ueber GVP/Festpunkte.
     *
     * Die GRA-Dateien koennen nicht immer direkt linear auf die TRA-Station gestreckt werden.
     * Deshalb wird fuer den Live-Test zusaetzlich ein lokaler km-Wert aus den GVP-Punkten
     * der gleichen Strecke berechnet. Das aendert noch keine Kamera-/BTB-Logik, verhindert
     * aber im Test offensichtliche km-Spruenge wie km 17,6 statt km 16,7.
     */
    private fun gvpKmDiagnose(
        kandidat: TrassenNaheBerechner.Ergebnis,
        gvpAlle: List<GvpPointEntity>,
        samplesDerAchse: List<TrassenSamplePointEntity>
    ): GvpKmDiagnose? {
        val strecke = kandidat.strecke?.trim().orEmpty()
        if (strecke.isBlank()) return null
        if (samplesDerAchse.size < 2) return null

        val gvp = gvpAlle.filter { it.strecke?.trim() == strecke }
        if (gvp.size < 4) return null

        val samples = samplesDerAchse.sortedBy { it.station }
        val anker = gvp.mapNotNull { punkt ->
            val km = punkt.kmMeter ?: return@mapNotNull null
            val naechsterSample = samples.minByOrNull { sample ->
                val dx = sample.xDbRef - punkt.xDbRef
                val dy = sample.yDbRef - punkt.yDbRef
                dx * dx + dy * dy
            } ?: return@mapNotNull null

            val dx = naechsterSample.xDbRef - punkt.xDbRef
            val dy = naechsterSample.yDbRef - punkt.yDbRef
            val abstand = kotlin.math.sqrt(dx * dx + dy * dy)

            // GVP/Festpunkte liegen oft seitlich neben der Achse. Bis 120 m wird fuer
            // die Diagnose akzeptiert, sortiert wird spaeter trotzdem nach Naehe.
            if (abstand > 120.0) return@mapNotNull null

            GvpKmAnker(
                station = naechsterSample.station,
                kmMeter = km,
                abstandZurAchseM = abstand,
                punktName = punkt.punktName
            )
        }

        if (anker.size < 4) return null

        val ankerGruppiert = anker
            .groupBy { kotlin.math.round(it.station / 2.0).toLong() }
            .values
            .mapNotNull { gruppe ->
                val bester = gruppe.minByOrNull { it.abstandZurAchseM } ?: return@mapNotNull null
                val punktNamen = gruppe
                    .sortedBy { it.abstandZurAchseM }
                    .map { it.punktName }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .take(3)
                    .joinToString("/")
                bester.copy(punktName = punktNamen.ifBlank { bester.punktName })
            }
            .sortedBy { it.station }

        if (ankerGruppiert.size < 2) return null

        val station = kandidat.station
        val links = ankerGruppiert.lastOrNull { it.station <= station }
        val rechts = ankerGruppiert.firstOrNull { it.station >= station }
        val naechster = ankerGruppiert.minByOrNull { kotlin.math.abs(it.station - station) } ?: return null

        val kmLokal = if (links != null && rechts != null && kotlin.math.abs(rechts.station - links.station) > 0.001) {
            val luecke = rechts.station - links.station
            if (luecke <= 450.0) {
                val t = ((station - links.station) / luecke).coerceIn(0.0, 1.0)
                links.kmMeter + (rechts.kmMeter - links.kmMeter) * t
            } else {
                naechster.kmMeter + (station - naechster.station)
            }
        } else {
            naechster.kmMeter + (station - naechster.station)
        }

        if (!kmLokal.isFinite() || kmLokal < 1_000.0) return null

        val quelle = buildString {
            append("GVP-Diagnose")
            append(" / naechste Festpunkte ")
            append(naechster.punktName)
            append(" (")
            append(String.format(Locale.GERMANY, "%.1f", kotlin.math.abs(naechster.station - station)))
            append(" m Stationsabstand; kein Mastpunkt)")
        }

        return GvpKmDiagnose(
            kmMeter = kmLokal,
            quelle = quelle
        )
    }

    private data class GvpKmAnker(
        val station: Double,
        val kmMeter: Double,
        val abstandZurAchseM: Double,
        val punktName: String
    )

    private data class GvpKmDiagnose(
        val kmMeter: Double,
        val quelle: String
    )



    suspend fun ladeKameraTrassenDaten(): KameraTrassenDaten = withContext(Dispatchers.IO) {
        val achsen = dao.alleAchsen().map { achse ->
            TrassenNaheBerechner.AchsInfo(
                achseId = achse.id,
                name = achse.name,
                achsTyp = achse.achsTyp,
                strecke = achse.strecke,
                gleis = achse.gleis,
                richtung = achse.richtung
            )
        }
        val samplepunkte = dao.alleSamplepunkteMitGps().mapNotNull { punkt ->
            val pLat = punkt.lat ?: return@mapNotNull null
            val pLon = punkt.lon ?: return@mapNotNull null
            TrassenNaheBerechner.Punkt(
                id = punkt.id,
                achseId = punkt.achseId,
                station = punkt.station,
                kmMeter = punkt.kmMeter.takeIf { punkt.kmQuelle != null },
                kmQuelle = punkt.kmQuelle,
                lat = pLat,
                lon = pLon,
                hoeheM = punkt.hoeheM,
                richtungDeg = punkt.richtungDeg
            )
        }
        KameraTrassenDaten(
            achsen = achsen,
            samplepunkte = samplepunkte,
            gvpPunkte = dao.alleGvpPunkte(),
            absteckpunkte = dao.alleAbsteckpunkte(),
            kmAnker = dao.alleKmAnker(),
            manuelleAbschnitte = dao.alleManuellenAbschnitte(),
            manuelleAbschnittKmPunkte = dao.alleAbschnittKmPunkte()
        )
    }

    suspend fun gpsKoordinatenNachberechnen(): KoordinatenNachberechnungErgebnis = withContext(Dispatchers.IO) {
        var sampleOk = 0
        var sampleFehler = 0
        dao.alleSamplepunkte().forEach { punkt ->
            val gps = DbRefKoordinatenwandler.dbRef2016Zone4ZuWgs84OrNull(punkt.xDbRef, punkt.yDbRef)
            if (gps != null) {
                sampleOk += dao.sampleGpsAktualisieren(punkt.id, gps.lat, gps.lon)
            } else {
                sampleFehler++
            }
        }

        var gvpOk = 0
        var gvpFehler = 0
        dao.alleGvpPunkte().forEach { punkt ->
            val gps = DbRefKoordinatenwandler.dbRef2016Zone4ZuWgs84OrNull(punkt.xDbRef, punkt.yDbRef)
            if (gps != null) {
                gvpOk += dao.gvpGpsAktualisieren(punkt.id, gps.lat, gps.lon)
            } else {
                gvpFehler++
            }
        }

        var korOk = 0
        var korFehler = 0
        dao.alleAbsteckpunkte().forEach { punkt ->
            val gps = DbRefKoordinatenwandler.dbRef2016Zone4ZuWgs84OrNull(punkt.xDbRef, punkt.yDbRef)
            if (gps != null) {
                korOk += dao.absteckpunktGpsAktualisieren(punkt.id, gps.lat, gps.lon)
            } else {
                korFehler++
            }
        }

        val fehler = sampleFehler + gvpFehler + korFehler
        dao.importStatusEinfuegen(
            TrassenImportStatusEntity(
                quelleDatei = "DB_REF2016_4",
                datenTyp = "GPS",
                status = TrassenAchseEntity.STATUS_IMPORTIERT,
                anzahlSamplepunkte = sampleOk,
                anzahlGvpPunkte = gvpOk,
                anzahlAbsteckpunkte = korOk,
                fehler = fehler,
                meldung = "DB_REF nach WGS84/GPS berechnet"
            )
        )

        KoordinatenNachberechnungErgebnis(
            samplepunkte = sampleOk,
            gvpPunkte = gvpOk,
            absteckpunkte = korOk,
            fehler = fehler
        )
    }

    /**
     * Reset fuer den sichtbaren Button "Vermessungsdaten loeschen".
     *
     * Seit Patch45ZZ ist dies bewusst derselbe vollstaendige DB-Reset wie
     * streckendatenKomplettZuruecksetzen(), damit nach einem frischen ZIP-Import
     * keine alten Achskorrekturen oder Zuordnungsgrundlagen wieder auftauchen.
     * SharedPreferences werden im aufrufenden UI-Kontext geloescht.
     */
    suspend fun allesLeeren(): Int = streckendatenKomplettZuruecksetzen().gesamt

    /**
     * Vollstaendiger Reset der importierten/abgeleiteten Strecken- und Vermessungsdaten.
     *
     * Bewusst NICHT enthalten sind die separat per GPS aufgenommenen Streckenpunkte
     * (StreckenpunktDao) sowie Baustellen/BTB/Fotos. Die Methode entfernt dagegen auch
     * dauerhafte Achskorrekturen, damit z. B. ein frueher manuell gesetztes "Gl 28"
     * nach einem frischen ZIP-Import nicht wieder auf eine Roh-TRA angewendet wird.
     */
    suspend fun streckendatenKomplettZuruecksetzen(): StreckendatenResetErgebnis = withContext(Dispatchers.IO) {
        val manuelleAbschnitte = dao.manuelleAbschnitteLeeren()
        val kmAnker = dao.kmAnkerLeeren()
        val achsKorrekturen = dao.achsKorrekturenLeeren()
        val graPunkte = dao.graLeeren()
        val absteckpunkte = dao.absteckpunkteLeeren()
        val gvpPunkte = dao.gvpLeeren()
        val achsen = dao.achsenLeeren()
        val importStatus = dao.importStatusLeeren()

        StreckendatenResetErgebnis(
            achsen = achsen,
            gvpPunkte = gvpPunkte,
            absteckpunkte = absteckpunkte,
            graPunkte = graPunkte,
            kmAnker = kmAnker,
            manuelleAbschnitte = manuelleAbschnitte,
            achsKorrekturen = achsKorrekturen,
            importStatus = importStatus
        )
    }
}

data class StreckendatenResetErgebnis(
    val achsen: Int,
    val gvpPunkte: Int,
    val absteckpunkte: Int,
    val graPunkte: Int,
    val kmAnker: Int,
    val manuelleAbschnitte: Int,
    val achsKorrekturen: Int,
    val importStatus: Int
) {
    val gesamt: Int
        get() = achsen + gvpPunkte + absteckpunkte + graPunkte + kmAnker +
            manuelleAbschnitte + achsKorrekturen + importStatus
}



data class KameraTrassenDaten(
    val achsen: List<TrassenNaheBerechner.AchsInfo>,
    val samplepunkte: List<TrassenNaheBerechner.Punkt>,
    val gvpPunkte: List<GvpPointEntity>,
    val absteckpunkte: List<AbsteckpunktEntity> = emptyList(),
    val kmAnker: List<TrassenKmAnkerEntity> = emptyList(),
    val manuelleAbschnitte: List<TrassenManuellerAbschnittEntity> = emptyList(),
    val manuelleAbschnittKmPunkte: List<TrassenAbschnittKmPunktEntity> = emptyList()
)

data class ManuelleKmKalibrierungErgebnis(
    val angewendet: Boolean,
    val status: String,
    val ankerAnzahl: Int,
    val stationAbstandM: Double? = null,
    val kmAbstandM: Double? = null,
    val steigend: Boolean? = null,
    val hinweis: String? = null
)


data class TrassenUebersicht(
    val anzahlAchsen: Int,
    val anzahlAchselemente: Int,
    val anzahlSamplepunkte: Int,
    val anzahlGvpPunkte: Int,
    val anzahlAbsteckpunkte: Int,
    val anzahlGraPunkte: Int,
    val anzahlSamplepunkteMitGps: Int,
    val anzahlSamplepunkteMitKm: Int,
    val anzahlGleisSamplepunkte: Int,
    val anzahlGleisSamplepunkteMitGps: Int,
    val anzahlGleisSamplepunkteMitKm: Int,
    val anzahlGvpPunkteMitGps: Int,
    val anzahlAbsteckpunkteMitGps: Int,
    val anzahlImportStatus: Int,
    val anzahlAchsKorrekturen: Int = 0,
    val anzahlKmAnker: Int = 0,
    val kmAutomatischAchsen: Int = 0,
    val kmManuellAchsen: Int = 0,
    val kmFehltAchsen: Int = 0,
    val kmUnplausibelAchsen: Int = 0,
    val letzteStatus: List<TrassenImportStatusEntity>,
    val achsen: List<TrassenAchseEntity> = emptyList(),
    val achsKorrekturen: List<TrassenAchseKorrekturEntity> = emptyList(),
    val gvpGruppen: List<GvpUebersichtRow> = emptyList(),
    val absteckpunktGruppen: List<AbsteckpunktUebersichtRow> = emptyList(),
    val graGruppen: List<GraUebersichtRow> = emptyList()
) {
    val istLeer: Boolean
        get() = anzahlAchsen == 0 &&
            anzahlAchselemente == 0 &&
            anzahlSamplepunkte == 0 &&
            anzahlGvpPunkte == 0 &&
            anzahlAbsteckpunkte == 0 &&
            anzahlGraPunkte == 0
}


data class GraVerknuepfungErgebnis(
    val achsenVerknuepft: Int,
    val samplepunkteVerknuepft: Int,
    val graGruppenVerwendet: Int,
    val gvpAchsenKalibriert: Int = 0,
    val gvpSamplepunkteKalibriert: Int = 0
)


data class KoordinatenNachberechnungErgebnis(
    val samplepunkte: Int,
    val gvpPunkte: Int,
    val absteckpunkte: Int,
    val fehler: Int
)
