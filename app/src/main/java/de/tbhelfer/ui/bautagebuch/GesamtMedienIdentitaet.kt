package de.tbhelfer.ui.bautagebuch

import java.security.MessageDigest
import java.util.Locale

/** Gemeinsamer Exportschluessel fuer Katalog und bestaetigte lokale Server-Zuordnung. */
internal object GesamtMedienIdentitaet {
    fun serverKey(attachmentId: String, sha256: String?): String =
        sha256?.lowercase(Locale.ROOT)?.takeIf { it.isNotBlank() }
            ?: MessageDigest.getInstance("SHA-256")
                .digest(attachmentId.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
}
