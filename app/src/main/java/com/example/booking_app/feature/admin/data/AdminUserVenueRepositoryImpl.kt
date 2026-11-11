package com.example.booking_app.feature.admin.data

import com.example.booking_app.core.network.FunctionErrors
import com.example.booking_app.feature.admin.domain.model.AdminUserLookup
import com.example.booking_app.feature.admin.domain.model.AdminVenueItem
import com.example.booking_app.feature.admin.domain.model.OwnerRevocationDecision
import com.example.booking_app.feature.admin.domain.model.UserAccountStatus
import com.example.booking_app.feature.admin.domain.repository.AdminUserVenueRepository
import com.google.firebase.firestore.FirebaseFirestore
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.ktor.client.call.body
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.Serializable
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class AdminUserDto(
    val uid: String? = null,
    val fullName: String? = null,
    val phone: String? = null,
    val role: String? = null,
    val ownerStatus: String? = null,
    val accountStatus: String? = null,
    val ownerRevokedReason: String? = null,
    val createdAt: String? = null,
)

@Serializable
data class AdminManageUserRequest(
    val action: String,
    val query: String? = null,
    val uid: String? = null,
    val accountStatus: String? = null,
)

@Serializable
data class AdminManageUserResponse(
    val success: Boolean = false,
    val user: AdminUserDto? = null,
    val uid: String? = null,
    val accountStatus: String? = null,
    val alreadyCommitted: Boolean = false,
)

@Serializable
data class AdminRevokeOwnerRequest(
    val uid: String,
    val reason: String,
)

@Serializable
data class AdminRevokeOwnerResponse(
    val success: Boolean = false,
    val uid: String? = null,
    val alreadyCommitted: Boolean = false,
)

@Serializable
data class AdminManageVenueRequest(
    val venueId: String,
    val status: String,
)

@Serializable
data class AdminManageVenueResponse(
    val success: Boolean = false,
    val venueId: String? = null,
    val status: String? = null,
    val alreadyCommitted: Boolean = false,
)

@Singleton
class AdminUserVenueRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val supabase: SupabaseClient,
    private val functionErrors: FunctionErrors,
) : AdminUserVenueRepository {

    override suspend fun lookupUser(query: String): Result<AdminUserLookup?> = runCatching {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@runCatching null

        val response = functionErrors.invokeWithAuthRetry {
            supabase.functions.invoke(
                FUNCTION_MANAGE_USER,
                body = AdminManageUserRequest(
                    action = "LOOKUP",
                    query = trimmed,
                ),
            ).body<AdminManageUserResponse>()
        }

        val dto = response.user ?: return@runCatching null
        AdminUserLookup(
            uid = dto.uid ?: trimmed,
            phone = dto.phone.orEmpty(),
            fullName = dto.fullName.orEmpty(),
            role = dto.role ?: "PLAYER",
            ownerStatus = dto.ownerStatus ?: "NONE",
            accountStatus = UserAccountStatus.fromString(dto.accountStatus),
            ownerRevokedReason = dto.ownerRevokedReason,
            createdAt = dto.createdAt,
        )
    }

    override suspend fun setAccountStatus(uid: String, status: UserAccountStatus): Result<Unit> = runCatching {
        functionErrors.invokeWithAuthRetry {
            supabase.functions.invoke(
                FUNCTION_MANAGE_USER,
                body = AdminManageUserRequest(
                    action = "SET_ACCOUNT_STATUS",
                    uid = uid,
                    accountStatus = status.name,
                ),
            ).body<AdminManageUserResponse>()
        }
    }

    override suspend fun revokeOwner(decision: OwnerRevocationDecision): Result<Unit> = runCatching {
        val err = decision.validate()
        if (err != null) throw IllegalArgumentException(err)

        functionErrors.invokeWithAuthRetry {
            supabase.functions.invoke(
                FUNCTION_REVOKE_OWNER,
                body = AdminRevokeOwnerRequest(
                    uid = decision.uid,
                    reason = decision.reason.trim(),
                ),
            ).body<AdminRevokeOwnerResponse>()
        }
    }

    override suspend fun getVenue(venueId: String): Result<AdminVenueItem?> = runCatching {
        val trimmed = venueId.trim()
        if (trimmed.isEmpty()) return@runCatching null

        val doc = firestore.collection("venues").document(trimmed).get().await()
        if (!doc.exists()) return@runCatching null

        AdminVenueItem(
            venueId = doc.id,
            name = doc.getString("name").orEmpty(),
            address = doc.getString("address").orEmpty(),
            status = doc.getString("status") ?: "ACTIVE",
            ownerId = doc.getString("ownerId").orEmpty(),
        )
    }

    override suspend fun setVenueStatus(venueId: String, status: String): Result<Unit> = runCatching {
        functionErrors.invokeWithAuthRetry {
            supabase.functions.invoke(
                FUNCTION_MANAGE_VENUE,
                body = AdminManageVenueRequest(
                    venueId = venueId.trim(),
                    status = status.trim().uppercase(Locale.ROOT),
                ),
            ).body<AdminManageVenueResponse>()
        }
    }

    companion object {
        private const val FUNCTION_MANAGE_USER = "admin-manage-user"
        private const val FUNCTION_REVOKE_OWNER = "admin-revoke-owner"
        private const val FUNCTION_MANAGE_VENUE = "admin-manage-venue"
    }
}
