package com.example.booking_app.feature.account.data

/** Giá trị đánh dấu "ghi giờ server"; `FirestoreAccountDataSource` đổi thành `FieldValue.serverTimestamp()`. */
data object ServerTimestamp

interface AccountDataSource {
    /** Cập nhật các trường của `users/{uid}`. Ném `AppError`. */
    suspend fun update(uid: String, fields: Map<String, Any?>)
}
