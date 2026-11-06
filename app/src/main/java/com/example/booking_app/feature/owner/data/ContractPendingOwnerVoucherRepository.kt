package com.example.booking_app.feature.owner.data

import com.example.booking_app.feature.owner.domain.voucher.OwnerVoucher
import com.example.booking_app.feature.owner.domain.voucher.OwnerVoucherContractUnavailableException
import com.example.booking_app.feature.owner.domain.voucher.OwnerVoucherDraft
import com.example.booking_app.feature.owner.domain.voucher.OwnerVoucherRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class ContractPendingOwnerVoucherRepository @Inject constructor() : OwnerVoucherRepository {
    override fun observeVouchers(venueId: String): Flow<List<OwnerVoucher>> = flow {
        throw OwnerVoucherContractUnavailableException()
    }

    override suspend fun create(venueId: String, draft: OwnerVoucherDraft): Result<Unit> =
        Result.failure(OwnerVoucherContractUnavailableException())

    override suspend fun update(venueId: String, code: String, draft: OwnerVoucherDraft): Result<Unit> =
        Result.failure(OwnerVoucherContractUnavailableException())

    override suspend fun setActive(venueId: String, code: String, isActive: Boolean): Result<Unit> =
        Result.failure(OwnerVoucherContractUnavailableException())
}
