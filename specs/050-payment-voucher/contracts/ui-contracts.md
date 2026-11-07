# UI & Domain Contracts: Thanh toán và Khuyến mãi giả lập (spec 050)

**Ngày**: 2026-10-15 · **Spec**: [spec.md](../spec.md) · **Plan**: [plan.md](../plan.md)

Tài liệu này xác lập các hợp đồng giao tiếp giữa các tầng kiến trúc trong tính năng `050-payment-voucher` và điểm giao thoa với các tính năng khác (`040`, `041`, `060`).

---

## 1. Hợp đồng Điều hướng (Navigation Safe Args)

### 1.1. Tiếp nhận dữ liệu đầu vào (Từ `040`, `041`, hoặc `060` - Tab Chờ thanh toán)
Thống nhất sử dụng chung lớp Safe Args `HoldToPaymentArgs` cho mọi luồng chuyển tới màn hình thanh toán (đơn ngày lẻ từ `[040]`, đơn chu kỳ từ `[041]`, hoặc bấm "Thanh toán tiếp" từ `[060]` theo FR-027):
```kotlin
@Parcelize
data class HoldToPaymentArgs(
    val bookingId: String,               // ID đơn lẻ hoặc recurringGroupId
    val venueId: String,
    val venueName: String,
    val isRecurring: Boolean,
    val totalSessions: Int,              // 1 (nếu đơn lẻ) hoặc N >= 4 (đơn cố định)
    val subtotalAmount: Long,
    val slotIds: Array<String>,          // Danh sách slot để cập nhật BOOKED khi trả xong
    val holdExpiresAtMillis: Long        // Mốc thời gian đếm ngược còn lại
) : Parcelable
```

### 1.2. Điều hướng sang Màn hình Quản lý đơn (`060-my-bookings`)
Khi người dùng bấm "Xem lịch đặt của tôi" từ màn hình Hóa đơn điện tử (`PaymentReceiptFragment`):
```kotlin
@Parcelize
data class ReceiptToMyBookingsArgs(
    val bookingId: String,               // ID đơn lẻ hoặc recurringGroupId
    val isRecurring: Boolean = false,
    val initialTab: String = "PENDING"   // Mở ngay tab "Chờ duyệt" (60 phút)
) : Parcelable
```

---

## 2. Hợp đồng Tầng Domain (Domain Repository Interface)

Đặt tại `com.example.booking_app.feature.payment.domain.repository.PaymentRepository`:

```kotlin
package com.example.booking_app.feature.payment.domain.repository

import com.example.booking_app.feature.payment.domain.model.PaymentMethod
import com.example.booking_app.feature.payment.domain.model.PaymentReceipt
import com.example.booking_app.feature.payment.domain.model.Voucher

interface PaymentRepository {
    /**
     * Lấy số dư ví giả lập hiện tại của người dùng
     */
    suspend fun getWalletBalance(userId: String): Result<Long>

    /**
     * Nạp tiền ảo vào ví giả lập (ghi log TOP_UP)
     */
    suspend fun topUpMockWallet(userId: String, amount: Long): Result<Long>

    /**
     * Đọc song song danh sách voucher khả dụng từ cả 2 nguồn (Cơ sở & Hệ thống)
     */
    suspend fun getAvailableVouchers(venueId: String): Result<List<Voucher>>

    /**
     * Tra cứu một voucher theo mã code từ 2 nguồn (ưu tiên cơ sở trước)
     */
    suspend fun getVoucherByCode(code: String, venueId: String): Result<Voucher?>

    /**
     * Kiểm tra số lần người dùng đã sử dụng mã voucher này
     */
    suspend fun getUserVoucherUsageCount(code: String, userId: String): Result<Int>

    /**
     * Thực thi Firestore Transaction nguyên tử thanh toán, trừ ví, tăng lượt voucher và khóa slot sang BOOKED
     */
    suspend fun processPaymentTransaction(
        userId: String,
        bookingId: String,
        isRecurring: Boolean,
        method: PaymentMethod,
        requiredAmount: Long,
        voucher: Voucher?,
        depositAmount: Long,
        discountAmount: Long,
        payableAmount: Long,
        slotIds: List<String>
    ): Result<PaymentReceipt>

    /**
     * Chủ động hủy phiên giữ chỗ và nhả các slot khi chạm mốc 00:00 hoặc người dùng bấm Hủy
     */
    suspend fun cancelPaymentHold(
        bookingId: String,
        isRecurring: Boolean,
        slotIds: List<String>
    ): Result<Unit>
}
```

---

## 3. Hợp đồng các UseCase cốt lõi

### 3.1. `ValidateAndApplyVoucherUseCase`
Kiểm tra tính hợp lệ và tính số tiền giảm giá:
```kotlin
class ValidateAndApplyVoucherUseCase {
    operator fun invoke(
        voucher: Voucher,
        subtotalAmount: Long,
        userUsageCount: Int
    ): VoucherValidationResult
}
```

### 3.2. `CalculatePaymentBreakdownUseCase`
Tính toán phân bổ số tiền theo các phương thức và quy tắc cọc 30% làm tròn:
```kotlin
class CalculatePaymentBreakdownUseCase {
    operator fun invoke(
        subtotalAmount: Long,
        discountAmount: Long,
        isRecurring: Boolean
    ): PaymentBreakdown
}
```

### 3.3. `ProcessMockPaymentUseCase`
Thực thi toàn bộ luồng thanh toán trong Firestore Transaction:
```kotlin
class ProcessMockPaymentUseCase {
    suspend operator fun invoke(
        userId: String,
        bookingId: String,
        isRecurring: Boolean,
        method: PaymentMethod,
        breakdown: PaymentBreakdown,
        appliedVoucher: Voucher?,
        slotIds: List<String>
    ): Result<PaymentReceipt>
}
```

### 3.4. `CancelPaymentHoldUseCase`
Giải phóng giữ chỗ khi hết hạn hoặc hủy:
```kotlin
class CancelPaymentHoldUseCase {
    suspend operator fun invoke(
        bookingId: String,
        isRecurring: Boolean,
        slotIds: List<String>
    ): Result<Unit>
}
```
