package de.tbhelfer.server.recovery

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import de.tbhelfer.server.SecureTokenStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.DateFormat
import java.util.Date

class RecoveryActivity : AppCompatActivity() {
    private lateinit var body: LinearLayout
    private lateinit var status: TextView
    private var saved: RecoveryArchive.Saved? = null
    private var serverComparison: JSONObject? = null
    private var compared = false
    private var busy = false
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(RecoveryWorkspace.wrap(newBase))
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 40, 32, 32) }
        setContentView(ScrollView(this).apply { addView(body) })
        title("Handybestand und Server")
        status = text("")
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (!busy) { if (RecoveryWorkspace.blocked()) finishAffinity() else finish() } }
        })
        if (RecoveryWorkspace.switchPending() ||
            (RecoveryWorkspace.blocked() && !RecoveryWorkspace.startedBlocked())) {
            // Quiesce the old UI/Room/DataStore/jobs before taking the snapshot too.
            status.text = "Synchronisierung angehalten. App wird für die sichere Bestandsprüfung neu gestartet …"
            restart()
            return
        }
        if (!RecoveryWorkspace.blocked()) { showArchives(); return }
        status.text = "Der Server wurde wiederhergestellt. Deine Handydaten bleiben erhalten. Alte lokale Änderungen dürfen jetzt nicht ungeprüft hochgeladen werden.\n\nSichere zuerst den Handybestand und lies den Server zum Vergleich. Bei einem Fehler bleibt die Synchronisierung gesperrt."
        button("Handybestand sichern und vergleichen") { prepare() }
        button("Gesicherte Handybestände ansehen") { showArchives() }
    }
    private fun prepare() {
        if (busy) return
        busy = true
        while (body.childCount > 4) body.removeViewAt(4)
        saved = null
        compared = false
        status.text = "Handybestand wird privat gesichert und geprüft. Danach wird der aktuelle Serverbestand ausschließlich gelesen …"
        lifecycleScope.launch {
            try {
                val pair = withContext(Dispatchers.IO) {
                    val token = SecureTokenStore(this@RecoveryActivity).load() ?: error("Geräteanmeldung fehlt. Der Handybestand bleibt erhalten.")
                    val epoch = RecoveryProtocol.probe(force = true) ?: error("Der Server meldet keinen Wiederherstellungsstand. Bitte Serverkonfiguration prüfen lassen.")
                    RecoveryWorkspace.block(epoch)
                    val owner = RecoveryProtocol.owner(token)
                    check(RecoveryWorkspace.ownerMatches(owner)) { "Diese Geräteanmeldung gehört nicht zum aktiven Handybestand. Kein automatischer Bestandswechsel möglich." }
                    val local = RecoveryArchive.save(this@RecoveryActivity, owner, epoch)
                    local to RecoveryComparison.load(token, epoch, local)
                }
                serverComparison = pair.second.server
                saved = pair.first
                compared = true
                status.text = "Handybestand gesichert und geprüft.\n\n${localDescription(pair.first)}\n\n${pair.second.description}"
                button("Lokale Änderungen prüfen (${pair.first.pending.length()})") { showPending(pair.first.pending, serverComparison) }
                val consent = CheckBox(this@RecoveryActivity).apply {
                    text = "Ich habe den Vergleich geprüft. Der alte Handybestand bleibt gesichert; seine Änderungen werden nicht automatisch hochgeladen."
                }
                body.addView(consent)
                val switch = button("Serverbestand neu einlesen") {
                    if (consent.isChecked) confirmSwitch() else status.text = "Bitte zuerst die lokalen Änderungen und den Serververgleich prüfen und die Auswahl bestätigen."
                }
                switch.isEnabled = false
                consent.setOnCheckedChangeListener { _, checked -> switch.isEnabled = checked && !busy }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                status.text = "Vergleich nicht abgeschlossen: ${error.message ?: "Bitte erneut versuchen."}\n\nDie Handydaten bleiben erhalten. Es wurde kein Bestand gewechselt."
            } finally { busy = false }
        }
    }
    private fun confirmSwitch() {
        val local = saved ?: return
        if (!compared || busy) return
        AlertDialog.Builder(this).setTitle("Serverbestand neu einlesen?")
            .setMessage("${local.pending.length()} lokale Änderung(en) bleiben im gesicherten Handybestand. Sie werden nicht automatisch übernommen. Auch lokale Fotos und Bautagebücher bleiben erhalten.\n\nDie App startet neu und lädt den wiederhergestellten Server in einen getrennten Bestand. Das Zusammenführen lokaler Ergänzungen bleibt eine bewusste spätere Aufgabe.")
            .setNegativeButton("Zurück", null)
            .setPositiveButton("Bestand wechseln") { _, _ -> switch(local) }.show()
    }
    private fun switch(local: RecoveryArchive.Saved) {
        if (busy) return
        busy = true
        status.text = "Letzte Sicherung und Anmeldung werden geprüft …"
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val token = SecureTokenStore(this@RecoveryActivity).load() ?: error("Geräteanmeldung fehlt.")
                    check(RecoveryProtocol.owner(token) == local.owner) { "Die Geräteanmeldung hat sich geändert. Vergleich neu starten." }
                    check(RecoveryProtocol.probe(force = true) == local.epoch) { "Der Serverstand hat sich geändert. Vergleich neu starten." }
                    // Seal the latest state too: background writes that finished during the first archive are retained.
                    val latest = RecoveryArchive.save(this@RecoveryActivity, local.owner, local.epoch)
                    check(latest.pending.toString() == local.pending.toString()) { "Lokale Änderungen haben sich verändert. Bitte Sicherung und Vergleich neu starten." }
                    RecoveryComparison.load(token, local.epoch, latest) // Authentication and authorized snapshot must still succeed.
                    val folder = File(RecoveryWorkspace.journal(), latest.id)
                    val manifest = JSONObject(File(folder, "manifest.json").readText())
                    check(RecoveryArchive.sha256(File(folder, "local.zip")) == manifest.getString("sha256")) { "Sicherungsprüfung fehlgeschlagen." }
                    RecoveryWorkspace.select(local.epoch, local.owner, latest.id)
                }
                restart()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                status.text = "Kein Bestandswechsel: ${error.message}. Bitte den Vergleich erneut starten."
                compared = false
            } finally { busy = false }
        }
    }
    private fun restart() {
        startActivity(Intent(this, RecoveryRestartActivity::class.java).putExtra("old_pid", android.os.Process.myPid()))
        // Old UI and singleton databases must never resume after selection.
    }
    private fun showArchives() {
        val token = SecureTokenStore(this).load()
        if (token == null) { text("Gesicherte Bestände bleiben erhalten. Zum Anzeigen ist die zugehörige Geräteanmeldung erforderlich."); return }
        val owner = RecoveryProtocol.owner(token)
        val archives = RecoveryWorkspace.journal().listFiles().orEmpty().mapNotNull { folder ->
            runCatching { JSONObject(File(folder, "manifest.json").readText()) }.getOrNull()
                ?.takeIf { it.optString("owner") == owner }?.let { folder to it }
        }.sortedByDescending { it.second.optLong("created") }
        title("Gesicherte Handybestände")
        if (archives.isEmpty()) text("Für diese Geräteanmeldung ist noch kein abgeschlossener gesicherter Bestand vorhanden.")
        archives.forEach { (folder, manifest) ->
            val pending = manifest.optJSONArray("pending") ?: JSONArray()
            text("${DateFormat.getDateTimeInstance().format(Date(manifest.optLong("created")))} · ${pending.length()} vorgemerkte Änderungen.\nOriginaldateien und Datenbankexport bleiben im privaten Sicherungsbestand.")
            button("Änderungen ansehen") {
                lifecycleScope.launch {
                    val remote = withContext(Dispatchers.IO) {
                        runCatching { JSONObject(File(folder, "server.json").readText()) }.getOrNull()
                    }
                    showPending(pending, remote)
                }
            }
            button("Sicherung exportieren") {
                lifecycleScope.launch {
                    try {
                        val file = File(folder, "local.zip")
                        check(withContext(Dispatchers.IO) { RecoveryArchive.sha256(file) } == manifest.getString("sha256")) { "Sicherungsprüfung fehlgeschlagen." }
                        val uri = FileProvider.getUriForFile(this@RecoveryActivity, "$packageName.dateianbieter", file)
                        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("application/zip")
                            .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Handybestand exportieren"))
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        status.text = "Export fehlgeschlagen: ${error.message}"
                    }
                }
            }
        }
    }
    private fun localDescription(local: RecoveryArchive.Saved): String = buildString {
        append("Handy: ${local.counts.optLong("baustelle")} Baustellen, ${local.counts.optLong("foto")} Fotozuordnungen.\n")
        append("${local.pending.length()} vorgemerkte Änderungen einschließlich Konflikten wurden gesichert. Keine davon wurde als hochgeladen markiert.")
    }
    private fun showPending(pending: JSONArray, server: JSONObject? = null) {
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 16, 24, 16) }
        if (pending.length() == 0) content.addView(TextView(this).apply { text = "Keine vorgemerkten Änderungen. Weitere lokale Daten sind trotzdem im Sicherungsbestand enthalten." })
        for (i in 0 until pending.length()) {
            val row = pending.getJSONObject(i)
            val id = row.optString("serverId").takeIf { it.isNotBlank() && it != "null" }
            val remote = if (id != null && server != null) find(server, id) else null
            val description = buildString {
                append("Änderung ${i + 1} · ${label(row.optString("entityType", row.optString("objectKind", "Eintrag")))}\n")
                append("Aktion: ${if (row.optString("operation") == "DELETE") "Löschen vorgemerkt" else "Ergänzen oder bearbeiten"}\n")
                append("Status: ${label(row.optString("state"))}\n")
                if (server == null) append("Serververgleich nicht abgeschlossen.\n")
                else if (id == null) append("Nur lokal oder noch keinem Servereintrag eindeutig zugeordnet.\n")
                else if (remote == null) append("Im gelesenen Serververgleich nicht gefunden. Das bestätigt keine Löschung; Dateien und Vermessungsdetails sind hier nicht vollständig enthalten.\n")
                else {
                    val version = listOf("current_version", "entity_version", "version").firstNotNullOfOrNull { key ->
                        if (remote.has(key)) remote.optLong(key) else null
                    }
                    val base = if (row.isNull("baseVersion")) null else row.optLong("baseVersion")
                    append("Servereintrag vorhanden.\n")
                    if (version != null && base != null) append(if (version == base)
                        "Serverversion entspricht der bisherigen Basis. Lokale Inhalte wurden noch nicht übernommen.\n"
                        else "Serverversion weicht von der bisherigen Basis ab. Manuelle Prüfung erforderlich.\n")
                    append("\nGelesener Serverinhalt:\n${readable(remote)}\n")
                }
                val payload = row.optString("payloadJson", "{}")
                append("\nGesicherter lokaler Inhalt:\n")
                append(runCatching { readable(JSONObject(payload)) }.getOrDefault(payload))
                append("\n\nKeine automatische Übernahme oder Löschung.\n")
            }
            content.addView(TextView(this).apply { text = description; setPadding(0, 0, 0, 24); setTextIsSelectable(true) })
        }
        AlertDialog.Builder(this).setTitle("Gesicherte lokale Änderungen")
            .setView(ScrollView(this).apply { addView(content) }).setPositiveButton("Schließen", null).show()
    }
    private fun find(value: Any?, id: String): JSONObject? {
        if (value is JSONObject) {
            if (value.optString("id") == id || value.optString("serverId") == id) return value
            value.keys().forEach { key -> find(value.opt(key), id)?.let { return it } }
        } else if (value is JSONArray) {
            for (i in 0 until value.length()) find(value.opt(i), id)?.let { return it }
        }
        return null
    }
    private fun readable(value: Any?, indent: String = ""): String = when (value) {
        is JSONObject -> value.keys().asSequence().joinToString("\n") { key ->
            "$indent${label(key)}: ${readable(value.opt(key), "$indent  ")}"
        }
        is JSONArray -> (0 until value.length()).joinToString("\n") { readable(value.opt(it), "$indent  ") }
        null, JSONObject.NULL -> "–"
        else -> value.toString()
    }
    private fun label(key: String): String = mapOf(
        "BTB" to "Bautagebuch", "DEFECT" to "Mangel", "ACCEPTANCE" to "Abnahme", "WORKSITE_NODE" to "Baustellenstruktur",
        "ROUTE" to "Strecke", "STATION" to "Bahnhof", "SECTION" to "Abschnitt", "OBJECT" to "Objekt",
        "PENDING" to "Ausstehend", "UPLOADING" to "Übertragung unbestätigt", "CONFLICT" to "Konflikt", "FAILED" to "Fehlgeschlagen",
        "name" to "Name", "designation" to "Bezeichnung", "status" to "Status", "route_number" to "Streckennummer",
        "station_code" to "Bahnhofskürzel", "header" to "Kopfdaten", "entries" to "Einträge", "title" to "Titel", "text" to "Text",
        "date" to "Datum", "start_time" to "Beginn", "end_time" to "Ende", "current_version" to "Serverversion"
    )[key] ?: key.replace('_', ' ').replace(Regex("([a-z])([A-Z])"), "$1 $2")
    private fun title(value: String) = text(value).apply { textSize = 22f }
    private fun text(value: String) = TextView(this).apply { text = value; textSize = 16f; setPadding(0, 0, 0, 20); body.addView(this) }
    private fun button(value: String, action: () -> Unit): Button = Button(this).apply { text = value; setOnClickListener { action() }; body.addView(this) }
}
