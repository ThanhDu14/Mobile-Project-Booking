package com.example.booking_app.feature.owner.data

import com.example.booking_app.core.network.FunctionErrors
import com.example.booking_app.feature.owner.domain.voucher.*
import com.google.firebase.firestore.FirebaseFirestore
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.Serializable
import java.time.ZoneId
import javax.inject.Inject

@Serializable
internal data class OwnerVoucherRequest(
    val action: String, val venueId: String, val code: String,
    val type: String? = null, val value: Long? = null, val maxDiscount: Long? = null,
    val minOrderTotal: Long? = null, val validFrom: String? = null, val validTo: String? = null,
    val usageLimit: Int? = null, val perUserLimit: Int? = null, val isActive: Boolean,
)

internal fun OwnerVoucherDraft.toRequest(action: String, venueId: String) = OwnerVoucherRequest(
    action, venueId, code, type.name, value, maxDiscountVnd, minOrderTotalVnd,
    validFrom.atStartOfDay(VOUCHER_ZONE).toInstant().toString(),
    validTo.plusDays(1).atStartOfDay(VOUCHER_ZONE).toInstant().minusMillis(1).toString(),
    usageLimit, perUserLimit, isActive,
)

private val VOUCHER_ZONE = ZoneId.of("Asia/Ho_Chi_Minh")

class FirestoreOwnerVoucherRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val supabase: SupabaseClient,
    private val functionErrors: FunctionErrors,
) : OwnerVoucherRepository {
    override fun observeVouchers(venueId: String): Flow<List<OwnerVoucher>> = callbackFlow {
        val listener = firestore.collection("venues").document(venueId).collection("vouchers")
            .addSnapshotListener { snapshot, error ->
                if (error != null) close(error)
                else if (snapshot != null) {
                    try {
                        trySend(snapshot.documents.map { doc ->
                            OwnerVoucher(doc.id, OwnerVoucherType.valueOf(doc.getString("type") ?: "FIXED"),
                                doc.getLong("value") ?: 0, doc.getLong("maxDiscount") ?: 0,
                                doc.getLong("minOrderTotal") ?: 0,
                                requireNotNull(ownerTimestamp(doc.get("validFrom"))).toDate().toInstant().atZone(VOUCHER_ZONE).toLocalDate(),
                                requireNotNull(ownerTimestamp(doc.get("validTo"))).toDate().toInstant().atZone(VOUCHER_ZONE).toLocalDate(),
                                doc.getLong("usageLimit")?.toInt(), (doc.getLong("usedCount") ?: 0).toInt(),
                                doc.getLong("perUserLimit")?.toInt(), doc.getBoolean("isActive") ?: false)
                        })
                    } catch (failure: Exception) { close(failure) }
                }
            }
        awaitClose { listener.remove() }
    }

    override suspend fun create(venueId: String, draft: OwnerVoucherDraft): Result<Unit> = send(draft.toRequest("CREATE", venueId))
    override suspend fun update(venueId: String, code: String, draft: OwnerVoucherDraft): Result<Unit> =
        if (code != draft.code) Result.failure(IllegalArgumentException("Không được thay đổi mã voucher"))
        else send(draft.toRequest("UPDATE", venueId))
    override suspend fun setActive(venueId: String, code: String, isActive: Boolean): Result<Unit> =
        send(OwnerVoucherRequest("SET_ACTIVE", venueId, code, isActive = isActive))

    private suspend fun send(request: OwnerVoucherRequest): Result<Unit> = runCatching {
        functionErrors.invokeWithAuthRetry { supabase.functions.invoke("owner-create-voucher", body = request) }
        Unit
    }
}
