package de.tbhelfer.ui.streckenpunkte

import de.tbhelfer.vermessung.KmBerechner

data class AutomatikTrackpunkt(
    val lat: Double,
    val lon: Double,
    val altM: Double?,
    val accuracyM: Float?,
    val epochMs: Long,
    val kmVorschlag: Double?,
    val kmSuffixVorschlag: Int?,
    val indikator: KmBerechner.Indikator?,
    val achsabstandM: Double?,
    val istWeichenmodus: Boolean,
    val headingDeg: Float? = null,
    val schritteSeitStart: Float? = null,
    val istUnsicher: Boolean = false,
    val fortschrittM: Double? = null,
    val seitenabstandM: Double? = null
)

data class AutomatikAnker(
    val km: Double,
    val kmSuffix: Int,
    val bezeichnung: String,
    val notiz: String?,
    val lat: Double,
    val lon: Double,
    val altM: Double?,
    val epochMs: Long
)

data class AutomatikSessionSnapshot(
    val startedAtEpochMs: Long,
    val stoppedAtEpochMs: Long,
    val streckennummer: String?,
    val bahnhof: String?,
    val gleis: String?,
    val weiche: String?,
    val sessionTitel: String?,
    val trackpunkte: List<AutomatikTrackpunkt>,
    val unsichereTrackpunkte: List<AutomatikTrackpunkt>,
    val anker: List<AutomatikAnker>,
    val gesamtDistanzM: Double,
    val gpsFixAnzahl: Int,
    val gruenAnzahl: Int,
    val gelbAnzahl: Int,
    val rotAnzahl: Int
)
