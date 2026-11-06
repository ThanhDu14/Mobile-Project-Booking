package com.example.booking_app.feature.owner.domain.dropin

import com.example.booking_app.feature.owner.domain.DropInEditableInput
import com.example.booking_app.feature.owner.domain.DropInValidation
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/** Owner safe projection; registration QR tokens and client-controlled counters are not part of this model. */
data class OwnerDropInSession(
    val sessionId: String,
    val venueId: String,
    val courtIds: List<String>,
    val startAt: Instant,
    val endAt: Instant,
    val capacity: Int,
    val registeredCount: Int,
    val pricePerPersonVnd: Long,
    val status: String,
)

data class OwnerDropInRegistration(
    val registrationId: String,
    val displayName: String,
    val partySize: Int,
    val status: String,
    val amountVnd: Long,
)

/** Editable values omit ownerId, registeredCount, status, amount and QR fields; server derives/validates them. */
data class OwnerDropInDraft(
    val venueId: String,
    val courtIds: List<String>,
    val date: String,
    val startTime: String,
    val endTime: String,
    val level: String,
    val capacity: Int,
    val pricePerPersonVnd: Long,
    val note: String,
)

object OwnerDropInDraftValidation {
    fun validate(draft: OwnerDropInDraft, registeredCount: Int = 0): List<String> = buildList {
        if (draft.venueId.isBlank()) add("Chọn cơ sở")
        if (draft.courtIds.isEmpty() || draft.courtIds.any(String::isBlank)) add("Chọn ít nhất một sân")
        if (draft.level.isBlank()) add("Chọn trình độ")
        addAll(DropInValidation.validate(DropInEditableInput(draft.capacity, draft.pricePerPersonVnd), registeredCount).map { it.message })
        val start = runCatching { java.time.LocalTime.parse(draft.startTime) }.getOrNull()
        val end = runCatching { java.time.LocalTime.parse(draft.endTime) }.getOrNull()
        if (runCatching { java.time.LocalDate.parse(draft.date, java.time.format.DateTimeFormatter.BASIC_ISO_DATE) }.isFailure) {
            add("Ngày không hợp lệ")
        }
        if (start == null || end == null || !start.isBefore(end)) add("Giờ bắt đầu và kết thúc không hợp lệ")
        else if (start.minute % 30 != 0 || end.minute % 30 != 0 || start.second != 0 || end.second != 0) {
            add("Thời gian phải theo khung 30 phút")
        }
    }
}

interface OwnerDropInRepository {
    fun observeSessions(venueId: String): Flow<List<OwnerDropInSession>>
    suspend fun getRoster(venueId: String, sessionId: String): Result<List<OwnerDropInRegistration>>
    suspend fun createSession(draft: OwnerDropInDraft): Result<String>
    suspend fun updateSession(venueId: String, sessionId: String, draft: OwnerDropInDraft): Result<Unit>
    suspend fun cancelSession(venueId: String, sessionId: String, reason: String): Result<Unit>
    suspend fun checkIn(venueId: String, sessionId: String, credential: String): Result<Unit>
}

class OwnerDropInContractUnavailableException :
    IllegalStateException("Hợp đồng đọc và vận hành DROP_IN của Feature 080/081 chưa được xác nhận.")
