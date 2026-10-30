# UI & Domain Contracts: Đặt sân cố định theo tuần (spec 041)

**Ngày**: 2026-10-15 · **Spec**: [spec.md](../spec.md) · **Plan**: [plan.md](../plan.md)

Tài liệu này xác lập các hợp đồng giao tiếp giữa các tầng kiến trúc trong tính năng `041-weekly-recurring-booking` và điểm giao thoa với các tính năng khác (`030`, `040`, `050`).

---

## 1. Hợp đồng Điều hướng (Navigation Safe Args)

### 1.1. Điều hướng vào màn hình Đặt cố định (Từ `030-court-detail` hoặc Tab từ `040`)
```xml
<action
    android:id="@+id/action_to_bookingRecurringConfigFragment"
    app:destination="@id/bookingRecurringConfigFragment">
    <argument
        android:name="venueId"
        app:argType="string" />
</action>
```

### 1.2. Điều hướng sang Màn hình Thanh toán / Đặt cọc (`050-payment-voucher`)
Khi giữ chỗ toàn chu kỳ thành công trong Transaction, ViewModel phát ra `BookingRecurringUiEffect.NavigateToPayment`:
```kotlin
@Parcelize
data class HoldToPaymentArgs(
    val bookingId: String,                // Mã nhóm recurringGroupId
    val venueId: String,
    val venueName: String,
    val isRecurring: Boolean = true,
    val totalSessions: Int,               // Số lượng buổi hợp lệ (>= 4)
    val subtotalAmount: Long,             // Tổng tiền toàn chu kỳ trước voucher
    val slotIds: Array<String>,           // Danh sách tất cả slotIds của các buổi hợp lệ
    val holdExpiresAtMillis: Long         // Mốc hết hạn 5 phút tính theo epoch millis
) : Parcelable
```

### 1.3. Điều hướng quay về Đặt sân lẻ (`040-booking-slot-grid`)
Nếu số buổi hợp lệ sau khi giải quyết xung đột $< 4$ buổi, hệ thống chặn luồng và điều hướng người dùng quay lại `040` với `BookingRecurringUiEffect.NavigateBackToGrid`.

---

## 2. Hợp đồng Tầng Domain (Domain Repository Interface)

Đặt tại `com.example.booking_app.feature.booking.domain.repository.RecurringBookingRepository`:

```kotlin
package com.example.booking_app.feature.booking.domain.repository

import com.example.booking_app.feature.booking.data.model.BookingSessionDto
import com.example.booking_app.feature.booking.data.model.RecurringGroupDto
import com.example.booking_app.feature.booking.data.model.SlotDto
import com.example.booking_app.feature.booking.domain.model.Court
import com.example.booking_app.feature.booking.domain.model.PriceRule

interface RecurringBookingRepository {
    /**
     * Kiểm tra trạng thái chấp nhận đặt cố định của cơ sở từ venues/{venueId}.acceptsRecurring (Hợp đồng tích hợp Feature 111)
     */
    suspend fun isRecurringAccepted(venueId: String): Result<Boolean>

    /**
     * Lấy danh sách sân con đang hoạt động của cơ sở
     */
    suspend fun getActiveCourts(venueId: String): Result<List<Court>>

    /**
     * Lấy danh sách bảng giá cấu hình của cơ sở
     */
    suspend fun getPriceRules(venueId: String): Result<List<PriceRule>>

    /**
     * Quét các slot bận của danh sách ngày trong chu kỳ.
     * Sử dụng dates.chunked(30) kết hợp Coroutines async/awaitAll.
     */
    suspend fun getBusySlotsForDates(venueId: String, dates: List<String>): Result<List<SlotDto>>

    /**
     * Thực thi Firestore Transaction giữ chỗ All-or-Nothing toàn bộ các slot của chu kỳ trong 5 phút.
     * Trả về recurringGroupId nếu thành công.
     */
    suspend fun holdRecurringBookingTransaction(
        group: RecurringGroupDto,
        sessions: List<BookingSessionDto>
    ): Result<String>

    /**
     * Chủ động giải phóng các slot đang HELD khi người dùng Back/Hủy từ màn hình xác nhận.
     */
    suspend fun releaseRecurringHold(
        recurringGroupId: String,
        slotIds: List<String>
    ): Result<Unit>
}
```

---

## 3. Hợp đồng các UseCase cốt lõi

### 3.1. `GenerateRecurringSessionsUseCase`
Nhận cấu hình (thứ trong tuần, giờ, số tuần, bắt đầu từ tuần này hay tuần sau) $\rightarrow$ Sinh danh sách các buổi với ngày cụ thể, tự động loại bỏ các thứ đã qua trong tuần hiện tại:
```kotlin
class GenerateRecurringSessionsUseCase {
    operator fun invoke(config: RecurringConfig): List<LocalDate>
}
```

### 3.2. `CheckRecurringAvailabilityUseCase`
Gửi danh sách ngày lên Repository, đối chiếu slot bận và tìm sân con thay thế khả dụng:
```kotlin
class CheckRecurringAvailabilityUseCase {
    suspend operator fun invoke(
        venueId: String,
        preferredCourtId: String,
        sessions: List<LocalDate>,
        startTime: String,
        endTime: String,
        allCourts: List<Court>,
        priceRules: List<PriceRule>
    ): Result<List<RecurringSession>>
}
```

### 3.3. `ResolveRecurringConflictUseCase`
Xử lý lựa chọn của người dùng (Đổi sân hoặc Bỏ buổi), kiểm tra điều kiện tiên quyết $\ge 4$ buổi:
```kotlin
class ResolveRecurringConflictUseCase {
    operator fun invoke(
        currentSessions: List<RecurringSession>,
        sessionId: String,
        action: ConflictAction
    ): ConflictResolutionResult
}

sealed interface ConflictAction {
    data class ChangeCourt(val newCourtId: String) : ConflictAction
    object SkipSession : ConflictAction
}

sealed interface ConflictResolutionResult {
    data class Success(val updatedSessions: List<RecurringSession>, val summary: RecurringSummary) : ConflictResolutionResult
    data class ErrorLessThanMinSessions(val validCount: Int) : ConflictResolutionResult
}
```

### 3.4. `HoldRecurringBookingUseCase`
Tạo `recurringGroupId` (UUID), `requestId` (UUID) và gọi Transaction giữ chỗ toàn chu kỳ:
```kotlin
class HoldRecurringBookingUseCase {
    suspend operator fun invoke(
        userId: String,
        venueId: String,
        venueName: String,
        config: RecurringConfig,
        validSessions: List<RecurringSession>,
        summary: RecurringSummary
    ): Result<RecurringToPaymentArgs>
}
```

### 3.5. `ReleaseRecurringHoldUseCase`
Chủ động xóa các slot `HELD` khi thoát phiên đặt:
```kotlin
class ReleaseRecurringHoldUseCase {
    suspend operator fun invoke(
        recurringGroupId: String,
        slotIds: List<String>
    ): Result<Unit>
}
```
