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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import java.io.File

/** Einfacher App-interner PDF-Viewer fuer BTB-Abschlussdokumente. */
class BtbPdfViewerActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(de.tbhelfer.server.recovery.RecoveryWorkspace.wrap(newBase))
    }


    companion object {
        const val EXTRA_FILE_PATH = "btb_pdf_file_path"
        const val EXTRA_TITLE = "btb_pdf_title"
    }

    private var descriptor: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    private var currentBitmap: Bitmap? = null
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

        descriptor = runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        }.getOrNull()
        renderer = descriptor?.let { fd -> runCatching { PdfRenderer(fd) }.getOrNull() }
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
            setPadding(dp(8), dp(8), dp(8), dp(10))
        }
        // Kamera/Statusleiste oben und Android-Navigationsleiste unten niemals ueberdecken.
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(dp(8), sys.top + dp(10), dp(8), sys.bottom + dp(14))
            insets
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, dp(6))
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
        header.addView(back)
        header.addView(titleView)
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
                topMargin = dp(4)
                bottomMargin = dp(10)
            }
        }
        root.addView(image)

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(2))
        }
        prev = MaterialButton(this).apply {
            text = "◀ Vorherige"
            isAllCaps = false
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                if (pageIndex > 0) {
                    pageIndex--
                    renderPage()
                }
            }
        }
        pageLabel = TextView(this).apply {
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.DKGRAY)
            setPadding(dp(8), 0, dp(8), 0)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        next = MaterialButton(this).apply {
            text = "Nächste ▶"
            isAllCaps = false
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                val count = renderer?.pageCount ?: 0
                if (pageIndex + 1 < count) {
                    pageIndex++
                    renderPage()
                }
            }
        }
        controls.addView(prev)
        controls.addView(pageLabel)
        controls.addView(next)
        root.addView(controls)

        setContentView(root)
        ViewCompat.requestApplyInsets(root)
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
        if (!::image.isInitialized || pageIndex !in 0 until r.pageCount) return
        r.openPage(pageIndex).use { page ->
            val maxWidth = resources.displayMetrics.widthPixels.coerceAtLeast(800)
            val scale = maxWidth.toFloat() / page.width.toFloat()
            val width = (page.width * scale).toInt().coerceAtLeast(1)
            val height = (page.height * scale).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            image.setImageDrawable(null)
            currentBitmap?.takeIf { !it.isRecycled }?.recycle()
            currentBitmap = bitmap
            image.setImageBitmap(bitmap)
        }
        pageLabel.text = "${pageIndex + 1} / ${r.pageCount}"
        prev.isEnabled = pageIndex > 0
        next.isEnabled = pageIndex + 1 < r.pageCount
    }

    override fun onDestroy() {
        if (::image.isInitialized) image.setImageDrawable(null)
        currentBitmap?.takeIf { !it.isRecycled }?.recycle()
        currentBitmap = null
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

