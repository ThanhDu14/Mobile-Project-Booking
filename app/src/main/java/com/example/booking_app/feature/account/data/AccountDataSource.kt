package com.example.booking_app.feature.account.data

import com.example.booking_app.feature.account.domain.model.OwnerApplicationSummary
import kotlinx.coroutines.flow.Flow

/** Giá trị đánh dấu "ghi giờ server"; `FirestoreAccountDataSource` đổi thành `FieldValue.serverTimestamp()`. */
data object ServerTimestamp

interface AccountDataSource {
    /** Cập nhật các trường của `users/{uid}`. Ném `AppError`. */
    suspend fun update(uid: String, fields: Map<String, Any?>)

    /**
     * Listener `ownerApplications where uid == [uid] order by submittedAt desc limit 1` (research R6).
     * Rules chặn thì flow kết thúc bằng `AccountError.PermissionDenied`.
     */
    fun observeLatestOwnerApplication(uid: String): Flow<OwnerApplicationSummary?>
}
