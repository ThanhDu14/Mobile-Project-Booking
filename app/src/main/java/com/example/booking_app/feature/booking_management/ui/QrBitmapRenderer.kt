package com.example.booking_app.feature.booking_management.ui

import android.graphics.Bitmap
import android.graphics.Color
import com.example.booking_app.feature.booking_management.domain.QrBitMatrix

/**
 * Renders a QrBitMatrix into an Android Bitmap with a pure white background.
 * Minimum 240dp on screen, crisp contrast safe in dark mode.
 */
object QrBitmapRenderer {
    fun render(matrix: QrBitMatrix): Bitmap {
        val width = matrix.width
        val height = matrix.height
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
                pixels[offset + x] = if (matrix.isSet(x, y)) Color.BLACK else Color.WHITE
            }
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }
}
