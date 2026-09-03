package de.tbhelfer.ui.einstellungen.overlay

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import de.tbhelfer.einstellungen.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Einstellungen für das Foto-Overlay (Darstellung im Bild).
 *
 * Ziel:
 * - Alles, was das Overlay betrifft, liegt im Overlay-Ordner.
 * - Konfigurierbar: Position, Transparenz, Auto-Kontrast, Inhalte ein/aus.
 */
class OverlayEinstellungenSpeicher(private val context: Context) {

    enum class Position { AUTO, LINKS_OBEN, RECHTS_OBEN, LINKS_UNTEN, RECHTS_UNTEN }

    /** Wie kompakt soll das Overlay sein? */
    enum class Darstellung { NORMAL, KOMPAKT_2ZEILIG }

    data class Einstellungen(
        val aktiv: Boolean = true,
        val position: Position = Position.AUTO,
        val darstellung: Darstellung = Darstellung.NORMAL,
        /** Wenn Position=AUTO: beste Ecke per Bildanalyse wählen */
        val autoPositionSmart: Boolean = true,
        /** Hintergrund-Alpha 0.1 .. 0.9 */
        val alpha: Float = 0.55f,
        /** Auto-Kontrast: Textfarbe + Hintergrund hell/dunkel je nach Bildbereich */
        val autoKontrast: Boolean = true,
        /** Schriftgröße 10 .. 22 */
        val textSizeSp: Int = 14,

        // Inhalte
        val zeigeBaustelle: Boolean = true,
        val zeigeUnterbaustelle: Boolean = true,
        val zeigeStreckeBahnhof: Boolean = true,
        val zeigeGleisWeicheObjekt: Boolean = true,
        val zeigeKm: Boolean = true,
        val zeigeGps: Boolean = true,
        val zeigeDatumZeit: Boolean = true
    )

    private object Keys {
        val AKTIV = booleanPreferencesKey("overlay_aktiv")
        val POS = stringPreferencesKey("overlay_position")
        val DARSTELLUNG = stringPreferencesKey("overlay_darstellung")
        val AUTO_POS_SMART = booleanPreferencesKey("overlay_auto_pos_smart")
        val ALPHA = floatPreferencesKey("overlay_alpha")
        val AUTO_KONTRAST = booleanPreferencesKey("overlay_auto_kontrast")
        val TEXT_SIZE = intPreferencesKey("overlay_text_size_sp")

        val Z_BAUSTELLE = booleanPreferencesKey("overlay_show_baustelle")
        val Z_UNTER = booleanPreferencesKey("overlay_show_unter")
        val Z_STRECKE = booleanPreferencesKey("overlay_show_strecke")
        val Z_GLEIS = booleanPreferencesKey("overlay_show_gleis")
        val Z_KM = booleanPreferencesKey("overlay_show_km")
        val Z_GPS = booleanPreferencesKey("overlay_show_gps")
        val Z_DT = booleanPreferencesKey("overlay_show_dt")
    }

    val einstellungen: Flow<Einstellungen> = context.dataStore.data.map { prefs ->
        val aktiv = prefs[Keys.AKTIV] ?: true
        val pos = runCatching {
            Position.valueOf(prefs[Keys.POS] ?: Position.AUTO.name)
        }.getOrDefault(Position.AUTO)
        val darstellung = runCatching {
            Darstellung.valueOf(prefs[Keys.DARSTELLUNG] ?: Darstellung.NORMAL.name)
        }.getOrDefault(Darstellung.NORMAL)
        val autoPosSmart = prefs[Keys.AUTO_POS_SMART] ?: true
        val alpha = (prefs[Keys.ALPHA] ?: 0.55f).coerceIn(0.1f, 0.9f)
        val autoKontrast = prefs[Keys.AUTO_KONTRAST] ?: true
        val textSize = (prefs[Keys.TEXT_SIZE] ?: 14).coerceIn(10, 22)

        Einstellungen(
            aktiv = aktiv,
            position = pos,
            darstellung = darstellung,
            autoPositionSmart = autoPosSmart,
            alpha = alpha,
            autoKontrast = autoKontrast,
            textSizeSp = textSize,
            zeigeBaustelle = prefs[Keys.Z_BAUSTELLE] ?: true,
            zeigeUnterbaustelle = prefs[Keys.Z_UNTER] ?: true,
            zeigeStreckeBahnhof = prefs[Keys.Z_STRECKE] ?: true,
            zeigeGleisWeicheObjekt = prefs[Keys.Z_GLEIS] ?: true,
            zeigeKm = prefs[Keys.Z_KM] ?: true,
            zeigeGps = prefs[Keys.Z_GPS] ?: true,
            zeigeDatumZeit = prefs[Keys.Z_DT] ?: true
        )
    }

    suspend fun speichern(neu: Einstellungen) {
        context.dataStore.edit { prefs ->
            prefs[Keys.AKTIV] = neu.aktiv
            prefs[Keys.POS] = neu.position.name
            prefs[Keys.DARSTELLUNG] = neu.darstellung.name
            prefs[Keys.AUTO_POS_SMART] = neu.autoPositionSmart
            prefs[Keys.ALPHA] = neu.alpha.coerceIn(0.1f, 0.9f)
            prefs[Keys.AUTO_KONTRAST] = neu.autoKontrast
            prefs[Keys.TEXT_SIZE] = neu.textSizeSp.coerceIn(10, 22)

            prefs[Keys.Z_BAUSTELLE] = neu.zeigeBaustelle
            prefs[Keys.Z_UNTER] = neu.zeigeUnterbaustelle
            prefs[Keys.Z_STRECKE] = neu.zeigeStreckeBahnhof
            prefs[Keys.Z_GLEIS] = neu.zeigeGleisWeicheObjekt
            prefs[Keys.Z_KM] = neu.zeigeKm
            prefs[Keys.Z_GPS] = neu.zeigeGps
            prefs[Keys.Z_DT] = neu.zeigeDatumZeit
        }
    }
}
