package com.example.booking_app.feature.booking.domain.usecase

import com.example.booking_app.feature.booking.domain.model.Court
import com.example.booking_app.feature.booking.domain.model.PriceRule
import com.example.booking_app.feature.booking.domain.model.RecurringSession
import com.example.booking_app.feature.booking.domain.model.RecurringSessionStatus
import com.example.booking_app.feature.booking.domain.model.SlotStatus
import com.example.booking_app.feature.booking.domain.repository.RecurringBookingRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

class CheckRecurringAvailabilityUseCase @Inject constructor(
    private val repository: RecurringBookingRepository,
    private val calculatePriceUseCase: CalculateRecurringPriceUseCase = CalculateRecurringPriceUseCase()
) {
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

    suspend operator fun invoke(
        venueId: String,
        preferredCourtId: String,
        dates: List<LocalDate>,
        startTime: String,
        endTime: String,
        allCourts: List<Court>,
        priceRules: List<PriceRule>
    ): Result<List<RecurringSession>> {
        val dateStrings = dates.map { it.format(dateFormatter) }
        val busySlotsResult = repository.getBusySlotsForDates(venueId, dateStrings)
        if (busySlotsResult.isFailure) {
            return Result.failure(busySlotsResult.exceptionOrNull() ?: RuntimeException("Không thể quét khả dụng"))
        }

        val allBusySlots = busySlotsResult.getOrNull().orEmpty()
        val preferredCourt = allCourts.firstOrNull { it.id == preferredCourtId }
            ?: return Result.failure(IllegalArgumentException("Không tìm thấy sân ưu tiên"))

        val slotTimes = generateSlotTimes(startTime, endTime)
        val sessions = mutableListOf<RecurringSession>()

        for (date in dates) {
            val dateStr = date.format(dateFormatter)
            val busySlotsForDate = allBusySlots.filter { it.date == dateStr }

            val price = calculatePriceUseCase.calculateSessionPrice(
                dayOfWeek = date.dayOfWeek.value,
                startTime = startTime,
                endTime = endTime,
                priceRules = priceRules
            )

            // Kiểm tra xem sân có bị vướng bất kỳ slot nào trong khung giờ không
            fun isCourtBusy(courtId: String): Boolean {
                return busySlotsForDate.any { slot ->
                    slot.courtId == courtId &&
                        slot.startTime in slotTimes &&
                        (slot.status == SlotStatus.BOOKED ||
                         slot.status == SlotStatus.BLOCKED ||
                         (slot.status == SlotStatus.HELD && (slot.holdExpiresAtMillis ?: 0L) > System.currentTimeMillis()))
                }
            }

            val preferredCourtBusy = isCourtBusy(preferredCourtId)

            if (!preferredCourtBusy) {
                // Sân ưu tiên hoàn toàn trống
                sessions.add(
                    RecurringSession(
                        date = date,
                        startTime = startTime,
                        endTime = endTime,
                        courtId = preferredCourtId,
                        courtName = preferredCourt.name,
                        surfaceType = preferredCourt.surfaceType,
                        slotIds = slotTimes.map { "${preferredCourtId}_${dateStr}_$it" },
                        price = price,
                        status = RecurringSessionStatus.AVAILABLE,
                        originalCourtId = preferredCourtId,
                        suggestedCourts = emptyList()
                    )
                )
            } else {
                // Sân ưu tiên bị vướng slot -> Quét các sân con khác
                val otherCourts = allCourts.filter { it.id != preferredCourtId && it.isActive }
                val availableAlternateCourts = otherCourts.filter { !isCourtBusy(it.id) }
                    .sortedWith(
                        compareByDescending<Court> { it.surfaceType == preferredCourt.surfaceType }
                            .thenBy { it.sortOrder }
                    )

                if (availableAlternateCourts.isNotEmpty()) {
                    sessions.add(
                        RecurringSession(
                            date = date,
                            startTime = startTime,
                            endTime = endTime,
                            courtId = preferredCourtId,
                            courtName = preferredCourt.name,
                            surfaceType = preferredCourt.surfaceType,
                            slotIds = slotTimes.map { "${preferredCourtId}_${dateStr}_$it" },
                            price = price,
                            status = RecurringSessionStatus.SUGGEST_CHANGE_COURT,
                            originalCourtId = preferredCourtId,
                            suggestedCourts = availableAlternateCourts
                        )
                    )
                } else {
                    // Tất cả các sân con đều bị vướng ít nhất 1 slot
                    sessions.add(
                        RecurringSession(
                            date = date,
                            startTime = startTime,
                            endTime = endTime,
                            courtId = preferredCourtId,
                            courtName = preferredCourt.name,
                            surfaceType = preferredCourt.surfaceType,
                            slotIds = slotTimes.map { "${preferredCourtId}_${dateStr}_$it" },
                            price = price,
                            status = RecurringSessionStatus.MUST_SKIP,
                            originalCourtId = preferredCourtId,
                            suggestedCourts = emptyList()
                        )
                    )
                }
            }
        }

        return Result.success(sessions)
    }

    private fun generateSlotTimes(startTime: String, endTime: String): List<String> {
        val slots = mutableListOf<String>()
        var current = startTime.substring(0, 2).toInt() * 60 + startTime.substring(2, 4).toInt()
        val end = endTime.substring(0, 2).toInt() * 60 + endTime.substring(2, 4).toInt()

        while (current < end) {
            val h = current / 60
            val m = current % 60
            slots.add(String.format("%02d%02d", h, m))
            current += 30
        }
        return slots
    }
}
