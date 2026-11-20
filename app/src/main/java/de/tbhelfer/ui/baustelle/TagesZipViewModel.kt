package de.tbhelfer.ui.baustelle

import android.content.Context
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.tbhelfer.server.ErrorHistory
import de.tbhelfer.server.ErrorLogStore
import de.tbhelfer.ui.bautagebuch.BautagebuchSpeicher
import de.tbhelfer.ui.bautagebuch.BtbExportPaket
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.zip.ZipFile

class TagesZipViewModel(private val savedState: SavedStateHandle) : ViewModel() {
    val speicherStatus = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    var speichernStattTeilen: Boolean
        get() = savedState["zipSave"] ?: false
        private set(value) { savedState["zipSave"] = value }
    private var schreibtDatei = false

    fun dokumentAuswahlStarten(file: java.io.File) { savedState["zipFile"] = file.absolutePath }

    fun speichern(context: Context, uri: android.net.Uri?) {
        if (schreibtDatei) return
        if (uri == null) { savedState.remove<String>("zipFile"); speicherStatus.value = "Speichern abgebrochen."; return }
        val path = savedState.get<String>("zipFile")
        val app = context.applicationContext
        schreibtDatei = true
        viewModelScope.launch {
            try {
                speicherStatus.value = "ZIP wird gespeichert …"
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val file = path?.let { java.io.File(it) }?.takeIf { it.isFile && it.length() > 0 }
                        ?: error("Exportdatei nicht mehr vorhanden")
                    val output = app.contentResolver.openOutputStream(uri, "wt") ?: error("Zieldatei nicht geöffnet")
                    output.use { target -> file.inputStream().use { input -> input.copyTo(target) } }
                }
                speicherStatus.value = "ZIP am gewählten Speicherort gespeichert."
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                protokolliere(app, error, true)
                speicherStatus.value = "ZIP konnte nicht gespeichert werden. Ziel prüfen und erneut versuchen."
            } finally {
                schreibtDatei = false
                savedState.remove<String>("zipFile")
            }
        }
    }

    val auftrag = TagesZipAuftrag(viewModelScope)

    fun starten(context: Context, item: BautagebuchSpeicher.VerlaufEintrag, speichern: Boolean = false): Boolean {
        if (schreibtDatei || savedState.get<String>("zipFile") != null ||
            auftrag.zustand.value is TagesZipAuftrag.Zustand.Laeuft ||
            auftrag.zustand.value is TagesZipAuftrag.Zustand.Fertig) return false
        speichernStattTeilen = speichern
        speicherStatus.value = null
        val app = context.applicationContext
        return auftrag.starten {
            try {
                val file = BtbExportPaket.erstellenLokalenBtbExport(app, item).zipFile
                // Reject incomplete/corrupt archives before Android shares a
                // supposedly successful daily export. Never mutate originals.
                withContext(Dispatchers.IO) {
                    ZipFile(file).use { archive ->
                        check(archive.size() > 0) { "ZIP enthält keine Dateien" }
                        val entries = archive.entries()
                        val seen = hashSetOf<String>()
                        val buffer = ByteArray(64 * 1024)
                        while (entries.hasMoreElements()) {
                            val entry = entries.nextElement()
                            check(seen.add(entry.name)) { "ZIP enthält doppelte Einträge" }
                            if (!entry.isDirectory) {
                                archive.getInputStream(entry).use { input ->
                                    while (input.read(buffer) != -1) { /* CRC is checked at stream end */ }
                                }
                            }
                        }
                    }
                }
                file
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                protokolliere(app, error, false)
                throw error
            }
        }
    }

    companion object {
        fun protokolliere(context: Context, error: Exception, teilen: Boolean) {
            val code = if (teilen) "ZIP_SHARE_FAILED" else "ZIP_EXPORT_FAILED"
            ErrorLogStore.record(context.applicationContext, ErrorHistory.Operation.ZIP, code)
            // Types and code locations preserve the original failure without raw messages,
            // paths, BTB contents, URLs or tokens in diagnostic output.
            val causes = generateSequence<Throwable>(error) { it.cause }.take(6)
            val trace = causes.joinToString("\nCaused by: ") { cause ->
                cause.javaClass.name + "\n" + cause.stackTrace.take(16).joinToString("\n") { "at $it" }
            }
            Log.e("TagesZip", "$code\n$trace")
        }
    }
}

