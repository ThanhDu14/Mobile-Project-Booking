# Data Model: Quản lý lịch đặt của tôi (spec 060)

**Ngày**: 2026-10-15 · **Spec**: [spec.md](spec.md) · **Plan**: [plan.md](plan.md)

Tài liệu này định nghĩa chi tiết mô hình cơ sở dữ liệu Firestore, Kotlin Data Transfer Objects (DTOs), Domain Entities và UI State/Effect cho module Quản lý lịch đặt của tôi (`060-my-bookings`).

---

## 1. Firestore Schema

### 1.1. Collection `bookings/{bookingId}`
Lưu trữ toàn bộ thông tin đơn đặt sân (cả đơn lẻ và từng buổi trong đơn cố định).
- `bookingId`: `String` (Document ID) - Mã đơn hàng duy nhất (ví dụ: `BK_1729012345678`)
- `userId`: `String` - ID người chơi đặt sân (`request.auth.uid`)
- `venueId`: `String` - ID cơ sở thể thao
- `venueName`: `String` - Tên cơ sở thể thao
- `courtId`: `String` - ID sân con cụ thể
- `courtName`: `String` - Tên sân con (ví dụ: "Sân 1", "Sân Pickleball VIP")
- `surfaceType`: `String` - Loại mặt sân (ví dụ: `HARD`, `INDOOR_VINYL`, `CARPET`)
- `date`: `String` - Ngày diễn ra buổi chơi theo định dạng `yyyy-MM-dd`
- `slotIds`: `List<String>` - Danh sách ID các slot 30 phút thuộc đơn (`{courtId}_{yyyyMMdd}_{HHmm}`). Lưu trữ dạng mảng ID gọn nhẹ trên Firestore; tầng Data Mapper (`BookingManagementMapper`) phân giải (hydrate) thành danh sách chi tiết các khung giờ `items: List<BookingSlotItem>` cho Domain và UI.
- `startAt`: `Timestamp` - Thời điểm bắt đầu buổi chơi (chuẩn server)
- `endAt`: `Timestamp` - Thời điểm kết thúc buổi chơi (chuẩn server)
- `status`: `String` - Trạng thái đơn: `"HOLD"`, `"PENDING"`, `"CONFIRMED"`, `"COMPLETED"`, `"CANCELLED"`, `"REJECTED"`, `"NO_SHOW"`
- `subtotalAmount`: `Long` - Tổng tiền gốc trước voucher (VND, cấm âm)
- `discountAmount`: `Long` - Số tiền được giảm qua voucher (VND)
- `depositAmount`: `Long` - Số tiền cọc quy định (30% hoặc 0đ)
- `paidAmount`: `Long` - Số tiền người chơi thực tế đã trả trước (VND)
- `remainingAmount`: `Long` - Số tiền còn lại phải trả tại sân khi check-in (VND)
- `paymentMethod`: `String` - Phương thức thanh toán: `"AT_VENUE"`, `"DEPOSIT"`, `"MOCK_WALLET"`, `"FREE"`
- `voucherCode`: `String?` - Mã khuyến mãi đã áp dụng (nếu có)
- `isRecurring`: `Boolean` - Đơn này có thuộc chuỗi cố định tuần không
- `recurringGroupId`: `String?` - ID nhóm chuỗi cố định (nếu `isRecurring == true`)
- `sessionIndex`: `Int?` - Thứ tự buổi trong chuỗi (ví dụ: 1/8)
- `totalSessions`: `Int?` - Tổng số buổi trong chuỗi
- `qrToken`: `String?` - Chuỗi token bí mật chỉ chủ đơn và chủ sân đọc được để sinh mã QR check-in
- `checkedInAt`: `Timestamp?` - Thời điểm chủ sân quét QR check-in thành công
- `cancelledAt`: `Timestamp?` - Thời điểm đơn bị hủy (nếu có)
- `cancelReason`: `String?` - Lý do hủy đơn (người dùng chọn hoặc hệ thống ghi nhận)
- `cancelledBy`: `String?` - Người thực hiện hủy: `"USER"` hoặc `"OWNER"` hoặc `"SYSTEM"`
- `refundAmount`: `Long?` - Số tiền đã hoàn vào ví giả lập khi hủy (VND)
- `penaltyAmount`: `Long?` - Số tiền bị khấu trừ/phạt theo chính sách mốc 240 phút (VND)
- `holdExpiresAt`: `Timestamp?` - Hạn chót giữ chỗ (đối với đơn `HOLD` 5 phút)
- `approvalDeadline`: `Timestamp?` - Hạn chót chủ sân duyệt đơn (đối với đơn `PENDING` 60 phút - Phương án 2.A)
- `hasReviewed`: `Boolean` - Người chơi đã đánh giá đơn này chưa (mặc định `false`)
- `createdAt`: `Timestamp` - Thời điểm tạo đơn
- `updatedAt`: `Timestamp` - Thời điểm cập nhật cuối cùng

### 1.2. Collection `recurringGroups/{recurringGroupId}`
Quản lý chuỗi lịch đặt định kỳ dài hạn từ Spec `[041]`.
- `recurringGroupId`: `String` (Document ID) - Mã chuỗi đặt sân
- `userId`: `String` - ID người chơi
- `venueId`: `String` - ID cơ sở
- `venueName`: `String` - Tên cơ sở
- `courtId`: `String` - ID sân ưu tiên
- `courtName`: `String` - Tên sân
- `startHour`: `String` - Giờ bắt đầu mỗi buổi (`HH:mm`)
- `endHour`: `String` - Giờ kết thúc mỗi buổi (`HH:mm`)
- `dayOfWeeks`: `List<Int>` - Các thứ trong tuần (`Calendar.MONDAY` .. `Calendar.SUNDAY`)
- `totalWeeks`: `Int` - Số tuần đã đặt (4 đến 12 tuần)
- `totalSessions`: `Int` - Tổng số buổi trong chuỗi
- `completedSessions`: `Int` - Số buổi đã hoàn thành
- `cancelledSessions`: `Int` - Số buổi đã bị hủy
- `subtotalAmount`: `Long` - Tổng tiền toàn chuỗi
- `paidAmount`: `Long` - Số tiền đã thanh toán (100% hoặc cọc 30%)
- `paymentMethod`: `String` - `"DEPOSIT"`, `"MOCK_WALLET"`
- `status`: `String` - `"ACTIVE"`, `"COMPLETED"`, `"CANCELLED"`
- `bookingIds`: `List<String>` - Danh sách ID các đơn buổi chơi cấu thành chuỗi
- `createdAt`: `Timestamp`
- `updatedAt`: `Timestamp`

### 1.3. Subcollection `users/{userId}/walletTransactions/{txId}`
Lưu trữ lịch sử giao dịch ví giả lập với Document ID tất định chống hoàn tiền trùng lặp (Idempotency).
- `txId`: `String` - ID giao dịch tất định: `refund_{bookingId}` cho đơn hủy, hoặc UUID cho nạp tiền
- `userId`: `String`
- `type`: `String` - `"REFUND"`, `"PAYMENT"`, `"DEPOSIT"`, `"TOP_UP"`
- `amount`: `Long` - Số tiền biến động (+ hoàn tiền, - thanh toán)
- `balanceAfter`: `Long` - Số dư ví giả lập sau giao dịch
- `bookingId`: `String?` - Mã đơn liên kết
- `description`: `String` - Nội dung diễn giải (ví dụ: "Hoàn tiền hủy đơn BK_1729012345678 (70% do hủy trước giờ chơi < 4 tiếng)")
- `createdAt`: `Timestamp`

---

## 2. Kotlin Data Transfer Objects (Data Layer)

```kotlin
package com.example.booking_app.feature.booking_management.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

data class BookingDto(
    @DocumentId val bookingId: String = "",
    val userId: String = "",
    val venueId: String = "",
    val venueName: String = "",
    val courtId: String = "",
    val courtName: String = "",
    val surfaceType: String = "",
    val date: String = "",
    val slotIds: List<String> = emptyList(),
    val startAt: Timestamp? = null,
    val endAt: Timestamp? = null,
    val status: String = "HOLD",
    val subtotalAmount: Long = 0L,
    val discountAmount: Long = 0L,
    val depositAmount: Long = 0L,
    val paidAmount: Long = 0L,
    val remainingAmount: Long = 0L,
    val paymentMethod: String = "MOCK_WALLET",
    val voucherCode: String? = null,
    val isRecurring: Boolean = false,
    val recurringGroupId: String? = null,
    val sessionIndex: Int? = null,
    val totalSessions: Int? = null,
    val qrToken: String? = null,
    val checkedInAt: Timestamp? = null,
    val cancelledAt: Timestamp? = null,
    val cancelReason: String? = null,
    val cancelledBy: String? = null,
    val refundAmount: Long? = null,
    val penaltyAmount: Long? = null,
    val holdExpiresAt: Timestamp? = null,
    val approvalDeadline: Timestamp? = null,
    val hasReviewed: Boolean = false,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

data class RecurringGroupDto(
    @DocumentId val recurringGroupId: String = "",
    val userId: String = "",
    val venueId: String = "",
    val venueName: String = "",
    val courtId: String = "",
    val courtName: String = "",
    val startHour: String = "",
    val endHour: String = "",
    val dayOfWeeks: List<Int> = emptyList(),
    val totalWeeks: Int = 0,
    val totalSessions: Int = 0,
    val completedSessions: Int = 0,
    val cancelledSessions: Int = 0,
    val subtotalAmount: Long = 0L,
    val paidAmount: Long = 0L,
    val paymentMethod: String = "MOCK_WALLET",
    val status: String = "ACTIVE",
    val bookingIds: List<String> = emptyList(),
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

data class CancelBookingRequestDto(
    val bookingId: String,
    val userId: String,
    val reason: String,
    val clientTimestampMillis: Long
)
```

---

## 3. Kotlin Domain Entities (Domain Layer - Pure Kotlin)

Tuân thủ nghiêm ngặt **Constitution I**: Tuyệt đối không import thư viện Android UI (`Bitmap`, `Context`) hoặc Firebase SDK (`Timestamp`) vào tầng này.

```kotlin
package com.example.booking_app.feature.booking_management.domain.model

enum class BookingTab {
    HOLD,       // Chờ thanh toán (đếm ngược 5 phút)
    PENDING,    // Chờ duyệt (đếm ngược 60 phút)
    UPCOMING,   // Sắp tới (đơn CONFIRMED chưa diễn ra)
    COMPLETED,  // Đã hoàn thành (COMPLETED hoặc NO_SHOW)
    CANCELLED   // Đã hủy (CANCELLED hoặc REJECTED)
}

enum class BookingStatus {
    HOLD,
    PENDING,
    CONFIRMED,
    COMPLETED,
    CANCELLED,
    REJECTED,
    NO_SHOW
}

enum class PaymentMethodDomain {
    AT_VENUE,
    DEPOSIT,
    MOCK_WALLET,
    FREE
}

/**
 * Ma trận bit thuần Kotlin đại diện cho mã QR.
 * Cho phép tầng domain tính toán/sinh dữ liệu QR mà không phụ thuộc android.graphics.Bitmap.
 */
data class QrBitMatrix(
    val width: Int,
    val height: Int,
    val bits: BooleanArray
) {
    fun isSet(x: Int, y: Int): Boolean {
        require(x in 0 until width && y in 0 until height) { "Tọa độ nằm ngoài ma trận" }
        return bits[y * width + x]
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as QrBitMatrix
        return width == other.width && height == other.height && bits.contentEquals(other.bits)
    }

    override fun hashCode(): Int {
        var result = width
        result = 31 * result + height
        result = 31 * result + bits.contentHashCode()
        return result
    }
}

/**
 * Tóm tắt đơn đặt sân hiển thị trên danh sách từng tab (RecyclerView Item).
 */
data class BookingSummaryItem(
    val bookingId: String,
    val venueId: String,
    val venueName: String,
    val courtName: String,
    val date: String,
    val timeRange: String,                 // "18:00 - 19:30"
    val startAtMillis: Long,
    val endAtMillis: Long,
    val status: BookingStatus,
    val totalAmount: Long,
    val paidAmount: Long,
    val paymentMethod: PaymentMethodDomain,
    val isRecurring: Boolean,
    val sessionProgress: String?,          // "Buổi 2/8" (nếu là cố định tuần)
    val holdExpiresAtMillis: Long?,
    val approvalDeadlineMillis: Long?,
    val hasReviewed: Boolean,
    val cancelReason: String?,
    val refundAmount: Long?
)

/**
 * Chi tiết từng khung giờ đặt sân (Slot Item) trong đơn.
 * Được ánh xạ và phân giải (hydrate) từ `slotIds` của BookingDto.
 */
data class BookingSlotItem(
    val slotId: String,
    val courtId: String,
    val courtName: String,
    val date: String,
    val startAt: String,                  // "18:00"
    val endAt: String,                    // "18:30"
    val price: Long                       // VND (Long, cấm âm)
)

/**
 * Chi tiết toàn diện của một đơn đặt sân.
 */
data class BookingDetail(
    val bookingId: String,
    val userId: String,
    val venueId: String,
    val venueName: String,
    val venueAddress: String = "",
    val venuePhone: String = "",
    val courtId: String,
    val courtName: String,
    val surfaceType: String,
    val date: String,
    val timeRange: String,
    val slotIds: List<String>,
    val items: List<BookingSlotItem> = emptyList(),
    val startAtMillis: Long,
    val endAtMillis: Long,
    val status: BookingStatus,
    val subtotalAmount: Long,
    val discountAmount: Long,
    val depositAmount: Long,
    val paidAmount: Long,
    val remainingAmount: Long,
    val paymentMethod: PaymentMethodDomain,
    val voucherCode: String?,
    val isRecurring: Boolean,
    val recurringGroupId: String?,
    val sessionIndex: Int?,
    val totalSessions: Int?,
    val qrToken: String?,
    val checkedInAtMillis: Long?,
    val cancelledAtMillis: Long?,
    val cancelReason: String?,
    val cancelledBy: String?,
    val refundAmount: Long?,
    val penaltyAmount: Long?,
    val holdExpiresAtMillis: Long?,
    val approvalDeadlineMillis: Long?,
    val hasReviewed: Boolean,
    val createdAtMillis: Long
)

/**
 * Báo giá chính sách hoàn tiền khi chuẩn bị hủy đơn (tính toán theo mốc 240 phút).
 */
data class RefundPolicyQuote(
    val canCancel: Boolean,
    val isEarlyCancel: Boolean,            // >= 240 phút trước giờ chơi
    val minutesUntilStart: Long,
    val paidAmount: Long,
    val penaltyAmount: Long,
    val refundAmount: Long,
    val explanationText: String
)

/**
 * Chi tiết nhóm đơn cố định theo tuần.
 */
data class RecurringBookingGroupDetail(
    val recurringGroupId: String,
    val venueName: String,
    val courtName: String,
    val timeRange: String,
    val dayOfWeeksText: String,            // "Thứ 3, Thứ 5 hàng tuần"
    val totalWeeks: Int,
    val totalSessions: Int,
    val completedSessions: Int,
    val cancelledSessions: Int,
    val remainingSessions: Int,
    val totalAmount: Long,
    val paidAmount: Long,
    val paymentMethod: PaymentMethodDomain,
    val status: String,
    val sessions: List<BookingSummaryItem>
)
```

---

## 4. UI State & Navigation Models (UI Layer)

```kotlin
package com.example.booking_app.feature.booking_management.ui

import android.os.Parcelable
import com.example.booking_app.feature.booking_management.domain.model.BookingDetail
import com.example.booking_app.feature.booking_management.domain.model.BookingSummaryItem
import com.example.booking_app.feature.booking_management.domain.model.BookingTab
import com.example.booking_app.feature.booking_management.domain.model.QrBitMatrix
import com.example.booking_app.feature.booking_management.domain.model.RefundPolicyQuote
import kotlinx.parcelize.Parcelize

/**
 * State cho màn hình danh sách 5 Tab (MyBookingsFragment).
 */
data class MyBookingsUiState(
    val selectedTab: BookingTab = BookingTab.UPCOMING,
    val isNetworkAvailable: Boolean = true,
    val lastSyncTimeMillis: Long = 0L
)

/**
 * State cho từng Tab danh sách con (BookingTabListFragment).
 */
data class BookingTabListUiState(
    val tab: BookingTab,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val items: List<BookingSummaryItem> = emptyList(),
    val isEndReached: Boolean = false,
    val errorMessage: String? = null,
    val serverNowMillis: Long = System.currentTimeMillis()
)

/**
 * State cho màn hình chi tiết đơn (BookingDetailFragment).
 */
data class BookingDetailUiState(
    val isLoading: Boolean = false,
    val isCancelling: Boolean = false,
    val booking: BookingDetail? = null,
    val qrBitMatrix: QrBitMatrix? = null,
    val isQrLoading: Boolean = false,
    val refundQuote: RefundPolicyQuote? = null,
    val isOfflineMode: Boolean = false,
    val errorMessage: String? = null
)

/**
 * Các hiệu ứng điều hướng hoặc thông báo 1 lần (One-time Side Effects).
 */
sealed interface BookingUiEffect {
    data class ShowToast(val message: String) : BookingUiEffect
    data class ShowErrorSnackbar(val message: String) : BookingUiEffect
    data class NavigateToDetail(val bookingId: String) : BookingUiEffect
    data class NavigateToPayment(
        val bookingId: String,
        val holdExpiresAtMillis: Long
    ) : BookingUiEffect
    data class NavigateToRebook(val venueId: String) : BookingUiEffect
    data class NavigateToReview(
        val bookingId: String,
        val venueId: String,
        val courtName: String
    ) : BookingUiEffect
    data class OpenCancelConfirmationDialog(
        val bookingId: String,
        val quote: RefundPolicyQuote
    ) : BookingUiEffect
}

/**
 * Arguments nhận từ màn hình E-Receipt của Spec 050.
 */
@Parcelize
data class ReceiptToMyBookingsArgs(
    val bookingId: String,
    val isRecurring: Boolean = false,
    val initialTab: String = "PENDING"
) : Parcelable
```

---

## 5. Quy tắc Ánh xạ & Phân giải Dữ liệu (`BookingManagementMapper`)

Quy chuẩn chuyển đổi giữa tầng Data (`BookingDto`) và tầng Domain (`BookingSummaryItem`, `BookingDetail`):

### 5.1. Phân giải `slotIds` thành `items: List<BookingSlotItem>`
- **Định dạng `slotId`**: `{courtId}_{yyyyMMdd}_{HHmm}` (ví dụ: `court_vip1_20261025_1800`).
- **Logic phân giải (Hydration)**:
  - `courtId`: Trích xuất từ phần đầu của `slotId` (hoặc `dto.courtId`).
  - `courtName`: Kế thừa từ `dto.courtName`.
  - `date`: `dto.date` (định dạng `yyyy-MM-dd`).
  - `startAt`: Trích xuất từ 4 ký tự cuối của `slotId` và format `HH:mm` (ví dụ `1800` $\rightarrow$ `"18:00"`).
  - `endAt`: Tính toán `startAt + 30 phút` (ví dụ `"18:30"`).
  - `price`: Phân bổ đều `dto.subtotalAmount / dto.slotIds.size` (hoặc khớp theo giá từng khung giờ).
- Đảm bảo danh sách `items` được sắp xếp theo thời gian tăng dần (`startAt`).

### 5.2. Chuẩn hóa Timestamp sang Milliseconds
- `approvalDeadlineMillis = dto.approvalDeadline?.toDate()?.time` (thay thế cho `pendingExpiresAt`).
- `holdExpiresAtMillis = dto.holdExpiresAt?.toDate()?.time`.
- `startAtMillis = dto.startAt?.toDate()?.time ?: 0L`.
- `endAtMillis = dto.endAt?.toDate()?.time ?: 0L`.
- `checkedInAtMillis = dto.checkedInAt?.toDate()?.time`.
- `cancelledAtMillis = dto.cancelledAt?.toDate()?.time`.
- `createdAtMillis = dto.createdAt?.toDate()?.time ?: 0L`.

### 5.3. Ánh xạ Enum an toàn
- `status`: Chuyển chuỗi Firestore sang `BookingStatus` enum tương ứng (`HOLD`, `PENDING`, `CONFIRMED`, `COMPLETED`, `CANCELLED`, `REJECTED`, `NO_SHOW`). Fallback an toàn nếu có giá trị ngoài danh mục.
- `paymentMethod`: Chuyển sang `PaymentMethodDomain` enum (`AT_VENUE`, `DEPOSIT`, `MOCK_WALLET`, `FREE`).

