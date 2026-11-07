package de.tbhelfer.ui.baustelle

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.tbhelfer.server.ErrorHistory
import de.tbhelfer.server.ErrorLogStore
import de.tbhelfer.ui.bautagebuch.BautagebuchSpeicher
import de.tbhelfer.ui.bautagebuch.BtbExportPaket
import kotlinx.coroutines.CancellationException

class TagesZipViewModel : ViewModel() {
    val auftrag = TagesZipAuftrag(viewModelScope)

    fun starten(context: Context, item: BautagebuchSpeicher.VerlaufEintrag): Boolean {
        val app = context.applicationContext
        return auftrag.starten {
            try {
                BtbExportPaket.erstellenBaustellenExport(
                    app, item.entry.baustelleId, item.entry.baustelleName, item.datum, item.datum
                ).zipFile
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
