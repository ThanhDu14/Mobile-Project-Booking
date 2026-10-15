# Data Model: Đặt sân cố định theo tuần (spec 041)

**Ngày**: 2026-10-15 · **Spec**: [spec.md](spec.md) · **Plan**: [plan.md](plan.md)

Tài liệu này định nghĩa chi tiết mô hình cơ sở dữ liệu Firestore, các Data Transfer Objects (DTO), Domain Entities và UI State cho tính năng đặt sân cố định theo tuần.

---

## 1. Firestore Schema

### 1.1. Collection `recurringGroups/{recurringGroupId}` (Mục 4.5a Database README)
Lưu thông tin tổng quan của cả chu kỳ đặt cố định.
- `id`: `String` - UUID định danh nhóm
- `userId`: `String` - UID của người chơi
- `venueId`: `String` - ID cơ sở sân
- `venueName`: `String` - Tên cơ sở sân hiển thị
- `daysOfWeek`: `List<Int>` - Các thứ trong tuần (1 = CN, 2 = T2, ..., 7 = T7)
- `startTime`: `String` - Giờ bắt đầu mỗi buổi (ví dụ `"1800"`)
- `endTime`: `String` - Giờ kết thúc mỗi buổi (ví dụ `"2000"`)
- `startDate`: `String` - Ngày bắt đầu chu kỳ (định dạng `yyyyMMdd`)
- `endDate`: `String` - Ngày kết thúc chu kỳ (định dạng `yyyyMMdd`)
- `totalSessions`: `Int` - Tổng số buổi hợp lệ trong chu kỳ ($\ge 4$)
- `subtotal`: `Long` - Tổng tiền trước giảm giá (VND)
- `discount`: `Long` - Số tiền được giảm (nếu có áp voucher ở 050)
- `total`: `Long` - Tổng tiền thực trả
- `depositAmount`: `Long` - Tiền đặt cọc 30% (`subtotal * 30 / 100`)
- `paymentStatus`: `String` - `"PENDING"`, `"PARTIALLY_PAID"` (đã cọc 30%), `"PAID"` (trả 100%)
- `status`: `String` - `"HOLD"`, `"CONFIRMED"`, `"CANCELLED"`, `"EXPIRED"`
- `holdExpiresAt`: `Timestamp` - Mốc hết hạn 5 phút giữ chỗ
- `requestId`: `String` - UUID chống double-submit
- `createdAt`: `Timestamp` - Thời điểm tạo nhóm

### 1.2. Collection `bookings/{bookingId}` (Các đơn con trong nhóm)
Mỗi buổi chơi trong chu kỳ là 1 document `bookings` riêng biệt có trường liên kết nhóm:
- `bookingId`: `String` - UUID của đơn con
- `recurringGroupId`: `String?` - Mã nhóm liên kết đến `recurringGroups`
- `userId`: `String`
- `venueId`: `String`
- `courtId`: `String` - Sân con của buổi này (có thể là sân gốc hoặc sân đã đổi)
- `date`: `String` - `yyyyMMdd`
- `startTime`: `String`
- `endTime`: `String`
- `slotIds`: `List<String>` - Danh sách các slot 30 phút trong buổi
- `status`: `String` - `"HOLD"`, `"PENDING"`, `"CONFIRMED"`, `"COMPLETED"`, `"CANCELLED"`, `"NO_SHOW"`
- `price`: `Long` - Đơn giá của buổi này
- `depositAmount`: `Long` - Số tiền cọc được phân bổ cho buổi này

---

## 2. Kotlin Data Transfer Objects (Data Layer)

```kotlin
package com.example.booking_app.feature.booking.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

data class RecurringGroupDto(
    @DocumentId val id: String = "",
    val userId: String = "",
    val venueId: String = "",
    val venueName: String = "",
    val daysOfWeek: List<Int> = emptyList(),
    val startTime: String = "",
    val endTime: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val totalSessions: Int = 0,
    val subtotal: Long = 0L,
    val discount: Long = 0L,
    val total: Long = 0L,
    val depositAmount: Long = 0L,
    val paymentStatus: String = "PENDING",
    val status: String = "HOLD",
    val holdExpiresAt: Timestamp? = null,
    val requestId: String = "",
    val createdAt: Timestamp? = null
)

data class BookingSessionDto(
    @DocumentId val id: String = "",
    val recurringGroupId: String = "",
    val userId: String = "",
    val venueId: String = "",
    val courtId: String = "",
    val courtName: String = "",
    val date: String = "",
    val startTime: String = "",
    val endTime: String = "",
    val slotIds: List<String> = emptyList(),
    val status: String = "HOLD",
    val price: Long = 0L,
    val depositAmount: Long = 0L
)
```

---

## 3. Kotlin Domain Entities (Domain Layer)

```kotlin
package com.example.booking_app.feature.booking.domain.model

import java.time.LocalDate

data class RecurringConfig(
    val venueId: String,
    val preferredCourtId: String,
    val daysOfWeek: Set<Int>,       // 1 = CN, 2 = T2, ..., 7 = T7
    val startTime: String,          // HHmm
    val endTime: String,            // HHmm
    val numberOfWeeks: Int,         // 4..12
    val startNextWeek: Boolean      // false: tuần hiện tại, true: tuần sau
) {
    val slotCountPerSession: Int
        get() {
            val startMin = startTime.substring(0, 2).toInt() * 60 + startTime.substring(2).toInt()
            val endMin = endTime.substring(0, 2).toInt() * 60 + endTime.substring(2).toInt()
            return (endMin - startMin) / 30
        }
}

enum class ConflictResolutionType {
    NONE,                   // Buổi hợp lệ không trùng
    SUGGEST_CHANGE_COURT,   // Đề xuất đổi sân con khác
    MUST_SKIP               // Toàn bộ các sân đều kín, bắt buộc bỏ buổi
}

data class RecurringSession(
    val id: String,                 // Tạm thời dạng {date}_{startTime}
    val date: LocalDate,
    val dateString: String,         // yyyyMMdd
    val courtId: String,
    val courtName: String,
    val originalCourtId: String,
    val isCourtChanged: Boolean = false,
    val slotTimes: List<String>,    // ["1800", "1830", "1900", "1930"]
    val slotIds: List<String>,      // ["{courtId}_{date}_1800", ...]
    val price: Long,
    val conflictType: ConflictResolutionType = ConflictResolutionType.NONE,
    val availableAlternateCourts: List<Court> = emptyList(),
    val isSkipped: Boolean = false
)

data class RecurringSummary(
    val totalSessions: Int,
    val activeSessions: List<RecurringSession>,
    val subtotalAmount: Long,
    val depositAmount: Long,
    val isValid: Boolean get() = activeSessions.size >= 4
)
```

---

## 4. UI State & Navigation Models (UI Layer)

```kotlin
package com.example.booking_app.feature.booking.ui.recurring

import android.os.Parcelable
import com.example.booking_app.feature.booking.domain.model.RecurringConfig
import com.example.booking_app.feature.booking.domain.model.RecurringSession
import com.example.booking_app.feature.booking.domain.model.RecurringSummary
import kotlinx.parcelize.Parcelize

data class BookingRecurringUiState(
    val isLoading: Boolean = false,
    val config: RecurringConfig? = null,
    val sessions: List<RecurringSession> = emptyList(),
    val summary: RecurringSummary? = null,
    val hasConflicts: Boolean = false,
    val holdCountdownSeconds: Int = 300,
    val errorMessage: String? = null
)

sealed interface BookingRecurringUiEffect {
    data class ShowToast(val message: String) : BookingRecurringUiEffect
    data class ShowErrorDialog(val title: String, val message: String) : BookingRecurringUiEffect
    data class NavigateToPayment(val args: RecurringToPaymentArgs) : BookingRecurringUiEffect
    object NavigateBackToGrid : BookingRecurringUiEffect
    object NavigateToLogin : BookingRecurringUiEffect
}

@Parcelize
data class RecurringToPaymentArgs(
    val recurringGroupId: String,
    val venueId: String,
    val venueName: String,
    val totalSessions: Int,
    val subtotalAmount: Long,
    val depositAmount: Long,
    val holdExpiresAtMillis: Long
) : Parcelable
```
