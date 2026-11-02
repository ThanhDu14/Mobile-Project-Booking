package com.example.booking_app.feature.account.domain

import com.example.booking_app.feature.account.domain.model.ProfileDraft

/** Ghi hồ sơ của người đang đăng nhập (`users/{uid}`). Lỗi mạng là `AccountError.Network`. */
interface AccountRepository {
    /** Chỉ ghi các trường khác [saved], kèm `updatedAt`. Trả `false` nếu không có gì để ghi. */
    suspend fun updateProfile(saved: ProfileDraft, draft: ProfileDraft): Boolean

    suspend fun setAvatarPath(path: String?)
}
