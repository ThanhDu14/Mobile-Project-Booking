package com.example.booking_app.feature.booking.domain.usecase

import com.example.booking_app.feature.booking.domain.model.RecurringSession
import com.example.booking_app.feature.booking.domain.model.RecurringSessionStatus
import com.example.booking_app.feature.booking.domain.model.RecurringSummary
import java.time.LocalDate
import javax.inject.Inject

sealed interface ConflictAction {
    data class ChangeCourt(
        val newCourtId: String,
        val newCourtName: String,
        val newSurfaceType: String
    ) : ConflictAction

    object SkipSession : ConflictAction
}

sealed interface ConflictResolutionResult {
    data class Success(
        val updatedSessions: List<RecurringSession>,
        val summary: RecurringSummary
    ) : ConflictResolutionResult

    data class ErrorLessThanMinSessions(val validCount: Int) : ConflictResolutionResult
}

class ResolveRecurringConflictUseCase @Inject constructor(
    private val calculateRecurringPriceUseCase: CalculateRecurringPriceUseCase
) {
    operator fun invoke(
        currentSessions: List<RecurringSession>,
        targetDate: LocalDate,
        action: ConflictAction
    ): ConflictResolutionResult {
        val updatedSessions = currentSessions.map { session ->
            if (session.date == targetDate) {
                when (action) {
                    is ConflictAction.ChangeCourt -> {
                        val newSlotIds = session.slotIds.map { slotId ->
                            // Thay thế courtId cũ bằng newCourtId ở tiền tố slotId: {courtId}_{date}_{startTime}
                            val parts = slotId.split("_")
                            if (parts.size >= 3) {
                                "${action.newCourtId}_${parts[1]}_${parts[2]}"
                            } else slotId
                        }
                        session.copy(
                            courtId = action.newCourtId,
                            courtName = action.newCourtName,
                            surfaceType = action.newSurfaceType,
                            slotIds = newSlotIds,
                            status = RecurringSessionStatus.AVAILABLE
                        )
                    }
                    is ConflictAction.SkipSession -> {
                        session.copy(
                            status = RecurringSessionStatus.EXCLUDED,
                            isExcluded = true
                        )
                    }
                }
            } else {
                session
            }
        }

        val summary = calculateRecurringPriceUseCase.calculateSummary(updatedSessions)

        // Ràng buộc tối thiểu 4 buổi sau khi xử lý (FR-014)
        if (action is ConflictAction.SkipSession && summary.totalSessions < 4) {
            return ConflictResolutionResult.ErrorLessThanMinSessions(validCount = summary.totalSessions)
        }

        return ConflictResolutionResult.Success(
            updatedSessions = updatedSessions,
            summary = summary
        )
    }
}
