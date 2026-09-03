package de.tbhelfer.datenbank

/**
 * Indikatorstatus zur Aufnahmezeit.
 *
 * Achtung: Werte sind absichtlich stabil (name wird in DB/Export genutzt).
 */
enum class Indikator {
    GRUEN,
    GELB,
    ROT;

    companion object {
        fun fromString(raw: String?): Indikator? {
            val v = raw?.trim()?.uppercase() ?: return null
            return values().firstOrNull { it.name == v }
        }
    }
}
