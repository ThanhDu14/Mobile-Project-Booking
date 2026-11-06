package com.example.booking_app.feature.admin.domain.repository

import com.example.booking_app.feature.admin.domain.model.AdminReport
import com.example.booking_app.feature.admin.domain.model.ModerateReportDecision
import kotlinx.coroutines.flow.Flow

interface AdminReportRepository {
    fun observeOpenReports(): Flow<List<AdminReport>>
    suspend fun getReport(reportId: String): Result<AdminReport>
    suspend fun moderateReport(decision: ModerateReportDecision): Result<Unit>
    suspend fun getTargetSummary(targetPath: String): Result<Map<String, Any?>>
}
