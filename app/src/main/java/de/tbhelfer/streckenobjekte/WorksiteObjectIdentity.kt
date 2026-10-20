package de.tbhelfer.streckenobjekte

import java.util.Locale

/** Additives Serverfeld und bestehende lokale Kennung beschreiben dieselbe Identitaet. */
object WorksiteObjectIdentity {
    private val known = setOf("GLEIS", "WEICHE", "WEICHENVERBINDUNG", "DKW_KREUZUNG")

    fun type(raw: String?, explicit: String? = null): String? {
        explicit?.trim()?.uppercase(Locale.ROOT)?.takeIf { it.isNotBlank() }?.let { return it }
        if (raw.isNullOrBlank()) return null
        return when (BaustellenObjektLogik.artAusKennung(raw)) {
            BaustellenObjektArt.WEICHE -> "WEICHE"
            BaustellenObjektArt.WEICHENVERBINDUNG -> "WEICHENVERBINDUNG"
            BaustellenObjektArt.DKW_KREUZUNG -> "DKW_KREUZUNG"
            else -> "GLEIS"
        }
    }

    fun number(raw: String?): String = BaustellenObjektLogik.normalisierteObjektKennung(raw)

    fun localKey(raw: String?, explicit: String?): String? {
        val text = raw?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val kind = type(text, explicit)
        if (kind !in known || explicit.isNullOrBlank()) return text
        if (kind == type(text) && BaustellenObjektLogik.artAusKennung(text) != BaustellenObjektArt.SONSTIGES) return text
        val number = number(text)
        return when (kind) {
            "WEICHE" -> "W $number"
            "WEICHENVERBINDUNG" -> "WV $number"
            "DKW_KREUZUNG" -> "DKW/Kreuzung $number"
            else -> "Gl $number"
        }
    }

    fun equivalent(local: String?, remote: String?, remoteType: String?): Boolean {
        val remoteKind = type(remote, remoteType)
        if (remoteKind !in known) return local == remote
        return type(local) == remoteKind && number(local) == number(remote)
    }

    fun outgoingType(local: String?, remote: String?, remoteType: String?): String? =
        if (!remoteType.isNullOrBlank() && type(remote, remoteType) !in known && local == remote) remoteType else type(local)

    fun wireKey(local: String?, kind: String?): String? = when {
        local.isNullOrBlank() -> null
        kind == "WEICHENVERBINDUNG" -> "WVB ${number(local)}"
        kind == "WEICHE" -> "W ${number(local)}"
        else -> local
    }
}
