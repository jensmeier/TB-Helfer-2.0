package de.tbhelfer.einstellungen

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Kamera-Einstellungen (global), wie im Projekt besprochen:
 * - Bildqualität (JPEG)
 * - Blitz: Aus/Auto/An
 * - Nachtsichtfoto (Low-Light): Torch + max. Belichtung (wenn möglich)
 * - Pinch-to-Zoom
 */
class KameraEinstellungenSpeicher(private val context: Context) {

    enum class BlitzModus { AUS, AUTO, AN }

    data class Einstellungen(
        val jpegQualitaet: Int = 92, // 70..100
        val blitzModus: BlitzModus = BlitzModus.AUTO,
        val nachtsicht: Boolean = false,
        val pinchZoom: Boolean = true
    )

    private object Keys {
        val JPEG = intPreferencesKey("kamera_jpeg")
        val BLITZ = stringPreferencesKey("kamera_blitz")
        val NACHT = booleanPreferencesKey("kamera_nachtsicht")
        val PINCH = booleanPreferencesKey("kamera_pinch_zoom")
    }

    val einstellungen: Flow<Einstellungen> = context.dataStore.data.map { pref ->
        val blitz = runCatching {
            BlitzModus.valueOf(pref[Keys.BLITZ] ?: BlitzModus.AUTO.name)
        }.getOrDefault(BlitzModus.AUTO)

        Einstellungen(
            jpegQualitaet = (pref[Keys.JPEG] ?: 92).coerceIn(70, 100),
            blitzModus = blitz,
            nachtsicht = pref[Keys.NACHT] ?: false,
            pinchZoom = pref[Keys.PINCH] ?: true
        )
    }

    suspend fun speichern(cfg: Einstellungen) {
        context.dataStore.edit { pref ->
            pref[Keys.JPEG] = cfg.jpegQualitaet.coerceIn(70, 100)
            pref[Keys.BLITZ] = cfg.blitzModus.name
            pref[Keys.NACHT] = cfg.nachtsicht
            pref[Keys.PINCH] = cfg.pinchZoom
        }
    }
}
