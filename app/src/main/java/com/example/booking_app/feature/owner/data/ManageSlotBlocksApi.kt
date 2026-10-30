package com.example.booking_app.feature.owner.data

import com.example.booking_app.core.network.FunctionErrors
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import kotlinx.serialization.Serializable
import javax.inject.Inject

@Serializable
data class ManageSlotBlocksRequest(
    val venueId: String,
    val courtId: String,
    val action: String,
    val date: String? = null,
    val slotIds: List<String>,
    val blockReason: String? = null
)

interface ManageSlotBlocksApi {
    suspend fun lockSlots(
        venueId: String,
        courtId: String,
        date: String,
        slotIds: List<String>,
        reason: String
    ): Result<Unit>

    suspend fun unlockSlots(
        venueId: String,
        courtId: String,
        slotIds: List<String>
    ): Result<Unit>
}

class SupabaseManageSlotBlocksApi @Inject constructor(
    private val supabase: SupabaseClient,
    private val functionErrors: FunctionErrors
) : ManageSlotBlocksApi {
    override suspend fun lockSlots(
        venueId: String,
        courtId: String,
        date: String,
        slotIds: List<String>,
        reason: String
    ): Result<Unit> = runCatching {
        functionErrors.invokeWithAuthRetry {
            supabase.functions.invoke(
                FUNCTION_NAME,
                body = ManageSlotBlocksRequest(
                    venueId = venueId,
                    courtId = courtId,
                    action = "LOCK",
                    date = date,
                    slotIds = slotIds,
                    blockReason = reason
                )
            )
        }
    }

    override suspend fun unlockSlots(
        venueId: String,
        courtId: String,
        slotIds: List<String>
    ): Result<Unit> = runCatching {
        functionErrors.invokeWithAuthRetry {
            supabase.functions.invoke(
                FUNCTION_NAME,
                body = ManageSlotBlocksRequest(
                    venueId = venueId,
                    courtId = courtId,
                    action = "UNLOCK",
                    slotIds = slotIds
                )
            )
        }
    }

    companion object {
        const val FUNCTION_NAME = "manage-slot-blocks"
    }
}
