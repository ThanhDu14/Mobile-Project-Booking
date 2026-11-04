package com.example.booking_app.feature.account

import com.example.booking_app.feature.account.data.AccountFunctionsApi
import com.example.booking_app.feature.account.domain.model.DeletionImpact
import com.example.booking_app.feature.account.domain.model.ImpactBooking
import java.time.Instant

/**
 * Ghi lại lời gọi tới các Edge Function của 011: `"sync"`, `"preview"`, `"confirm:<version>:<text>"`.
 * [confirmResults] lấy lần lượt cho từng lần `confirm`; hết thì thành công.
 */
open class FakeAccountFunctionsApi : AccountFunctionsApi {
    val calls = mutableListOf<String>()
    var syncResult: suspend () -> Boolean = { true }
    var previewResult: suspend () -> DeletionImpact = { IMPACT }
    val confirmResults = ArrayDeque<suspend () -> Unit>()

    override suspend fun syncAccountEmail(): Boolean {
        calls += "sync"
        return syncResult()
    }

    override suspend fun previewDeletion(): DeletionImpact {
        calls += "preview"
        return previewResult()
    }

    override suspend fun confirmDeletion(impactVersion: String, confirmText: String) {
        calls += "confirm:$impactVersion:$confirmText"
        confirmResults.removeFirstOrNull()?.invoke()
    }

    companion object {
        val IMPACT = DeletionImpact(
            impactVersion = "sha256:v1",
            myBookings = listOf(
                ImpactBooking("b1", "Sân cầu lông Hòa Bình", Instant.parse("2026-11-10T12:00:00Z"), "PENDING", 0, 0),
                ImpactBooking("b2", "Sân Kỳ Hòa Center", Instant.parse("2026-11-12T11:00:00Z"), "CONFIRMED", 60_000, 60_000),
            ),
            myDropIns = emptyList(),
            waitlistCount = 0,
            ownerImpact = null,
            walletBalance = 50_000,
        )
    }
}
