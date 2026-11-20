package com.example.booking_app.feature.review.data

import com.example.booking_app.core.image.ImageCompressor
import com.example.booking_app.core.image.ImageTooLargeException
import com.example.booking_app.core.image.ImageUnreadableException
import com.example.booking_app.core.network.toAppError
import com.example.booking_app.core.session.SessionManager
import com.example.booking_app.feature.review.domain.ReviewError
import com.example.booking_app.feature.review.domain.ReviewException
import com.example.booking_app.feature.review.domain.ReviewPhotoTooLargeException
import com.example.booking_app.feature.review.domain.ReviewPhotoUploader
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlinx.coroutines.CancellationException
import java.util.UUID
import javax.inject.Inject

/**
 * T043: đọc ảnh từ `Uri` (Photo Picker hoặc FileProvider của camera), nén bằng [ImageCompressor] (cạnh dài ≤ 1600 px,
 * JPEG chất lượng 80, dùng chung với spec 090) rồi tải lên `review-photos/{uid}/{bookingId}/{uuid}.jpg`. RLS chỉ cho ghi
 * vào thư mục `{uid}/…` của chính mình (migration `*_review_photos_bucket.sql`).
 *
 * Trạng thái từng ảnh (`UPLOADING` → `UPLOADED`/`FAILED`) do `WriteReviewViewModel` giữ, nên hàm ở đây
 * là `suspend` thay vì trả `Flow`.
 */
class SupabaseReviewPhotoUploader @Inject constructor(
    private val imageCompressor: ImageCompressor,
    private val supabase: SupabaseClient,
    private val sessionManager: SessionManager,
) : ReviewPhotoUploader {

    override suspend fun upload(bookingId: String, localUri: String): String {
        val uid = sessionManager.currentUid() ?: throw ReviewException(ReviewError.UNAUTHENTICATED)
        val jpeg = try {
            imageCompressor.compress(localUri)
        } catch (e: ImageTooLargeException) {
            throw ReviewPhotoTooLargeException()
        } catch (e: ImageUnreadableException) {
            throw ReviewException(ReviewError.INVALID_ARGUMENT, e)
        }

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

    private companion object {
        const val BUCKET = "review-photos"
    }
}
