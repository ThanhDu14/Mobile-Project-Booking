package com.example.booking_app.core.session

import kotlinx.coroutines.flow.Flow

interface UserProfileReader {
    /** Hồ sơ của người đang đăng nhập; `null` khi là khách hoặc document chưa có. */
    fun observeMyProfile(): Flow<MyProfile?>
}
