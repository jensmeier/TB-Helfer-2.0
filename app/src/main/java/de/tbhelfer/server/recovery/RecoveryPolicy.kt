package de.tbhelfer.server.recovery

import java.io.IOException
import java.util.UUID

/** No Android dependencies: the gate can be exercised with real JVM tests. */
internal object RecoveryPolicy {
    fun epoch(value: String?): String? {
        if (value == null) return null
        val parsed = try { UUID.fromString(value).toString() } catch (_: IllegalArgumentException) {
            throw IOException("Ungültige Wiederherstellungskennung des Servers.")
        }
        if (!parsed.equals(value, ignoreCase = true)) throw IOException("Ungültige Wiederherstellungskennung des Servers.")
        return parsed
    }

    fun permits(observed: String?, acknowledged: String?, ownerMatches: Boolean): Boolean =
        ownerMatches && observed == acknowledged
}
