package de.tbhelfer.ui.streckenpunkte

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import de.tbhelfer.datenbank.StreckenpunktEntity
import de.tbhelfer.datenbank.StreckenpunktTyp
import de.tbhelfer.vermessung.KmBerechner
import java.util.Locale
import kotlin.math.max

class StreckenpunkteSkizzeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val linienPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.DKGRAY
        strokeWidth = 4f
        style = Paint.Style.STROKE
    }

    private val punktPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 30f
    }

    private val hinweisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GRAY
        textSize = 30f
    }

    private val livePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLUE
        style = Paint.Style.FILL
    }

    private val liveTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLUE
        textSize = 28f
    }

    private var punkte: List<StreckenpunktEntity> = emptyList()
    private var automatikKm: Double? = null
    private var automatikIndikator: KmBerechner.Indikator? = null
    private var automatikAktiv: Boolean = false

    fun setPunkte(neu: List<StreckenpunktEntity>) {
        punkte = neu.sortedBy { it.km }.take(12)
        invalidate()
    }

    fun setAutomatikMarker(km: Double?, indikator: KmBerechner.Indikator?, aktiv: Boolean) {
        automatikKm = km
        automatikIndikator = indikator
        automatikAktiv = aktiv
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val left = paddingLeft + 24f
        val right = w - paddingRight - 24f
        val centerY = h * 0.56f

        canvas.drawLine(left, centerY, right, centerY, linienPaint)

        if (punkte.isEmpty()) {
            canvas.drawText("Noch keine Streckenpunkte", left, centerY - 18f, hinweisPaint)
            canvas.drawText("hier erscheint die einfache Skizze", left, centerY + 26f, hinweisPaint)
            return
        }

        val step = if (punkte.size == 1) 0f else (right - left) / max(1, punkte.size - 1)
        punkte.forEachIndexed { index, punkt ->
            val x = left + (step * index)
            zeichneMarker(canvas, x, centerY, punkt)
        }

        if (automatikAktiv && automatikKm != null) {
            zeichneAutomatikMarker(canvas, left, right, centerY)
        }

        val first = punkte.first()
        val last = punkte.last()
        canvas.drawText(formatKm(first), left, h - 14f, textPaint)
        if (punkte.size > 1) {
            val text = formatKm(last)
            val textWidth = textPaint.measureText(text)
            canvas.drawText(text, right - textWidth, h - 14f, textPaint)
        }
    }

    private fun zeichneMarker(canvas: Canvas, x: Float, centerY: Float, punkt: StreckenpunktEntity) {
        when (punkt.typ) {
            StreckenpunktTyp.EXAKT -> {
                val path = Path().apply {
                    moveTo(x, centerY - 28f)
                    lineTo(x - 18f, centerY + 8f)
                    lineTo(x + 18f, centerY + 8f)
                    close()
                }
                canvas.drawPath(path, punktPaint)
            }
            StreckenpunktTyp.HILFS -> {
                canvas.drawCircle(x, centerY, 12f, punktPaint)
            }
            else -> {
                canvas.drawRect(x - 14f, centerY - 14f, x + 14f, centerY + 14f, punktPaint)
            }
        }

        canvas.drawLine(x, centerY - 36f, x, centerY + 20f, linienPaint)
        val label = when {
            punkt.bezeichnung.length <= 8 -> punkt.bezeichnung
            else -> punkt.bezeichnung.take(8) + "…"
        }
        canvas.drawText(label, x - (textPaint.measureText(label) / 2f), centerY - 46f, textPaint)
    }

    private fun zeichneAutomatikMarker(canvas: Canvas, left: Float, right: Float, centerY: Float) {
        val km = automatikKm ?: return
        val minKm = punkte.minOfOrNull { it.km } ?: return
        val maxKm = punkte.maxOfOrNull { it.km } ?: return
        val x = when {
            maxKm - minKm < 0.0001 -> (left + right) / 2f
            km <= minKm -> left
            km >= maxKm -> right
            else -> left + (((km - minKm) / (maxKm - minKm)) * (right - left)).toFloat()
        }
        livePaint.color = when (automatikIndikator) {
            KmBerechner.Indikator.GRUEN -> Color.parseColor("#2E7D32")
            KmBerechner.Indikator.GELB -> Color.parseColor("#F9A825")
            KmBerechner.Indikator.ROT -> Color.parseColor("#C62828")
            null -> Color.BLUE
        }
        liveTextPaint.color = livePaint.color
        canvas.drawCircle(x, centerY + 28f, 12f, livePaint)
        canvas.drawLine(x, centerY - 10f, x, centerY + 28f, livePaint)
        val label = String.format(Locale.GERMANY, "Live %.3f", km)
        canvas.drawText(label, x - (liveTextPaint.measureText(label) / 2f), centerY + 64f, liveTextPaint)
    }

    private fun formatKm(punkt: StreckenpunktEntity): String {
        val basis = String.format(Locale.GERMANY, "%.3f", punkt.km)
        return if (punkt.kmSuffix > 0) "$basis-${punkt.kmSuffix}" else basis
    }
}
