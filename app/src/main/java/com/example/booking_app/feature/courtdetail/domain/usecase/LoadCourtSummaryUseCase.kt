package com.example.booking_app.feature.courtdetail.domain.usecase

import com.example.booking_app.feature.courtdetail.domain.CourtSummaries
import com.example.booking_app.feature.courtdetail.domain.VenueDetailRepository
import com.example.booking_app.feature.courtdetail.domain.model.CourtSummary
import javax.inject.Inject

class LoadCourtSummaryUseCase @Inject constructor(
    private val repository: VenueDetailRepository,
) {
    /** Ném [com.example.booking_app.core.network.AppError]. */
    suspend operator fun invoke(venueId: String): CourtSummary = CourtSummaries.from(repository.loadCourts(venueId))
}
