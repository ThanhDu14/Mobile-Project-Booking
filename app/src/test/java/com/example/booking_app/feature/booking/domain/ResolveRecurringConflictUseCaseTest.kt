package com.example.booking_app.feature.booking.domain

import com.example.booking_app.feature.booking.domain.model.RecurringSession
import com.example.booking_app.feature.booking.domain.model.RecurringSessionStatus
import com.example.booking_app.feature.booking.domain.usecase.CalculateRecurringPriceUseCase
import com.example.booking_app.feature.booking.domain.usecase.ConflictAction
import com.example.booking_app.feature.booking.domain.usecase.ConflictResolutionResult
import com.example.booking_app.feature.booking.domain.usecase.ResolveRecurringConflictUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class ResolveRecurringConflictUseCaseTest {

    private lateinit var useCase: ResolveRecurringConflictUseCase
    private val priceUseCase = CalculateRecurringPriceUseCase()

    @Before
    fun setUp() {
        useCase = ResolveRecurringConflictUseCase(priceUseCase)
    }

    private fun createDummySessions(count: Int, conflictIndex: Int? = null): List<RecurringSession> {
        return (1..count).map { i ->
            val isConflict = (i == conflictIndex)
            RecurringSession(
                date = LocalDate.of(2026, 11, i),
                startTime = "1800",
                endTime = "2000",
                courtId = "court-01",
                courtName = "Sân 1",
                surfaceType = "PU",
                slotIds = listOf("court-01_2026110${i}_1800", "court-01_2026110${i}_1830"),
                price = 200_000L,
                status = if (isConflict) RecurringSessionStatus.SUGGEST_CHANGE_COURT else RecurringSessionStatus.AVAILABLE
            )
        }
    }

    @Test
    fun changeCourtAction_updatesCourtAndPreservesPrice() {
        val sessions = createDummySessions(6, conflictIndex = 2)
        val targetDate = LocalDate.of(2026, 11, 2)

        val result = useCase(
            currentSessions = sessions,
            targetDate = targetDate,
            action = ConflictAction.ChangeCourt(
                newCourtId = "court-02",
                newCourtName = "Sân 2",
                newSurfaceType = "PU"
            )
        )

        assertTrue(result is ConflictResolutionResult.Success)
        val success = result as ConflictResolutionResult.Success
        val updatedSession = success.updatedSessions.first { it.date == targetDate }

        assertEquals("court-02", updatedSession.courtId)
        assertEquals("Sân 2", updatedSession.courtName)
        assertEquals("PU", updatedSession.surfaceType)
        assertEquals(RecurringSessionStatus.AVAILABLE, updatedSession.status)
        assertTrue(updatedSession.isCourtChanged)
        // Đơn giá buổi giữ nguyên 200.000 đ theo phương án 1.A
        assertEquals(200_000L, updatedSession.price)
        assertEquals(6, success.summary.totalSessions)
        assertEquals(1_200_000L, success.summary.subtotalAmount)
    }

    @Test
    fun skipSessionAction_reducesSessionCountAndDeductsPrice() {
        val sessions = createDummySessions(6, conflictIndex = 3)
        val targetDate = LocalDate.of(2026, 11, 3)

        val result = useCase(
            currentSessions = sessions,
            targetDate = targetDate,
            action = ConflictAction.SkipSession
        )

        assertTrue(result is ConflictResolutionResult.Success)
        val success = result as ConflictResolutionResult.Success

        // Buổi bị bỏ phải có status EXCLUDED / isExcluded = true
        val skippedSession = success.updatedSessions.first { it.date == targetDate }
        assertTrue(skippedSession.isExcluded || skippedSession.status == RecurringSessionStatus.EXCLUDED)

        // Tổng số buổi hợp lệ giảm từ 6 xuống 5
        assertEquals(5, success.summary.totalSessions)
        // Tổng tiền giảm 200.000 đ
        assertEquals(1_000_000L, success.summary.subtotalAmount)
    }

    @Test
    fun skipSessionAction_resultingInLessThan4Sessions_failsWithMinSessionError() {
        // Có đúng 4 buổi ban đầu, bỏ 1 buổi -> còn 3 buổi (< 4)
        val sessions = createDummySessions(4, conflictIndex = 4)
        val targetDate = LocalDate.of(2026, 11, 4)

        val result = useCase(
            currentSessions = sessions,
            targetDate = targetDate,
            action = ConflictAction.SkipSession
        )

        assertTrue(result is ConflictResolutionResult.ErrorLessThanMinSessions)
        val error = result as ConflictResolutionResult.ErrorLessThanMinSessions
        assertEquals(3, error.validCount)
    }
}
