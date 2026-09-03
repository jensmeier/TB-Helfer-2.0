package de.tbhelfer.ui.suche

import android.content.Context
import android.os.Environment
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId


// * BTB liegen (aktuell) im selben App-Pictures-Ordner wie die Fotos, damit FileProvider passt:
 //* /Android/data/<paket>/files/Pictures/TB-Helfer/<Baustelle>/BTB/*.pdf
// */
object BtbSpeicher {

    fun basisOrdner(context: Context): File {
        val basis = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            ?: throw IllegalStateException("Kein externer App-Ordner verfügbar")
        val ordner = File(basis, "TB-Helfer")
        if (!ordner.exists()) ordner.mkdirs()
        return ordner
    }

    fun btbsFuerBaustelle(context: Context, baustellenNameOrdner: String, datumIso: String? = null): File {
        val basis = File(basisOrdner(context), "$baustellenNameOrdner/BTB")
        if (!basis.exists()) basis.mkdirs()
        if (datumIso.isNullOrBlank()) return basis
        val d = File(basis, datumIso)
        if (!d.exists()) d.mkdirs()
        return d
    }

    /**
     * A1: Ordner nur über Baustellen-ID (rename-sicher).
     *
     * Pfad:
     * /Android/data/<paket>/files/Pictures/TB-Helfer/baustelle_<id>/BTB/<yyyy-MM-dd>/
     */
    fun btbsFuerBaustelle(context: Context, baustelleId: Long, datumIso: String? = null): File {
        val top = "baustelle_${baustelleId}"
        val basis = File(basisOrdner(context), "$top/BTB")
        if (!basis.exists()) basis.mkdirs()
        if (datumIso.isNullOrBlank()) return basis
        val d = File(basis, datumIso)
        if (!d.exists()) d.mkdirs()
        return d
    }

    fun alleBtbs(context: Context): List<File> {
        val basis = basisOrdner(context)
        if (!basis.exists()) return emptyList()
        return basis.walkTopDown()
            .filter { it.isFile && it.extension.equals("pdf", ignoreCase = true) }
            .sortedByDescending { it.lastModified() }
            .toList()
    }

    fun dateFromLastModified(file: File): LocalDate {
        val instant = Instant.ofEpochMilli(file.lastModified())
        return instant.atZone(ZoneId.systemDefault()).toLocalDate()
    }
}
