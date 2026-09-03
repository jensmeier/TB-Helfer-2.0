package de.tbhelfer.ui.meinordner

import android.content.Context

class MeinOrdnerEinstellungenSpeicher(context: Context) {
    private val prefs = context.getSharedPreferences("mein_ordner_einstellungen", Context.MODE_PRIVATE)

    var ansicht: MeinOrdnerAnsicht
        get() = runCatching {
            MeinOrdnerAnsicht.valueOf(prefs.getString("ansicht", MeinOrdnerAnsicht.LISTE.name) ?: MeinOrdnerAnsicht.LISTE.name)
        }.getOrDefault(MeinOrdnerAnsicht.LISTE)
        set(value) = prefs.edit().putString("ansicht", value.name).apply()

    var sortierung: MeinOrdnerSortierung
        get() = runCatching {
            MeinOrdnerSortierung.valueOf(prefs.getString("sortierung", MeinOrdnerSortierung.NAME_AUF.name) ?: MeinOrdnerSortierung.NAME_AUF.name)
        }.getOrDefault(MeinOrdnerSortierung.NAME_AUF)
        set(value) = prefs.edit().putString("sortierung", value.name).apply()
}
