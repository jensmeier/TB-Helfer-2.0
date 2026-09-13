package de.tbhelfer.ui.bautagebuch

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

/**
 * Übergangsbrücke für den bestehenden großen PDF-Renderer.
 *
 * Der fachliche Originalstand bleibt unveraendert. Fuer die reine PDF-Darstellung wird jedoch eine
 * temporaere Spiegel-Baseline erzeugt: Arbeitszeit, Personal/Firmen, Geraete/Maschinen und Wetter
 * werden aus dem aktuellen BTB in diese Spiegelkopie uebernommen. Diese Kopfdaten duerfen sich nach
 * Benutzerentscheidung aendern, sollen im PDF aber nicht rot erscheinen. Die fachlichen Abschnitte
 * 4–8 bleiben dagegen aus dem ersten Original erhalten und werden weiterhin echt verglichen.
 */
object BtbRendererBaselineBridge {

    private fun token(entry: BautagebuchEintrag): String = entry.btbId
        ?.lowercase()
        ?.replace(Regex("[^a-z0-9_-]"), "_")
        ?.take(80)
        ?.ifBlank { "legacy" }
        ?: "legacy_${entry.datumIso}_${entry.schicht.name}".lowercase()

    fun prepare(context: Context, entry: BautagebuchEintrag) {
        val date = runCatching { LocalDate.parse(entry.datumIso) }.getOrNull() ?: return
        val specificDir = BautagebuchSpeicher.ordner(context, entry.baustelleId, entry.baustelleName, date)
        val specific = if (!entry.btbId.isNullOrBlank()) {
            File(specificDir, "btb_${token(entry)}_original.json")
        } else {
            File(specificDir, if (entry.schicht == SchichtTyp.NACHT) "bautagebuch_nacht_original.json" else "bautagebuch_tag_original.json")
        }
        val current = if (!entry.btbId.isNullOrBlank()) {
            File(specificDir, "btb_${token(entry)}.json")
        } else {
            File(specificDir, if (entry.schicht == SchichtTyp.NACHT) "bautagebuch_nacht.json" else "bautagebuch_tag.json")
        }

        val legacyDir = BautagebuchSpeicher.ordner(context, entry.baustelleName, date)
        val mirror = File(
            legacyDir,
            if (entry.schicht == SchichtTyp.NACHT) "bautagebuch_nacht_original.json" else "bautagebuch_tag_original.json"
        )

        runCatching {
            mirror.parentFile?.mkdirs()
            if (specific.exists()) {
                val originalJson = JSONObject(specific.readText(Charsets.UTF_8))
                val currentJson = current.takeIf { it.exists() }
                    ?.let { JSONObject(it.readText(Charsets.UTF_8)) }
                if (currentJson != null) neutralisiereKopfdaten(originalJson, currentJson)
                mirror.writeText(originalJson.toString(2), Charsets.UTF_8)
            } else if (mirror.exists()) {
                mirror.delete()
            }
        }
    }

    /** Nur die PDF-Vergleichskopie wird angepasst; die echte Originaldatei bleibt unberuehrt. */
    private fun neutralisiereKopfdaten(original: JSONObject, current: JSONObject) {
        listOf(
            "datumIso",
            "endeDatumIso",
            "schicht",
            "startUhrzeit",
            "endeUhrzeit",
            "wetter",
            "weather",
            "weatherSlots",
            "personal",
            "geraete"
        ).forEach { key -> copyKey(current, original, key) }

        val originalFirmen = original.optJSONArray("firmen") ?: return
        val currentFirmen = current.optJSONArray("firmen") ?: return
        val currentById = mutableMapOf<String, JSONObject>()
        val currentByName = mutableMapOf<String, JSONObject>()
        for (i in 0 until currentFirmen.length()) {
            val f = currentFirmen.optJSONObject(i) ?: continue
            f.optString("firmaId").takeIf { it.isNotBlank() }?.let { currentById[it] = f }
            f.optString("name").trim().lowercase().takeIf { it.isNotBlank() }?.let { currentByName[it] = f }
        }

        for (i in 0 until originalFirmen.length()) {
            val base = originalFirmen.optJSONObject(i) ?: continue
            val id = base.optString("firmaId")
            val name = base.optString("name").trim().lowercase()
            val now = currentById[id] ?: currentByName[name] ?: continue
            listOf(
                "name",
                "personalVon",
                "personalBis",
                "maschinenVon",
                "maschinenBis",
                "visibleInPersonal",
                "visibleInMaschinen",
                "personal",
                "geraete"
            ).forEach { key -> copyKey(now, base, key) }
        }

        // Firmen, die nur im aktuellen Stand fuer Personal/Geraete neu hinzugekommen sind, muessen
        // ebenfalls in der Vergleichskopie vorhanden sein. Ihre Leistungen werden absichtlich leer
        // gehalten, damit neue fachliche Arbeiten weiterhin gegen das Original rot bleiben.
        val originalIds = (0 until originalFirmen.length()).mapNotNull { idx ->
            originalFirmen.optJSONObject(idx)?.optString("firmaId")?.takeIf { it.isNotBlank() }
        }.toSet()
        for (i in 0 until currentFirmen.length()) {
            val now = currentFirmen.optJSONObject(i) ?: continue
            val id = now.optString("firmaId")
            if (id.isBlank() || id in originalIds) continue
            val neutral = JSONObject(now.toString()).apply {
                put("leistungen", JSONArray())
            }
            originalFirmen.put(neutral)
        }
    }

    private fun copyKey(from: JSONObject, to: JSONObject, key: String) {
        if (!from.has(key)) {
            to.remove(key)
            return
        }
        val value = from.opt(key)
        when (value) {
            is JSONObject -> to.put(key, JSONObject(value.toString()))
            is JSONArray -> to.put(key, JSONArray(value.toString()))
            null -> to.put(key, JSONObject.NULL)
            else -> to.put(key, value)
        }
    }
}
