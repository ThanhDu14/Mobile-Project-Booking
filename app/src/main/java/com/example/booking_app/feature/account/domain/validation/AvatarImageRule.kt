package com.example.booking_app.feature.account.domain.validation

import com.example.booking_app.feature.account.domain.model.AccountError

/** Ảnh đại diện (research R4): định dạng, cỡ sau nén, kích thước thu nhỏ và các mức chất lượng JPEG. */
object AvatarImageRule {
    const val MAX_BYTES = 2_097_152
    const val MAX_DIMENSION = 512
    val SUPPORTED_MIME = setOf("image/jpeg", "image/png", "image/webp")

    /** Bắt đầu 85, giảm 10 mỗi lần, tối thiểu 50. */
    val QUALITY_STEPS = listOf(85, 75, 65, 55, 50)

    fun mimeError(mime: String?): AccountError? = if (mime in SUPPORTED_MIME) null else AccountError.UnsupportedImage

    fun sizeError(byteCount: Int): AccountError? = if (byteCount <= MAX_BYTES) null else AccountError.ImageTooLarge

    /** Thu về cạnh dài tối đa [MAX_DIMENSION], giữ tỉ lệ; ảnh nhỏ hơn thì giữ nguyên. */
    fun targetSize(width: Int, height: Int): Pair<Int, Int> {
        val longest = maxOf(width, height)
        if (longest <= MAX_DIMENSION) return width to height
        val scale = MAX_DIMENSION.toDouble() / longest
        return Math.round(width * scale).toInt() to Math.round(height * scale).toInt()
    }
}
