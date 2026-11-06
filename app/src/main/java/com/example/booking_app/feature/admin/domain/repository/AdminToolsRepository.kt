package com.example.booking_app.feature.admin.domain.repository

import com.example.booking_app.feature.admin.domain.model.AdminDistrict
import com.example.booking_app.feature.admin.domain.model.AdminSystemStats
import com.example.booking_app.feature.admin.domain.model.AdminSystemVoucher
import com.example.booking_app.feature.admin.domain.model.BroadcastNotificationRequest

interface AdminToolsRepository {
    suspend fun getSystemStats(): Result<AdminSystemStats>
    suspend fun getSystemVouchers(): Result<List<AdminSystemVoucher>>
    suspend fun createSystemVoucher(voucher: AdminSystemVoucher): Result<Unit>
    suspend fun toggleVoucherActive(code: String, isActive: Boolean): Result<Unit>
    suspend fun getDistricts(): Result<List<AdminDistrict>>
    suspend fun saveDistrict(district: AdminDistrict): Result<Unit>
    suspend fun broadcastSystemNotification(request: BroadcastNotificationRequest): Result<Int>
}
