package de.tbhelfer.ui.bautagebuch

import android.content.Context
import android.os.Environment
import org.json.JSONObject
import java.io.File

/**
 * Liefert für die lokalen Foto-Hilfsspeicher den aktuell aktiven stabilen BTB-Schlüssel.
 *
 * Neue Multi-BTB-Berichte dürfen Auswahl/Text/Start/KM-Overrides nicht mehr nur über
 * Datum+Schicht+Zeit teilen. Altbestände ohne stabile BTB-ID bleiben bewusst im Legacy-Schema.
 */
object BtbPhotoInstanzKey {

    fun activeBtbId(context: Context, baustelleId: Long): String? {
        if (baustelleId <= 0L) return null
        val pictures = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: return null
        val root = File(pictures, "TB-Helfer")
        if (!root.exists()) return null

        val canonicalBtbDir = File(root, "baustelle_${baustelleId}/Bautagebuch")
        readPointer(File(canonicalBtbDir, ".active_btb.json"))?.let { return it }

        // Rückfall für einen sehr alten Ordner, der auf dem Gerät nicht nach baustelle_<id>
        // umbenannt werden konnte. Nur Ordner akzeptieren, in denen ein BTB-JSON dieselbe
        // baustelleId trägt; sichtbare Namen sind niemals technische Schlüssel.
        root.listFiles()?.asSequence()
            ?.filter { it.isDirectory && it != canonicalBtbDir.parentFile }
            ?.forEach { top ->
                val btbDir = File(top, "Bautagebuch")
                val pointer = readPointer(File(btbDir, ".active_btb.json")) ?: return@forEach
                if (containsBaustelleId(btbDir, baustelleId)) return pointer
            }
        return null
    }

    fun activeToken(context: Context, baustelleId: Long): String? =
        activeBtbId(context, baustelleId)?.let(::safeToken)

    private fun readPointer(file: File): String? = runCatching {
        if (!file.exists() || !file.isFile) return@runCatching null
        JSONObject(file.readText(Charsets.UTF_8))
            .optString("btbId")
            .trim()
            .takeIf { it.isNotBlank() }
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
