package de.tbhelfer.ui.einstellungen.nutzer

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * Geraetelokaler Offline-Cache der persoenlichen Kontaktdaten.
 * Source of Truth ist der Server-Benutzer; Firmenstammdaten bleiben getrennt in NutzerProfil.
 */
data class PersoenlichesNutzerProfil(
    val userId: String? = null,
    val name: String? = null,
    val telefon: String? = null,
    val email: String? = null,
    val profileVersion: Long = 1L
)

object PersoenlichesNutzerProfilSpeicher {
    private const val JSON_NAME = "persoenliches_nutzerprofil.json"

    private fun datei(context: Context): File = File(NutzerSpeicher.ordner(context), JSON_NAME)

    fun laden(context: Context): PersoenlichesNutzerProfil? {
        val file = datei(context)
        if (!file.exists()) return null
        return runCatching {
            val json = JSONObject(file.readText(Charsets.UTF_8))
            PersoenlichesNutzerProfil(
                userId = json.optString("userId").takeIf { it.isNotBlank() },
                name = json.optString("name").takeIf { it.isNotBlank() },
                telefon = json.optString("telefon").takeIf { it.isNotBlank() },
                email = json.optString("email").takeIf { it.isNotBlank() },
                profileVersion = json.optLong("profileVersion", 1L).coerceAtLeast(1L)
            )
        }.getOrNull()
    }

    fun speichern(context: Context, profil: PersoenlichesNutzerProfil) {
        val json = JSONObject()
            .put("userId", profil.userId)
            .put("name", profil.name)
            .put("telefon", profil.telefon)
            .put("email", profil.email)
            .put("profileVersion", profil.profileVersion)
        datei(context).writeText(json.toString(2), Charsets.UTF_8)
    }
}
