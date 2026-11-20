package de.tbhelfer.ui.bautagebuch

import android.content.Context
import de.tbhelfer.datenbank.Bildtyp
import de.tbhelfer.datenbank.FotoEntity
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * BTB-lokale Korrekturen fuer Foto-Kontextdaten, ohne den urspruenglichen Foto-Datensatz
 * oder das eingebrannte Bild zu veraendern.
 *
 * Der stabile btbId-Key ist absichtlich die Identitaet. Damit bleiben Korrekturen auch bei
 * geaenderter Schichtzeit oder geaendertem Datum beim selben fachlichen BTB.
 */
object BtbPhotoContextOverrideStore {

    data class ContextOverride(
        val firma: String = "",
        val firmaId: String? = null,
        val objektKennung: String? = null,
        val objektBereich: String? = null,
        /** null = Ort nicht ueberschreiben; 0 = Hauptbaustelle; >0 = lokale BaustellenKnotenId. */
        val ortId: Long? = null,
        /** Kanonische geräteübergreifende Unterbaustellenreferenz. */
        val worksiteNodeKey: String? = null,
        /** Fachlicher BTB-Punkt des Fotos; Datei/GPS/Aufnahmezeit bleiben unverändert. */
        val bildtyp: Bildtyp? = null,
        val kurztext: String? = null,
        val notiz: String? = null
    )

    private const val DIR_NAME = "btb_photo_context_overrides"
    private const val VERSION = 3

    fun load(context: Context, btbId: String?): MutableMap<Long, ContextOverride> {
        val id = btbId?.trim().orEmpty()
        if (id.isBlank()) return mutableMapOf()
        val file = fileFor(context, id)
        if (!file.exists()) return mutableMapOf()
        return runCatching {
            val root = JSONObject(file.readText(StandardCharsets.UTF_8))
            val obj = root.optJSONObject("overrides") ?: JSONObject()
            val out = linkedMapOf<Long, ContextOverride>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val fotoId = key.toLongOrNull() ?: continue
                val value = obj.optJSONObject(key) ?: continue
                val ortId = if (value.has("ortId") && !value.isNull("ortId")) {
                    value.optLong("ortId", Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }
                } else null
                out[fotoId] = ContextOverride(
                    firma = value.optString("firma", "").trim(),
                    firmaId = value.optString("firmaId", "").trim().takeIf { it.isNotBlank() },
                    objektKennung = value.optString("objektKennung", "")
                        .trim().takeIf { it.isNotBlank() },
                    objektBereich = value.optString("objektBereich", "")
                        .trim().takeIf { it.isNotBlank() },
                    ortId = ortId,
                    worksiteNodeKey = value.optString("worksiteNodeKey", "")
                        .trim().takeIf { it.isNotBlank() },
                    bildtyp = value.optString("bildtyp", "").trim().takeIf { it.isNotBlank() }
                        ?.let { runCatching { Bildtyp.valueOf(it) }.getOrNull() },
                    kurztext = if (value.has("kurztext")) value.optString("kurztext") else null,
                    notiz = if (value.has("notiz")) value.optString("notiz") else null
                )
            }
            out
        }.getOrDefault(mutableMapOf())
    }

    @Synchronized
    fun setOverride(
        context: Context,
        btbId: String?,
        fotoId: Long,
        value: ContextOverride?
    ) {
        val id = btbId?.trim().orEmpty()
        if (id.isBlank() || fotoId <= 0L) return
        val map = load(context, id)
        if (value == null) map.remove(fotoId) else map[fotoId] = value
        save(context, id, map)
    }

    fun clearForPhoto(context: Context, btbId: String?, fotoId: Long) =
        setOverride(context, btbId, fotoId, null)

    fun applyToFoto(context: Context, foto: FotoEntity): FotoEntity {
        val base = BtbPhotoMetadatenStore.apply(context, foto)
        val btbId = BtbFotoZuordnungSpeicher.btbId(context, foto.id) ?: return base
        val value = load(context, btbId)[foto.id] ?: return base
        return applyValue(base, value).mitMetadatenVon(base)
    }

    fun applyToFotos(context: Context, fotos: List<FotoEntity>): List<FotoEntity> {
        if (fotos.isEmpty()) return fotos
        val cache = hashMapOf<String, Map<Long, ContextOverride>>()
        return fotos.map { foto ->
            val base = BtbPhotoMetadatenStore.apply(context, foto)
            val btbId = BtbFotoZuordnungSpeicher.btbId(context, foto.id) ?: return@map base
            val map = cache.getOrPut(btbId) { load(context, btbId) }
            map[foto.id]?.let { applyValue(base, it).mitMetadatenVon(base) } ?: base
        }
    }

    @Synchronized
    fun clearIfUnchanged(context: Context, btbId: String, fotoId: Long, expected: ContextOverride?) {
        if (expected == null) return
        val values = load(context, btbId)
        if (values[fotoId] != expected) return
        values.remove(fotoId)
        save(context, btbId, values)
    }

    private fun applyValue(foto: FotoEntity, value: ContextOverride): FotoEntity {
        val nodeId = if (value.ortId != null) value.ortId.takeIf { it > 0L } else foto.baustellenKnotenId
        return foto.copy(
            baustellenKnotenId = nodeId,
            firma = value.firma.trim(),
            kurztext = value.kurztext ?: foto.kurztext,
            notiz = value.notiz ?: foto.notiz,
            bildtyp = value.bildtyp ?: foto.bildtyp,
            objektKennung = value.objektKennung?.trim()?.takeIf { it.isNotBlank() },
            objektBereich = value.objektBereich?.trim()?.takeIf { it.isNotBlank() }
        )
    }

    private fun save(context: Context, btbId: String, map: Map<Long, ContextOverride>) {
        val file = fileFor(context, btbId)
        if (map.isEmpty()) {
            runCatching { file.delete() }
            return
        }
        val root = JSONObject().apply {
            put("version", VERSION)
            put("updatedEpochMs", System.currentTimeMillis())
            put("btbId", btbId)
        }
        val obj = JSONObject()
        map.forEach { (fotoId, value) ->
            obj.put(fotoId.toString(), JSONObject().apply {
                put("firma", value.firma.trim())
                put("firmaId", value.firmaId?.trim().orEmpty())
                put("objektKennung", value.objektKennung?.trim().orEmpty())
                put("objektBereich", value.objektBereich?.trim().orEmpty())
                value.ortId?.let { put("ortId", it) }
                put("worksiteNodeKey", value.worksiteNodeKey?.trim().orEmpty())
                put("bildtyp", value.bildtyp?.name.orEmpty())
                value.kurztext?.let { put("kurztext", it) }
                value.notiz?.let { put("notiz", it) }
            })
        }
        root.put("overrides", obj)
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(root.toString(), StandardCharsets.UTF_8)
        if (file.exists()) file.delete()
        if (!tmp.renameTo(file)) {
            tmp.copyTo(file, overwrite = true)
            tmp.delete()
        }
    }

    private fun fileFor(context: Context, btbId: String): File {
        val dir = File(context.filesDir, DIR_NAME).apply { mkdirs() }
        return File(dir, "ctx_${stableToken(btbId)}.json")
    }

    private fun stableToken(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
        .take(32)
}

