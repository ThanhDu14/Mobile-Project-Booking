package com.example.booking_app.feature.admin.domain.repository

import com.example.booking_app.feature.admin.domain.model.AdminOwnerApplication
import com.example.booking_app.feature.admin.domain.model.ApplicationReviewDecision
import kotlinx.coroutines.flow.Flow

interface AdminApplicationRepository {
    fun observePendingApplications(): Flow<List<AdminOwnerApplication>>
    suspend fun getApplication(applicationId: String): Result<AdminOwnerApplication>
    suspend fun reviewApplication(decision: ApplicationReviewDecision): Result<Unit>
    suspend fun getSignedDocumentUrl(applicationId: String, path: String): Result<String>
}
