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
        if (RecoveryWorkspace.switchPending()) { restart(); return }
        if (!RecoveryWorkspace.blocked()) { showArchives(); return }
        status.text = "Der Server wurde wiederhergestellt. Deine Handydaten bleiben erhalten. Alte lokale Änderungen dürfen jetzt nicht ungeprüft hochgeladen werden.\n\nSichere zuerst den Handybestand und lies den Server zum Vergleich. Bei einem Fehler bleibt die Synchronisierung gesperrt."
        button("Handybestand sichern und vergleichen") { prepare() }
        button("Gesicherte Handybestände ansehen") { showArchives() }
    }
    private fun prepare() {
        if (busy) return
        busy = true
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
                    val local = RecoveryArchive.save(this@RecoveryActivity, owner, epoch)
                    local to RecoveryComparison.load(token, epoch, local)
                }
                saved = pair.first
                compared = true
                status.text = "Handybestand gesichert und geprüft.\n\n${localDescription(pair.first)}\n\n${pair.second.description}"
                button("Lokale Änderungen prüfen (${pair.first.pending.length()})") { showPending(pair.first.pending) }
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
            button("Änderungen ansehen") { showPending(pending) }
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
    private fun showPending(pending: JSONArray) {
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 16, 24, 16) }
        if (pending.length() == 0) content.addView(TextView(this).apply { text = "Keine vorgemerkten Änderungen. Weitere lokale Daten sind trotzdem im Sicherungsbestand enthalten." })
        for (i in 0 until pending.length()) {
            val row = pending.getJSONObject(i)
            val description = buildString {
                append("Änderung ${i + 1}\n")
                row.keys().forEach { key ->
                    if (key != "_table") {
                        val label = mapOf("entityType" to "Bereich", "entity_type" to "Bereich", "operation" to "Aktion", "state" to "Status", "payloadJson" to "Lokaler Inhalt", "baseVersion" to "Bisherige Serverversion", "lastError" to "Letzter Hinweis")[key] ?: key
                        val value = row.opt(key)
                        if (value != null && value != JSONObject.NULL) {
                            val readable = if (key.contains("payload", true)) runCatching { JSONObject(value.toString()).toString(2) }.getOrDefault(value.toString()) else value.toString()
                            append("$label: $readable\n")
                        }
                    }
                }
                append("Serverstand: separat gelesen; keine automatische Übernahme oder Löschung.\n")
            }
            content.addView(TextView(this).apply { text = description; setPadding(0, 0, 0, 24); setTextIsSelectable(true) })
        }
        AlertDialog.Builder(this).setTitle("Gesicherte lokale Änderungen")
            .setView(ScrollView(this).apply { addView(content) }).setPositiveButton("Schließen", null).show()
    }
    private fun title(value: String) = text(value).apply { textSize = 22f }
    private fun text(value: String) = TextView(this).apply { text = value; textSize = 16f; setPadding(0, 0, 0, 20); body.addView(this) }
    private fun button(value: String, action: () -> Unit): Button = Button(this).apply { text = value; setOnClickListener { action() }; body.addView(this) }
}
