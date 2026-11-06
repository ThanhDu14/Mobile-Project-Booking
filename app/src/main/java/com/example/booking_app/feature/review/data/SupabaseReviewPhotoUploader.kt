package com.example.booking_app.feature.review.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import com.example.booking_app.core.network.toAppError
import com.example.booking_app.core.session.SessionManager
import com.example.booking_app.feature.review.domain.ReviewError
import com.example.booking_app.feature.review.domain.ReviewException
import com.example.booking_app.feature.review.domain.ReviewPhotoTooLargeException
import com.example.booking_app.feature.review.domain.ReviewPhotoUploader
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * T043: đọc ảnh từ `Uri` (Photo Picker hoặc FileProvider của camera), thu nhỏ cạnh dài ≤ 1600 px,
 * nén JPEG chất lượng 80 rồi tải lên `review-photos/{uid}/{bookingId}/{uuid}.jpg`. RLS chỉ cho ghi
 * vào thư mục `{uid}/…` của chính mình (migration `*_review_photos_bucket.sql`).
 *
 * Trạng thái từng ảnh (`UPLOADING` → `UPLOADED`/`FAILED`) do `WriteReviewViewModel` giữ, nên hàm ở đây
 * là `suspend` thay vì trả `Flow`.
 */
class SupabaseReviewPhotoUploader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val supabase: SupabaseClient,
    private val sessionManager: SessionManager,
) : ReviewPhotoUploader {

    override suspend fun upload(bookingId: String, localUri: String): String {
        val uid = sessionManager.currentUid() ?: throw ReviewException(ReviewError.UNAUTHENTICATED)
        val jpeg = withContext(Dispatchers.Default) { compress(Uri.parse(localUri)) }
        if (jpeg.size > ReviewPhotoUploader.MAX_BYTES) throw ReviewPhotoTooLargeException()

        val path = "$uid/$bookingId/${UUID.randomUUID()}.jpg"
        try {
            supabase.storage.from(BUCKET).upload(path, jpeg) {
                upsert = false
                contentType = ContentType.Image.JPEG
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw ReviewException(e.toAppError().toReviewError(), e)
        }
        return path
    }

    private fun compress(uri: Uri): ByteArray {
        val bitmap = try {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val longSide = max(info.size.width, info.size.height)
                if (longSide > MAX_SIDE) {
                    val scale = MAX_SIDE.toFloat() / longSide
                    decoder.setTargetSize(
                        (info.size.width * scale).roundToInt().coerceAtLeast(1),
                        (info.size.height * scale).roundToInt().coerceAtLeast(1),
                    )
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } catch (e: Exception) {
            throw ReviewException(ReviewError.INVALID_ARGUMENT, e)
        }
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            bitmap.recycle()
            out.toByteArray()
        }
    }

    private companion object {
        const val BUCKET = "review-photos"
        const val MAX_SIDE = 1600
        const val JPEG_QUALITY = 80
    }
}
