package com.luma.gallery.web

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/** QR code for the current web address, shown in Settings. */
object WebQr {

    fun bitmap(text: String, sizePx: Int = 720): Bitmap? = runCatching {
        val size = sizePx.coerceIn(240, 1_200)
        val matrix = QRCodeWriter().encode(
            text,
            BarcodeFormat.QR_CODE,
            size,
            size,
            mapOf(EncodeHintType.MARGIN to 1)
        )
        val pixels = IntArray(size * size)
        for (y in 0 until size) {
            val row = y * size
            for (x in 0 until size) {
                pixels[row + x] = if (matrix[x, y]) DARK else Color.WHITE
            }
        }
        Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }.getOrNull()

    private const val DARK = 0xFF12101A.toInt()
}
