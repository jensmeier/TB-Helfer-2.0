package de.tbhelfer.ui.sync

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Erzeugt den Baustellen-Einladungs-QR vollständig lokal auf dem Gerät.
 * Im QR steht nur derselbe kurzlebige Deep-Link, der auch über "Teilen" versendet wird.
 */
object InvitationQr {
    fun bitmap(payload: String, sizePx: Int = 720): Bitmap {
        require(payload.isNotBlank()) { "QR-Inhalt fehlt." }
        val size = sizePx.coerceIn(320, 1400)
        val matrix = QRCodeWriter().encode(
            payload,
            BarcodeFormat.QR_CODE,
            size,
            size,
            mapOf(
                EncodeHintType.MARGIN to 1,
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M
            )
        )
        return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
            for (y in 0 until matrix.height) {
                for (x in 0 until matrix.width) {
                    setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
        }
    }
}
