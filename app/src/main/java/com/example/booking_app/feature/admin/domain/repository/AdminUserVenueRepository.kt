package com.example.booking_app.feature.admin.domain.repository

import com.example.booking_app.feature.admin.domain.model.AdminUserLookup
import com.example.booking_app.feature.admin.domain.model.AdminUserPage
import com.example.booking_app.feature.admin.domain.model.AdminVenueItem
import com.example.booking_app.feature.admin.domain.model.OwnerRevocationDecision
import com.example.booking_app.feature.admin.domain.model.UserAccountStatus

interface AdminUserVenueRepository {
    suspend fun lookupUser(query: String): Result<AdminUserLookup?>
    suspend fun listUsers(query: String, filter: String, pageToken: String): Result<AdminUserPage> = Result.success(AdminUserPage(emptyList()))
    suspend fun setAccountStatus(uid: String, status: UserAccountStatus): Result<Unit>
    suspend fun revokeOwner(decision: OwnerRevocationDecision): Result<Unit>
    suspend fun getVenue(venueId: String): Result<AdminVenueItem?>
    suspend fun setVenueStatus(venueId: String, status: String): Result<Unit>
}
