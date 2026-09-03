@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)

package de.tbhelfer.ui.streckenpunkte

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.tbhelfer.ServiceLocator
import de.tbhelfer.datenbank.BaustelleEntity
import de.tbhelfer.datenbank.BaustellenBaumRepository
import de.tbhelfer.datenbank.BaustellenRepository
import de.tbhelfer.datenbank.StreckenpunktEntity
import de.tbhelfer.datenbank.istStandaloneStreckenpunkteBaustelle
import de.tbhelfer.datenbank.StreckenpunktTyp
import de.tbhelfer.datenbank.StreckenpunkteRepository
import de.tbhelfer.einstellungen.AktiveBaustelleValidator
import de.tbhelfer.einstellungen.AktuelleBaustelleSpeicher
import de.tbhelfer.vermessung.KmBerechner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

private data class AutomatikIntern(
    val aktiv: Boolean = false,
    val pausiert: Boolean = false,
    val startedAtEpochMs: Long = 0L,
    val sessionTitel: String? = null,
    val streckennummer: String? = null,
    val bahnhof: String? = null,
    val gleis: String? = null,
    val weiche: String? = null,
    val liveLat: Double? = null,
    val liveLon: Double? = null,
    val liveAltM: Double? = null,
    val liveAccuracyM: Float? = null,
    val lastUpdateEpochMs: Long? = null,
    val kmVorschlag: Double? = null,
    val kmSuffixVorschlag: Int? = null,
    val indikator: KmBerechner.Indikator? = null,
    val achsabstandM: Double? = null,
    val istWeichenmodus: Boolean = false,
    val headingDeg: Float? = null,
    val schritteSeitStart: Float? = null,
    val gpsFixAnzahl: Int = 0,
    val gruenAnzahl: Int = 0,
    val gelbAnzahl: Int = 0,
    val rotAnzahl: Int = 0,
    val pendingStartKm: Double? = null,
    val pendingStartNotiz: String? = null,
    val trackpunkte: List<AutomatikTrackpunkt> = emptyList(),
    val unsichereTrackpunkte: List<AutomatikTrackpunkt> = emptyList(),
    val anker: List<AutomatikAnker> = emptyList(),
    val gesamtDistanzM: Double = 0.0,
    val sensorSchritteAktiv: Boolean = false,
    val sensorKompassAktiv: Boolean = false,
    val fixKandidaten: List<AutomatikTrackpunkt> = emptyList()
)

private data class UiGrundlage(
    val aktuelleBaustelleId: Long?,
    val baustelle: BaustelleEntity?,
    val aktiverKontext: AktiverTrassenKontext?,
    val baustellenPunkte: List<StreckenpunktEntity>,
    val allePunkte: List<StreckenpunktEntity>,
    val suchtext: String,
    val listenFilter: ListenFilterUi
)

data class StreckenpunkteUiState(
    val aktuelleBaustelleId: Long?,
    val baustellenname: String?,
    val aktiverKontext: AktiverTrassenKontext?,
    val allePunkte: List<StreckenpunktEntity>,
    val kontextPunkte: List<StreckenpunktEntity>,
    val punkte: List<StreckenpunktEntity>,
    val listenItems: List<StreckenpunktListenEintrag>,
    val suchtext: String,
    val listenFilter: ListenFilterUi,
    val statistikText: String,
    val hinweisText: String?,
    val automatikAktiv: Boolean,
    val automatikPausiert: Boolean,
    val automatikTrackpunkte: Int,
    val automatikUnsichereTrackpunkte: Int,
    val automatikAnker: Int,
    val automatikDistanzText: String,
    val automatikStatusText: String,
    val automatikGpsFixAnzahl: Int,
    val automatikGruenAnzahl: Int,
    val automatikGelbAnzahl: Int,
    val automatikRotAnzahl: Int,
    val automatikLiveKm: Double?,
    val automatikLiveIndikator: KmBerechner.Indikator?,
    val automatikSessionTitel: String?,
    val automatikStreckennummer: String?,
    val automatikBahnhof: String?,
    val automatikGleis: String?,
    val automatikWeiche: String?
)

class StreckenpunkteViewModel(
    private val streckenRepo: StreckenpunkteRepository,
    private val baustellenRepo: BaustellenRepository,
    private val baumRepo: BaustellenBaumRepository,
    private val aktuelleSpeicher: AktuelleBaustelleSpeicher
) : ViewModel() {

    init {
        viewModelScope.launch {
            AktiveBaustelleValidator.bereinigeWennUngueltig(
                speicher = aktuelleSpeicher,
                baumRepo = baumRepo,
                baustellenRepo = baustellenRepo
            )
        }
    }

    private val aktuelleIdFlow = aktuelleSpeicher.aktuelleBaustelleId
    private val aktuellerKnotenFlow = aktuelleSpeicher.aktuellerKnotenId
    private val aktuelleHauptFlow = aktuelleSpeicher.aktuelleHauptbaustelleKnotenId
    private val suchtextFlow = MutableStateFlow("")
    private val listenFilterFlow = MutableStateFlow(ListenFilterUi.KONTEXT)
    private val automatikFlow = MutableStateFlow(AutomatikIntern())
    private val kmBerechner = KmBerechner()

    private val baustelleFlow = aktuelleIdFlow.flatMapLatest { id ->
        if (id == null) flowOf(null) else baustellenRepo.baustelleMitId(id)
    }

    private val punkteFlow = aktuelleIdFlow.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else streckenRepo.punkteFuerBaustelle(id)
    }

    private val allePunkteFlow = streckenRepo.allePunkte()

    private val aktiverKontextFlow: Flow<AktiverTrassenKontext?> =
        combine(aktuellerKnotenFlow, aktuelleHauptFlow, baustelleFlow) { aktuellerKnotenId, hauptKnotenId, baustelle ->
            Triple(aktuellerKnotenId, hauptKnotenId, baustelle)
        }.flatMapLatest { (aktuellerKnotenId, hauptKnotenId, baustelle) ->
            flow {
                emit(resolveAktiverKontext(aktuellerKnotenId ?: hauptKnotenId, baustelle))
            }
        }

    private val uiIdBaustelleFlow: Flow<Pair<Long?, BaustelleEntity?>> =
        combine(aktuelleIdFlow, baustelleFlow) { id: Long?, baustelle: BaustelleEntity? ->
            id to baustelle
        }

    private val uiSuchFilterFlow: Flow<Pair<String, ListenFilterUi>> =
        combine(suchtextFlow, listenFilterFlow) { suchtext: String, listenFilter: ListenFilterUi ->
            suchtext to listenFilter
        }

    private val uiGrundlageFlow: Flow<UiGrundlage> =
        combine(
            uiIdBaustelleFlow,
            aktiverKontextFlow,
            punkteFlow,
            allePunkteFlow,
            uiSuchFilterFlow
        ) { idBaustelle, aktiverKontext, baustellenPunkte, allePunkte, suchFilter ->
            UiGrundlage(
                aktuelleBaustelleId = idBaustelle.first,
                baustelle = idBaustelle.second,
                aktiverKontext = aktiverKontext,
                baustellenPunkte = baustellenPunkte,
                allePunkte = allePunkte,
                suchtext = suchFilter.first,
                listenFilter = suchFilter.second
            )
        }

    val uiState: StateFlow<StreckenpunkteUiState> =
        combine(uiGrundlageFlow, automatikFlow) { grundlage, automatik ->
            val id = grundlage.aktuelleBaustelleId
            val baustelle = grundlage.baustelle
            val aktiverKontext = grundlage.aktiverKontext
            val baustellenPunkte = grundlage.baustellenPunkte
            val allePunkte = grundlage.allePunkte
            val suchtext = grundlage.suchtext
            val listenFilter = grundlage.listenFilter
            val kontextPunkte = filterNachKontext(allePunkte, aktiverKontext)
            val gefiltert = filterPunkte(allePunkte, kontextPunkte, suchtext, listenFilter)
            val sichtbarePunkte = gefiltert.sortedByDescending { it.erstelltAmEpochMs }
            val listenItems = buildListenEintraege(sichtbarePunkte, kontextPunkte)

            val istStandalone = baustelle?.baustellenname?.let { istStandaloneStreckenpunkteBaustelle(it) } == true
            val hinweis = when {
                (id == null || istStandalone) && aktiverKontext == null -> "Keine aktuelle Baustelle gesetzt. Punkte können auch ohne Baustelle über Strecke oder Bahnhof erfasst werden."
                aktiverKontext == null -> "Aktuelle Baustelle hat noch keine geladene Strecke / keinen geladenen Bahnhof. Bitte in der Baustelle freie Strecke oder Bahnhof wählen."
                kontextPunkte.isEmpty() && suchtext.isBlank() && !automatik.aktiv -> "Für den geladenen Kontext sind noch keine Streckenpunkte vorhanden."
                gefiltert.isEmpty() && (suchtext.isNotBlank() || listenFilter != ListenFilterUi.KONTEXT) -> "Keine Treffer für Suche / Filter im geladenen Kontext gefunden."
                else -> null
            }

            val statistik = when {
                id == null || istStandalone -> if (aktiverKontext == null) "Keine aktive Baustelle · Standalone-Erfassung möglich" else "${kontextPunkte.size} Punkte im geladenen Kontext · Standalone"
                aktiverKontext == null -> "${baustellenPunkte.size} Streckenpunkte in der Baustelle · noch kein geladener Kontext"
                suchtext.isBlank() && listenFilter == ListenFilterUi.KONTEXT -> "${kontextPunkte.size} Punkte im geladenen Kontext · ${baustellenPunkte.size} gesamt in Baustelle"
                else -> "${sichtbarePunkte.size} von ${kontextPunkte.size} Treffern im geladenen Kontext sichtbar"
            }

            StreckenpunkteUiState(
                aktuelleBaustelleId = id,
                baustellenname = baustelle?.baustellenname?.takeUnless { istStandaloneStreckenpunkteBaustelle(it) },
                aktiverKontext = aktiverKontext,
                allePunkte = allePunkte,
                kontextPunkte = kontextPunkte,
                punkte = sichtbarePunkte,
                listenItems = listenItems,
                suchtext = suchtext,
                listenFilter = listenFilter,
                statistikText = statistik,
                hinweisText = hinweis,
                automatikAktiv = automatik.aktiv,
                automatikPausiert = automatik.pausiert,
                automatikTrackpunkte = automatik.trackpunkte.size,
                automatikUnsichereTrackpunkte = automatik.unsichereTrackpunkte.size,
                automatikAnker = automatik.anker.size,
                automatikDistanzText = formatDistanz(automatik.gesamtDistanzM),
                automatikStatusText = buildAutomatikStatus(automatik),
                automatikGpsFixAnzahl = automatik.gpsFixAnzahl,
                automatikGruenAnzahl = automatik.gruenAnzahl,
                automatikGelbAnzahl = automatik.gelbAnzahl,
                automatikRotAnzahl = automatik.rotAnzahl,
                automatikLiveKm = automatik.kmVorschlag,
                automatikLiveIndikator = automatik.indikator,
                automatikSessionTitel = automatik.sessionTitel,
                automatikStreckennummer = automatik.streckennummer,
                automatikBahnhof = automatik.bahnhof,
                automatikGleis = automatik.gleis,
                automatikWeiche = automatik.weiche
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            StreckenpunkteUiState(
                aktuelleBaustelleId = null,
                baustellenname = null,
                aktiverKontext = null,
                allePunkte = emptyList(),
                kontextPunkte = emptyList(),
                punkte = emptyList(),
                listenItems = emptyList(),
                suchtext = "",
                listenFilter = ListenFilterUi.KONTEXT,
                statistikText = "Lade...",
                hinweisText = "Lade...",
                automatikAktiv = false,
                automatikPausiert = false,
                automatikTrackpunkte = 0,
                automatikUnsichereTrackpunkte = 0,
                automatikAnker = 0,
                automatikDistanzText = "0 m",
                automatikStatusText = "Automatik: aus",
                automatikGpsFixAnzahl = 0,
                automatikGruenAnzahl = 0,
                automatikGelbAnzahl = 0,
                automatikRotAnzahl = 0,
                automatikLiveKm = null,
                automatikLiveIndikator = null,
                automatikSessionTitel = null,
                automatikStreckennummer = null,
                automatikBahnhof = null,
                automatikGleis = null,
                automatikWeiche = null
            )
        )

    fun sucheSetzen(text: String) {
        suchtextFlow.value = text.trim()
    }

    fun listenFilterSetzen(filter: ListenFilterUi) {
        listenFilterFlow.value = filter
    }

    fun neuenPunktAnlegen(
        kmText: String,
        bezeichnung: String,
        notiz: String?,
        kmSuffix: Int,
        typ: StreckenpunktTyp,
        typFreitext: String?,
        streckennummer: String?,
        bahnhof: String?,
        gleis: String?,
        weiche: String?,
        objektKennung: String?,
        gpsLat: Double?,
        gpsLon: Double?,
        gpsAltM: Double?,
        kontextOverride: AktiverTrassenKontext? = null
    ) {
        val kontext = kontextOverride ?: uiState.value.aktiverKontext

        val km = parseKmOptional(kmText) ?: return
        val name = bezeichnung.trim()
        if (name.isBlank()) return

        viewModelScope.launch {
            val id = resolveSpeicherBaustelleId(kontext) ?: return@launch
            streckenRepo.neuenPunkt(
                baustelleId = id,
                km = km,
                bezeichnung = name,
                notiz = notiz,
                gpsLat = gpsLat ?: 0.0,
                gpsLon = gpsLon ?: 0.0,
                gpsAltM = gpsAltM,
                kmSuffix = kmSuffix,
                typ = typ,
                typFreitext = typFreitext,
                streckennummer = streckennummer?.trim().takeUnless { it.isNullOrBlank() } ?: kontext?.streckennummer,
                bahnhof = bahnhof?.trim().takeUnless { it.isNullOrBlank() } ?: kontext?.bahnhof,
                gleis = gleis?.trim().takeUnless { it.isNullOrBlank() } ?: kontext?.gleis,
                weiche = weiche?.trim().takeUnless { it.isNullOrBlank() } ?: kontext?.weiche,
                objektKennung = objektKennung?.trim().takeUnless { it.isNullOrBlank() } ?: kontext?.objektKennung
            )
        }
    }

    fun punktAktualisieren(
        punkt: StreckenpunktEntity,
        kmText: String,
        bezeichnung: String,
        notiz: String?,
        kmSuffix: Int,
        typ: StreckenpunktTyp,
        typFreitext: String?,
        streckennummer: String?,
        bahnhof: String?,
        gleis: String?,
        weiche: String?,
        objektKennung: String?,
        gpsLat: Double?,
        gpsLon: Double?,
        gpsAltM: Double?
    ) {
        val km = parseKmOptional(kmText) ?: return
        val name = bezeichnung.trim()
        if (name.isBlank()) return

        viewModelScope.launch {
            streckenRepo.aktualisieren(
                punkt.copy(
                    km = km,
                    kmSuffix = kmSuffix,
                    typ = typ,
                    typFreitext = typFreitext?.trim().takeUnless { it.isNullOrBlank() },
                    streckennummer = streckennummer?.trim().takeUnless { it.isNullOrBlank() },
                    bahnhof = bahnhof?.trim().takeUnless { it.isNullOrBlank() },
                    gleis = gleis?.trim().takeUnless { it.isNullOrBlank() },
                    weiche = weiche?.trim().takeUnless { it.isNullOrBlank() },
                    objektKennung = objektKennung?.trim().takeUnless { it.isNullOrBlank() },
                    bezeichnung = name,
                    notiz = notiz?.trim().takeUnless { it.isNullOrBlank() },
                    gpsLat = gpsLat ?: 0.0,
                    gpsLon = gpsLon ?: 0.0,
                    gpsAltM = gpsAltM
                )
            )
        }
    }

    fun loeschen(punkt: StreckenpunktEntity) {
        viewModelScope.launch {
            streckenRepo.loeschen(punkt)
        }
    }

    fun automatikStarten(
        streckennummer: String?,
        bahnhof: String?,
        gleis: String?,
        weiche: String?,
        kmStartText: String,
        startNotiz: String?,
        kontextOverride: AktiverTrassenKontext? = null
    ) {
        val kontext = kontextOverride ?: uiState.value.aktiverKontext
        val kmStart = kmStartText.trim().replace(',', '.').toDoubleOrNull() ?: return
        automatikFlow.value = AutomatikIntern(
            aktiv = true,
            pausiert = false,
            startedAtEpochMs = System.currentTimeMillis(),
            sessionTitel = null,
            streckennummer = streckennummer?.trim().takeUnless { it.isNullOrBlank() } ?: kontext?.streckennummer,
            bahnhof = bahnhof?.trim().takeUnless { it.isNullOrBlank() } ?: kontext?.bahnhof,
            gleis = gleis?.trim().takeUnless { it.isNullOrBlank() } ?: kontext?.gleis,
            weiche = weiche?.trim().takeUnless { it.isNullOrBlank() } ?: kontext?.weiche,
            pendingStartKm = kmStart,
            pendingStartNotiz = startNotiz?.trim().takeUnless { it.isNullOrBlank() }
        )
    }

    fun automatikSensorStatusSetzen(schritteAktiv: Boolean? = null, kompassAktiv: Boolean? = null) {
        val current = automatikFlow.value
        if (!current.aktiv) return
        automatikFlow.value = current.copy(
            sensorSchritteAktiv = schritteAktiv ?: current.sensorSchritteAktiv,
            sensorKompassAktiv = kompassAktiv ?: current.sensorKompassAktiv
        )
    }

    fun automatikSchritteAktualisieren(schritteSeitStart: Float) {
        val current = automatikFlow.value
        if (!current.aktiv) return
        automatikFlow.value = current.copy(schritteSeitStart = schritteSeitStart)
    }

    fun automatikHeadingAktualisieren(headingDeg: Float?) {
        val current = automatikFlow.value
        if (!current.aktiv) return
        automatikFlow.value = current.copy(headingDeg = headingDeg)
    }

    fun liveStandortAktualisieren(
        lat: Double,
        lon: Double,
        altM: Double?,
        accuracyM: Float?,
        epochMs: Long = System.currentTimeMillis()
    ) {
        val current = automatikFlow.value
        if (!current.aktiv) return

        val referenzKontext = kontextAusAutomatik(current) ?: uiState.value.aktiverKontext
        val referenzpunkte = filterNachKontext(uiState.value.allePunkte, referenzKontext)
            .filter(::hatKm)
            .filter { it.gpsLat != 0.0 || it.gpsLon != 0.0 }
            .sortedWith(compareBy<StreckenpunktEntity> { it.km }.thenBy { it.kmSuffix }.thenBy { it.id })

        val berechnung = if (referenzpunkte.size >= 2) {
            runCatching { kmBerechner.berechne(referenzpunkte, lat, lon) }.getOrNull()
        } else null

        val qualitaet = bewerteAutomatikQualitaet(accuracyM, berechnung?.achsabstandM)
        val fortschritt = berechneAchsenFortschritt(current.trackpunkte, lat, lon)
        val seitenabstand = berechneSeitenAbstand(current.trackpunkte, lat, lon)
        val schrittDistanzSeitLetztemGruen = berechneSchrittDistanzSeitLetztemGruen(current)
        val richtungsKonflikt = hatRichtungsKonflikt(current.trackpunkte, current.headingDeg)

        val effektiveQualitaet = when {
            richtungsKonflikt && qualitaet == KmBerechner.Indikator.GRUEN -> KmBerechner.Indikator.GELB
            else -> qualitaet
        }

        val neuerTrackpunkt = AutomatikTrackpunkt(
            lat = lat,
            lon = lon,
            altM = altM,
            accuracyM = accuracyM,
            epochMs = epochMs,
            kmVorschlag = berechnung?.km,
            kmSuffixVorschlag = berechnung?.kmSuffix,
            indikator = effektiveQualitaet,
            achsabstandM = berechnung?.achsabstandM,
            istWeichenmodus = berechnung?.istWeichenmodus == true,
            headingDeg = current.headingDeg,
            schritteSeitStart = current.schritteSeitStart,
            istUnsicher = effektiveQualitaet != KmBerechner.Indikator.GRUEN,
            fortschrittM = fortschritt,
            seitenabstandM = seitenabstand
        )

        val neueGruenAnzahl = current.gruenAnzahl + if (effektiveQualitaet == KmBerechner.Indikator.GRUEN) 1 else 0
        val neueGelbAnzahl = current.gelbAnzahl + if (effektiveQualitaet == KmBerechner.Indikator.GELB) 1 else 0
        val neueRotAnzahl = current.rotAnzahl + if (effektiveQualitaet == KmBerechner.Indikator.ROT) 1 else 0
        val neueGpsFixAnzahl = current.gpsFixAnzahl + 1
        val fixKandidaten = (current.fixKandidaten + neuerTrackpunkt)
            .filter { kandidat -> epochMs - kandidat.epochMs <= 2_500L }
            .takeLast(8)
        val besterKandidat = waehleBestenAutomatikFix(fixKandidaten)
        val fortschrittSpeicher = fortschrittFuerKandidat(current.trackpunkte, besterKandidat)
        val lateralOkSpeicher = istLateralerKandidatOk(current.trackpunkte, besterKandidat)
        val letzterGruenerSpeicher = current.trackpunkte.lastOrNull()
        val luftlinieSpeicher = if (letzterGruenerSpeicher != null && besterKandidat != null) {
            distanzMeter(letzterGruenerSpeicher.lat, letzterGruenerSpeicher.lon, besterKandidat.lat, besterKandidat.lon)
        } else 0.0
        val schrittFortschrittOk = schrittDistanzSeitLetztemGruen != null && schrittDistanzSeitLetztemGruen >= 2.0
        val hatGenugFortschrittSpeicher = letzterGruenerSpeicher == null || (fortschrittSpeicher ?: luftlinieSpeicher) >= 2.0 || schrittFortschrittOk
        val sollAlsGruenSpeichern = !current.pausiert &&
            besterKandidat?.indikator == KmBerechner.Indikator.GRUEN &&
            hatGenugFortschrittSpeicher &&
            lateralOkSpeicher
        val trackpunktZumSpeichern = if (sollAlsGruenSpeichern) besterKandidat?.copy(istUnsicher = false) else null

        var updated = current.copy(
            liveLat = lat,
            liveLon = lon,
            liveAltM = altM,
            liveAccuracyM = accuracyM,
            lastUpdateEpochMs = epochMs,
            kmVorschlag = berechnung?.km,
            kmSuffixVorschlag = berechnung?.kmSuffix,
            indikator = effektiveQualitaet,
            achsabstandM = berechnung?.achsabstandM,
            istWeichenmodus = berechnung?.istWeichenmodus == true,
            gpsFixAnzahl = neueGpsFixAnzahl,
            gruenAnzahl = neueGruenAnzahl,
            gelbAnzahl = neueGelbAnzahl,
            rotAnzahl = neueRotAnzahl,
            fixKandidaten = if (trackpunktZumSpeichern != null) {
                fixKandidaten.filterNot { kandidat -> kandidat.epochMs == trackpunktZumSpeichern.epochMs }
            } else {
                fixKandidaten
            },
            trackpunkte = if (trackpunktZumSpeichern != null) current.trackpunkte + trackpunktZumSpeichern else current.trackpunkte,
            unsichereTrackpunkte = if (!current.pausiert && effektiveQualitaet != KmBerechner.Indikator.GRUEN) current.unsichereTrackpunkte + neuerTrackpunkt.copy(istUnsicher = true) else current.unsichereTrackpunkte,
            gesamtDistanzM = if (trackpunktZumSpeichern != null) current.gesamtDistanzM + (fortschrittSpeicher ?: luftlinieSpeicher) else current.gesamtDistanzM
        )

        if (updated.pendingStartKm != null) {
            val name = "KM-Start"
            val anker = AutomatikAnker(
                km = updated.pendingStartKm,
                kmSuffix = 0,
                bezeichnung = name,
                notiz = updated.pendingStartNotiz,
                lat = lat,
                lon = lon,
                altM = altM,
                epochMs = epochMs
            )
            updated = updated.copy(
                anker = updated.anker + anker,
                pendingStartKm = null,
                pendingStartNotiz = null
            )
            val speicherKontext = kontextAusAutomatik(updated)
            viewModelScope.launch {
                val baustelleId = resolveSpeicherBaustelleId(speicherKontext) ?: return@launch
                streckenRepo.neuenPunkt(
                    baustelleId = baustelleId,
                    km = anker.km,
                    bezeichnung = name,
                    notiz = anker.notiz,
                    gpsLat = lat,
                    gpsLon = lon,
                    gpsAltM = altM,
                    kmSuffix = 0,
                    typ = StreckenpunktTyp.EXAKT,
                    typFreitext = "Anker",
                    streckennummer = updated.streckennummer,
                    bahnhof = updated.bahnhof,
                    gleis = updated.gleis,
                    weiche = updated.weiche,
                    objektKennung = updated.weiche ?: updated.gleis ?: updated.streckennummer
                )
            }
        }

        automatikFlow.value = updated
    }

    fun automatikAnkerSpeichern(
        kmText: String,
        kmSuffix: Int,
        bezeichnung: String?,
        notiz: String?,
        gpsLatOverride: Double? = null,
        gpsLonOverride: Double? = null,
        gpsAltOverrideM: Double? = null,
        gpsEpochMsOverride: Long? = null
    ): Boolean {
        val current = automatikFlow.value
        if (!current.aktiv) return false
        val lat = gpsLatOverride ?: current.liveLat ?: return false
        val lon = gpsLonOverride ?: current.liveLon ?: return false
        val km = kmText.trim().replace(',', '.').toDoubleOrNull() ?: return false
        val name = bezeichnung?.trim().takeUnless { it.isNullOrBlank() }
            ?: "KM-Anker ${current.anker.size + 1}"
        val anker = AutomatikAnker(
            km = km,
            kmSuffix = kmSuffix,
            bezeichnung = name,
            notiz = notiz?.trim().takeUnless { it.isNullOrBlank() },
            lat = lat,
            lon = lon,
            altM = gpsAltOverrideM ?: current.liveAltM,
            epochMs = gpsEpochMsOverride ?: current.lastUpdateEpochMs ?: System.currentTimeMillis()
        )
        automatikFlow.value = current.copy(anker = current.anker + anker)

        val speicherKontext = kontextAusAutomatik(current)

        viewModelScope.launch {
            val baustelleId = resolveSpeicherBaustelleId(speicherKontext) ?: return@launch
            streckenRepo.neuenPunkt(
                baustelleId = baustelleId,
                km = km,
                bezeichnung = name,
                notiz = anker.notiz,
                gpsLat = lat,
                gpsLon = lon,
                gpsAltM = anker.altM,
                kmSuffix = kmSuffix,
                typ = StreckenpunktTyp.EXAKT,
                typFreitext = "Anker",
                streckennummer = current.streckennummer,
                bahnhof = current.bahnhof,
                gleis = current.gleis,
                weiche = current.weiche,
                objektKennung = current.weiche ?: current.gleis ?: current.streckennummer
            )
        }
        return true
    }

    fun automatikStoppen(): AutomatikSessionSnapshot? {
        val current = automatikFlow.value
        if (!current.aktiv) return null
        val snapshot = AutomatikSessionSnapshot(
            startedAtEpochMs = current.startedAtEpochMs,
            stoppedAtEpochMs = System.currentTimeMillis(),
            streckennummer = current.streckennummer,
            bahnhof = current.bahnhof,
            gleis = current.gleis,
            weiche = current.weiche,
            sessionTitel = current.sessionTitel,
            trackpunkte = current.trackpunkte,
            unsichereTrackpunkte = current.unsichereTrackpunkte,
            anker = current.anker,
            gesamtDistanzM = current.gesamtDistanzM,
            gpsFixAnzahl = current.gpsFixAnzahl,
            gruenAnzahl = current.gruenAnzahl,
            gelbAnzahl = current.gelbAnzahl,
            rotAnzahl = current.rotAnzahl
        )
        automatikFlow.value = AutomatikIntern()
        return snapshot
    }

    fun automatikPausieren() {
        val current = automatikFlow.value
        if (!current.aktiv) return
        automatikFlow.value = current.copy(pausiert = true)
    }

    fun automatikFortsetzen() {
        val current = automatikFlow.value
        if (!current.aktiv) return
        automatikFlow.value = current.copy(pausiert = false)
    }

    fun automatikAbbrechen() {
        automatikFlow.value = AutomatikIntern()
    }


    suspend fun standaloneKontextAktivieren(kontext: AktiverTrassenKontext): Long {
        val id = baustellenRepo.standaloneStreckenpunkteBaustelleHolenOderAktualisieren(
            streckennummer = kontext.streckennummer,
            bahnhof = kontext.bahnhof,
            gleis = kontext.gleis,
            weiche = kontext.weiche
        )
        aktuelleSpeicher.setzenStandalone(id)
        return id
    }


    suspend fun kontextSetzen(kontext: AktiverTrassenKontext): Long {
        val id = uiState.value.aktuelleBaustelleId
        val hatEchteBaustelle = !uiState.value.baustellenname.isNullOrBlank()
        if (id == null || !hatEchteBaustelle) {
            return standaloneKontextAktivieren(kontext)
        }
        baustellenRepo.trassenKontextAktualisieren(
            id = id,
            streckennummer = kontext.streckennummer,
            bahnhof = kontext.bahnhof,
            gleis = kontext.gleis,
            weiche = kontext.weiche
        )
        aktuelleSpeicher.setzenOhneKnoten(id)
        return id
    }

    fun kontextNeuWaehlen() {
        viewModelScope.launch {
            val id = uiState.value.aktuelleBaustelleId
            val hatEchteBaustelle = !uiState.value.baustellenname.isNullOrBlank()
            if (id == null || !hatEchteBaustelle) {
                aktuelleSpeicher.loeschen()
            } else {
                baustellenRepo.trassenKontextAktualisieren(
                    id = id,
                    streckennummer = null,
                    bahnhof = null,
                    gleis = null,
                    weiche = null
                )
                aktuelleSpeicher.setzenOhneKnoten(id)
            }
        }
    }



    private suspend fun resolveSpeicherBaustelleId(kontext: AktiverTrassenKontext?): Long? {
        val id = uiState.value.aktuelleBaustelleId
        if (id != null) return id
        if (kontext == null) return null
        return baustellenRepo.standaloneStreckenpunkteBaustelleHolenOderAktualisieren(
            streckennummer = kontext.streckennummer,
            bahnhof = kontext.bahnhof,
            gleis = kontext.gleis,
            weiche = kontext.weiche
        )
    }

    private fun kontextAusAutomatik(automatik: AutomatikIntern): AktiverTrassenKontext? {
        return when {
            !automatik.streckennummer.isNullOrBlank() -> AktiverTrassenKontext(
                art = TrassenKontextArt.STRECKE,
                streckennummer = automatik.streckennummer,
                labelKurz = automatik.streckennummer,
                labelLang = "Freie Strecke ${automatik.streckennummer}",
                objektKennung = automatik.streckennummer
            )
            !automatik.bahnhof.isNullOrBlank() && !automatik.weiche.isNullOrBlank() -> AktiverTrassenKontext(
                art = TrassenKontextArt.BAHNHOF_WEICHE,
                bahnhof = automatik.bahnhof,
                weiche = automatik.weiche,
                labelKurz = "${automatik.bahnhof} · ${automatik.weiche}",
                labelLang = "Bahnhof ${automatik.bahnhof} · Weiche ${automatik.weiche}",
                objektKennung = automatik.weiche
            )
            !automatik.bahnhof.isNullOrBlank() && !automatik.gleis.isNullOrBlank() -> AktiverTrassenKontext(
                art = TrassenKontextArt.BAHNHOF_GLEIS,
                bahnhof = automatik.bahnhof,
                gleis = automatik.gleis,
                labelKurz = "${automatik.bahnhof} · ${automatik.gleis}",
                labelLang = "Bahnhof ${automatik.bahnhof} · Gleis ${automatik.gleis}",
                objektKennung = automatik.gleis
            )
            else -> null
        }
    }

    private suspend fun resolveAktiverKontext(startId: Long?, baustelle: BaustelleEntity?): AktiverTrassenKontext? {
        if (startId != null) {
            val pfad = runCatching { baumRepo.pfadVonRootBis(startId) }.getOrDefault(emptyList())
            AktiverTrassenKontextResolver.ausPfad(pfad, baustelle)?.let { return it }
        }
        return AktiverTrassenKontextResolver.ausLegacy(baustelle)
    }

    private fun filterNachKontext(
        punkte: List<StreckenpunktEntity>,
        kontext: AktiverTrassenKontext?
    ): List<StreckenpunktEntity> = TrassenKontextFilter.filterPunkte(punkte, kontext)

    private fun filterPunkte(
        allePunkte: List<StreckenpunktEntity>,
        kontextPunkte: List<StreckenpunktEntity>,
        suchtext: String,
        listenFilter: ListenFilterUi
    ): List<StreckenpunktEntity> {
        val basis = when (listenFilter) {
            ListenFilterUi.KONTEXT -> kontextPunkte
            ListenFilterUi.ALLES -> allePunkte
            ListenFilterUi.STRECKE -> allePunkte.filter { !it.streckennummer.isNullOrBlank() }
            ListenFilterUi.BAHNHOF -> allePunkte.filter { !it.bahnhof.isNullOrBlank() }
        }
        val query = suchtext.trim().lowercase(Locale.GERMANY)
        if (query.isBlank()) return basis
        return basis.filter { punkt ->
            val suchfelder = buildList<String> {
                listOfNotNull(
                    punkt.bezeichnung,
                    punkt.notiz,
                    punkt.objektKennung,
                    punkt.streckennummer,
                    punkt.bahnhof,
                    punkt.gleis,
                    punkt.weiche,
                    punkt.typ.name,
                    punkt.typFreitext,
                    formatKmSearch(punkt),
                    kontextLabelFuerPunkt(punkt)
                ).forEach { add(it) }
                de.tbhelfer.util.KontextNormalisierung.gleisSuchbegriffe(punkt.gleis).forEach { add(it) }
            }
            suchfelder.any { it.lowercase(Locale.GERMANY).contains(query) }
        }
    }

    private fun formatKmSearch(punkt: StreckenpunktEntity): String {
        if (!hatKm(punkt)) return "km offen"
        val kmText = String.format(Locale.GERMANY, "%.3f", punkt.km)
        return if (punkt.kmSuffix > 0) "$kmText-${punkt.kmSuffix}" else kmText
    }

    private fun parseKmOptional(kmText: String): Double? {
        val normalized = kmText.trim().replace(',', '.')
        if (normalized.isBlank()) return KM_OFFEN_WERT
        return normalized.toDoubleOrNull()
    }

    private fun buildListenEintraege(
        sichtbarePunkte: List<StreckenpunktEntity>,
        kontextPunkte: List<StreckenpunktEntity>
    ): List<StreckenpunktListenEintrag> {
        if (sichtbarePunkte.isEmpty()) return emptyList()
        val result = mutableListOf<StreckenpunktListenEintrag>()
        sichtbarePunkte
            .groupBy { kontextLabelFuerPunkt(it) }
            .toSortedMap(String.CASE_INSENSITIVE_ORDER)
            .forEach { (kontextLabel, punkte) ->
                result += StreckenpunktListenEintrag.Header(
                    key = kontextLabel,
                    text = kontextLabel
                )
                punkte
                    .sortedByDescending { it.erstelltAmEpochMs }
                    .forEach { punkt ->
                        val kmText = if (hatKm(punkt)) formatKmText(punkt.km, punkt.kmSuffix) else "km offen"
                        val kmHinweisText = if (!hatKm(punkt)) {
                            berechneKmVorschlagFuerListe(punkt, kontextPunkte)?.let { (km, suffix) ->
                                "Vorschlag: ${formatKmText(km, suffix)}"
                            }
                        } else null
                        result += StreckenpunktListenEintrag.Punkt(
                            StreckenpunktListenItem(
                                punkt = punkt,
                                kmText = kmText,
                                kmHinweisText = kmHinweisText,
                                typText = formatTypAnzeige(punkt.typ, punkt.typFreitext),
                                kontextText = kontextLabel
                            )
                        )
                    }
            }
        return result
    }

    private fun berechneKmVorschlagFuerListe(
        punkt: StreckenpunktEntity,
        kontextPunkte: List<StreckenpunktEntity>
    ): Pair<Double, Int>? {
        if (punkt.gpsLat == 0.0 && punkt.gpsLon == 0.0) return null
        val referenzpunkte = kontextPunkte
            .filter { it.id != punkt.id }
            .filter(::hatKm)
            .filter { it.gpsLat != 0.0 || it.gpsLon != 0.0 }
        if (referenzpunkte.size < 2) return null
        val erg = runCatching { kmBerechner.berechne(referenzpunkte, punkt.gpsLat, punkt.gpsLon) }.getOrNull() ?: return null
        val km = erg.km ?: return null
        return km to (erg.kmSuffix ?: 0)
    }

    private fun bewerteAutomatikQualitaet(accuracyM: Float?, achsabstandM: Double?): KmBerechner.Indikator {
        val accuracyBewertung = when {
            accuracyM == null -> null
            accuracyM <= 2f -> KmBerechner.Indikator.GRUEN
            accuracyM <= 3f -> KmBerechner.Indikator.GELB
            else -> KmBerechner.Indikator.ROT
        }
        val achsBewertung = when {
            achsabstandM == null -> null
            achsabstandM <= 2.0 -> KmBerechner.Indikator.GRUEN
            achsabstandM <= 3.0 -> KmBerechner.Indikator.GELB
            else -> KmBerechner.Indikator.ROT
        }
        return listOfNotNull(accuracyBewertung, achsBewertung).maxByOrNull { indikatorWert(it) } ?: KmBerechner.Indikator.ROT
    }

    private fun indikatorWert(indikator: KmBerechner.Indikator): Int = when (indikator) {
        KmBerechner.Indikator.GRUEN -> 0
        KmBerechner.Indikator.GELB -> 1
        KmBerechner.Indikator.ROT -> 2
    }

    private fun berechneSchrittDistanzSeitLetztemGruen(current: AutomatikIntern): Double? {
        val schritte = current.schritteSeitStart ?: return null
        val letzte = current.trackpunkte.lastOrNull()?.schritteSeitStart ?: return null
        val delta = (schritte - letzte).coerceAtLeast(0f)
        return delta * 0.75
    }

    private fun waehleBestenAutomatikFix(kandidaten: List<AutomatikTrackpunkt>): AutomatikTrackpunkt? {
        return kandidaten.maxByOrNull { kandidat ->
            val qualitaetScore = when (kandidat.indikator) {
                KmBerechner.Indikator.GRUEN -> 1_000.0
                KmBerechner.Indikator.GELB -> 250.0
                KmBerechner.Indikator.ROT, null -> 0.0
            }
            val accuracyScore = kandidat.accuracyM?.toDouble()?.let { (12.0 - it.coerceAtMost(12.0)).coerceAtLeast(0.0) * 18.0 } ?: 0.0
            val achsScore = kandidat.achsabstandM?.let { (6.0 - it.coerceAtMost(6.0)).coerceAtLeast(0.0) * 12.0 } ?: 0.0
            val seitenPenalty = kandidat.seitenabstandM?.takeIf { it > 2.0 }?.let { (it - 2.0) * 40.0 } ?: 0.0
            val alterSekunden = ((System.currentTimeMillis() - kandidat.epochMs).coerceAtLeast(0L) / 1000.0)
            val recencyScore = (4.0 - alterSekunden).coerceAtLeast(0.0) * 10.0
            qualitaetScore + accuracyScore + achsScore + recencyScore - seitenPenalty
        }
    }

    private fun fortschrittFuerKandidat(
        trackpunkte: List<AutomatikTrackpunkt>,
        kandidat: AutomatikTrackpunkt?
    ): Double? {
        val punkt = kandidat ?: return null
        return punkt.fortschrittM ?: trackpunkte.lastOrNull()?.let { letzte ->
            distanzMeter(letzte.lat, letzte.lon, punkt.lat, punkt.lon)
        }
    }

    private fun istLateralerKandidatOk(
        trackpunkte: List<AutomatikTrackpunkt>,
        kandidat: AutomatikTrackpunkt?
    ): Boolean {
        val punkt = kandidat ?: return false
        return punkt.seitenabstandM == null || punkt.seitenabstandM <= 2.0 || trackpunkte.isEmpty()
    }

    private fun berechneAchsenFortschritt(trackpunkte: List<AutomatikTrackpunkt>, lat: Double, lon: Double): Double? {
        val letzte = trackpunkte.lastOrNull() ?: return null
        val richtung = berechneRichtungsVektor(trackpunkte) ?: return distanzMeter(letzte.lat, letzte.lon, lat, lon)
        val meterX = meterLon(lat, lon - letzte.lon)
        val meterY = meterLat(lat - letzte.lat)
        val laenge = sqrt(richtung.first * richtung.first + richtung.second * richtung.second)
        if (laenge <= 0.0001) return distanzMeter(letzte.lat, letzte.lon, lat, lon)
        val nx = richtung.first / laenge
        val ny = richtung.second / laenge
        return max(0.0, meterX * nx + meterY * ny)
    }

    private fun berechneSeitenAbstand(trackpunkte: List<AutomatikTrackpunkt>, lat: Double, lon: Double): Double? {
        val letzte = trackpunkte.lastOrNull() ?: return null
        val richtung = berechneRichtungsVektor(trackpunkte) ?: return null
        val meterX = meterLon(lat, lon - letzte.lon)
        val meterY = meterLat(lat - letzte.lat)
        val laenge = sqrt(richtung.first * richtung.first + richtung.second * richtung.second)
        if (laenge <= 0.0001) return null
        val nx = richtung.first / laenge
        val ny = richtung.second / laenge
        return abs(meterX * ny - meterY * nx)
    }

    private fun berechneRichtungsVektor(trackpunkte: List<AutomatikTrackpunkt>): Pair<Double, Double>? {
        if (trackpunkte.size < 2) return null
        val fenster = when {
            trackpunkte.size >= 7 -> 7
            trackpunkte.size >= 5 -> 5
            else -> minOf(3, trackpunkte.size)
        }
        val basis = trackpunkte.takeLast(fenster)
        val first = basis.first()
        val last = basis.last()
        val dx = meterLon((first.lat + last.lat) / 2.0, last.lon - first.lon)
        val dy = meterLat(last.lat - first.lat)
        return dx to dy
    }

    private fun hatRichtungsKonflikt(trackpunkte: List<AutomatikTrackpunkt>, headingDeg: Float?): Boolean {
        if (headingDeg == null || trackpunkte.size < 2) return false
        val richtung = berechneRichtungsVektor(trackpunkte) ?: return false
        val achsenHeading = (Math.toDegrees(atan2(richtung.first, richtung.second)) + 360.0) % 360.0
        val diff = winkelDifferenzGrad(achsenHeading, headingDeg.toDouble())
        return diff > 80.0
    }

    private fun winkelDifferenzGrad(a: Double, b: Double): Double {
        val diff = abs(a - b) % 360.0
        return if (diff > 180.0) 360.0 - diff else diff
    }

    private fun meterLat(deltaLat: Double): Double = deltaLat * 111_320.0
    private fun meterLon(refLat: Double, deltaLon: Double): Double = deltaLon * 111_320.0 * cos(Math.toRadians(refLat))

    private fun formatDistanz(distanzM: Double): String {
        return if (distanzM >= 1000.0) {
            String.format(Locale.GERMANY, "%.2f km", distanzM / 1000.0)
        } else {
            String.format(Locale.GERMANY, "%.0f m", distanzM)
        }
    }

    private fun buildAutomatikStatus(automatik: AutomatikIntern): String {
        if (!automatik.aktiv) return "Automatik: aus"
        val basis = "GPS ${automatik.gpsFixAnzahl} · Grün ${automatik.gruenAnzahl} · Gelb ${automatik.gelbAnzahl} · Rot ${automatik.rotAnzahl}"
        return if (automatik.pausiert) "$basis\nPausiert" else basis
    }

    private fun distanzMeter(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    class Fabrik(
        private val streckenRepo: StreckenpunkteRepository,
        private val baustellenRepo: BaustellenRepository,
        private val baumRepo: BaustellenBaumRepository,
        private val aktuelleSpeicher: AktuelleBaustelleSpeicher
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return StreckenpunkteViewModel(streckenRepo, baustellenRepo, baumRepo, aktuelleSpeicher) as T
        }
    }
}
