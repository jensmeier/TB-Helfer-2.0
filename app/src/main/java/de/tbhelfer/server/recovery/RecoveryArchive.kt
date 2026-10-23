package de.tbhelfer.server.recovery

import android.content.Context
import android.database.Cursor
import android.util.Base64
import android.util.JsonWriter
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import androidx.work.WorkManager
import de.tbhelfer.ServiceLocator
import de.tbhelfer.server.Server1DeltaPoller
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** Private logical DB export plus original files. Never deletes, moves or edits the old stock. */
object RecoveryArchive {
    data class Saved(val id: String, val owner: String, val epoch: String, val pending: JSONArray, val counts: JSONObject)

    suspend fun save(context: Context, owner: String, epoch: String): Saved {
        Server1DeltaPoller.stop()
        WorkManager.getInstance(context).cancelAllWork().result.get()
        check(context.getExternalFilesDir(null) != null && RecoveryWorkspace.raw.getExternalFilesDirs(null).all { it != null }) {
            "App-Dateispeicher ist nicht vollständig erreichbar. Bitte Speicherkarte/Handyspeicher prüfen und erneut versuchen."
        }
        val id = UUID.randomUUID().toString()
        val folder = File(RecoveryWorkspace.journal(), id).apply { check(mkdirs()) }
        val partial = File(folder, "local.zip.partial")
        val pending = JSONArray()
        val counts = JSONObject()
        val hashes = JSONObject()
        FileOutputStream(partial).use { output ->
            // Do not close the underlying stream until after fsync.
            val zip = ZipOutputStream(output)
            exportDatabase(zip, "database/baustellen.json", ServiceLocator.datenbank, pending, counts)
            exportDatabase(zip, "database/synchronisierung.json", ServiceLocator.syncDatenbank, pending, counts)
            copyTree(zip, context.filesDir, "files", hashes, RecoveryWorkspace.generation == null)
            context.getExternalFilesDirs(null).forEachIndexed { index, root ->
                if (root != null) copyTree(zip, root, "external/$index", hashes, RecoveryWorkspace.generation == null)
            }
            // apply() updates in-memory values immediately: capture these instead of potentially stale XML.
            val prefs = JSONObject()
            val names = RecoveryWorkspace.preferenceNames.toMutableSet()
            File(RecoveryWorkspace.raw.applicationInfo.dataDir, "shared_prefs").listFiles()?.forEach { file ->
                if (file.extension == "xml") {
                    val stored = file.nameWithoutExtension
                    val prefix = RecoveryWorkspace.generation?.let { "tbw_${it}__" }
                    if (prefix != null && stored.startsWith(prefix)) names.add(stored.removePrefix(prefix))
                    else if (prefix == null && !stored.startsWith("tbw_")) names.add(stored)
                }
            }
            names.forEach { logical ->
                if (RecoveryWorkspace.globalPreference(logical)) return@forEach
                val values = JSONObject()
                context.getSharedPreferences(logical, Context.MODE_PRIVATE).all.forEach { (key, value) ->
                    values.put(key, when (value) {
                        is Set<*> -> JSONArray(value.toList())
                        null -> JSONObject.NULL
                        else -> value
                    })
                }
                prefs.put(logical, values)
            }
            put(zip, "preferences.json", prefs.toString().toByteArray(Charsets.UTF_8))
            put(zip, "file-hashes.json", hashes.toString().toByteArray(Charsets.UTF_8))
            zip.finish()
            zip.flush()
            output.fd.sync()
        }
        val sealed = File(folder, "local.zip")
        check(partial.renameTo(sealed)) { "Sicherung konnte nicht abgeschlossen werden." }
        verifyFiles(sealed, hashes)
        val metadata = JSONObject().put("id", id).put("owner", owner).put("epoch", epoch)
            .put("generation", RecoveryWorkspace.generation ?: "legacy")
            .put("created", System.currentTimeMillis()).put("sha256", sha256(sealed))
            .put("pending", pending).put("counts", counts)
        atomicJson(File(folder, "manifest.json"), metadata)
        return Saved(id, owner, epoch, pending, counts)
    }

    private suspend fun exportDatabase(zip: ZipOutputStream, name: String, database: RoomDatabase, pending: JSONArray, counts: JSONObject) {
        database.withTransaction {
            val db = database.openHelper.writableDatabase
            zip.putNextEntry(ZipEntry(name))
            val writer = JsonWriter(OutputStreamWriter(zip, Charsets.UTF_8))
            writer.beginObject().name("version").value(db.version.toLong()).name("tables").beginObject()
            val tables = mutableListOf<Pair<String, String>>()
            db.query("SELECT name, sql FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name").use { cursor ->
                while (cursor.moveToNext()) tables += cursor.getString(0) to cursor.getString(1).orEmpty()
            }
            for ((table, schema) in tables) {
                writer.name(table).beginObject().name("schema").value(schema).name("rows").beginArray()
                var count = 0L
                db.query("SELECT * FROM \"${table.replace("\"", "\"\"")}\"").use { cursor ->
                    while (cursor.moveToNext()) {
                        count++
                        val review = if (table in setOf("sync_pending_change", "sync_collab_pending")) JSONObject().put("_table", table) else null
                        writer.beginObject()
                        cursor.columnNames.forEachIndexed { index, column ->
                            writer.name(column)
                            when (cursor.getType(index)) {
                                Cursor.FIELD_TYPE_NULL -> { writer.nullValue(); review?.put(column, JSONObject.NULL) }
                                Cursor.FIELD_TYPE_INTEGER -> { val v = cursor.getLong(index); writer.value(v); review?.put(column, v) }
                                Cursor.FIELD_TYPE_FLOAT -> { val v = cursor.getDouble(index); writer.value(v); review?.put(column, v) }
                                Cursor.FIELD_TYPE_BLOB -> writer.beginObject().name("base64").value(Base64.encodeToString(cursor.getBlob(index), Base64.NO_WRAP)).endObject()
                                else -> { val v = cursor.getString(index); writer.value(v); review?.put(column, v) }
                            }
                        }
                        writer.endObject()
                        if (review != null) pending.put(review)
                    }
                }
                counts.put(table, count)
                writer.endArray().endObject()
            }
            writer.endObject().endObject()
            writer.flush() // Closing the writer would close the ZIP stream.
            zip.closeEntry()
        }
    }

    private fun copyTree(zip: ZipOutputStream, root: File, prefix: String, hashes: JSONObject, legacy: Boolean) {
        if (!root.exists()) return
        val canonical = root.canonicalFile
        root.walkTopDown().onEnter { dir ->
            !(legacy && dir.parentFile == root && dir.name in setOf("recovery-journal", "recovery-workspaces"))
        }.forEach { file ->
            if (!file.canonicalPath.startsWith(canonical.path + File.separator) && file.canonicalFile != canonical) throw IOException("Sicherung enthält einen unzulässigen Dateipfad.")
            if (file.isFile) {
                val length = file.length()
                val modified = file.lastModified()
                val name = "$prefix/${file.relativeTo(root).invariantSeparatorsPath}"
                val digest = MessageDigest.getInstance("SHA-256")
                zip.putNextEntry(ZipEntry(name))
                file.inputStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        zip.write(buffer, 0, n)
                        digest.update(buffer, 0, n)
                    }
                }
                zip.closeEntry()
                if (length != file.length() || modified != file.lastModified()) throw IOException("Handydaten ändern sich noch. Sicherung bitte erneut starten.")
                hashes.put(name, hex(digest.digest()))
            }
        }
    }
    private fun verifyFiles(file: File, hashes: JSONObject) {
        ZipFile(file).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                val digest = MessageDigest.getInstance("SHA-256")
                val crc = java.util.zip.CRC32()
                zip.getInputStream(entry).use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n); crc.update(buffer, 0, n) }
                }
                check(crc.value == entry.crc) { "Sicherungsprüfung fehlgeschlagen." }
                if (hashes.has(entry.name)) check(hashes.getString(entry.name) == hex(digest.digest())) { "Sicherungsprüfung fehlgeschlagen." }
            }
        }
    }
    fun sha256(file: File): String = file.inputStream().use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
        hex(digest.digest())
    }
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
    private fun put(zip: ZipOutputStream, name: String, bytes: ByteArray) { zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() }
    fun atomicJson(file: File, json: JSONObject) {
        val partial = File(file.parentFile, file.name + ".partial")
        FileOutputStream(partial).use { it.write(json.toString().toByteArray(Charsets.UTF_8)); it.fd.sync() }
        check(partial.renameTo(file)) { "Vergleich konnte nicht gespeichert werden." }
    }
}
