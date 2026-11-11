package com.example.booking_app.feature.dropin.data.dto

import com.example.booking_app.core.network.AppError
import com.example.booking_app.core.network.FunctionHttpException
import com.example.booking_app.feature.dropin.domain.model.CancelQuote
import com.example.booking_app.feature.dropin.domain.model.CancelReason
import com.example.booking_app.feature.dropin.domain.model.CancelledBy
import com.example.booking_app.feature.dropin.domain.model.DropInError
import com.example.booking_app.feature.dropin.domain.model.DropInRegistration
import com.example.booking_app.feature.dropin.domain.model.DropInSession
import com.example.booking_app.feature.dropin.domain.model.Level
import com.example.booking_app.feature.dropin.domain.model.PaymentMethod
import com.example.booking_app.feature.dropin.domain.model.PaymentStatus
import com.example.booking_app.feature.dropin.domain.model.RegistrationStatus
import com.example.booking_app.feature.dropin.domain.model.SessionStatus
import com.example.booking_app.feature.dropin.domain.model.TimeSlot
import com.google.firebase.Timestamp
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

private val ZONE: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")
private val DATE_KEY: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT)

// ---------- Đọc giá trị thô từ Firestore ----------

internal fun Map<String, Any?>.string(key: String): String? = this[key] as? String

/** `Timestamp`, `Date` hoặc chuỗi ISO-8601; kiểu khác trả `null`. */
internal fun Any?.asInstant(): Instant? = when (this) {
    is Timestamp -> toInstant()
    is Date -> toInstant()
    is Instant -> this
    is String -> runCatching { Instant.parse(this) }.getOrNull()
    else -> null
}

/** Firestore trả số nguyên là `Long`, đôi khi `Double` (ghi từ JS); làm tròn xuống. */
internal fun Any?.asLong(): Long? = when (this) {
    is Long -> this
    is Int -> toLong()
    is Number -> toLong()
    is String -> toLongOrNull()
    else -> null
}

/** `20261112` theo giờ Việt Nam, đúng trường `date` của buổi. */
fun LocalDate.toDateKey(): String = DATE_KEY.format(this)

private inline fun <reified E : Enum<E>> String?.toEnumOrNull(): E? =
    this?.let { raw -> enumValues<E>().firstOrNull { it.name == raw.trim().uppercase(Locale.ROOT) } }

// ---------- Buổi ----------

/**
 * `null` khi thiếu dữ liệu bắt buộc (`venueId`, giờ bắt đầu/kết thúc), để danh sách bỏ qua
 * document hỏng thay vì crash.
 * - `level` = `ALL`, thiếu hoặc lạ → `null` (mọi trình độ).
 * - `status` lạ hoặc thiếu → `CANCELLED`: không cho đăng ký khi không hiểu trạng thái (server vẫn là nơi quyết định).
 * - `timeSlot` thiếu hoặc lạ → tính lại từ `startAt`.
 */
fun SessionDto.toDomain(venueActive: Boolean = true, courtNames: List<String> = emptyList()): DropInSession? {
    val venue = venueId?.takeIf { it.isNotBlank() } ?: return null
    val start = startAt ?: combine(date, startTime) ?: return null
    val end = endAt ?: combine(date, endTime) ?: return null
    val cap = (capacity ?: 0L).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
    val count = (registeredCount ?: 0L).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
    return DropInSession(
        id = id,
        venueId = venue,
        ownerId = ownerId.orEmpty(),
        districtId = districtId.orEmpty(),
        venueName = venueName.orEmpty(),
        venueAddress = venueAddress.orEmpty(),
        districtName = districtName.orEmpty(),
        courtIds = courtIds,
        courtNames = courtNames,
        startAt = start,
        endAt = end,
        timeSlot = timeSlot.toEnumOrNull<TimeSlot>() ?: TimeSlot.of(start),
        level = level.toEnumOrNull<Level>(),
        capacity = cap,
        registeredCount = count,
        pricePerPerson = (pricePerPerson ?: 0L).coerceAtLeast(0L),
        status = status.toEnumOrNull<SessionStatus>() ?: SessionStatus.CANCELLED,
        note = note?.takeIf { it.isNotBlank() },
        venueActive = venueActive,
    )
}

/** `date` (`yyyyMMdd`) + `HH:mm` theo giờ Việt Nam. */
private fun combine(date: String?, time: String?): Instant? {
    if (date == null || time == null) return null
    return runCatching {
        val day = LocalDate.parse(date, DATE_KEY)
        val clock = LocalTime.parse(time.trim())
        day.atTime(clock).atZone(ZONE).toInstant()
    }.getOrNull()
}

// ---------- Đăng ký ----------

/**
 * `null` khi `status` thiếu hoặc lạ (không biết đăng ký còn hiệu lực hay không).
 * `paymentMethod` lạ → `AT_VENUE`; `paymentStatus` lạ → suy từ phương thức (`MOCK_PAY` → `PAID`).
 */
fun RegistrationDto.toDomain(): DropInRegistration? {
    val regStatus = status.toEnumOrNull<RegistrationStatus>() ?: return null
    val method = paymentMethod.toEnumOrNull<PaymentMethod>() ?: PaymentMethod.AT_VENUE
    val party = (partySize ?: 1L).coerceIn(1L, 4L).toInt()
    val price = (pricePerPerson ?: 0L).coerceAtLeast(0L)
    return DropInRegistration(
        sessionId = sessionId,
        userId = userId,
        partySize = party,
        status = regStatus,
        paymentMethod = method,
        paymentStatus = paymentStatus.toEnumOrNull<PaymentStatus>() ?: defaultPaymentStatus(method),
        pricePerPerson = price,
        amount = amount ?: (price * party),
        refundAmount = refundAmount,
        qrToken = qrToken?.takeIf { it.isNotBlank() },
        ticketCode = ticketCode?.takeIf { it.isNotBlank() },
        sessionChanged = sessionChanged ?: false,
        sessionStartAt = sessionStartAt,
        sessionEndAt = sessionEndAt,
        registeredAt = registeredAt,
        cancelledBy = cancelledBy.toEnumOrNull<CancelledBy>(),
    )
}

/**
 * Response của `register-drop-in`. Không có `qrToken` (đọc qua Firestore). [fallbackSessionId],
 * [uid] dùng khi response thiếu trường. Thiếu `status` coi như `REGISTERED` vì function chỉ trả
 * 200/201 khi đăng ký thành công.
 */
fun RegistrationResultDto.toDomain(fallbackSessionId: String, uid: String): DropInRegistration {
    val method = paymentMethod.toEnumOrNull<PaymentMethod>() ?: PaymentMethod.AT_VENUE
    val party = (partySize ?: 1).coerceIn(1, 4)
    val price = (pricePerPerson ?: 0L).coerceAtLeast(0L)
    return DropInRegistration(
        sessionId = sessionId ?: fallbackSessionId,
        userId = uid,
        partySize = party,
        status = status.toEnumOrNull<RegistrationStatus>() ?: RegistrationStatus.REGISTERED,
        paymentMethod = method,
        paymentStatus = paymentStatus.toEnumOrNull<PaymentStatus>() ?: defaultPaymentStatus(method),
        pricePerPerson = price,
        amount = amount ?: (price * party),
        refundAmount = refundAmount,
        qrToken = null,
        ticketCode = ticketCode?.takeIf { it.isNotBlank() },
        sessionChanged = false,
        sessionStartAt = null,
        sessionEndAt = null,
        registeredAt = registeredAt.asInstant(),
        cancelledBy = null,
    )
}

private fun defaultPaymentStatus(method: PaymentMethod): PaymentStatus =
    if (method == PaymentMethod.MOCK_PAY) PaymentStatus.PAID else PaymentStatus.UNPAID

/** Tuần 7 (US4). `reason` lạ → `null`. */
fun CancelQuoteDto.toDomain(): CancelQuote? {
    val r = reason.toEnumOrNull<CancelReason>() ?: return null
    return CancelQuote(refundAmount = (refundAmount ?: 0L).coerceAtLeast(0L), reason = r)
}

// ---------- Lỗi ----------

/** `error.code` của Edge Function (contract mục 4) → [DropInError]. */
fun dropInErrorOf(code: String?, remaining: Int? = null, httpStatus: Int? = null): DropInError = when (code) {
    "SESSION_FULL" -> DropInError.SessionFull(remaining = (remaining ?: 0).coerceAtLeast(0))
    "SESSION_STARTED" -> DropInError.SessionStarted()
    "SESSION_CANCELLED" -> DropInError.SessionCancelled()
    "VENUE_INACTIVE" -> DropInError.VenueInactive()
    "OWN_SESSION" -> DropInError.OwnSession()
    "ALREADY_REGISTERED" -> DropInError.AlreadyRegistered()
    "ALREADY_WAITLISTED" -> DropInError.AlreadyWaitlisted()
    "NOT_CANCELLABLE" -> DropInError.NotCancellable()
    "NOT_FOUND", "SESSION_NOT_FOUND" -> DropInError.NotFound()
    "ACCOUNT_LOCKED" -> DropInError.AccountLocked()
    "INVALID_ARGUMENT" -> DropInError.InvalidArgument()
    "UNAUTHENTICATED" -> DropInError.Unauthenticated()
    "BUSY" -> DropInError.Busy()
    null -> if (httpStatus == 503) DropInError.Busy() else DropInError.Unknown(null)
    else -> DropInError.Unknown(code)
}

/**
 * Đổi lỗi sau [com.example.booking_app.core.network.FunctionErrors] (hoặc lỗi Firestore đã qua
 * `toAppError`) thành [DropInError]. `FunctionErrors` đã làm mới token khi `401` và báo
 * `ACCOUNT_LOCKED`; mã nghiệp vụ nằm trong body của [FunctionHttpException] bọc trong [AppError.Unknown].
 */
fun Throwable.toDropInError(): DropInError = when (this) {
    is DropInError -> this
    AppError.Network, AppError.Timeout -> DropInError.Network()
    AppError.Locked -> DropInError.AccountLocked()
    AppError.NotSignedIn, AppError.SessionRevoked, AppError.AccountDeleted -> DropInError.Unauthenticated()
    AppError.RateLimited -> DropInError.Unknown("RATE_LIMITED")
    is AppError.Unknown -> when (val c = cause) {
        is FunctionHttpException -> c.toDropInError()
        is IOException -> DropInError.Network()
        else -> DropInError.Unknown(null)
    }
    is FunctionHttpException -> toDropInError()
    is IOException -> DropInError.Network()
    else -> DropInError.Unknown(null)
}

private fun FunctionHttpException.toDropInError(): DropInError {
    val parsed = FunctionErrorBody.parse(body)
    return dropInErrorOf(parsed?.code, parsed?.remaining, status)
}
