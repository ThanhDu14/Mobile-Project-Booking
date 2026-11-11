package com.example.booking_app.feature.booking_management.domain

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

class QrBitMatrix(val width: Int, val height: Int, private val bits: BooleanArray) {
    fun isSet(x: Int, y: Int): Boolean {
        require(x in 0 until width && y in 0 until height)
        return bits[y * width + x]
    }
}

/** Generates pixels without Android graphics dependencies. */
class GenerateQrCodeUseCase {
    operator fun invoke(token: String): QrBitMatrix {
        val matrix = QRCodeWriter().encode(
            token, BarcodeFormat.QR_CODE, SIZE, SIZE,
            mapOf(EncodeHintType.CHARACTER_SET to "UTF-8")
        )
        return QrBitMatrix(matrix.width, matrix.height,
            BooleanArray(matrix.width * matrix.height) { i -> matrix[i % matrix.width, i / matrix.width] })
    }

    private companion object { const val SIZE = 512 }
}
