package de.tbhelfer.ui.importexport

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import de.tbhelfer.datenbank.TrassenAchseEntity
import de.tbhelfer.vermessung.TrassenAnschlussLogik
import de.tbhelfer.vermessung.TrassenKmStatusLogik
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

/**
 * Offline-Pruefansicht fuer importierte Streckendaten.
 *
 * Patch44:
 * - Achsfarbe zeigt den KM-Status: GRUEN automatisch, ORANGE manuell, ROT ohne belastbare KM.
 * - GVP/Festpunkte und KM-Beschriftungen erscheinen zoomabhaengig.
 * - Achsanfang/-ende und echte Anschlussknoten werden sichtbar markiert.
 * - Die Ansicht passt sich nach jeder echten Groessenaenderung automatisch an das verfuegbare Display an.
 */
class TrassenGrafikView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var achsen: List<TrassenGrafikAchse> = emptyList()
    private var festpunkte: List<TrassenGrafikFestpunkt> = emptyList()
    private var festpunkteSichtbar: Boolean = true
    private var sichtbareAchseIds: Set<Long>? = null
    private var sichtbarePunktArten: Set<String>? = null
    private var legendeSichtbar: Boolean = true
    private var sichtbareTypen: Set<String> = setOf(
        TrassenAchseEntity.ACHSTYP_GLEIS,
        TrassenAchseEntity.ACHSTYP_WEICHE,
        TrassenAchseEntity.ACHSTYP_WEICHENVERBINDUNG,
        TrassenAchseEntity.ACHSTYP_UNBEKANNT
    )
    private var bounds: RectF? = null
    private var scale = 1f
    private var fitScale = 1f
    private var offsetX = 0f
    private var offsetY = 0f
    private var needsFit = true
    private var userTransformAktiv = false
    private var knoten: List<TrassenGrafikKnoten> = emptyList()
    private var onAchseInfoRequested: ((TrassenGrafikAchse) -> Unit)? = null
    private var onAchsePunktRequested: ((TrassenGrafikAchse, Double) -> Unit)? = null
    private var onFestpunktInfoRequested: ((TrassenGrafikFestpunkt) -> Unit)? = null
    private var onFestpunkteInfoRequested: ((List<TrassenGrafikFestpunkt>) -> Unit)? = null
    private var festpunktLabelTreffer: List<FestpunktLabelTreffer> = emptyList()
    private var onAchsenLongPressRequested: ((List<TrassenGrafikAchse>) -> Unit)? = null
    private var onAchsenLongPressMitStationRequested: ((List<Pair<TrassenGrafikAchse, Double>>) -> Unit)? = null
    private var aktiveAchseId: Long? = null
    private var ankerProjektion: TrassenGrafikProjektion? = null
    private var aktiverFestpunkt: TrassenGrafikFestpunkt? = null
    private var objektMarker: List<TrassenGrafikMarker> = emptyList()

    private val darkMode: Boolean
        get() = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    private val achsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val punktPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val punktRandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 30f
        style = Paint.Style.FILL
    }
    private val labelBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val factor = detector.scaleFactor.coerceIn(0.7f, 1.4f)
            val oldScale = scale
            val newScale = (scale * factor).coerceIn(0.002f, 160f)
            val realFactor = newScale / oldScale
            val fx = detector.focusX
            val fy = detector.focusY
            offsetX = fx - (fx - offsetX) * realFactor
            offsetY = fy - (fy - offsetY) * realFactor
            scale = newScale
            userTransformAktiv = true
            invalidate()
            return true
        }
    })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            offsetX -= distanceX
            offsetY -= distanceY
            userTransformAktiv = true
            invalidate()
            return true
        }

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            val punktTreffer = findeFestpunkteBeiBildpunkt(e.x, e.y)
            if (punktTreffer.isNotEmpty()) {
                onFestpunkteInfoRequested?.let { listener ->
                    listener.invoke(punktTreffer)
                    return true
                }
                onFestpunktInfoRequested?.let { listener ->
                    listener.invoke(punktTreffer.first())
                    return true
                }
            }
            val hitMitStation = findeAchseUndStationBeiBildpunkt(e.x, e.y)
            return if (hitMitStation != null) {
                val punktListener = onAchsePunktRequested
                if (punktListener != null) {
                    punktListener.invoke(hitMitStation.first, hitMitStation.second)
                } else {
                    onAchseInfoRequested?.invoke(hitMitStation.first)
                }
                true
            } else false
        }

        override fun onLongPress(e: MotionEvent) {
            val kandidatenMitStation = findeAchsenUndStationBeiBildpunkt(e.x, e.y)
                .map { it.first to it.second }
                .distinctBy { it.first.achseId }
            if (kandidatenMitStation.isEmpty()) return
            val mitStation = onAchsenLongPressMitStationRequested
            if (mitStation != null) {
                mitStation.invoke(kandidatenMitStation)
            } else {
                onAchsenLongPressRequested?.invoke(kandidatenMitStation.map { it.first })
            }
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            fitToContent()
            return true
        }
    })

    fun setAchsen(neueAchsen: List<TrassenGrafikAchse>) {
        achsen = neueAchsen
        bounds = berechneAktuelleBounds()
        knoten = berechneAnschlussKnoten(sichtbareAchsen())
        needsFit = true
        userTransformAktiv = false
        requestLayout()
        invalidate()
    }

    fun setFestpunkte(neueFestpunkte: List<TrassenGrafikFestpunkt>) {
        festpunkte = neueFestpunkte
        bounds = berechneAktuelleBounds()
        needsFit = true
        invalidate()
    }

    fun setFestpunkteSichtbar(sichtbar: Boolean) {
        festpunkteSichtbar = sichtbar
        bounds = berechneAktuelleBounds()
        needsFit = true
        invalidate()
    }

    fun setSichtbareAchseIds(ids: Set<Long>?) {
        sichtbareAchseIds = ids
        bounds = berechneAktuelleBounds()
        knoten = berechneAnschlussKnoten(sichtbareAchsen())
        needsFit = true
        userTransformAktiv = false
        requestLayout()
        invalidate()
    }

    fun setSichtbarePunktArten(arten: Set<String>?) {
        sichtbarePunktArten = arten
        bounds = berechneAktuelleBounds()
        needsFit = true
        invalidate()
    }

    fun setLegendeSichtbar(sichtbar: Boolean) {
        legendeSichtbar = sichtbar
        invalidate()
    }

    fun setSichtbareTypen(typen: Set<String>) {
        sichtbareTypen = typen
        bounds = berechneAktuelleBounds()
        knoten = berechneAnschlussKnoten(sichtbareAchsen())
        needsFit = true
        userTransformAktiv = false
        requestLayout()
        invalidate()
    }

    fun setOnAchseInfoRequested(listener: ((TrassenGrafikAchse) -> Unit)?) {
        onAchseInfoRequested = listener
    }

    fun setOnFestpunktInfoRequested(listener: ((TrassenGrafikFestpunkt) -> Unit)?) {
        onFestpunktInfoRequested = listener
    }

    fun setOnFestpunkteInfoRequested(listener: ((List<TrassenGrafikFestpunkt>) -> Unit)?) {
        onFestpunkteInfoRequested = listener
    }

    fun setOnAchsenLongPressRequested(listener: ((List<TrassenGrafikAchse>) -> Unit)?) {
        onAchsenLongPressRequested = listener
    }

    /** Langer Druck mit der zum Treffer gehoerenden TRA-Station; wichtig bei ueberlagerten Achsen. */
    fun setOnAchsenLongPressMitStationRequested(listener: ((List<Pair<TrassenGrafikAchse, Double>>) -> Unit)?) {
        onAchsenLongPressMitStationRequested = listener
    }

    /** Auswahlmodus fuer Start-/Endpunkte: Tippen auf eine Linie liefert Achse + interpolierte TRA-Station. */
    fun setOnAchsePunktRequested(listener: ((TrassenGrafikAchse, Double) -> Unit)?) {
        onAchsePunktRequested = listener
    }

    /** Hervorhebung der fachlich festgelegten Zielachse im KM-Anker-Auswahlmodus. */
    fun setAktiveAchseId(achseId: Long?) {
        aktiveAchseId = achseId
        invalidate()
    }

    /** Zeigt die Projektion eines gewählten Vermessungspunktes auf die Ziel-TRA. */
    fun setAnkerProjektion(projektion: TrassenGrafikProjektion?) {
        ankerProjektion = projektion
        invalidate()
    }

    /** Temporäre Auswahlmarkierung; Blau ist ausschließlich Auswahl/Markierung. */
    fun setAktiverFestpunkt(punkt: TrassenGrafikFestpunkt?) {
        aktiverFestpunkt = punkt
        invalidate()
    }

    /** Sichtbare Fachpunkte des aktuell bearbeiteten Objekts, z. B. WA/WE/WEZ oder A/B/C/D. */
    fun setObjektMarker(marker: List<TrassenGrafikMarker>) {
        objektMarker = marker
        invalidate()
    }

    fun fitToContent() {
        val b = bounds ?: return
        if (width <= 0 || height <= 0) return
        val pad = 42f
        val dx = max(1f, b.width())
        val dy = max(1f, b.height())
        val sx = (width - pad * 2f) / dx
        val sy = (height - pad * 2f) / dy
        scale = min(sx, sy).coerceAtLeast(0.002f)
        fitScale = scale.coerceAtLeast(0.000001f)
        val contentW = dx * scale
        val contentH = dy * scale
        offsetX = (width - contentW) / 2f
        offsetY = (height - contentH) / 2f
        needsFit = false
        userTransformAktiv = false
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        val ersteGroesse = oldw <= 0 || oldh <= 0
        val deutlichGeaendert = !ersteGroesse && (
            abs(w - oldw) > max(8, oldw / 50) || abs(h - oldh) > max(8, oldh / 50)
        )
        // Wichtig fuer Pixel/Poco und andere Seitenverhaeltnisse: Die erste Dialoggroesse
        // kann sich nach WindowInsets/Cutout noch einmal aendern. Solange der Nutzer nicht
        // selbst gezoomt/verschoben hat, wird dann erneut sauber auf den echten Restplatz gefittet.
        if (needsFit || ersteGroesse || (deutlichGeaendert && !userTransformAktiv)) {
            post { fitToContent() }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> parent?.requestDisallowInterceptTouchEvent(true)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> parent?.requestDisallowInterceptTouchEvent(false)
        }
        scaleDetector.onTouchEvent(event)
        if (!scaleDetector.isInProgress) gestureDetector.onTouchEvent(event)
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val isDark = darkMode
        canvas.drawColor(if (isDark) Color.rgb(18, 18, 18) else Color.rgb(250, 250, 250))

        val gefilterteAchsen = sichtbareAchsen()
        val gefiltertePunkte = if (festpunkteSichtbar) sichtbareFestpunkte() else emptyList()
        val b = bounds
        if (b == null || (gefilterteAchsen.isEmpty() && gefiltertePunkte.isEmpty())) {
            textPaint.color = if (isDark) Color.WHITE else Color.BLACK
            textPaint.textSize = sp(16f)
            if (achsen.isEmpty() && festpunkte.isEmpty()) {
                canvas.drawText("Keine berechneten Achspunkte vorhanden.", dpF(18f), dpF(36f), textPaint)
                canvas.drawText("Erst ZIP importieren und Daten berechnen.", dpF(18f), dpF(62f), textPaint)
            } else {
                canvas.drawText("In der aktuellen Anzeige ist nichts sichtbar.", dpF(18f), dpF(36f), textPaint)
            }
            return
        }
        if (needsFit) fitToContent()

        zeichneRaster(canvas, b, isDark)
        gefilterteAchsen.sortedBy { typReihenfolge(it.achsTyp) }.forEach { achse ->
            zeichneAchse(canvas, achse, isDark)
        }
        zeichneAchsendenUndKnoten(canvas, b, isDark)
        zeichneFestpunkte(canvas, b, isDark)
        zeichneObjektMarker(canvas, b, isDark)
        zeichneAnkerProjektion(canvas, b, isDark)
        if (legendeSichtbar) zeichneLegende(canvas, isDark)
    }

    private fun zoomFaktor(): Float = (scale / fitScale.coerceAtLeast(0.000001f)).coerceAtLeast(0.01f)

    private fun zeichneRaster(canvas: Canvas, b: RectF, isDark: Boolean) {
        val gridStep = when {
            scale > 6f -> 10.0
            scale > 2.5f -> 25.0
            scale > 1f -> 50.0
            scale > 0.4f -> 100.0
            else -> 250.0
        }
        gridPaint.color = if (isDark) Color.argb(45, 255, 255, 255) else Color.argb(35, 0, 0, 0)
        val startX = kotlin.math.floor(b.left / gridStep).toInt() * gridStep
        var x = startX
        while (x <= b.right) {
            val sx = sx(x.toFloat(), b)
            canvas.drawLine(sx, 0f, sx, height.toFloat(), gridPaint)
            x += gridStep
        }
        val startY = kotlin.math.floor(b.top / gridStep).toInt() * gridStep
        var y = startY
        while (y <= b.bottom) {
            val sy = sy(y.toFloat(), b)
            canvas.drawLine(0f, sy, width.toFloat(), sy, gridPaint)
            y += gridStep
        }
    }

    private fun zeichneAchse(canvas: Canvas, achse: TrassenGrafikAchse, isDark: Boolean) {
        val b = bounds ?: return
        if (achse.punkte.size < 2) return

        // Patch45ZQ: Weichen und vorhandene WV/WVB-Achsen sind in der
        // Vermessungsgrafik bewusst blau. WV bleibt gestrichelt, damit Weiche
        // und Weichenverbindung trotz gleicher Grundfarbe unterscheidbar sind.
        // Normale Gleis-/Streckenachsen behalten weiterhin ihre KM-Statusfarbe.
        val statusFarbe = when {
            achse.achsTyp == TrassenAchseEntity.ACHSTYP_WEICHE ||
                achse.achsTyp == TrassenAchseEntity.ACHSTYP_WEICHENVERBINDUNG -> weichenBlau(isDark)
            achse.neutralDarstellung -> if (isDark) Color.rgb(115, 115, 115) else Color.rgb(145, 145, 145)
            else -> farbeFuerKmStatus(achse.kmStatus, isDark)
        }
        achsPaint.color = statusFarbe
        // Patch45ZU: Betriebliche Abschnitte bewusst deutlich breiter als die Roh-TRA.
        // Dadurch sind z. B. Orange (manuelle KM) und eine darunterliegende rote Rohachse
        // auch auf hochaufloesenden Displays klar zu unterscheiden. Alle Werte in dp.
        val basisBreiteDp = when (achse.achsTyp) {
            TrassenAchseEntity.ACHSTYP_GLEIS -> 2.8f
            TrassenAchseEntity.ACHSTYP_WEICHE -> 4.2f
            TrassenAchseEntity.ACHSTYP_WEICHENVERBINDUNG -> 3.8f
            TrassenAchseEntity.ACHSTYP_STRECKE -> 2.3f
            else -> 2.3f
        }
        val breiteDp = when {
            achse.manuellerAbschnittId != null && achse.achseId == aktiveAchseId -> 9.0f
            achse.manuellerAbschnittId != null -> 7.0f
            achse.achseId == aktiveAchseId -> maxOf(5.5f, basisBreiteDp + 2.0f)
            else -> basisBreiteDp
        }
        achsPaint.strokeWidth = dpF(breiteDp)
        achsPaint.pathEffect = if (achse.achsTyp == TrassenAchseEntity.ACHSTYP_WEICHENVERBINDUNG) {
            DashPathEffect(floatArrayOf(16f, 10f), 0f)
        } else null

        val path = Path()
        val first = achse.punkte.first()
        path.moveTo(sx(first.x, b), sy(first.y, b))
        achse.punkte.drop(1).forEach { p -> path.lineTo(sx(p.x, b), sy(p.y, b)) }
        canvas.drawPath(path, achsPaint)
        achsPaint.pathEffect = null

        if (zoomFaktor() >= 1.8f) {
            punktPaint.color = statusFarbe
            val step = max(1, achse.punkte.size / 120)
            achse.punkte.forEachIndexed { index, p ->
                if (index % step == 0) canvas.drawCircle(sx(p.x, b), sy(p.y, b), dpF(1.8f), punktPaint)
            }
        }

        zeichneManuelleAnker(canvas, achse, b, isDark)
        zeichneAbschnittsGrenzen(canvas, achse, b, isDark)
        if (zoomFaktor() >= 2.8f) zeichneKmBeschriftungen(canvas, achse, b, isDark)
        if (achse.labelSichtbar) zeichneLabel(canvas, achse, isDark)
    }

    private fun zeichneManuelleAnker(canvas: Canvas, achse: TrassenGrafikAchse, b: RectF, isDark: Boolean) {
        if (achse.manuelleAnker.isEmpty()) return
        achse.manuelleAnker.forEach { anker ->
            val p = achse.punkte.minByOrNull { abs(it.station - anker.station) } ?: return@forEach
            val x = sx(p.x, b)
            val y = sy(p.y, b)
            punktPaint.color = if (isDark) Color.rgb(255, 183, 77) else Color.rgb(230, 126, 0)
            punktRandPaint.color = if (isDark) Color.WHITE else Color.BLACK
            canvas.drawCircle(x, y, 9f, punktPaint)
            canvas.drawCircle(x, y, 9f, punktRandPaint)
            val kurz = "P${anker.punktNummer}"
            textPaint.textSize = sp(11f)
            textPaint.color = if (isDark) Color.BLACK else Color.WHITE
            canvas.drawText(kurz, x - 8f, y + 7f, textPaint)
            if (zoomFaktor() >= 3.5f) {
                val detailName = anker.label?.takeIf { it.isNotBlank() } ?: kurz
                val text = "$detailName · " + String.format(java.util.Locale.GERMANY, "%.3f", anker.kmMeter / 1000.0)
                textPaint.textSize = sp(12f)
                textPaint.color = if (isDark) Color.WHITE else Color.rgb(20, 20, 20)
                val w = textPaint.measureText(text)
                labelBgPaint.color = if (isDark) Color.argb(190, 0, 0, 0) else Color.argb(220, 255, 255, 255)
                canvas.drawRoundRect(x + 11f, y - 24f, x + w + 21f, y + 5f, 6f, 6f, labelBgPaint)
                canvas.drawText(text, x + 16f, y - 2f, textPaint)
            }
        }
    }

    private fun zeichneAbschnittsGrenzen(canvas: Canvas, achse: TrassenGrafikAchse, b: RectF, isDark: Boolean) {
        if (achse.abschnittStartStation == null && achse.abschnittEndeStation == null) return

        fun zeichne(station: Double?, kuerzel: String) {
            val s = station ?: return
            val p = achse.punkte.minByOrNull { abs(it.station - s) } ?: return
            val x = sx(p.x, b)
            val y = sy(p.y, b)
            val radius = dpF(12f)
            punktPaint.color = if (isDark) Color.WHITE else Color.rgb(35, 35, 35)
            canvas.drawCircle(x, y, radius, punktPaint)
            punktRandPaint.color = if (isDark) Color.BLACK else Color.WHITE
            punktRandPaint.strokeWidth = dpF(1.5f)
            canvas.drawCircle(x, y, radius, punktRandPaint)
            punktRandPaint.strokeWidth = 2f

            textPaint.textSize = sp(11f)
            textPaint.color = if (isDark) Color.BLACK else Color.WHITE
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(kuerzel, x, y + dpF(4f), textPaint)
            textPaint.textAlign = Paint.Align.LEFT
        }

        zeichne(achse.abschnittStartStation, "G1")
        zeichne(achse.abschnittEndeStation, "G2")
    }

    private fun zeichneObjektMarker(canvas: Canvas, b: RectF, isDark: Boolean) {
        val sichtbarIds = sichtbareAchseIds
        objektMarker
            .filter { marker -> sichtbarIds == null || marker.achseId == null || marker.achseId in sichtbarIds }
            .forEach { marker ->
                val x = sx(marker.x, b)
                val y = sy(marker.y, b)
                if (x < -40f || x > width + 40f || y < -40f || y > height + 40f) return@forEach
                val radius = dpF(if (marker.aktiv) 11f else 9f)
                punktPaint.color = if (marker.aktiv) {
                    if (isDark) Color.rgb(100, 181, 246) else Color.rgb(25, 118, 210)
                } else {
                    if (isDark) Color.rgb(238, 238, 238) else Color.rgb(55, 55, 55)
                }
                punktRandPaint.color = if (isDark) Color.BLACK else Color.WHITE
                punktRandPaint.strokeWidth = dpF(1.5f)
                canvas.drawCircle(x, y, radius, punktPaint)
                canvas.drawCircle(x, y, radius, punktRandPaint)
                punktRandPaint.strokeWidth = 2f

                textPaint.textAlign = Paint.Align.LEFT
                textPaint.textSize = sp(12f)
                textPaint.color = if (isDark) Color.WHITE else Color.rgb(20, 20, 20)
                val label = marker.label
                val fm = textPaint.fontMetrics
                val padX = dpF(5f)
                val padY = dpF(3f)
                val w = textPaint.measureText(label) + padX * 2f
                val h = fm.descent - fm.ascent + padY * 2f
                val left = (x + radius + dpF(4f)).coerceAtMost((width - w - dpF(2f)).coerceAtLeast(dpF(2f)))
                val top = (y - h / 2f).coerceIn(dpF(2f), (height - h - dpF(2f)).coerceAtLeast(dpF(2f)))
                labelBgPaint.color = if (isDark) Color.argb(205, 0, 0, 0) else Color.argb(225, 255, 255, 255)
                canvas.drawRoundRect(left, top, left + w, top + h, dpF(5f), dpF(5f), labelBgPaint)
                canvas.drawText(label, left + padX, top + padY - fm.ascent, textPaint)
            }
        textPaint.textAlign = Paint.Align.LEFT
    }

    private fun zeichneKmBeschriftungen(canvas: Canvas, achse: TrassenGrafikAchse, b: RectF, isDark: Boolean) {
        val mitKm = achse.punkte.filter { it.kmMeter?.isFinite() == true }
        if (mitKm.size < 2) return
        val zoom = zoomFaktor()
        val schrittM = when {
            zoom >= 12f -> 20.0
            zoom >= 7f -> 50.0
            else -> 100.0
        }
        val benutzt = mutableSetOf<Long>()
        var labels = 0
        for (p in mitKm) {
            val km = p.kmMeter ?: continue
            val bucket = round(km / schrittM).toLong()
            if (!benutzt.add(bucket)) continue
            if (abs(km - bucket * schrittM) > max(2.5, schrittM * 0.08)) continue
            val x = sx(p.x, b)
            val y = sy(p.y, b)
            if (x < -30f || x > width + 30f || y < -30f || y > height + 30f) continue
            val text = "km " + String.format(java.util.Locale.GERMANY, "%.3f", km / 1000.0)
            textPaint.textSize = sp(12f)
            textPaint.color = if (isDark) Color.WHITE else Color.rgb(30, 30, 30)
            val w = textPaint.measureText(text)
            labelBgPaint.color = if (isDark) Color.argb(190, 0, 0, 0) else Color.argb(220, 255, 255, 255)
            canvas.drawRoundRect(x + 5f, y - 23f, x + w + 15f, y + 5f, 6f, 6f, labelBgPaint)
            canvas.drawText(text, x + 10f, y - 2f, textPaint)
            labels++
            if (labels >= 35) break
        }
    }

    private fun zeichneAchsendenUndKnoten(canvas: Canvas, b: RectF, isDark: Boolean) {
        val zoom = zoomFaktor()
        if (zoom < 1.15f) return

        // Freie Achsenden werden erst beim naeheren Zoom sichtbar. Anschlussknoten bleiben
        // deutlich erkennbar, weil dort Gleise/Weichen/WV zusammentreffen oder auseinandergehen.
        if (zoom >= 2.0f) {
            sichtbareAchsen().forEach { achse ->
                if (achse.punkte.isEmpty()) return@forEach
                val start = achse.punkte.first()
                val ende = achse.punkte.last()
                zeichneAchsEndeSymbol(canvas, sx(start.x, b), sy(start.y, b), istStart = true, isDark = isDark)
                if (achse.punkte.size > 1) {
                    zeichneAchsEndeSymbol(canvas, sx(ende.x, b), sy(ende.y, b), istStart = false, isDark = isDark)
                }
            }
        }

        knoten.forEach { knotenPunkt ->
            val x = sx(knotenPunkt.x, b)
            val y = sy(knotenPunkt.y, b)
            if (x < -30f || x > width + 30f || y < -30f || y > height + 30f) return@forEach

            val r = if (zoom >= 4f) 9f else 7f
            val path = Path().apply {
                moveTo(x, y - r)
                lineTo(x + r, y)
                lineTo(x, y + r)
                lineTo(x - r, y)
                close()
            }
            punktPaint.color = if (isDark) Color.rgb(255, 235, 59) else Color.rgb(245, 166, 35)
            punktRandPaint.color = if (isDark) Color.BLACK else Color.rgb(45, 45, 45)
            canvas.drawPath(path, punktPaint)
            canvas.drawPath(path, punktRandPaint)

            if (zoom >= 5.0f) {
                val text = knotenPunkt.labels.take(3).joinToString(" ↔ ")
                textPaint.textSize = sp(12f)
                textPaint.color = if (isDark) Color.WHITE else Color.rgb(20, 20, 20)
                val w = textPaint.measureText(text)
                labelBgPaint.color = if (isDark) Color.argb(195, 0, 0, 0) else Color.argb(225, 255, 255, 255)
                canvas.drawRoundRect(x + 9f, y - 23f, x + w + 19f, y + 5f, 6f, 6f, labelBgPaint)
                canvas.drawText(text, x + 14f, y - 2f, textPaint)
            }
        }
    }

    private fun zeichneAchsEndeSymbol(canvas: Canvas, x: Float, y: Float, istStart: Boolean, isDark: Boolean) {
        val r = 5.5f
        punktRandPaint.strokeWidth = 2f
        punktRandPaint.color = if (isDark) Color.WHITE else Color.rgb(45, 45, 45)
        if (istStart) {
            punktPaint.color = if (isDark) Color.rgb(40, 40, 40) else Color.WHITE
            canvas.drawCircle(x, y, r, punktPaint)
            canvas.drawCircle(x, y, r, punktRandPaint)
        } else {
            punktPaint.color = if (isDark) Color.rgb(220, 220, 220) else Color.rgb(45, 45, 45)
            canvas.drawRect(x - r, y - r, x + r, y + r, punktPaint)
            canvas.drawRect(x - r, y - r, x + r, y + r, punktRandPaint)
        }
    }

    private fun berechneAnschlussKnoten(achsen: List<TrassenGrafikAchse>): List<TrassenGrafikKnoten> {
        if (achsen.size < 2) return emptyList()
        val toleranzM = 2.5f
        val fachAchsen = achsen.map { achse ->
            TrassenAnschlussLogik.Achse(
                id = achse.achseId,
                name = achse.label.takeIf(String::isNotBlank) ?: achse.name,
                achsTyp = achse.achsTyp,
                punkte = achse.punkte.map { punkt ->
                    TrassenAnschlussLogik.Punkt(
                        station = punkt.station,
                        x = punkt.x.toDouble(),
                        y = punkt.y.toDouble()
                    )
                }
            )
        }

        val roh = TrassenAnschlussLogik.findeAnschluesse(
            achsen = fachAchsen,
            toleranzM = toleranzM.toDouble()
        ).map { anschluss ->
            TrassenGrafikKnoten(
                x = anschluss.x.toFloat(),
                y = anschluss.y.toFloat(),
                labels = listOf(anschluss.a.achseName, anschluss.b.achseName).distinct()
            )
        }

        // Mehrere paarweise Anschluesse koennen denselben realen Weichen-/DKW-Knoten
        // beschreiben. Fuer die Anzeige bleiben sie zu einem gemeinsamen Diamanten gebuendelt.
        val zusammen = mutableListOf<TrassenGrafikKnoten>()
        roh.forEach { kandidat ->
            val index = zusammen.indexOfFirst { abstand(it.x, it.y, kandidat.x, kandidat.y) <= toleranzM }
            if (index < 0) {
                zusammen += kandidat
            } else {
                val alt = zusammen[index]
                zusammen[index] = TrassenGrafikKnoten(
                    x = (alt.x + kandidat.x) / 2f,
                    y = (alt.y + kandidat.y) / 2f,
                    labels = (alt.labels + kandidat.labels).distinct()
                )
            }
        }
        return zusammen
    }

    private fun abstand(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        return kotlin.math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
    }

    private fun zeichneFestpunkte(canvas: Canvas, b: RectF, isDark: Boolean) {
        val sichtbarePunkte = sichtbareFestpunkte()
        festpunktLabelTreffer = emptyList()
        if (!festpunkteSichtbar || sichtbarePunkte.isEmpty()) return
        val zoom = zoomFaktor()
        val labelTreffer = mutableListOf<FestpunktLabelTreffer>()
        val maxLabels = when {
            zoom >= 8f -> 160
            zoom >= 5.5f -> 110
            zoom >= 3.5f -> 70
            zoom >= 2.2f -> 40
            else -> 0
        }
        var labelCount = 0
        sichtbarePunkte.forEach { punkt ->
            val x = sx(punkt.x, b)
            val y = sy(punkt.y, b)
            if (x < -dpF(24f) || x > width + dpF(24f) || y < -dpF(24f) || y > height + dpF(24f)) return@forEach

            punktPaint.color = when {
                punkt.art.equals("GVP", ignoreCase = true) || punkt.art.equals("FESTPUNKT", ignoreCase = true) ->
                    if (isDark) Color.rgb(255, 235, 59) else Color.rgb(180, 140, 0)
                punkt.art.equals("WEICHENPUNKT", ignoreCase = true) ->
                    if (isDark) Color.rgb(128, 222, 234) else Color.rgb(0, 121, 107)
                else -> if (isDark) Color.rgb(206, 147, 216) else Color.rgb(123, 31, 162)
            }
            punktRandPaint.color = if (isDark) Color.BLACK else Color.WHITE
            punktRandPaint.strokeWidth = dpF(1.2f)
            val radius = when {
                zoom >= 6f -> dpF(6f)
                zoom >= 3f -> dpF(5f)
                else -> dpF(3.5f)
            }
            val basisPunktFarbe = punktPaint.color
            if (punkt == aktiverFestpunkt) {
                punktPaint.color = if (isDark) Color.rgb(100, 181, 246) else Color.rgb(25, 118, 210)
                canvas.drawCircle(x, y, radius + dpF(4.5f), punktPaint)
                punktPaint.color = basisPunktFarbe
            }
            canvas.drawCircle(x, y, radius, punktPaint)
            canvas.drawCircle(x, y, radius, punktRandPaint)

            if (maxLabels > 0 && labelCount < maxLabels) {
                val basis = punkt.label.ifBlank { punkt.art }
                val kmText = if (zoom >= 5.5f) {
                    punkt.kmMeter?.let { " · km " + String.format(java.util.Locale.GERMANY, "%.3f", it / 1000.0) }.orEmpty()
                } else {
                    ""
                }
                val text = basis + kmText
                val schriftSp = when {
                    zoom >= 8f -> 16f
                    zoom >= 5.5f -> 15f
                    zoom >= 3.5f -> 13f
                    else -> 11f
                }
                textPaint.textSize = sp(schriftSp)
                textPaint.color = if (isDark) Color.WHITE else Color.rgb(20, 20, 20)
                val fm = textPaint.fontMetrics
                val gap = dpF(6f)
                val padX = dpF(6f)
                val padY = dpF(4f)
                val labelWidth = padX + textPaint.measureText(text) + padX
                val labelHeight = (fm.descent - fm.ascent) + padY * 2f
                val desiredLeft = x + gap
                val left = desiredLeft.coerceIn(dpF(2f), (width - labelWidth - dpF(2f)).coerceAtLeast(dpF(2f)))
                val desiredTop = y - labelHeight / 2f
                val top = desiredTop.coerceIn(dpF(2f), (height - labelHeight - dpF(2f)).coerceAtLeast(dpF(2f)))
                val right = left + labelWidth
                val bottom = top + labelHeight
                val baseline = top + padY - fm.ascent
                val rect = RectF(left, top, right, bottom)
                labelBgPaint.color = if (isDark) Color.argb(190, 0, 0, 0) else Color.argb(220, 255, 255, 255)
                canvas.drawRoundRect(rect, dpF(5f), dpF(5f), labelBgPaint)
                canvas.drawText(text, left + padX, baseline, textPaint)
                labelTreffer += FestpunktLabelTreffer(punkt, RectF(rect).apply { inset(-dpF(4f), -dpF(4f)) })
                labelCount++
            }
        }
        festpunktLabelTreffer = labelTreffer
    }

    private fun zeichneLabel(canvas: Canvas, achse: TrassenGrafikAchse, isDark: Boolean) {
        val b = bounds ?: return
        if (achse.punkte.isEmpty()) return
        val label = labelFuerAktuellenZoom(achse)
        textPaint.textSize = sp(when {
            zoomFaktor() > 6f -> 17f
            zoomFaktor() > 2f -> 15f
            else -> 13f
        })
        textPaint.color = if (isDark) Color.WHITE else Color.rgb(20, 20, 20)
        val w = textPaint.measureText(label)
        val anchor = sichtbarerLabelAnker(achse, b)
        val maxX = (width - w - 12f).coerceAtLeast(4f)
        val x = (anchor.first + 8f).coerceIn(4f, maxX)
        val y = (anchor.second - 8f).coerceIn(textPaint.textSize + 8f, height - 8f)
        labelBgPaint.color = if (isDark) Color.argb(178, 0, 0, 0) else Color.argb(205, 255, 255, 255)
        canvas.drawRoundRect(x - 6f, y - textPaint.textSize, x + w + 6f, y + 8f, 8f, 8f, labelBgPaint)
        canvas.drawText(label, x, y, textPaint)
    }

    private fun labelFuerAktuellenZoom(achse: TrassenGrafikAchse): String {
        val roh = achse.label.takeIf { it.isNotBlank() } ?: achse.name
        val statusZusatz = if (achse.manuellerAbschnittId == null && zoomFaktor() >= 4f && achse.kmStatus == TrassenKmStatusLogik.STATUS_MANUELL) " · manuell" else ""
        val quellRichtung = achse.richtungQuelle
            ?.trim()
            ?.uppercase(java.util.Locale.GERMANY)
            ?.takeIf { it in setOf("L", "R", "S", "SL") }
        val quellenZusatz = if (achse.manuellerAbschnittId == null && achse.achsTyp == TrassenAchseEntity.ACHSTYP_STRECKE && zoomFaktor() >= 2f && quellRichtung != null) {
            " · Quelle $quellRichtung"
        } else ""
        return when (achse.achsTyp) {
            TrassenAchseEntity.ACHSTYP_STRECKE -> if (zoomFaktor() < 2f) roh.removePrefix("Str ") + statusZusatz else roh + quellenZusatz + statusZusatz
            TrassenAchseEntity.ACHSTYP_GLEIS -> if (zoomFaktor() < 2f) achse.gleis?.takeIf { it.isNotBlank() }?.let { "Gl $it" } ?: roh else roh + statusZusatz
            else -> roh + statusZusatz
        }
    }

    private fun sichtbarerLabelAnker(achse: TrassenGrafikAchse, b: RectF): Pair<Float, Float> {
        if (achse.punkte.size == 1) {
            val p = achse.punkte.first()
            return sx(p.x, b) to sy(p.y, b)
        }
        val cx = width / 2f
        val cy = height / 2f
        val margin = 90f
        var bestX = sx(achse.punkte[achse.punkte.size / 2].x, b)
        var bestY = sy(achse.punkte[achse.punkte.size / 2].y, b)
        var bestScore = Float.MAX_VALUE

        for (i in 0 until achse.punkte.size - 1) {
            val p1 = achse.punkte[i]
            val p2 = achse.punkte[i + 1]
            val x1 = sx(p1.x, b)
            val y1 = sy(p1.y, b)
            val x2 = sx(p2.x, b)
            val y2 = sy(p2.y, b)
            val mx = (x1 + x2) / 2f
            val my = (y1 + y2) / 2f
            val sichtbar = mx >= -margin && mx <= width + margin && my >= -margin && my <= height + margin
            val dx = mx - cx
            val dy = my - cy
            val score = dx * dx + dy * dy + if (sichtbar) 0f else 1_000_000f
            if (score < bestScore) {
                bestScore = score
                bestX = mx
                bestY = my
            }
        }
        return bestX to bestY
    }

    private fun zeichneAnkerProjektion(canvas: Canvas, b: RectF, isDark: Boolean) {
        val projektion = ankerProjektion ?: return
        val px = sx(projektion.punktX, b)
        val py = sy(projektion.punktY, b)
        val ax = sx(projektion.projX, b)
        val ay = sy(projektion.projY, b)

        achsPaint.pathEffect = DashPathEffect(floatArrayOf(dpF(7f), dpF(5f)), 0f)
        achsPaint.strokeWidth = dpF(2f)
        achsPaint.color = if (isDark) Color.rgb(255, 235, 59) else Color.rgb(190, 145, 0)
        canvas.drawLine(px, py, ax, ay, achsPaint)
        achsPaint.pathEffect = null

        punktPaint.color = if (isDark) Color.WHITE else Color.BLACK
        canvas.drawCircle(ax, ay, dpF(4.5f), punktPaint)
        punktRandPaint.color = if (isDark) Color.BLACK else Color.WHITE
        punktRandPaint.strokeWidth = dpF(1.5f)
        canvas.drawCircle(ax, ay, dpF(4.5f), punktRandPaint)
        punktRandPaint.strokeWidth = 2f
    }

    private fun zeichneLegende(canvas: Canvas, isDark: Boolean) {
        val zeilen = listOf(
            "KM: Grün automatisch · Orange manuell",
            "Rot = keine belastbare KM · Grau = ohne Vermessungsgeometrie",
            "Weiche/WV blau · WV gestrichelt · ◇ Anschluss",
            "Festpunkte und KM beim Reinzoomen"
        )
        val maxBreite = (width - 44f).coerceAtLeast(120f)
        var schrift = 19f
        textPaint.textSize = schrift
        while (schrift > 12f && zeilen.maxOf { textPaint.measureText(it) } > maxBreite) {
            schrift -= 1f
            textPaint.textSize = schrift
        }
        textPaint.color = if (isDark) Color.WHITE else Color.rgb(20, 20, 20)
        labelBgPaint.color = if (isDark) Color.argb(190, 0, 0, 0) else Color.argb(215, 255, 255, 255)
        val w = zeilen.maxOf { textPaint.measureText(it) }
        val lineHeight = schrift + 7f
        val bottom = 20f + zeilen.size * lineHeight
        canvas.drawRoundRect(12f, 12f, min(width - 12f, w + 32f), bottom, 10f, 10f, labelBgPaint)
        zeilen.forEachIndexed { index, zeile ->
            canvas.drawText(zeile, 22f, 12f + (index + 1) * lineHeight - 3f, textPaint)
        }
    }

    private fun findeAchseBeiBildpunkt(x: Float, y: Float): TrassenGrafikAchse? =
        findeAchseUndStationBeiBildpunkt(x, y)?.first

    private fun findeAchsenBeiBildpunkt(x: Float, y: Float): List<TrassenGrafikAchse> =
        findeAchsenUndStationBeiBildpunkt(x, y).map { it.first }.distinctBy { it.achseId }

    private fun findeAchseUndStationBeiBildpunkt(x: Float, y: Float): Pair<TrassenGrafikAchse, Double>? =
        findeAchsenUndStationBeiBildpunkt(x, y).firstOrNull()?.let { it.first to it.second }

    private fun findeAchsenUndStationBeiBildpunkt(x: Float, y: Float): List<Triple<TrassenGrafikAchse, Double, Float>> {
        val b = bounds ?: return emptyList()
        val toleranz = 30f
        return sichtbareAchsen()
            .mapNotNull { achse ->
                if (achse.punkte.size < 2) return@mapNotNull null
                var besteDistanz = Float.MAX_VALUE
                var besteStation: Double? = null
                for (i in 0 until achse.punkte.size - 1) {
                    val p1 = achse.punkte[i]
                    val p2 = achse.punkte[i + 1]
                    val x1 = sx(p1.x, b)
                    val y1 = sy(p1.y, b)
                    val x2 = sx(p2.x, b)
                    val y2 = sy(p2.y, b)
                    if (x < min(x1, x2) - toleranz || x > max(x1, x2) + toleranz ||
                        y < min(y1, y2) - toleranz || y > max(y1, y2) + toleranz
                    ) continue
                    val dx = x2 - x1
                    val dy = y2 - y1
                    val len2 = dx * dx + dy * dy
                    val t = if (len2 <= 0.0001f) 0f else (((x - x1) * dx + (y - y1) * dy) / len2).coerceIn(0f, 1f)
                    val projX = x1 + t * dx
                    val projY = y1 + t * dy
                    val ex = x - projX
                    val ey = y - projY
                    val distanz = kotlin.math.sqrt((ex * ex + ey * ey).toDouble()).toFloat()
                    if (distanz < besteDistanz) {
                        besteDistanz = distanz
                        besteStation = p1.station + (p2.station - p1.station) * t.toDouble()
                    }
                }
                if (besteDistanz <= toleranz && besteStation != null) Triple(achse, besteStation, besteDistanz) else null
            }
            .sortedWith(compareBy<Triple<TrassenGrafikAchse, Double, Float>> { it.third }.thenByDescending { typReihenfolge(it.first.achsTyp) })
    }

    private fun findeFestpunkteBeiBildpunkt(x: Float, y: Float): List<TrassenGrafikFestpunkt> {
        if (!festpunkteSichtbar) return emptyList()
        val b = bounds ?: return emptyList()
        val touchRadius = dpF(24f)
        val markerTreffer = sichtbareFestpunkte()
            .map { punkt ->
                val dx = x - sx(punkt.x, b)
                val dy = y - sy(punkt.y, b)
                punkt to kotlin.math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
            }
            .filter { it.second <= touchRadius }
            .sortedBy { it.second }
            .map { it.first }

        val labelTreffer = festpunktLabelTreffer
            .filter { it.rect.contains(x, y) }
            .map { it.punkt }

        return (labelTreffer + markerTreffer).distinct()
    }

    private fun distanzPunktZuSegment(px: Float, py: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        val len2 = dx * dx + dy * dy
        if (len2 <= 0.0001f) {
            val ex = px - x1
            val ey = py - y1
            return kotlin.math.sqrt((ex * ex + ey * ey).toDouble()).toFloat()
        }
        val t = (((px - x1) * dx + (py - y1) * dy) / len2).coerceIn(0f, 1f)
        val projX = x1 + t * dx
        val projY = y1 + t * dy
        val ex = px - projX
        val ey = py - projY
        return kotlin.math.sqrt((ex * ex + ey * ey).toDouble()).toFloat()
    }

    private fun sichtbareAchsen(): List<TrassenGrafikAchse> = achsen.filter { achse ->
        achse.achsTyp in sichtbareTypen && (sichtbareAchseIds == null || achse.achseId in sichtbareAchseIds.orEmpty())
    }

    private fun sichtbareFestpunkte(): List<TrassenGrafikFestpunkt> = festpunkte.filter { punkt ->
        sichtbarePunktArten == null || punkt.art in sichtbarePunktArten.orEmpty()
    }

    private fun dpF(value: Float): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value,
        resources.displayMetrics
    )

    private fun sp(value: Float): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP,
        value,
        resources.displayMetrics
    )

    private fun sx(x: Float, b: RectF): Float = offsetX + (x - b.left) * scale
    private fun sy(y: Float, b: RectF): Float = offsetY + (b.bottom - y) * scale

    private fun weichenBlau(dark: Boolean): Int =
        if (dark) Color.rgb(66, 165, 245) else Color.rgb(25, 118, 210)

    private fun farbeFuerKmStatus(status: String, dark: Boolean): Int = when (status) {
        TrassenKmStatusLogik.STATUS_AUTOMATISCH -> if (dark) Color.rgb(76, 217, 100) else Color.rgb(0, 145, 65)
        TrassenKmStatusLogik.STATUS_MANUELL -> if (dark) Color.rgb(255, 183, 77) else Color.rgb(230, 126, 0)
        TrassenKmStatusLogik.STATUS_FEHLT -> if (dark) Color.rgb(255, 82, 82) else Color.rgb(205, 35, 45)
        TrassenKmStatusLogik.STATUS_UNPLAUSIBEL -> if (dark) Color.rgb(255, 82, 82) else Color.rgb(205, 35, 45)
        else -> if (dark) Color.rgb(255, 82, 82) else Color.rgb(205, 35, 45)
    }

    private fun typReihenfolge(typ: String): Int = when (typ) {
        TrassenAchseEntity.ACHSTYP_STRECKE -> 0
        TrassenAchseEntity.ACHSTYP_GLEIS -> 1
        TrassenAchseEntity.ACHSTYP_WEICHENVERBINDUNG -> 2
        TrassenAchseEntity.ACHSTYP_WEICHE -> 3
        else -> 4
    }

    private fun berechneAktuelleBounds(): RectF? = berechneBounds(
        sichtbareAchsen(),
        if (festpunkteSichtbar) sichtbareFestpunkte() else emptyList()
    )

    private fun berechneBounds(
        achsen: List<TrassenGrafikAchse>,
        punkte: List<TrassenGrafikFestpunkt> = emptyList()
    ): RectF? {
        val achsPunkte = achsen.flatMap { it.punkte }
        if (achsPunkte.isEmpty() && punkte.isEmpty()) return null
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        achsPunkte.forEach { p ->
            minX = min(minX, p.x)
            maxX = max(maxX, p.x)
            minY = min(minY, p.y)
            maxY = max(maxY, p.y)
        }
        punkte.forEach { p ->
            minX = min(minX, p.x)
            maxX = max(maxX, p.x)
            minY = min(minY, p.y)
            maxY = max(maxY, p.y)
        }
        if (minX == Float.MAX_VALUE || minY == Float.MAX_VALUE) return null
        if (maxX - minX < 1f) maxX = minX + 1f
        if (maxY - minY < 1f) maxY = minY + 1f
        return RectF(minX, minY, maxX, maxY)
    }
    private data class FestpunktLabelTreffer(
        val punkt: TrassenGrafikFestpunkt,
        val rect: RectF
    )
}

data class TrassenGrafikKnoten(
    val x: Float,
    val y: Float,
    val labels: List<String>
)

data class TrassenGrafikAchse(
    val achseId: Long,
    val name: String,
    val label: String,
    val achsTyp: String,
    val strecke: String?,
    val gleis: String?,
    val richtung: String?,
    val richtungQuelle: String?,
    val quelleDatei: String,
    val dateiSignatur: String?,
    val notiz: String?,
    val istManuell: Boolean = false,
    val kmStatus: String = TrassenKmStatusLogik.STATUS_FEHLT,
    val kmStatusGrund: String = "Keine KM-Kalibrierung",
    /** Rohachse unter einem betrieblichen Abschnitt neutral darstellen. */
    val neutralDarstellung: Boolean = false,
    /** Technische Roh-TRA-Namen werden nach fachlicher Abschnittszuordnung in der normalen Grafik ausgeblendet. */
    val labelSichtbar: Boolean = true,
    val manuelleAnker: List<TrassenGrafikKmAnker> = emptyList(),
    val manuellerAbschnittId: Long? = null,
    val abschnittStartStation: Double? = null,
    val abschnittEndeStation: Double? = null,
    val punkte: List<TrassenGrafikPunkt>
)

data class TrassenGrafikPunkt(
    val station: Double,
    val x: Float,
    val y: Float,
    val kmMeter: Double?
)

data class TrassenGrafikKmAnker(
    val punktNummer: Int,
    val station: Double,
    val kmMeter: Double,
    val label: String? = null
)

data class TrassenGrafikProjektion(
    val punktX: Float,
    val punktY: Float,
    val projX: Float,
    val projY: Float
)

data class TrassenGrafikMarker(
    val label: String,
    val x: Float,
    val y: Float,
    val achseId: Long? = null,
    val aktiv: Boolean = false
)

data class TrassenGrafikFestpunkt(
    val label: String,
    val art: String,
    val x: Float,
    val y: Float,
    val kmMeter: Double?,
    val strecke: String? = null,
    val gleis: String? = null,
    val hoeheM: Double? = null,
    val typ: String? = null,
    val quelleDatei: String? = null,
    val punktId: String? = null
)
