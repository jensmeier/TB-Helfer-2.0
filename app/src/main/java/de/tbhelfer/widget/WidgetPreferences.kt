package de.tbhelfer.widget

import android.appwidget.AppWidgetManager
import android.content.Context

/** Einstellungen des frei konfigurierbaren TB-Helfer-Widgets. */
object WidgetPreferences {
    enum class Ansicht { KAMERA, LETZTE_EINTRAEGE, BAUSTELLENSTATUS, WO_BIN_ICH, SCHNELLZUGRIFF, GEMISCHT }
    enum class HauptKlick { AKTUELLER_BTB, AKTUELLE_BAUSTELLE, KAMERA, ABNAHMEN, MAENGEL, STARTSEITE }
    enum class ButtonAktion {
        PUNKT_4, PUNKT_5, PUNKT_6, PUNKT_7, PUNKT_8,
        NEUE_ABNAHME, KAMERA, AKTUELLER_BTB, ABNAHMEN, MAENGEL, POSITION_AKTUALISIEREN
    }
    enum class PositionModus { LETZTE_APP_POSITION, NUR_MANUELL }
    enum class Darstellung { AUTOMATISCH, HELL, DUNKEL, TRANSPARENT }

    data class Config(
        val ansicht: Ansicht = Ansicht.LETZTE_EINTRAEGE,
        val hauptKlick: HauptKlick = HauptKlick.AKTUELLER_BTB,
        val letzteEintraege: Int = 3,
        val punkt4: Boolean = true,
        val punkt5: Boolean = true,
        val punkt6: Boolean = true,
        val punkt7: Boolean = true,
        val punkt8: Boolean = true,
        val buttonsAnzeigen: Boolean = true,
        val button1: ButtonAktion = ButtonAktion.PUNKT_4,
        val button2: ButtonAktion = ButtonAktion.NEUE_ABNAHME,
        val kameraAnzeigen: Boolean = true,
        val positionModus: PositionModus = PositionModus.LETZTE_APP_POSITION,
        val darstellung: Darstellung = Darstellung.AUTOMATISCH
    )

    private const val PREF = "tb_helfer_widget_config_v1"
    private const val DEFAULT_PREFIX = "default_"

    private fun prefix(appWidgetId: Int): String = if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) DEFAULT_PREFIX else "w${appWidgetId}_"

    fun load(context: Context, appWidgetId: Int): Config {
        val p = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val ownPrefix = prefix(appWidgetId)
        val hasOwn = appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID && p.contains(ownPrefix + "ansicht")
        val pre = if (hasOwn) ownPrefix else DEFAULT_PREFIX
        return Config(
            ansicht = enumValue(p.getString(pre + "ansicht", null), Ansicht.LETZTE_EINTRAEGE),
            hauptKlick = enumValue(p.getString(pre + "hauptKlick", null), HauptKlick.AKTUELLER_BTB),
            letzteEintraege = p.getInt(pre + "letzteEintraege", 3).coerceIn(1, 10),
            punkt4 = p.getBoolean(pre + "punkt4", true),
            punkt5 = p.getBoolean(pre + "punkt5", true),
            punkt6 = p.getBoolean(pre + "punkt6", true),
            punkt7 = p.getBoolean(pre + "punkt7", true),
            punkt8 = p.getBoolean(pre + "punkt8", true),
            buttonsAnzeigen = p.getBoolean(pre + "buttonsAnzeigen", true),
            button1 = enumValue(p.getString(pre + "button1", null), ButtonAktion.PUNKT_4),
            button2 = enumValue(p.getString(pre + "button2", null), ButtonAktion.NEUE_ABNAHME),
            kameraAnzeigen = p.getBoolean(pre + "kameraAnzeigen", true),
            positionModus = enumValue(p.getString(pre + "positionModus", null), PositionModus.LETZTE_APP_POSITION),
            darstellung = enumValue(p.getString(pre + "darstellung", null), Darstellung.AUTOMATISCH)
        )
    }

    fun save(context: Context, appWidgetId: Int, c: Config) {
        val pre = prefix(appWidgetId)
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString(pre + "ansicht", c.ansicht.name)
            .putString(pre + "hauptKlick", c.hauptKlick.name)
            .putInt(pre + "letzteEintraege", c.letzteEintraege.coerceIn(1, 10))
            .putBoolean(pre + "punkt4", c.punkt4)
            .putBoolean(pre + "punkt5", c.punkt5)
            .putBoolean(pre + "punkt6", c.punkt6)
            .putBoolean(pre + "punkt7", c.punkt7)
            .putBoolean(pre + "punkt8", c.punkt8)
            .putBoolean(pre + "buttonsAnzeigen", c.buttonsAnzeigen)
            .putString(pre + "button1", c.button1.name)
            .putString(pre + "button2", c.button2.name)
            .putBoolean(pre + "kameraAnzeigen", c.kameraAnzeigen)
            .putString(pre + "positionModus", c.positionModus.name)
            .putString(pre + "darstellung", c.darstellung.name)
            .apply()
    }

    fun delete(context: Context, appWidgetId: Int) {
        val pre = prefix(appWidgetId)
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val edit = prefs.edit()
        prefs.all.keys.filter { it.startsWith(pre) }.forEach { edit.remove(it) }
        edit.apply()
    }

    fun applyDefaultsToAll(context: Context, ids: IntArray, config: Config) {
        save(context, AppWidgetManager.INVALID_APPWIDGET_ID, config)
        ids.forEach { save(context, it, config) }
    }

    private inline fun <reified T : Enum<T>> enumValue(raw: String?, fallback: T): T =
        runCatching { if (raw.isNullOrBlank()) fallback else enumValueOf<T>(raw) }.getOrDefault(fallback)
}
