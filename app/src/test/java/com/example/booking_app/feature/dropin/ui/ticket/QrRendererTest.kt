package com.example.booking_app.feature.dropin.ui.ticket

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T039: mã QR giải mã lại ra đúng `qrToken`. Chạy thuần JVM: test phần [QrRenderer.encode] và [QrRenderer.toPixels]
 * (bước tạo `Bitmap` cần Android thật nên không test ở đây).
 */
class QrRendererTest {

    private fun decode(token: String, size: Int): String {
        val matrix = QrRenderer.encode(token, size)
        val pixels = QrRenderer.toPixels(matrix)
        val source = RGBLuminanceSource(matrix.width, matrix.height, pixels)
        val bitmap = BinaryBitmap(HybridBinarizer(source))
        return QRCodeReader().decode(bitmap, mapOf(DecodeHintType.TRY_HARDER to true)).text
    }

    @Test
    fun `giai ma lai ra dung token`() {
        val token = "b3f1c2d4-5e6f-4a7b-8c9d-0e1f2a3b4c5d.Kx9_Zq-7"
        assertEquals(token, decode(token, 512))
    }

    @Test
    fun `token dai 64 ky tu van giai ma duoc o kich thuoc nho`() {
        val token = "x".repeat(32) + "0123456789abcdefghijklmnopqrstuv"
        assertEquals(token, decode(token, 240))
    }

    @Test
    fun `anh vuong dung kich thuoc, chi co den va trang, vien ngoai trang`() {
        val matrix = QrRenderer.encode("token", 300)
        assertEquals(300, matrix.width)
        assertEquals(300, matrix.height)

        val pixels = QrRenderer.toPixels(matrix).toSet()
        assertTrue(pixels.all { it == 0xFF000000.toInt() || it == 0xFFFFFFFF.toInt() })
        // Viền ngoài cùng là vùng trống màu trắng.
        assertEquals(0xFFFFFFFF.toInt(), QrRenderer.toPixels(matrix)[0])
    }
}
