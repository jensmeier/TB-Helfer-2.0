package de.tbhelfer.ui.baustelle

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSInteger
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionURI
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink
import java.io.ByteArrayOutputStream
import java.util.Locale

/**
 * Android 200058:
 * Der bestehende android.graphics.pdf.PdfDocument-Renderer bleibt unverändert.
 * Erst danach werden echte URI-Link-Annotationen exakt über die gezeichneten GPS-Zeilen gelegt.
 * So können normale PDF-Reader die Koordinaten direkt zu Google Maps öffnen.
 */
internal object PdfGpsLinkAnnotator {

    data class Target(
        val pageIndex: Int,
        val x: Float,
        val top: Float,
        val width: Float,
        val height: Float,
        val lat: Double,
        val lon: Double
    )

    fun addGoogleMapsLinks(
        context: Context,
        pdfBytes: ByteArray,
        targets: List<Target>
    ): ByteArray {
        if (targets.isEmpty()) return pdfBytes

        PDFBoxResourceLoader.init(context.applicationContext)

        val document = PDDocument.load(pdfBytes)
        return document.use { doc ->
            targets.forEach { target ->
                if (target.pageIndex !in 0 until doc.numberOfPages) return@forEach

                val page = doc.getPage(target.pageIndex)
                val pageHeight = page.cropBox.height
                val lowerLeftY = pageHeight - (target.top + target.height)

                val action = PDActionURI().apply {
                    setURI(googleMapsUrl(target.lat, target.lon))
                }
                val link = PDAnnotationLink().apply {
                    setRectangle(
                        PDRectangle(
                            target.x,
                            lowerLeftY,
                            target.width.coerceAtLeast(1f),
                            target.height.coerceAtLeast(1f)
                        )
                    )
                    setAction(action)
                    setPage(page)
                    setBorder(
                        COSArray().apply {
                            add(COSInteger.ZERO)
                            add(COSInteger.ZERO)
                            add(COSInteger.ZERO)
                        }
                    )
                }
                page.annotations.add(link)
            }

            ByteArrayOutputStream().use { out ->
                doc.save(out)
                out.toByteArray()
            }
        }
    }

    private fun googleMapsUrl(lat: Double, lon: Double): String {
        val latText = String.format(Locale.US, "%.6f", lat)
        val lonText = String.format(Locale.US, "%.6f", lon)
        return "https://www.google.com/maps/search/?api=1&query=$latText,$lonText"
    }
}
