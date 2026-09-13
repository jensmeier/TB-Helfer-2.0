package de.tbhelfer.ui.bautagebuch

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import java.io.File

/** Einfacher App-interner PDF-Viewer für BTB-Abschlussdokumente. */
class BtbPdfViewerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FILE_PATH = "btb_pdf_file_path"
        const val EXTRA_TITLE = "btb_pdf_title"
    }

    private var descriptor: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    private var pageIndex = 0
    private lateinit var image: ImageView
    private lateinit var pageLabel: TextView
    private lateinit var prev: MaterialButton
    private lateinit var next: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = intent.getStringExtra(EXTRA_TITLE)?.takeIf { it.isNotBlank() } ?: "BTB-PDF"

        val path = intent.getStringExtra(EXTRA_FILE_PATH).orEmpty()
        val file = File(path)
        if (!file.exists() || !file.isFile || !file.extension.equals("pdf", true)) {
            finish()
            return
        }

        descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        renderer = descriptor?.let(::PdfRenderer)
        if ((renderer?.pageCount ?: 0) <= 0) {
            closePdf()
            finish()
            return
        }

        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(245, 245, 245))
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val back = MaterialButton(this).apply {
            text = "Zurück"
            isAllCaps = false
            setOnClickListener { finish() }
        }
        val titleView = TextView(this).apply {
            text = title
            textSize = 16f
            setTextColor(Color.BLACK)
            setPadding(dp(12), 0, dp(8), 0)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        pageLabel = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.DKGRAY)
        }
        header.addView(back)
        header.addView(titleView)
        header.addView(pageLabel)
        root.addView(header)

        image = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply {
                topMargin = dp(8)
                bottomMargin = dp(8)
            }
        }
        root.addView(image)

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        prev = MaterialButton(this).apply {
            text = "◀ Vorherige"
            isAllCaps = false
            setOnClickListener {
                if (pageIndex > 0) {
                    pageIndex--
                    renderPage()
                }
            }
        }
        next = MaterialButton(this).apply {
            text = "Nächste ▶"
            isAllCaps = false
            setOnClickListener {
                val count = renderer?.pageCount ?: 0
                if (pageIndex + 1 < count) {
                    pageIndex++
                    renderPage()
                }
            }
        }
        controls.addView(prev)
        controls.addView(next)
        root.addView(controls)

        setContentView(root)
        pageIndex = savedInstanceState?.getInt("pageIndex", 0)?.coerceAtLeast(0) ?: 0
        pageIndex = pageIndex.coerceAtMost((renderer?.pageCount ?: 1) - 1)
        renderPage()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("pageIndex", pageIndex)
        super.onSaveInstanceState(outState)
    }

    private fun renderPage() {
        val r = renderer ?: return
        if (pageIndex !in 0 until r.pageCount) return
        r.openPage(pageIndex).use { page ->
            val maxWidth = resources.displayMetrics.widthPixels.coerceAtLeast(800)
            val scale = maxWidth.toFloat() / page.width.toFloat()
            val width = (page.width * scale).toInt().coerceAtLeast(1)
            val height = (page.height * scale).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            image.setImageBitmap(bitmap)
        }
        pageLabel.text = "${pageIndex + 1} / ${r.pageCount}"
        prev.isEnabled = pageIndex > 0
        next.isEnabled = pageIndex + 1 < r.pageCount
    }

    override fun onDestroy() {
        image.drawable?.let { drawable ->
            if (drawable is android.graphics.drawable.BitmapDrawable) drawable.bitmap?.recycle()
        }
        closePdf()
        super.onDestroy()
    }

    private fun closePdf() {
        runCatching { renderer?.close() }
        renderer = null
        runCatching { descriptor?.close() }
        descriptor = null
    }
}
