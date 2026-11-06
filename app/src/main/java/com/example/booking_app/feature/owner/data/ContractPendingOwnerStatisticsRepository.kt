package com.example.booking_app.feature.owner.data

import com.example.booking_app.feature.owner.domain.statistics.OwnerStatisticsContractUnavailableException
import com.example.booking_app.feature.owner.domain.statistics.OwnerStatisticsQuery
import com.example.booking_app.feature.owner.domain.statistics.OwnerStatisticsRepository
import com.example.booking_app.feature.owner.domain.statistics.OwnerStatisticsResult
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** No client booking reads and no locally fabricated aggregates while the server query is pending. */
class ContractPendingOwnerStatisticsRepository @Inject constructor() : OwnerStatisticsRepository {
    override fun observe(venueId: String, query: OwnerStatisticsQuery): Flow<OwnerStatisticsResult?> = flow {
        throw OwnerStatisticsContractUnavailableException()
    }
}
