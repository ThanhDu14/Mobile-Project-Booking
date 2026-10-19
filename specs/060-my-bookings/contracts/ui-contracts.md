# UI & Domain Contracts: Quản lý lịch đặt của tôi (spec 060)

**Ngày**: 2026-10-15 · **Spec**: [spec.md](../spec.md) · **Plan**: [plan.md](../plan.md)

Tài liệu này xác lập các hợp đồng giao tiếp giữa các tầng kiến trúc trong tính năng `060-my-bookings` và điểm giao thoa điều hướng với các tính năng khác (`040`, `041`, `050`, `070`) cùng quy tắc bảo mật Firestore Security Rules.

---

## 1. Hợp đồng Điều hướng (Navigation Safe Args)

### 1.1. Tiếp nhận điều hướng từ Hóa đơn điện tử (`050-payment-voucher`)
Khi thanh toán xong, người chơi bấm "Xem lịch đặt của tôi":
```kotlin
package com.example.booking_app.feature.booking_management.ui

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class ReceiptToMyBookingsArgs(
    val bookingId: String,
    val isRecurring: Boolean = false,
    val initialTab: String = "PENDING"   // Mở ngay tab "Chờ duyệt"
) : Parcelable
```

### 1.2. Tiếp tục thanh toán đơn giữ chỗ (`HOLD`) sang `050-payment-voucher`
Khi người dùng bấm "Thanh toán tiếp" từ Tab 1 (Chờ thanh toán) trước khi hết hạn 5 phút, sử dụng chung lớp Safe Args `HoldToPaymentArgs`:
```kotlin
@Parcelize
data class HoldToPaymentArgs(
    val bookingId: String,
    val venueId: String,
    val venueName: String,
    val isRecurring: Boolean,
    val totalSessions: Int,
    val subtotalAmount: Long,
    val slotIds: Array<String>,
    val holdExpiresAtMillis: Long        // Kế thừa mốc thời gian còn lại, không reset 5 phút
) : Parcelable
```

### 1.3. Đặt lại sân này sang `040-booking-slot-grid`
Khi người dùng bấm "Đặt lại sân này" từ đơn đã hoàn thành hoặc đã hủy:
```kotlin
@Parcelize
data class RebookArgs(
    val venueId: String
) : Parcelable
```

### 1.4. Viết đánh giá sang `070-reviews-ratings`
Với các đơn `COMPLETED` (và chưa từng đánh giá `hasReviewed == false`), mở màn hình viết đánh giá:
```kotlin
@Parcelize
data class BookingToReviewArgs(
    val bookingId: String,
    val venueId: String,
    val venueName: String,
    val courtName: String
) : Parcelable
```
*Lưu ý: Đơn mang trạng thái `NO_SHOW` bị vô hiệu hóa hoàn toàn nút "Viết đánh giá" theo Phương án 5.A.*

---

## 2. Hợp đồng Tầng Domain (Domain Repository Interface)

Được định nghĩa thuần túy tại `com.example.booking_app.feature.booking_management.domain.repository.BookingManagementRepository`:

```kotlin
package com.example.booking_app.feature.booking_management.domain.repository

import com.example.booking_app.feature.booking_management.domain.model.BookingDetail
import com.example.booking_app.feature.booking_management.domain.model.BookingSummaryItem
import com.example.booking_app.feature.booking_management.domain.model.BookingTab
import com.example.booking_app.feature.booking_management.domain.model.RecurringBookingGroupDetail
import com.example.booking_app.feature.booking_management.domain.model.RefundPolicyQuote
import kotlinx.coroutines.flow.Flow

interface BookingManagementRepository {

    /**
     * Lấy danh sách tóm tắt các đơn đặt sân theo từng Tab phân trang.
     * @param userId ID người chơi hiện tại
     * @param tab Tab cần truy vấn (HOLD, PENDING, UPCOMING, COMPLETED, CANCELLED)
     * @param lastVisibleTimestamp Mốc phân trang của item cuối cùng ở trang trước
     * @param pageSize Số lượng bản ghi mỗi trang (mặc định 10)
     */
    suspend fun getBookingsByTab(
        userId: String,
        tab: BookingTab,
        lastVisibleTimestamp: Long?,
        pageSize: Int = 10
    ): Result<List<BookingSummaryItem>>

    /**
     * Lắng nghe realtime trạng thái chi tiết của 1 đơn hàng (kịp thời nhận biết khi chủ sân quét QR check-in).
     */
    fun observeBookingDetail(bookingId: String): Flow<Result<BookingDetail>>

    /**
     * Lấy chi tiết chuỗi đơn đặt cố định theo tuần và danh sách các buổi con.
     */
    suspend fun getRecurringGroupDetail(groupId: String): Result<RecurringBookingGroupDetail>

    /**
     * Thực thi Firestore Transaction hủy một đơn lẻ:
     * - Kiểm tra điều kiện mốc 240 phút và tính toán quote
     * - Chống trùng lặp (Idempotent) qua document `users/{userId}/walletTransactions/refund_{bookingId}`
     * - Cập nhật trạng thái đơn sang CANCELLED
     * - Hoàn tiền vào ví giả lập nếu có
     * - Xóa toàn bộ các document trong `slots/{slotId}` để nhả sân ngay lập tức trên lưới 040
     */
    suspend fun cancelBooking(
        userId: String,
        bookingId: String,
        reason: String,
        quote: RefundPolicyQuote
    ): Result<Unit>

    /**
     * Hủy riêng lẻ một buổi trong chuỗi cố định tuần.
     */
    suspend fun cancelRecurringSession(
        userId: String,
        recurringGroupId: String,
        bookingId: String,
        reason: String,
        quote: RefundPolicyQuote
    ): Result<Unit>

    /**
     * Lấy giờ chuẩn hiện tại từ Firebase Server để đồng bộ thời gian đếm ngược chính xác.
     */
    suspend fun getServerTimeMillis(): Result<Long>
}
```

---

## 3. Hợp đồng các UseCase cốt lõi

### 3.1. `GetMyBookingsByTabUseCase`
```kotlin
class GetMyBookingsByTabUseCase(private val repository: BookingManagementRepository) {
    suspend operator fun invoke(
        userId: String,
        tab: BookingTab,
        lastVisibleTimestamp: Long? = null
    ): Result<List<BookingSummaryItem>>
}
```

### 3.2. `GetBookingDetailUseCase`
```kotlin
class GetBookingDetailUseCase(private val repository: BookingManagementRepository) {
    operator fun invoke(bookingId: String): Flow<Result<BookingDetail>>
}
```

### 3.3. `CalculateRefundQuoteUseCase`
Tính toán số tiền phạt và hoàn theo mốc 240 phút (Pure Kotlin, không gọi mạng):
```kotlin
class CalculateRefundQuoteUseCase {
    operator fun invoke(
        booking: BookingDetail,
        serverNowMillis: Long
    ): RefundPolicyQuote
}
```

### 3.4. `CancelBookingUseCase`
```kotlin
class CancelBookingUseCase(
    private val repository: BookingManagementRepository,
    private val calculateRefundQuoteUseCase: CalculateRefundQuoteUseCase
) {
    suspend operator fun invoke(
        userId: String,
        booking: BookingDetail,
        reason: String
    ): Result<RefundPolicyQuote>
}
```

### 3.5. `GenerateCheckInQrUseCase`
Sinh dữ liệu ma trận bit thuần Kotlin từ chuỗi `qrToken` bằng thư viện ZXing Core:
```kotlin
class GenerateCheckInQrUseCase {
    operator fun invoke(
        qrToken: String,
        width: Int = 512,
        height: Int = 512
    ): Result<QrBitMatrix>
}
```

---

## 4. Hợp đồng Bảo mật Firestore Security Rules

Tuân thủ nghiêm ngặt **Constitution II**: Ngăn chặn người dùng sửa giờ máy Client để gian lận tiền hoàn hủy trễ bằng cách ép sử dụng `request.time` (Server Time của Google Cloud):

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {

    // Quy tắc cho collection bookings
    match /bookings/{bookingId} {
      // Chỉ chủ đơn hoặc chủ cơ sở mới được đọc dữ liệu chi tiết (bao gồm qrToken)
      allow read: if request.auth != null && (
        resource.data.userId == request.auth.uid ||
        request.auth.token.role == "VENUE_OWNER"
      );

      // Cho phép hủy đơn phía Client thông qua Transaction nếu tuân thủ chặt chẽ các điều kiện:
      allow update: if request.auth != null &&
        resource.data.userId == request.auth.uid &&
        // Chỉ được đổi trạng thái từ PENDING hoặc CONFIRMED sang CANCELLED
        resource.data.status in ['PENDING', 'CONFIRMED'] &&
        request.resource.data.status == 'CANCELLED' &&
        // Chưa từng check-in
        resource.data.checkedInAt == null &&
        // Bắt buộc giờ chơi phải lớn hơn giờ chuẩn server Google lúc gửi request
        resource.data.startAt > request.time &&
        // Kiểm tra tiền hoàn theo mốc 240 phút dựa trên request.time:
        (
          // Trường hợp 1: Hủy sớm >= 240 phút trước giờ chơi -> Hoàn 100%
          (resource.data.startAt.toMillis() - request.time.toMillis() >= 240 * 60 * 1000 &&
           request.resource.data.refundAmount == resource.data.paidAmount)
          ||
          // Trường hợp 2: Hủy trễ < 240 phút -> Hoàn tối đa 70% nếu trả ví, hoặc 0đ nếu cọc/sân
          (resource.data.startAt.toMillis() - request.time.toMillis() < 240 * 60 * 1000 &&
           request.resource.data.refundAmount <= resource.data.paidAmount)
        );
    }

    // Quy tắc hoàn tiền ví giả lập
    match /users/{userId}/walletTransactions/{txId} {
      // Document ID phải tuân theo định dạng tất định refund_{bookingId}
      allow create: if request.auth != null &&
        request.auth.uid == userId &&
        txId.matches('refund_.*') &&
        request.resource.data.type == 'REFUND';
      allow read: if request.auth != null && request.auth.uid == userId;
    }
  }
}
```
