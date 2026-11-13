package com.example.booking_app.feature.admin.domain.repository

import com.example.booking_app.feature.admin.domain.model.AdminDistrict
import com.example.booking_app.feature.admin.domain.model.AdminCategoryKind
import com.example.booking_app.feature.admin.domain.model.AdminSystemStats
import com.example.booking_app.feature.admin.domain.model.AdminSystemVoucher
import com.example.booking_app.feature.admin.domain.model.BroadcastNotificationRequest

interface AdminToolsRepository {
    suspend fun getSystemStats(): Result<AdminSystemStats>
    suspend fun getSystemStats(date: String): Result<AdminSystemStats> = getSystemStats()
    suspend fun getSystemVouchers(): Result<List<AdminSystemVoucher>>
    suspend fun createSystemVoucher(voucher: AdminSystemVoucher): Result<Unit>
    suspend fun updateSystemVoucher(voucher: AdminSystemVoucher): Result<Unit> = Result.failure(UnsupportedOperationException())
    suspend fun toggleVoucherActive(code: String, isActive: Boolean): Result<Unit>
    suspend fun getDistricts(): Result<List<AdminDistrict>>
    suspend fun saveDistrict(district: AdminDistrict): Result<Unit>
    suspend fun getCategories(kind: AdminCategoryKind): Result<List<AdminDistrict>> = getDistricts()
    suspend fun broadcastSystemNotification(request: BroadcastNotificationRequest): Result<Int>
}
