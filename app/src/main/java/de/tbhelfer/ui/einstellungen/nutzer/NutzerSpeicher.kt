package de.tbhelfer.ui.einstellungen.nutzer

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Environment
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

object NutzerSpeicher {

    private const val JSON_NAME = "nutzer.json"

    fun ordner(context: Context): File {
        val base = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            ?: throw IllegalStateException("Kein externer App-Ordner verfügbar")
        val root = File(base, "TB-Helfer")
        val dir = File(root, "Einstellungen/Nutzer")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun datei(context: Context): File = File(ordner(context), JSON_NAME)

    fun laden(context: Context): NutzerProfil? {
        val f = datei(context)
        if (!f.exists()) return null
        val json = runCatching { f.readText(Charsets.UTF_8) }.getOrNull() ?: return null
        return runCatching { fromJson(JSONObject(json)) }.getOrNull()
    }

    fun speichern(context: Context, profil: NutzerProfil) {
        val f = datei(context)
        f.writeText(toJson(profil).toString(2), Charsets.UTF_8)
    }

    /** Kopiert ein ausgewähltes Logo (freier Dateiname) in den Nutzer-Ordner und gibt den gespeicherten Dateinamen zurück. */
    fun importiereLogo(context: Context, uri: Uri): String? {
        val dir = ordner(context)
        val name = queryDisplayName(context.contentResolver, uri)?.takeIf { it.isNotBlank() }
            ?: "logo_${System.currentTimeMillis()}.png"

        // Kollision vermeiden
        val safeName = if (File(dir, name).exists()) {
            val dot = name.lastIndexOf('.')
            val base = if (dot > 0) name.substring(0, dot) else name
            val ext = if (dot > 0) name.substring(dot) else ""
            "${base}_${System.currentTimeMillis()}$ext"
        } else name

        val outFile = File(dir, safeName)
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(outFile).use { output -> input.copyTo(output) }
            } ?: return null
            safeName
        }.getOrNull()
    }

    private fun queryDisplayName(cr: ContentResolver, uri: Uri): String? {
        val proj = arrayOf(android.provider.OpenableColumns.DISPLAY_NAME)
        cr.query(uri, proj, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) return c.getString(idx)
        }
        return null
    }

    private fun toJson(p: NutzerProfil): JSONObject = JSONObject().apply {
        put("firmenname", p.firmenname)
        put("anschrift1", p.anschrift1)
        put("anschrift2", p.anschrift2)
        put("telefon", p.telefon)
        put("email", p.email)
        put("logoDateiname", p.logoDateiname)
        put("maxFotosInPdf", p.maxFotosInPdf)
    }

    private fun fromJson(o: JSONObject): NutzerProfil = NutzerProfil(
        firmenname = o.optString("firmenname").takeUnless { it.isBlank() },
        anschrift1 = o.optString("anschrift1").takeUnless { it.isBlank() },
        anschrift2 = o.optString("anschrift2").takeUnless { it.isBlank() },
        telefon = o.optString("telefon").takeUnless { it.isBlank() },
        email = o.optString("email").takeUnless { it.isBlank() },
        logoDateiname = o.optString("logoDateiname").takeUnless { it.isBlank() },
        maxFotosInPdf = o.optInt("maxFotosInPdf", 60).coerceIn(0, 500)
    )
}
