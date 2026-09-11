package de.tbhelfer.ui.bautagebuch

import android.content.Context
import android.os.Environment
import org.json.JSONObject
import java.io.File

/**
 * Liefert für die lokalen Foto-Hilfsspeicher den stabilen BTB-Schlüssel des aktuell
 * bearbeiteten Berichtskontexts.
 *
 * Neue Multi-BTB-Berichte dürfen Auswahl/Text/Start/KM-Overrides nicht mehr nur über
 * Datum+Schicht+Zeit teilen. Ein Pointer wird deshalb nur akzeptiert, wenn auch Datum und Schicht
 * zum angefragten BTB passen. Altbestände ohne passende stabile BTB-ID bleiben im Legacy-Schema.
 */
object BtbPhotoInstanzKey {

    private data class Pointer(
        val btbId: String,
        val datumIso: String,
        val schicht: SchichtTyp
    )

    fun activeBtbId(
        context: Context,
        baustelleId: Long,
        datumIso: String,
        schicht: SchichtTyp
    ): String? {
        if (baustelleId <= 0L || datumIso.isBlank()) return null
        val pictures = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: return null
        val root = File(pictures, "TB-Helfer")
        if (!root.exists()) return null

        fun matchingPointer(btbDir: File): String? {
            val pointer = readPointer(File(btbDir, ".active_btb.json")) ?: return null
            if (pointer.datumIso != datumIso || pointer.schicht != schicht) return null
            return pointer.btbId
        }

        val canonicalBtbDir = File(root, "baustelle_${baustelleId}/Bautagebuch")
        matchingPointer(canonicalBtbDir)?.let { return it }

        // Rückfall für einen alten Ordner, der auf dem Gerät nicht nach baustelle_<id>
        // umbenannt werden konnte. Nur Ordner akzeptieren, in denen ein BTB-JSON dieselbe
        // baustelleId trägt; sichtbare Namen sind niemals technische Schlüssel.
        root.listFiles()?.asSequence()
            ?.filter { it.isDirectory && it != canonicalBtbDir.parentFile }
            ?.forEach { top ->
                val btbDir = File(top, "Bautagebuch")
                val pointerId = matchingPointer(btbDir) ?: return@forEach
                if (containsBaustelleId(btbDir, baustelleId)) return pointerId
            }
        return null
    }

    fun activeToken(
        context: Context,
        baustelleId: Long,
        datumIso: String,
        schicht: SchichtTyp
    ): String? = activeBtbId(context, baustelleId, datumIso, schicht)?.let(::safeToken)

    private fun readPointer(file: File): Pointer? = runCatching {
        if (!file.exists() || !file.isFile) return@runCatching null
        val json = JSONObject(file.readText(Charsets.UTF_8))
        val id = json.optString("btbId").trim().takeIf { it.isNotBlank() } ?: return@runCatching null
        val datum = json.optString("datumIso").trim().takeIf { it.isNotBlank() } ?: return@runCatching null
        val schicht = runCatching { SchichtTyp.valueOf(json.optString("schicht")) }.getOrNull()
            ?: return@runCatching null
        Pointer(id, datum, schicht)
    }.getOrNull()

    private fun containsBaustelleId(btbDir: File, baustelleId: Long): Boolean {
        if (!btbDir.exists() || !btbDir.isDirectory) return false
        val dateDirs = btbDir.listFiles()?.filter { it.isDirectory }
            ?.sortedByDescending { it.name }
            ?.take(24)
            .orEmpty()
        for (dateDir in dateDirs) {
            val candidates = dateDir.listFiles()?.filter { file ->
                file.isFile && file.extension.equals("json", ignoreCase = true) &&
                    (file.name.startsWith("btb_") || file.name.startsWith("bautagebuch")) &&
                    !file.name.contains("_original")
            }.orEmpty()
            for (file in candidates) {
                val matches = runCatching {
                    JSONObject(file.readText(Charsets.UTF_8)).optLong("baustelleId", Long.MIN_VALUE) == baustelleId
                }.getOrDefault(false)
                if (matches) return true
            }
        }
        return false
    }

    private fun safeToken(value: String): String = value
        .trim()
        .lowercase()
        .replace(Regex("[^a-z0-9_-]"), "_")
        .take(80)
        .ifBlank { "unknown" }
}
