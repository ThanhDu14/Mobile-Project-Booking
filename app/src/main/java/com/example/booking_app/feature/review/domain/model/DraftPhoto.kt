package com.example.booking_app.feature.review.domain.model

/** Ảnh đang đính kèm khi viết đánh giá; [remotePath] có sau khi tải lên xong. */
data class DraftPhoto(
    val localUri: String,
    val remotePath: String? = null,
    val state: State = State.PENDING,
) {
    enum class State { PENDING, UPLOADING, UPLOADED, FAILED }
}
