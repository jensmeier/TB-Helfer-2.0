package de.tbhelfer.ui.importexport

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.max

/** Kleine Orientierungshilfe: nur die beiden TB-Helfer-Achsen D-A und C-B. */
class DkwKreuzungSchemaView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.DKGRAY
        strokeWidth = 4f * resources.displayMetrics.density
        style = Paint.Style.STROKE
    }
    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(35, 105, 210)
        style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.DKGRAY
        textSize = android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_SP, 16f, resources.displayMetrics
        )
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GRAY
        textSize = android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_SP, 12f, resources.displayMetrics
        )
        textAlign = Paint.Align.CENTER
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val wantedH = (165f * resources.displayMetrics.density).toInt()
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), resolveSize(wantedH, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val padX = 42f * resources.displayMetrics.density
        val top = 34f * resources.displayMetrics.density
        val bottom = height - 48f * resources.displayMetrics.density
        val left = padX
        val right = max(left + 1f, width - padX)
        val radius = 5f * resources.displayMetrics.density

        // D-A und C-B kreuzen sich.
        canvas.drawLine(left, top, right, bottom, linePaint)
        canvas.drawLine(left, bottom, right, top, linePaint)

        val points = listOf(
            Triple(left, top, "D"),
            Triple(right, top, "B"),
            Triple(left, bottom, "C"),
            Triple(right, bottom, "A")
        )
        points.forEach { (x, y, label) ->
            canvas.drawCircle(x, y, radius, pointPaint)
            val dy = if (y == top) -12f * resources.displayMetrics.density else 22f * resources.displayMetrics.density
            canvas.drawText(label, x, y + dy, textPaint)
        }
        canvas.drawText("Achsen: D–A und C–B", width / 2f, height - 10f * resources.displayMetrics.density, hintPaint)
    }
}
