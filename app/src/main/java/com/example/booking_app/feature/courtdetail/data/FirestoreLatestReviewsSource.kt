package com.example.booking_app.feature.courtdetail.data

import com.example.booking_app.core.media.PhotoUrlResolver
import com.example.booking_app.core.network.AppError
import com.example.booking_app.core.network.awaitWithTimeout
import com.example.booking_app.core.network.toAppError
import com.example.booking_app.feature.courtdetail.domain.LatestReviewsSource
import com.example.booking_app.feature.courtdetail.domain.model.ReviewPreview
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import javax.inject.Inject

/**
 * Bản tạm của [LatestReviewsSource] khi 070 chưa có `ReviewRepository.latestVisible` (research R3, T055).
 * Truy vấn dùng index `reviews` (`venueId`, `status`, `createdAt desc`) đã có trong `firestore.indexes.json`.
 *
 * Rules hiện tại chỉ cho người đã đăng nhập đọc `reviews`, nên khách nhận `PERMISSION_DENIED` → [AppError.NotSignedIn]
 * và chỉ mục đánh giá báo lỗi (research R10, bảng phụ thuộc C).
 */
// TODO(C): thay bằng ReviewRepository.latestVisible (research R3)
class FirestoreLatestReviewsSource @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val resolver: PhotoUrlResolver,
) : LatestReviewsSource {

    override suspend fun latest(venueId: String, limit: Int): List<ReviewPreview> {
        val snapshot = try {
            firestore.collection("reviews")
                .whereEqualTo("venueId", venueId)
                .whereEqualTo("status", STATUS_VISIBLE)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .get()
                .awaitWithTimeout()
        } catch (e: FirebaseFirestoreException) {
            throw when (e.code) {
                FirebaseFirestoreException.Code.PERMISSION_DENIED -> AppError.NotSignedIn
                FirebaseFirestoreException.Code.UNAVAILABLE -> AppError.Network
                FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> AppError.Timeout
                else -> e.toAppError()
            }
        }
        return snapshot.documents.mapNotNull { doc -> doc.data?.let { ReviewPreviewMapper.map(doc.id, it, resolver) } }
    }

    private companion object {
        const val STATUS_VISIBLE = "VISIBLE"
    }
}
