package com.example.booking_app.core.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import com.example.booking_app.core.di.DefaultDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.max

/**
 * Quy tắc nén ảnh tải lên (spec 070 đánh giá, spec 090 bảng tin): cạnh dài ≤ 1600 px, JPEG chất lượng 80,
 * sau nén phải ≤ 2 MB. Hàm thuần, test được trên JVM.
 */
object ImageCompressionRules {
    const val MAX_SIDE = 1600
    const val JPEG_QUALITY = 80
    const val MAX_BYTES = 2L * 1024 * 1024

    /** Thu về cạnh dài tối đa [MAX_SIDE], giữ tỉ lệ; ảnh nhỏ hơn thì giữ nguyên. Mỗi cạnh tối thiểu 1 px. */
    fun targetSize(width: Int, height: Int): Pair<Int, Int> {
        val longSide = max(width, height)
        if (longSide <= MAX_SIDE) return width to height
        val scale = MAX_SIDE.toDouble() / longSide
        return Math.round(width * scale).toInt().coerceAtLeast(1) to Math.round(height * scale).toInt().coerceAtLeast(1)
    }

    /** `true` khi ảnh đã nén vượt 2 MB (đúng 2 MB vẫn hợp lệ). */
    fun isTooLarge(byteCount: Int): Boolean = byteCount > MAX_BYTES
}

/** Ảnh sau nén vẫn lớn hơn 2 MB. */
class ImageTooLargeException : Exception("Ảnh lớn hơn 2 MB sau khi nén")

/** Không đọc hoặc giải mã được ảnh (hỏng, định dạng lạ). */
class ImageUnreadableException(cause: Throwable? = null) : Exception("Không đọc được ảnh", cause)

/** Đọc ảnh từ `Uri` (Photo Picker, FileProvider), thu nhỏ và nén JPEG theo [ImageCompressionRules]. */
interface ImageCompressor {
    /** @throws ImageUnreadableException khi không giải mã được; @throws ImageTooLargeException khi sau nén > 2 MB. */
    suspend fun compress(uri: String): ByteArray
}

class AndroidImageCompressor @Inject constructor(
    @ApplicationContext private val context: Context,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher,
) : ImageCompressor {

    override suspend fun compress(uri: String): ByteArray = withContext(dispatcher) {
        val bitmap = try {
            val source = ImageDecoder.createSource(context.contentResolver, Uri.parse(uri))
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val (width, height) = ImageCompressionRules.targetSize(info.size.width, info.size.height)
                if (width != info.size.width || height != info.size.height) decoder.setTargetSize(width, height)
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw ImageUnreadableException(e)
        }
        val bytes = ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, ImageCompressionRules.JPEG_QUALITY, out)
            bitmap.recycle()
            out.toByteArray()
        }
        if (ImageCompressionRules.isTooLarge(bytes.size)) throw ImageTooLargeException()
        bytes
    }
}
