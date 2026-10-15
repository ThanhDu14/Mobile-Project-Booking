# Data Model: Lưới khung giờ đặt sân và giữ chỗ (spec 040)

**Ngày**: 2026-10-15 · **Spec**: [spec.md](spec.md) · **Plan**: [plan.md](plan.md)

Tài liệu này định nghĩa chi tiết mô hình dữ liệu ở Firestore, các Kotlin DTOs ở tầng Data, các Domain Models ở tầng nghiệp vụ và các UI Models hiển thị trên giao diện.

---

## 1. Firestore Schema

### 1.1. Collection `venues/{venueId}` (Đọc)
Chứa thông tin cơ sở và khung giờ hoạt động chung.
- `name`: `String` - Tên cụm sân
- `openTime`: `String` - Giờ mở cửa, định dạng `HHmm` (ví dụ `"0600"`)
- `closeTime`: `String` - Giờ đóng cửa, định dạng `HHmm` (ví dụ `"2200"`)
- `status`: `String` - Trạng thái hoạt động (`ACTIVE`, `INACTIVE`)

### 1.2. Subcollection `venues/{venueId}/courts/{courtId}` (Đọc)
Danh sách các sân con trực thuộc cơ sở.
- `name`: `String` - Tên sân con (ví dụ `"Sân 1"`, `"Sân 2"`)
- `surfaceType`: `String` - Loại mặt sân (`WOOD`, `PU`, `MAT`)
- `isActive`: `Boolean` - Đang hoạt động hay tạm khóa
- `sortOrder`: `Int` - Thứ tự hiển thị tab

### 1.3. Subcollection `venues/{venueId}/priceRules/{id}` (Đọc)
Bảng giá theo khung giờ (Thống nhất phương án 1.A).
- `daysOfWeek`: `List<Int>` - Danh sách các thứ trong tuần (1 = CN, 2 = T2, ..., 7 = T7)
- `startTime`: `String` - Giờ bắt đầu, định dạng `HHmm` (bội số 30 phút, ví dụ `"0600"`, `"1700"`)
- `endTime`: `String` - Giờ kết thúc, định dạng `HHmm` (bội số 30 phút, ví dụ `"1700"`, `"2200"`)
- `pricePerHour`: `Long` - Đơn giá theo giờ (VND, chia hết cho 2.000)
- `label`: `String` - Phân loại (`NORMAL`, `PEAK`, `WEEKEND`)

### 1.4. Root collection `slots/{courtId}_{yyyyMMdd}_{HHmm}` (Lắng nghe Realtime)
Document ID tất định. Chỉ tồn tại khi slot bị bận hoặc khóa (Sparse Storage).
- `venueId`: `String` - ID cơ sở (dùng để truy vấn kết hợp với `date`)
- `courtId`: `String` - ID sân con
- `date`: `String` - Ngày chơi dạng `yyyyMMdd` (ví dụ `"20261015"`)
- `startTime`: `String` - Giờ bắt đầu dạng `HHmm` (ví dụ `"1800"`)
- `endTime`: `String` - Giờ kết thúc dạng `HHmm` (ví dụ `"1830"`)
- `status`: `String` - Trạng thái: `"HELD"`, `"BOOKED"`, `"BLOCKED"`
- `blockReason`: `String?` - Lý do khóa nếu status là `BLOCKED` (ví dụ `"DROP_IN"`, `"MAINTENANCE"`)
- `holdExpiresAt`: `Timestamp?` - Thời điểm hết hạn giữ chỗ (khi status = `HELD`)
- `holdBy`: `String?` - UID người dùng đang giữ chỗ
- `bookingId`: `String?` - ID đơn đặt sân liên kết (khi status = `BOOKED` hoặc `HELD`)

---

## 2. Kotlin Data Transfer Objects (Data Layer)

```kotlin
package com.example.booking_app.feature.booking.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

data class CourtDto(
    @DocumentId val id: String = "",
    val name: String = "",
    val surfaceType: String = "MAT",
    val isActive: Boolean = true,
    val sortOrder: Int = 0
)

data class PriceRuleDto(
    @DocumentId val id: String = "",
    val daysOfWeek: List<Int> = emptyList(),
    val startTime: String = "",
    val endTime: String = "",
    val pricePerHour: Long = 0L,
    val label: String = "NORMAL"
)

data class SlotDto(
    @DocumentId val id: String = "",
    val venueId: String = "",
    val courtId: String = "",
    val date: String = "",
    val startTime: String = "",
    val endTime: String = "",
    val status: String = "AVAILABLE",
    val blockReason: String? = null,
    val holdExpiresAt: Timestamp? = null,
    val holdBy: String? = null,
    val bookingId: String? = null
)
```

---

## 3. Kotlin Domain Models (Domain Layer)

```kotlin
package com.example.booking_app.feature.booking.domain.model

enum class SlotStatus {
    AVAILABLE,   // Còn trống (mặc định được tạo ở Client)
    SELECTED,    // Người dùng đang chọn tạm thời trên UI
    BOOKED,      // Đã được đặt và xác nhận
    HELD,        // Đang tạm giữ trong 5 phút
    BLOCKED,     // Bị khóa (do ca Drop-in hoặc bảo trì sân)
    PAST         // Khung giờ đã qua so với thời gian hiện tại
}

data class Court(
    val id: String,
    val name: String,
    val surfaceType: String,
    val isActive: Boolean,
    val sortOrder: Int
)

data class PriceRule(
    val id: String,
    val daysOfWeek: List<Int>,
    val startTime: String,
    val endTime: String,
    val pricePerHour: Long,
    val label: String
) {
    val pricePerSlot: Long get() = pricePerHour / 2
}

data class TimeSlot(
    val id: String,             // Dạng {courtId}_{yyyyMMdd}_{HHmm}
    val courtId: String,
    val date: String,          // yyyyMMdd
    val startTime: String,     // HHmm
    val endTime: String,       // HHmm
    val displayTime: String,   // "18:00 - 18:30"
    val status: SlotStatus,
    val price: Long,           // Giá tính theo PriceRule cho ca 30 phút này
    val blockReason: String? = null
)

data class SlotSelectionState(
    val courtId: String? = null,
    val courtName: String? = null,
    val selectedSlots: List<TimeSlot> = emptyList(),
    val totalAmount: Long = 0L
) {
    val slotCount: Int get() = selectedSlots.size
    val totalHoursText: String get() {
        val totalMinutes = slotCount * 30
        val hours = totalMinutes / 60
        val mins = totalMinutes % 60
        return if (mins == 0) "${hours}h" else "${hours}h${mins}p"
    }
}
```

---

## 4. UI State & Navigation Models (UI Layer)

```kotlin
package com.example.booking_app.feature.booking.ui

import com.example.booking_app.feature.booking.domain.model.Court
import com.example.booking_app.feature.booking.domain.model.SlotSelectionState
import com.example.booking_app.feature.booking.domain.model.TimeSlot
import java.time.LocalDate

data class DateItem(
    val date: LocalDate,
    val dateString: String,      // yyyyMMdd
    val dayOfWeekLabel: String,  // "T2", "T3", ..., "CN"
    val dayOfMonth: Int,         // 15
    val isSelected: Boolean
)

data class BookingSlotUiState(
    val isLoading: Boolean = true,
    val venueId: String = "",
    val venueName: String = "",
    val dates: List<DateItem> = emptyList(),
    val selectedDate: String = "",
    val courts: List<Court> = emptyList(),
    val selectedCourtId: String = "",
    val dailySlots: List<TimeSlot> = emptyList(),
    val selectionState: SlotSelectionState = SlotSelectionState(),
    val errorMessage: String? = null
)

sealed interface BookingSlotUiEffect {
    data class ShowToast(val message: String) : BookingSlotUiEffect
    data class ShowConflictWarning(val message: String) : BookingSlotUiEffect
    data class NavigateToBookingSummary(
        val venueId: String,
        val courtId: String,
        val courtName: String,
        val date: String,
        val selectedSlotTimes: Array<String>,
        val subtotalAmount: Long
    ) : BookingSlotUiEffect
    object NavigateToLogin : BookingSlotUiEffect
}
```
