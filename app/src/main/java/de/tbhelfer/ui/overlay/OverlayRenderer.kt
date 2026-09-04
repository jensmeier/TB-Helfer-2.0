package de.tbhelfer.ui.overlay

import android.content.Context
import androidx.exifinterface.media.ExifInterface
import de.tbhelfer.ui.einstellungen.overlay.OverlayEinstellungenSpeicher
import java.io.File

/**
 * Kompatibilitaets-Huelle fuer den bisherigen Foto-Overlay-Aufruf.
 *
 * Ab Teststand 008 werden Fachinformationen (Baustelle, Strecke, Gleis/Weiche,
 * km, GPS, Datum/Zeit) nicht mehr in die Pixel des Original-JPG geschrieben.
 * Die Daten bleiben separat am Foto gespeichert und werden spaeter beim
 * BTB-/Foto-PDF in die Dokumentdarstellung gerendert.
 */
object OverlayRenderer {

    data class Daten(
        val baustellenName: String,
        val unterbaustellenName: String?,
        val streckeOderBahnhof: String,
        val gleisOderWeiche: String?,
        val kmText: String?,
        val gpsText: String,
        val datumZeitText: String,
        val bereichText: String? = null
    )

    fun schreibeExif(datei: File, lat: Double, lon: Double) {
        runCatching {
            val exif = ExifInterface(datei)
            exif.setLatLong(lat, lon)
            exif.saveAttributes()
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun renderOverlayWennAktiv(
        context: Context,
        datei: File,
        daten: Daten,
        einstellungen: OverlayEinstellungenSpeicher.Einstellungen,
        jpegQualitaet: Int = 92
    ): Boolean {
        // 008: Originalfoto visuell unveraendert lassen. Der Rueckgabewert false
        // sorgt ausserdem dafuer, dass EXIF-Orientierung nicht faelschlich als
        // bereits in die Pixel normalisiert markiert wird.
        return false
    }
}
