package de.tbhelfer.ui.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import androidx.exifinterface.media.ExifInterface
import de.tbhelfer.ui.einstellungen.overlay.OverlayEinstellungenSpeicher
import java.io.File
import java.io.FileOutputStream
import de.tbhelfer.util.ImageExifUtil
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Rendert das konfigurierbare Overlay ins Foto.
 *
 * Hinweis:
 * - Das Overlay wird in die gespeicherte JPG-Datei geschrieben (Dokumentationsversion).
 * - EXIF (GPS) kann separat geschrieben werden.
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

    fun renderOverlayWennAktiv(
        context: Context,
        datei: File,
        daten: Daten,
        einstellungen: OverlayEinstellungenSpeicher.Einstellungen,
        jpegQualitaet: Int = 92
    ): Boolean {
        if (!einstellungen.aktiv) return false

        val bmp = ImageExifUtil.decodeSampledBitmapRespectingExif(datei.absolutePath, 2400, 2400, Bitmap.Config.ARGB_8888) ?: return false
        val out = bmp.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)

        val lines = baueLines(daten, einstellungen)
        if (lines.isEmpty()) {
            bmp.recycle(); out.recycle()
            return false
        }

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            val base = einstellungen.textSizeSp.toFloat()
            val adj = if (einstellungen.darstellung == OverlayEinstellungenSpeicher.Darstellung.KOMPAKT_2ZEILIG) {
                max(10f, base - 1f)
            } else base
            textSize = spToPx(context, adj)
        }
        val gpsTextPaint = Paint(textPaint).apply {
            // Patch 33: GPS bleibt im Overlay, aber kleiner/ruhiger als die Fachzeilen.
            textSize = textPaint.textSize * 0.82f
        }
        fun paintForLine(line: String): Paint =
            if (line.trimStart().startsWith("GPS", ignoreCase = true)) gpsTextPaint else textPaint

        // Text bounds bestimmen. Patch 33: dynamische Box mit Mindestbreite,
        // damit das Infofenster im Foto und im BTB/PDF lesbar bleibt.
        val paddingSp = if (einstellungen.darstellung == OverlayEinstellungenSpeicher.Darstellung.KOMPAKT_2ZEILIG) 6f else 8f
        val padding = spToPx(context, paddingSp).toInt()
        val lineGap = max(2, padding / 2)
        val maxAllowedWidth = (out.width * 0.92f).toInt()
        var maxWidth = 0
        val safeLines = lines.map { line -> ellipsize(paintForLine(line), line, maxAllowedWidth - padding * 2) }
        val lineHeights = safeLines.map { line ->
            val fm = paintForLine(line).fontMetrics
            ceil((fm.descent - fm.ascent).toDouble()).toInt().coerceAtLeast(1)
        }
        val bounds = Rect()
        safeLines.forEach { line ->
            val paint = paintForLine(line)
            paint.getTextBounds(line, 0, line.length.coerceAtLeast(0), bounds)
            maxWidth = max(maxWidth, ceil(paint.measureText(line).toDouble()).toInt())
        }
        var totalHeight = padding * 2 + lineHeights.sum()
        if (safeLines.size > 1) totalHeight += lineGap * (safeLines.size - 1)
        maxWidth += padding * 2
        if (safeLines.size >= 3) {
            val minBoxWidth = min(maxAllowedWidth, (out.width * 0.38f).toInt())
            maxWidth = max(maxWidth, minBoxWidth)
        }

        // Falls Ellipsize extrem kurz wird, lieber nicht rendern
        if (maxWidth <= padding * 2 || totalHeight <= padding * 2) {
            bmp.recycle(); out.recycle()
            return false
        }

        val (x0, y0) = berechnePosition(
            bmp = out,
            pos = einstellungen.position,
            smart = einstellungen.autoPositionSmart,
            w = out.width,
            h = out.height,
            boxW = maxWidth,
            boxH = totalHeight,
            pad = padding,
            autoKontrast = einstellungen.autoKontrast
        )

        val (bgColor, textColor) = if (einstellungen.autoKontrast) {
            autoKontrastFarben(out, x0, y0, maxWidth, totalHeight, einstellungen.alpha)
        } else {
            // Standard: weißer Text auf dunklem Hintergrund
            val a = (einstellungen.alpha.coerceIn(0.1f, 0.9f) * 255).toInt()
            (Color.argb(a, 0, 0, 0)) to Color.WHITE
        }

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bgColor }
        textPaint.color = textColor
        gpsTextPaint.color = textColor

        // Hintergrund
        canvas.drawRoundRect(
            x0.toFloat(),
            y0.toFloat(),
            (x0 + maxWidth).toFloat(),
            (y0 + totalHeight).toFloat(),
            padding.toFloat(),
            padding.toFloat(),
            bgPaint
        )

        // Text
        var y = y0 + padding.toFloat()
        for (i in safeLines.indices) {
            val l = safeLines[i]
            val paint = paintForLine(l)
            val fm = paint.fontMetrics
            y += -fm.ascent
            canvas.drawText(l, (x0 + padding).toFloat(), y, paint)
            y += fm.descent + lineGap
        }

        // zurück schreiben
        FileOutputStream(datei).use { fos ->
            out.compress(Bitmap.CompressFormat.JPEG, jpegQualitaet.coerceIn(70, 100), fos)
        }

        bmp.recycle()
        out.recycle()
        return true
    }

    private fun baueLines(
        daten: Daten,
        e: OverlayEinstellungenSpeicher.Einstellungen
    ): List<String> {
        return if (e.darstellung == OverlayEinstellungenSpeicher.Darstellung.KOMPAKT_2ZEILIG) {
            baueKompaktLines(daten, e)
        } else {
            baueNormalLines(daten, e)
        }
    }

    private fun baueNormalLines(daten: Daten, e: OverlayEinstellungenSpeicher.Einstellungen): List<String> {
        val lines = ArrayList<String>(6)

        if (e.zeigeBaustelle) {
            val base = daten.baustellenName
            if (e.zeigeUnterbaustelle && !daten.unterbaustellenName.isNullOrBlank()) {
                lines.add("$base / ${daten.unterbaustellenName}")
            } else {
                lines.add(base)
            }
        }

        if (e.zeigeStreckeBahnhof || e.zeigeGleisWeicheObjekt) {
            val s = if (e.zeigeStreckeBahnhof) daten.streckeOderBahnhof else null
            val g = if (e.zeigeGleisWeicheObjekt) daten.gleisOderWeiche else null
            val line = listOfNotNull(s, g).joinToString(" · ").trim()
            if (line.isNotBlank()) lines.add(line)
        }

        if (e.zeigeKm) {
            daten.kmText?.let { lines.add("km $it") }
        }
        if (e.zeigeGps) {
            lines.add("GPS: ${daten.gpsText}")
        }
        daten.bereichText?.takeIf { it.isNotBlank() }?.let { lines.add("Bereich: $it") }
        if (e.zeigeDatumZeit) {
            lines.add(daten.datumZeitText)
        }
        return lines
    }

    /**
     * Kompakt (2 Zeilen):
     * 1) Baustelle / Unterbaustelle (oder Strecke)
     * 2) Kurzinformationen mit Trennzeichen
     */
    private fun baueKompaktLines(daten: Daten, e: OverlayEinstellungenSpeicher.Einstellungen): List<String> {
        val line1 = run {
            if (e.zeigeBaustelle) {
                val base = daten.baustellenName
                if (e.zeigeUnterbaustelle && !daten.unterbaustellenName.isNullOrBlank()) {
                    "$base / ${daten.unterbaustellenName}"
                } else base
            } else {
                // fallback
                daten.streckeOderBahnhof
            }
        }.trim()

        val parts = ArrayList<String>(6)
        if (!e.zeigeBaustelle) {
            // wenn Baustelle nicht gezeigt wird, dann Strecke ggf. in Zeile2
            if (e.zeigeStreckeBahnhof && daten.streckeOderBahnhof.isNotBlank()) parts += daten.streckeOderBahnhof
        } else {
            // Baustelle steht schon oben, Strecke/Objekt ggf. unten
            if (e.zeigeStreckeBahnhof && daten.streckeOderBahnhof.isNotBlank()) parts += daten.streckeOderBahnhof
        }
        if (e.zeigeGleisWeicheObjekt && !daten.gleisOderWeiche.isNullOrBlank()) parts += daten.gleisOderWeiche
        if (e.zeigeKm && !daten.kmText.isNullOrBlank()) parts += "km ${daten.kmText}"
        if (e.zeigeDatumZeit) parts += daten.datumZeitText
        if (e.zeigeGps) parts += "GPS ${shortGps(daten.gpsText)}"

        // Teile zusammenfassen (nicht zu lang). Ein konkret gewählter Bereich
        // bleibt auch im Kompaktmodus eine eigene, dauerhaft eingebrannte Zeile
        // direkt oberhalb der Zeile mit Datum/Zeit.
        val line2 = parts.joinToString(" | ").trim()

        val out = ArrayList<String>(3)
        if (line1.isNotBlank()) out += line1
        daten.bereichText?.takeIf { it.isNotBlank() }?.let { out += "Bereich: $it" }
        if (line2.isNotBlank()) out += line2
        return out
    }

    private fun berechnePosition(
        bmp: Bitmap,
        pos: OverlayEinstellungenSpeicher.Position,
        smart: Boolean,
        w: Int,
        h: Int,
        boxW: Int,
        boxH: Int,
        pad: Int,
        autoKontrast: Boolean
    ): Pair<Int, Int> {
        fun coords(p: OverlayEinstellungenSpeicher.Position): Pair<Int, Int> {
            return when (p) {
                OverlayEinstellungenSpeicher.Position.LINKS_OBEN -> pad to pad
                OverlayEinstellungenSpeicher.Position.RECHTS_OBEN -> (w - boxW - pad) to pad
                OverlayEinstellungenSpeicher.Position.LINKS_UNTEN -> pad to (h - boxH - pad)
                OverlayEinstellungenSpeicher.Position.RECHTS_UNTEN -> (w - boxW - pad) to (h - boxH - pad)
                OverlayEinstellungenSpeicher.Position.AUTO -> pad to (h - boxH - pad)
            }
        }

        if (pos != OverlayEinstellungenSpeicher.Position.AUTO) return coords(pos)
        if (!smart) return coords(OverlayEinstellungenSpeicher.Position.LINKS_UNTEN)

        val kandidaten = listOf(
            OverlayEinstellungenSpeicher.Position.LINKS_OBEN,
            OverlayEinstellungenSpeicher.Position.RECHTS_OBEN,
            OverlayEinstellungenSpeicher.Position.LINKS_UNTEN,
            OverlayEinstellungenSpeicher.Position.RECHTS_UNTEN
        )

        var best = OverlayEinstellungenSpeicher.Position.LINKS_UNTEN
        var bestScore = Double.POSITIVE_INFINITY
        for (k in kandidaten) {
            val (x, y) = coords(k)
            val stats = regionStats(bmp, x, y, boxW, boxH)
            val score = if (autoKontrast) {
                // bevorzugt: wenig "Unruhe" (geringe Varianz) UND klare Entscheidung hell/dunkel
                stats.stdDev + 0.002 * (255.0 - kotlin.math.abs(stats.avg - 140.0))
            } else {
                // ohne Auto-Kontrast: dunkle Bereiche sind besser (weiß auf dunkel)
                stats.stdDev + 0.004 * stats.avg
            }
            if (score < bestScore) {
                bestScore = score
                best = k
            }
        }
        return coords(best)
    }

    private data class Stats(val avg: Double, val stdDev: Double)

    private fun regionStats(bmp: Bitmap, x: Int, y: Int, w: Int, h: Int): Stats {
        val sx0 = x.coerceIn(0, bmp.width - 1)
        val sy0 = y.coerceIn(0, bmp.height - 1)
        val sx1 = (x + w).coerceIn(0, bmp.width)
        val sy1 = (y + h).coerceIn(0, bmp.height)
        if (sx1 <= sx0 || sy1 <= sy0) return Stats(0.0, 999.0)

        val stepX = max(1, (sx1 - sx0) / 32)
        val stepY = max(1, (sy1 - sy0) / 32)

        var sum = 0.0
        var sumSq = 0.0
        var count = 0
        var yy = sy0
        while (yy < sy1) {
            var xx = sx0
            while (xx < sx1) {
                val c = bmp.getPixel(xx, yy)
                val r = Color.red(c)
                val g = Color.green(c)
                val b = Color.blue(c)
                val lum = 0.2126 * r + 0.7152 * g + 0.0722 * b
                sum += lum
                sumSq += lum * lum
                count++
                xx += stepX
            }
            yy += stepY
        }
        if (count <= 1) return Stats(sum, 0.0)
        val avg = sum / count
        val var0 = (sumSq / count) - (avg * avg)
        val std = sqrt(max(0.0, var0))
        return Stats(avg, std)
    }

    private fun autoKontrastFarben(
        bmp: Bitmap,
        x: Int,
        y: Int,
        w: Int,
        h: Int,
        alpha: Float
    ): Pair<Int, Int> {
        // sehr günstiges Sampling (max 32x32 Samples)
        val sx0 = x.coerceIn(0, bmp.width - 1)
        val sy0 = y.coerceIn(0, bmp.height - 1)
        val sx1 = (x + w).coerceIn(0, bmp.width)
        val sy1 = (y + h).coerceIn(0, bmp.height)
        if (sx1 <= sx0 || sy1 <= sy0) {
            val a = (alpha.coerceIn(0.1f, 0.9f) * 255).toInt()
            return Color.argb(a, 0, 0, 0) to Color.WHITE
        }

        val stepX = max(1, (sx1 - sx0) / 32)
        val stepY = max(1, (sy1 - sy0) / 32)

        var sum = 0.0
        var count = 0
        var yy = sy0
        while (yy < sy1) {
            var xx = sx0
            while (xx < sx1) {
                val c = bmp.getPixel(xx, yy)
                val r = Color.red(c)
                val g = Color.green(c)
                val b = Color.blue(c)
                // relative luminance
                val lum = 0.2126 * r + 0.7152 * g + 0.0722 * b
                sum += lum
                count++
                xx += stepX
            }
            yy += stepY
        }

        val avg = if (count == 0) 0.0 else sum / count
        val a = (alpha.coerceIn(0.1f, 0.9f) * 255).toInt()
        return if (avg >= 140.0) {
            // Heller Bildbereich: schwarze Schrift auf leicht hellem Feld.
            Color.argb(a, 255, 255, 255) to Color.BLACK
        } else {
            // Dunkler Bildbereich: weiße Schrift auf leicht dunklem Feld.
            Color.argb(a, 0, 0, 0) to Color.WHITE
        }
    }

    private fun shortGps(gpsText: String): String {
        // erwartet häufig "lat, lon" oder "lat lon"; sonst unverändert
        val cleaned = gpsText.trim()
        val parts = cleaned
            .replace(";", ",")
            .replace(" ", ",")
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (parts.size < 2) return cleaned
        fun fmt(s: String): String {
            val v = s.toDoubleOrNull() ?: return s
            return String.format(java.util.Locale.US, "%.5f", v)
        }
        return "${fmt(parts[0])},${fmt(parts[1])}"
    }

    private fun ellipsize(paint: Paint, text: String, maxPx: Int): String {
        if (maxPx <= 0) return ""
        if (paint.measureText(text) <= maxPx) return text
        val ell = "…"
        val ellW = paint.measureText(ell)
        var lo = 0
        var hi = text.length
        while (lo < hi) {
            val mid = (lo + hi) / 2
            val sub = text.substring(0, mid)
            if (paint.measureText(sub) + ellW <= maxPx) lo = mid + 1 else hi = mid
        }
        val cut = max(0, lo - 1)
        return text.substring(0, cut).trimEnd() + ell
    }

    private fun spToPx(context: Context, sp: Float): Float {
        return sp * context.resources.displayMetrics.scaledDensity
    }
}
