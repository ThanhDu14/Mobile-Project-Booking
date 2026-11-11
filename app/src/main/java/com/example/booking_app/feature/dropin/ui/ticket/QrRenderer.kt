package com.example.booking_app.feature.dropin.ui.ticket

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Vẽ mã QR check-in từ `qrToken` (T039). Tách làm hai bước để test thuần JVM không cần Robolectric:
 * [encode] (ZXing core, không phụ thuộc Android) và [toBitmap] (đen trên nền trắng tuyệt đối, kể cả chế độ tối,
 * để máy quét của chủ sân đọc được). Fragment hiển thị QR cạnh ≥ 240dp.
 */
object QrRenderer {

    /** Lề trống quanh mã (đơn vị module). Chuẩn khuyên 4; nền trắng của thẻ vé đã có thêm khoảng trống. */
    private const val QUIET_ZONE = 2

    fun encode(token: String, sizePx: Int): BitMatrix {
        require(token.isNotEmpty()) { "qrToken rỗng" }
        require(sizePx > 0) { "Kích thước phải dương" }
        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to QUIET_ZONE,
        )
        return QRCodeWriter().encode(token, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
    }

    /** Điểm ảnh ARGB, hàng nối tiếp hàng; dùng chung cho [toBitmap] và test giải mã. */
    fun toPixels(matrix: BitMatrix): IntArray {
        val width = matrix.width
        val height = matrix.height
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
                pixels[offset + x] = if (matrix[x, y]) BLACK else WHITE
            }
        }
        return pixels
    }

    fun toBitmap(matrix: BitMatrix): Bitmap {
        val bitmap = Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(toPixels(matrix), 0, matrix.width, 0, 0, matrix.width, matrix.height)
        return bitmap
    }

    fun render(token: String, sizePx: Int): Bitmap = toBitmap(encode(token, sizePx))

    // Không dùng android.graphics.Color trong toPixels để test JVM không gọi vào stub Android.
    private const val BLACK = 0xFF000000.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()
}
