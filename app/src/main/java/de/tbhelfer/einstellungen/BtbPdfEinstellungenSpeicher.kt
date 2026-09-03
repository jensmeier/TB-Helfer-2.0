package de.tbhelfer.einstellungen

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Einstellungen für BTB/PDF:
 * - Raster/Layout im Foto-Anhang
 * - Welche Metadaten pro Foto im PDF angezeigt werden
 * - GPS optional als Maps-Link (damit PDF-Viewer das anklickbar machen können)
 */
class BtbPdfEinstellungenSpeicher(private val context: Context) {

    enum class FotoRaster {
        RASTER_1x6,
        RASTER_2x6,
        RASTER_3x3,
        RASTER_1x1
    }

    data class Einstellungen(
        val raster: FotoRaster = FotoRaster.RASTER_3x3,

        // Metadaten
        val zeigeDatum: Boolean = false,
        val zeigeUhrzeit: Boolean = true,
        val zeigeKm: Boolean = true,
        val zeigeGps: Boolean = true,
        val gpsAlsMapsLink: Boolean = true,
        val zeigeBaustelle: Boolean = false,
        val zeigeKurztext: Boolean = true,
        val zeigeNotiz: Boolean = false,
        val zeigeFirma: Boolean = false,
        val zeigeIndikator: Boolean = false,
        val zeigeDateiname: Boolean = true
    )

    private object Keys {
        val RASTER = stringPreferencesKey("btbpdf_raster")

        val DATUM = booleanPreferencesKey("btbpdf_show_datum")
        val UHRZEIT = booleanPreferencesKey("btbpdf_show_uhrzeit")
        val KM = booleanPreferencesKey("btbpdf_show_km")
        val GPS = booleanPreferencesKey("btbpdf_show_gps")
        val GPS_LINK = booleanPreferencesKey("btbpdf_gps_as_maps_link")
        val BAUSTELLE = booleanPreferencesKey("btbpdf_show_baustelle")
        val KURZTEXT = booleanPreferencesKey("btbpdf_show_kurztext")
        val NOTIZ = booleanPreferencesKey("btbpdf_show_notiz")
        val FIRMA = booleanPreferencesKey("btbpdf_show_firma")
        val INDIKATOR = booleanPreferencesKey("btbpdf_show_indikator")
        val DATEINAME = booleanPreferencesKey("btbpdf_show_dateiname")
    }

    val einstellungen: Flow<Einstellungen> = context.dataStore.data.map { pref ->
        val raster = runCatching {
            FotoRaster.valueOf(pref[Keys.RASTER] ?: FotoRaster.RASTER_3x3.name)
        }.getOrDefault(FotoRaster.RASTER_3x3)

        Einstellungen(
            raster = raster,
            zeigeDatum = pref[Keys.DATUM] ?: false,
            zeigeUhrzeit = pref[Keys.UHRZEIT] ?: true,
            zeigeKm = pref[Keys.KM] ?: true,
            zeigeGps = pref[Keys.GPS] ?: true,
            gpsAlsMapsLink = pref[Keys.GPS_LINK] ?: true,
            zeigeBaustelle = pref[Keys.BAUSTELLE] ?: false,
            zeigeKurztext = pref[Keys.KURZTEXT] ?: true,
            zeigeNotiz = pref[Keys.NOTIZ] ?: false,
            zeigeFirma = pref[Keys.FIRMA] ?: false,
            zeigeIndikator = pref[Keys.INDIKATOR] ?: false,
            zeigeDateiname = pref[Keys.DATEINAME] ?: true
        )
    }

    suspend fun speichern(cfg: Einstellungen) {
        context.dataStore.edit { pref ->
            pref[Keys.RASTER] = cfg.raster.name

            pref[Keys.DATUM] = cfg.zeigeDatum
            pref[Keys.UHRZEIT] = cfg.zeigeUhrzeit
            pref[Keys.KM] = cfg.zeigeKm
            pref[Keys.GPS] = cfg.zeigeGps
            pref[Keys.GPS_LINK] = cfg.gpsAlsMapsLink
            pref[Keys.BAUSTELLE] = cfg.zeigeBaustelle
            pref[Keys.KURZTEXT] = cfg.zeigeKurztext
            pref[Keys.NOTIZ] = cfg.zeigeNotiz
            pref[Keys.FIRMA] = cfg.zeigeFirma
            pref[Keys.INDIKATOR] = cfg.zeigeIndikator
            pref[Keys.DATEINAME] = cfg.zeigeDateiname
        }
    }
}
