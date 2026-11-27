package com.example.booking_app.feature.payment.data

import com.example.booking_app.feature.payment.domain.model.PayoutAccount
import com.example.booking_app.feature.payment.domain.repository.VenuePayoutRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class FirestoreVenuePayoutRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) : VenuePayoutRepository {

    override suspend fun payoutAccount(venueId: String): Result<PayoutAccount?> = runCatching {
        val raw = firestore.collection("venues").document(venueId).get().await().get("payoutAccount")
        toPayoutAccount(raw as? Map<*, *>)
    }

    internal companion object {
        /** Chỉ coi là có tài khoản nhận tiền khi đủ cả 4 trường không rỗng. */
        fun toPayoutAccount(map: Map<*, *>?): PayoutAccount? {
            map ?: return null
            fun field(name: String) = (map[name] as? String)?.trim()?.takeIf { it.isNotEmpty() }
            return PayoutAccount(
                bankBin = field("bankBin") ?: return null,
                bankShortName = field("bankShortName") ?: return null,
                accountNumber = field("accountNumber") ?: return null,
                accountHolderName = field("accountHolderName") ?: return null,
            )
        }
    }
}
