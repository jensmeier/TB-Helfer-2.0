package de.tbhelfer.einstellungen

import android.content.Context
import android.graphics.Color
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AnsichtEinstellungenSpeicher(private val context: Context) {

    companion object {
        // Startup-Cache: Damit wir den NightMode ohne runBlocking/DataStore-IO
        // sehr früh im Application-Start setzen können.
        private const val PREFS_STARTUP_CACHE = "tbhelfer_startup_cache"
        private const val KEY_NIGHT_MODE = "night_mode"

        fun readCachedNightMode(context: Context): NightMode {
            val s = context.getSharedPreferences(PREFS_STARTUP_CACHE, Context.MODE_PRIVATE)
                .getString(KEY_NIGHT_MODE, null)
            return when (s) {
                NightMode.LIGHT.name -> NightMode.LIGHT
                NightMode.DARK.name -> NightMode.DARK
                else -> NightMode.SYSTEM
            }
        }

        fun writeCachedNightMode(context: Context, nightMode: NightMode) {
            context.getSharedPreferences(PREFS_STARTUP_CACHE, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_NIGHT_MODE, nightMode.name)
                .apply()
        }
    }

    enum class MenuLayout { LISTE, KACHEL }

    enum class NightMode { SYSTEM, LIGHT, DARK }

    data class Einstellungen(
        val menuButtonColor: Int,
        val menuTextColor: Int,
        val menuTextSizeSp: Int,
        val menuLayout: MenuLayout,
        val sucheGridSpalten: Int,
        val nightMode: NightMode
    )

    // Keys bleiben aus Kompatibilitätsgründen bestehen,
    // aber Farben werden nicht mehr aus den Einstellungen übernommen.
    private val keyMenuButtonColor = intPreferencesKey("menu_button_color")
    private val keyMenuTextColor = intPreferencesKey("menu_text_color")
    private val keyMenuTextSize = intPreferencesKey("menu_text_size_sp")
    private val keyMenuLayout = stringPreferencesKey("menu_layout")
    private val keySucheGridSpalten = intPreferencesKey("suche_grid_spalten")

    private val keyNightMode = stringPreferencesKey("night_mode") // SYSTEM/LIGHT/DARK

    private val fixedButtonColor = Color.parseColor("#2F7BFF") // TB-Blau
    private val fixedTextColor = Color.BLACK

    val einstellungen: Flow<Einstellungen> = context.dataStore.data.map { prefs ->
        val textSize = prefs[keyMenuTextSize] ?: 16
        val layout = when (prefs[keyMenuLayout]) {
            MenuLayout.KACHEL.name -> MenuLayout.KACHEL
            else -> MenuLayout.LISTE
        }
        val grid = (prefs[keySucheGridSpalten] ?: 2).coerceIn(1, 3)

        val night = when (prefs[keyNightMode]) {
            NightMode.LIGHT.name -> NightMode.LIGHT
            NightMode.DARK.name -> NightMode.DARK
            else -> NightMode.SYSTEM
        }

        Einstellungen(
            menuButtonColor = fixedButtonColor,
            menuTextColor = fixedTextColor,
            menuTextSizeSp = textSize.coerceIn(12, 24),
            menuLayout = layout,
            sucheGridSpalten = grid,
            nightMode = night
        )
    }

    suspend fun speichern(cfg: Einstellungen) {
        context.dataStore.edit { prefs ->
            // Farben immer fix (unabhängig vom übergebenen cfg)
            prefs[keyMenuButtonColor] = fixedButtonColor
            prefs[keyMenuTextColor] = fixedTextColor

            prefs[keyMenuTextSize] = cfg.menuTextSizeSp.coerceIn(12, 24)
            prefs[keyMenuLayout] = cfg.menuLayout.name
            prefs[keySucheGridSpalten] = cfg.sucheGridSpalten.coerceIn(1, 3)
            prefs[keyNightMode] = cfg.nightMode.name
        }

        // Zusätzlich: synchroner Startup-Cache, damit TBHelferApp ohne runBlocking starten kann.
        writeCachedNightMode(context, cfg.nightMode)
    }
}
