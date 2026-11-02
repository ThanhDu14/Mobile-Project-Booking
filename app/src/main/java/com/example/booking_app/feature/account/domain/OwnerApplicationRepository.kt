package com.example.booking_app.feature.account.domain

import com.example.booking_app.feature.account.domain.model.OwnerApplicationSummary
import kotlinx.coroutines.flow.Flow

/** Hồ sơ chủ sân gần nhất của người đang đăng nhập. Rules chặn hoặc mất mạng thì flow ném `AccountError`. */
interface OwnerApplicationRepository {
    /** `null` là chưa có hồ sơ nào. */
    fun observeLatest(): Flow<OwnerApplicationSummary?>
}
