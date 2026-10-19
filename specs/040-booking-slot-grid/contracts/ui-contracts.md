# UI & Domain Contracts: Lưới khung giờ đặt sân và giữ chỗ (spec 040)

**Ngày**: 2026-10-15 · **Spec**: [spec.md](../spec.md) · **Plan**: [plan.md](../plan.md)

Tài liệu này xác lập các hợp đồng giao tiếp (Contracts) giữa các tầng kiến trúc trong tính năng `040-booking-slot-grid` và điểm giao thoa với các tính năng khác (`010`, `030`, `041`).

---

## 1. Hợp đồng Điều hướng (Navigation Safe Args)

### 1.1. Điều hướng vào màn hình Lưới slot (Từ `030-court-detail`)
Màn hình chi tiết cơ sở (`CourtDetailFragment`) bấm "Đặt sân" sẽ kích hoạt Navigation Action:
```xml
<action
    android:id="@+id/action_courtDetail_to_bookingSlotGrid"
    app:destination="@id/bookingSlotGridFragment">
    <argument
        android:name="venueId"
        app:argType="string" />
</action>
```

### 1.2. Điều hướng sang Màn hình Thanh toán & Giữ chỗ (`050-payment-voucher`)
Khi người dùng bấm "Tiếp tục" và giữ chỗ 5 phút thành công, ViewModel phát ra `BookingSlotUiEffect.NavigateToPayment`:
```kotlin
@Parcelize
data class HoldToPaymentArgs(
    val bookingId: String,               // Mã đơn giữ chỗ (requestId UUID)
    val venueId: String,
    val venueName: String,
    val isRecurring: Boolean = false,
    val totalSessions: Int = 1,          // Đơn lẻ = 1 buổi
    val subtotalAmount: Long,            // Tổng tiền tạm tính
    val slotIds: Array<String>,          // Danh sách các slot giữ chỗ
    val holdExpiresAtMillis: Long        // Mốc thời gian hết hạn 5 phút đếm ngược
) : Parcelable
```

### 1.3. Điều hướng sang Màn hình Đăng nhập (`010-account-auth`)
Nếu người dùng chưa đăng nhập bấm "Tiếp tục":
- ViewModel lưu trạng thái lựa chọn hiện tại vào Memory / SavedStateHandle.
- Phát ra `BookingSlotUiEffect.NavigateToLogin`.
- Khi người dùng đăng nhập thành công và quay lại, `BookingSlotGridFragment` kiểm tra và khôi phục các slot đã chọn nếu chúng vẫn còn trạng thái `AVAILABLE`.

---

## 2. Hợp đồng Tầng Domain (Domain Repository Interface)

Đặt tại `com.example.booking_app.feature.booking.domain.repository.BookingRepository`:

```kotlin
package com.example.booking_app.feature.booking.domain.repository

import com.example.booking_app.feature.booking.data.model.SlotDto
import com.example.booking_app.feature.booking.domain.model.Court
import com.example.booking_app.feature.booking.domain.model.PriceRule
import kotlinx.coroutines.flow.Flow

interface BookingRepository {
    /**
     * Lấy giờ mở/đóng cửa của cơ sở (trả về Pair<openTime, closeTime> dạng "0600", "2200")
     */
    suspend fun getVenueOperatingHours(venueId: String): Result<Pair<String, String>>

    /**
     * Lấy danh sách sân con đang hoạt động (isActive == true) của cơ sở
     */
    suspend fun getActiveCourts(venueId: String): Result<List<Court>>

    /**
     * Lấy danh sách bảng giá cấu hình của cơ sở
     */
    suspend fun getPriceRules(venueId: String): Result<List<PriceRule>>

    /**
     * Lắng nghe Realtime các slot bận (BOOKED, HELD, BLOCKED) trong ngày cụ thể
     * Sử dụng callbackFlow, tự động remove listener trong awaitClose.
     */
    fun getBusySlotsRealtime(venueId: String, date: String): Flow<List<SlotDto>>
}
```

---

## 3. Hợp đồng các UseCase cốt lõi

### 3.1. `GenerateDailySlotsUseCase`
Nhận giờ mở/đóng cửa, ngày đã chọn, danh sách `PriceRule` và danh sách slot bận từ Firestore để sinh lưới hoàn chỉnh:
```kotlin
class GenerateDailySlotsUseCase {
    operator fun invoke(
        courtId: String,
        date: String,
        openTime: String,
        closeTime: String,
        priceRules: List<PriceRule>,
        busySlots: List<SlotDto>,
        selectedSlotIds: Set<String>
    ): List<TimeSlot>
}
```

### 3.2. `ValidateSlotSelectionUseCase`
Kiểm tra tính hợp lệ và xử lý chọn/bỏ chọn slot theo thuật toán liên tiếp:
```kotlin
class ValidateSlotSelectionUseCase {
    operator fun invoke(
        currentSelection: SlotSelectionState,
        clickedSlot: TimeSlot,
        allSlots: List<TimeSlot>
    ): SlotSelectionResult
}

sealed interface SlotSelectionResult {
    data class Success(val newState: SlotSelectionState) : SlotSelectionResult
    data class Error(val message: String) : SlotSelectionResult
}
```

### 3.3. `CalculateTotalPriceUseCase`
Tính tổng tiền dựa trên các slot đã chọn và quy tắc 30 phút (`pricePerSlot = pricePerHour / 2`):
```kotlin
class CalculateTotalPriceUseCase {
    operator fun invoke(
        selectedSlots: List<TimeSlot>
    ): Long
}
```

---

## 4. Đặc tả Giao diện & Mã màu trạng thái (Design System Tokens)

Theo wireframe SmashNow (Spec 040), lưới slot sử dụng các mã màu Material 3 trực quan:

| Trạng thái | Nền (Background) | Viền (Border) | Chữ (Text) | Hành vi tương tác |
|---|---|---|---|---|
| **Còn trống (`AVAILABLE`)** | Trắng (`colorSurface`) | Xám nhạt (`colorOutlineVariant`) | Đen/Tối (`colorOnSurface`) | Bấm để chọn |
| **Đang chọn (`SELECTED`)** | Xanh dương chính (`colorPrimary`) | Xanh dương đậm | Trắng (`colorOnPrimary`) | Bấm để bỏ chọn |
| **Đang giữ (`HELD`)** | Vàng nhạt (`colorTertiaryContainer`) | Vàng cam (`colorTertiary`) | Nâu đất | Bị vô hiệu hóa, hiện nhãn "Đang giữ" |
| **Đã đặt (`BOOKED`)** | Xám nhạt (`colorSurfaceVariant`) | Không | Xám tối (`colorOnSurfaceVariant`) | Bị vô hiệu hóa |
| **Bị khóa (`BLOCKED` - `DROP_IN`)** | Xám trung tính (`colorOutlineVariant`) | Xám đậm | Xám đậm | Bị vô hiệu hóa, icon khóa $\theta$ hoặc nhãn "Vãng lai" |
| **Đã qua giờ (`PAST`)** | Xám mờ (Alpha 38%) | Không | Xám mờ | Bị vô hiệu hóa |
