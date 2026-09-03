package de.tbhelfer.ui.abnahme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Lokaler Planausschnitt mit Drehen, Zuschneiden und einer einfachen Planmarkierung. */
class PlanCropView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatImageView(context, attrs) {

    enum class MarkerMode { NONE, CIRCLE, ARROW }

    private var sourceBitmap: Bitmap? = null
    private val workMatrix = Matrix()
    private val inverse = Matrix()
    private val cropRect = RectF()
    private val shadePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x88000000.toInt() }
    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 2f
    }
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 4f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private var lastX = 0f
    private var lastY = 0f
    private var dragging = false
    private var markerMode: MarkerMode = MarkerMode.NONE
    private var markerStart: PointF? = null
    private var markerEnd: PointF? = null
    private var marking = false
    private var annotationText: String = ""

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            if (markerMode != MarkerMode.NONE) return false
            val factor = detector.scaleFactor.coerceIn(0.8f, 1.25f)
            workMatrix.postScale(factor, factor, detector.focusX, detector.focusY)
            constrainMatrix()
            imageMatrix = workMatrix
            invalidate()
            return true
        }
    })

    init {
        scaleType = ScaleType.MATRIX
        setBackgroundColor(Color.BLACK)
    }

    fun setSourceBitmap(bitmap: Bitmap) {
        sourceBitmap?.takeIf { it !== bitmap }?.recycle()
        sourceBitmap = bitmap
        setImageBitmap(bitmap)
        post { resetMatrix() }
    }

    fun rotate90() {
        val src = sourceBitmap ?: return
        val m = Matrix().apply { postRotate(90f) }
        val rotated = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
        setSourceBitmap(rotated)
    }

    fun setMarkerMode(mode: MarkerMode) {
        markerMode = mode
        marking = false
        invalidate()
    }

    fun clearMarker() {
        markerStart = null
        markerEnd = null
        markerMode = MarkerMode.NONE
        marking = false
        annotationText = ""
        invalidate()
    }

    fun setAnnotationText(text: String) {
        annotationText = text.trim()
    }

    fun createCroppedBitmap(maxOutputPx: Int = 2600): Bitmap? {
        val src = sourceBitmap ?: return null
        if (!workMatrix.invert(inverse)) return null
        val mapped = RectF(cropRect)
        inverse.mapRect(mapped)
        val left = mapped.left.coerceIn(0f, src.width.toFloat())
        val top = mapped.top.coerceIn(0f, src.height.toFloat())
        val right = mapped.right.coerceIn(left + 1f, src.width.toFloat())
        val bottom = mapped.bottom.coerceIn(top + 1f, src.height.toFloat())
        val x = left.toInt().coerceIn(0, src.width - 1)
        val y = top.toInt().coerceIn(0, src.height - 1)
        val w = (right - left).toInt().coerceIn(1, src.width - x)
        val h = (bottom - top).toInt().coerceIn(1, src.height - y)
        val cropped = Bitmap.createBitmap(src, x, y, w, h)
        val maxDim = max(cropped.width, cropped.height)
        val result = if (maxDim > maxOutputPx) {
            val scale = maxOutputPx.toFloat() / maxDim.toFloat()
            Bitmap.createScaledBitmap(
                cropped,
                max(1, (cropped.width * scale).toInt()),
                max(1, (cropped.height * scale).toInt()),
                true
            ).also { if (it !== cropped) cropped.recycle() }
        } else cropped

        val needsDrawing = markerStart != null && markerEnd != null || annotationText.isNotBlank()
        if (!needsDrawing) return result
        val mutable = Bitmap.createBitmap(result.width, result.height, Bitmap.Config.ARGB_8888)
        Canvas(mutable).drawBitmap(result, 0f, 0f, null)
        result.recycle()
        drawMarkerOnOutput(mutable)
        return mutable
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val marginX = w * 0.06f
        val marginY = h * 0.10f
        cropRect.set(marginX, marginY, w - marginX, h - marginY)
        resetMatrix()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (cropRect.isEmpty) return
        canvas.drawRect(0f, 0f, width.toFloat(), cropRect.top, shadePaint)
        canvas.drawRect(0f, cropRect.bottom, width.toFloat(), height.toFloat(), shadePaint)
        canvas.drawRect(0f, cropRect.top, cropRect.left, cropRect.bottom, shadePaint)
        canvas.drawRect(cropRect.right, cropRect.top, width.toFloat(), cropRect.bottom, shadePaint)
        canvas.drawRect(cropRect, framePaint)

        val thirdW = cropRect.width() / 3f
        val thirdH = cropRect.height() / 3f
        framePaint.alpha = 150
        canvas.drawLine(cropRect.left + thirdW, cropRect.top, cropRect.left + thirdW, cropRect.bottom, framePaint)
        canvas.drawLine(cropRect.left + 2 * thirdW, cropRect.top, cropRect.left + 2 * thirdW, cropRect.bottom, framePaint)
        canvas.drawLine(cropRect.left, cropRect.top + thirdH, cropRect.right, cropRect.top + thirdH, framePaint)
        canvas.drawLine(cropRect.left, cropRect.top + 2 * thirdH, cropRect.right, cropRect.top + 2 * thirdH, framePaint)
        framePaint.alpha = 255

        val start = markerStart
        val end = markerEnd
        if (start != null && end != null) drawMarker(canvas, markerMode, start.x, start.y, end.x, end.y, markerPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (markerMode != MarkerMode.NONE && event.pointerCount == 1) {
            val x = event.x.coerceIn(cropRect.left, cropRect.right)
            val y = event.y.coerceIn(cropRect.top, cropRect.bottom)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (!cropRect.contains(event.x, event.y)) return true
                    markerStart = PointF(x, y)
                    markerEnd = PointF(x, y)
                    marking = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    invalidate()
                }
                MotionEvent.ACTION_MOVE -> if (marking) {
                    markerEnd = PointF(x, y)
                    invalidate()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (marking) markerEnd = PointF(x, y)
                    marking = false
                    parent?.requestDisallowInterceptTouchEvent(false)
                    invalidate()
                }
            }
            return true
        }

        scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                dragging = true
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> if (dragging && !scaleDetector.isInProgress) {
                val dx = event.x - lastX
                val dy = event.y - lastY
                workMatrix.postTranslate(dx, dy)
                constrainMatrix()
                imageMatrix = workMatrix
                lastX = event.x
                lastY = event.y
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }

    private fun drawMarkerOnOutput(bitmap: Bitmap) {
        val canvas = Canvas(bitmap)
        val scaleStroke = max(4f, max(bitmap.width, bitmap.height) / 260f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.RED
            style = Paint.Style.STROKE
            strokeWidth = scaleStroke
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val start = markerStart
        val end = markerEnd
        if (start != null && end != null && !cropRect.isEmpty) {
            val sx = ((start.x - cropRect.left) / cropRect.width()).coerceIn(0f, 1f) * bitmap.width
            val sy = ((start.y - cropRect.top) / cropRect.height()).coerceIn(0f, 1f) * bitmap.height
            val ex = ((end.x - cropRect.left) / cropRect.width()).coerceIn(0f, 1f) * bitmap.width
            val ey = ((end.y - cropRect.top) / cropRect.height()).coerceIn(0f, 1f) * bitmap.height
            drawMarker(canvas, markerMode, sx, sy, ex, ey, paint)
        }
        if (annotationText.isNotBlank()) {
            val textSize = max(24f, bitmap.width / 32f)
            val x = bitmap.width * 0.035f
            val y = bitmap.height - bitmap.height * 0.035f
            val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                this.textSize = textSize
                style = Paint.Style.STROKE
                strokeWidth = max(4f, textSize / 7f)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            val fill = Paint(outline).apply { color = Color.RED; style = Paint.Style.FILL }
            canvas.drawText(annotationText.take(80), x, y, outline)
            canvas.drawText(annotationText.take(80), x, y, fill)
        }
    }

    private fun drawMarker(canvas: Canvas, mode: MarkerMode, sx: Float, sy: Float, ex: Float, ey: Float, paint: Paint) {
        when (mode) {
            MarkerMode.CIRCLE -> canvas.drawOval(RectF(min(sx, ex), min(sy, ey), max(sx, ex), max(sy, ey)), paint)
            MarkerMode.ARROW -> {
                canvas.drawLine(sx, sy, ex, ey, paint)
                val angle = atan2((ey - sy).toDouble(), (ex - sx).toDouble())
                val head = max(20f, paint.strokeWidth * 5f)
                val spread = Math.toRadians(28.0)
                canvas.drawLine(ex, ey, ex - (head * cos(angle - spread)).toFloat(), ey - (head * sin(angle - spread)).toFloat(), paint)
                canvas.drawLine(ex, ey, ex - (head * cos(angle + spread)).toFloat(), ey - (head * sin(angle + spread)).toFloat(), paint)
            }
            MarkerMode.NONE -> Unit
        }
    }

    private fun resetMatrix() {
        val bmp = sourceBitmap ?: return
        if (width <= 0 || height <= 0 || cropRect.isEmpty) return
        workMatrix.reset()
        val scale = max(cropRect.width() / bmp.width.toFloat(), cropRect.height() / bmp.height.toFloat())
        val dx = cropRect.centerX() - bmp.width * scale / 2f
        val dy = cropRect.centerY() - bmp.height * scale / 2f
        workMatrix.postScale(scale, scale)
        workMatrix.postTranslate(dx, dy)
        imageMatrix = workMatrix
        invalidate()
    }

    private fun constrainMatrix() {
        val bmp = sourceBitmap ?: return
        val rect = RectF(0f, 0f, bmp.width.toFloat(), bmp.height.toFloat())
        workMatrix.mapRect(rect)

        var dx = 0f
        var dy = 0f
        if (rect.width() < cropRect.width() || rect.height() < cropRect.height()) {
            resetMatrix()
            return
        }
        if (rect.left > cropRect.left) dx = cropRect.left - rect.left
        if (rect.right < cropRect.right) dx = cropRect.right - rect.right
        if (rect.top > cropRect.top) dy = cropRect.top - rect.top
        if (rect.bottom < cropRect.bottom) dy = cropRect.bottom - rect.bottom
        workMatrix.postTranslate(dx, dy)
    }
}
