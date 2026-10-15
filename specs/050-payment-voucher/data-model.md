# Data Model: Thanh toán và Khuyến mãi giả lập (spec 050)

**Ngày**: 2026-10-15 · **Spec**: [spec.md](spec.md) · **Plan**: [plan.md](plan.md)

Tài liệu này định nghĩa chi tiết mô hình cơ sở dữ liệu Firestore, Kotlin DTOs, Domain Entities và UI Models cho module thanh toán và khuyến mãi giả lập.

---

## 1. Firestore Schema

### 1.1. Collection `venues/{venueId}/vouchers/{code}` & `systemVouchers/{code}` (Phương án 3.B)
Document ID chính là mã code viết hoa không dấu (ví dụ `SMASH50K`, `HELLOSMASH`).
- `code`: `String` - Mã voucher
- `title`: `String` - Tên voucher hiển thị
- `description`: `String` - Mô tả điều kiện
- `scope`: `String` - `"VENUE"` hoặc `"SYSTEM"`
- `venueId`: `String?` - ID cơ sở áp dụng (nếu là voucher cơ sở)
- `discountType`: `String` - `"PERCENTAGE"` hoặc `"FIXED_AMOUNT"`
- `discountValue`: `Long` - Tỷ lệ % hoặc số tiền giảm cố định (VND)
- `maxDiscount`: `Long?` - Mức giảm tối đa (khi giảm theo %)
- `minOrderTotal`: `Long` - Giá trị đơn hàng tối thiểu để áp dụng
- `usageLimit`: `Int` - Tổng số lượt dùng tối đa trên toàn hệ thống
- `usedCount`: `Int` - Số lượt đã sử dụng thành công
- `perUserLimit`: `Int` - Số lượt dùng tối đa cho mỗi người chơi
- `validFrom`: `Timestamp` - Thời điểm bắt đầu hiệu lực
- `validUntil`: `Timestamp` - Thời điểm hết hạn
- `isActive`: `Boolean` - Đang kích hoạt hay tạm khóa

### 1.2. Subcollection `.../vouchers/{code}/userUsage/{userId}`
Lưu số lượt user này đã sử dụng voucher, dùng để kiểm tra `perUserLimit` trong Transaction.
- `userId`: `String`
- `count`: `Int` - Số lần đã sử dụng thành công
- `lastUsedAt`: `Timestamp`

### 1.3. Subcollection `.../vouchers/{code}/redemptions/{bookingId}`
Ghi nhận chi tiết từng giao dịch áp dụng voucher thành công.
- `bookingId`: `String`
- `userId`: `String`
- `discountAmount`: `Long`
- `redeemedAt`: `Timestamp`

### 1.4. Subcollection `users/{userId}/walletTransactions/{txId}`
Lịch sử biến động số dư ví giả lập.
- `txId`: `String` - UUID giao dịch
- `userId`: `String`
- `type`: `String` - `"TOP_UP"`, `"PAYMENT"`, `"DEPOSIT"`, `"REFUND"`
- `amount`: `Long` - Số tiền biến động (+ hoặc -)
- `balanceAfter`: `Long` - Số dư ví sau giao dịch
- `bookingId`: `String?` - Đơn đặt liên kết
- `description`: `String`
- `createdAt`: `Timestamp`

---

## 2. Kotlin Data Transfer Objects (Data Layer)

```kotlin
package com.example.booking_app.feature.payment.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

data class VoucherDto(
    @DocumentId val code: String = "",
    val title: String = "",
    val description: String = "",
    val scope: String = "VENUE",
    val venueId: String? = null,
    val discountType: String = "PERCENTAGE",
    val discountValue: Long = 0L,
    val maxDiscount: Long? = null,
    val minOrderTotal: Long = 0L,
    val usageLimit: Int = 100,
    val usedCount: Int = 0,
    val perUserLimit: Int = 1,
    val validFrom: Timestamp? = null,
    val validUntil: Timestamp? = null,
    val isActive: Boolean = true
)

data class VoucherUserUsageDto(
    @DocumentId val userId: String = "",
    val count: Int = 0,
    val lastUsedAt: Timestamp? = null
)

data class VoucherRedemptionDto(
    @DocumentId val bookingId: String = "",
    val userId: String = "",
    val discountAmount: Long = 0L,
    val redeemedAt: Timestamp? = null
)

data class WalletTransactionDto(
    @DocumentId val txId: String = "",
    val userId: String = "",
    val type: String = "TOP_UP",
    val amount: Long = 0L,
    val balanceAfter: Long = 0L,
    val bookingId: String? = null,
    val description: String = "",
    val createdAt: Timestamp? = null
)
```

---

## 3. Kotlin Domain Entities (Domain Layer)

```kotlin
package com.example.booking_app.feature.payment.domain.model

enum class VoucherScope { VENUE, SYSTEM }
enum class DiscountType { PERCENTAGE, FIXED_AMOUNT }

data class Voucher(
    val code: String,
    val title: String,
    val description: String,
    val scope: VoucherScope,
    val venueId: String?,
    val discountType: DiscountType,
    val discountValue: Long,
    val maxDiscount: Long?,
    val minOrderTotal: Long,
    val usageLimit: Int,
    val usedCount: Int,
    val perUserLimit: Int,
    val validFromMillis: Long,
    val validUntilMillis: Long,
    val isActive: Boolean
)

sealed interface VoucherValidationResult {
    data class Valid(val discountAmount: Long, val voucher: Voucher) : VoucherValidationResult
    object NotFound : VoucherValidationResult
    object Expired : VoucherValidationResult
    data class MinOrderNotMet(val minAmount: Long) : VoucherValidationResult
    object GlobalUsageLimitReached : VoucherValidationResult
    object PerUserLimitReached : VoucherValidationResult
    object VenueNotApplicable : VoucherValidationResult
}

enum class PaymentMethod {
    AT_VENUE,       // Trả tại sân (chỉ đơn lẻ)
    DEPOSIT,        // Cọc 30% ví giả lập
    MOCK_WALLET,    // Trả 100% ví giả lập
    FREE            // Giảm 100% qua voucher
}

data class PaymentBreakdown(
    val subtotalAmount: Long,
    val discountAmount: Long,
    val payableAmount: Long,
    val depositAmount: Long,
    val remainingAmount: Long,
    val isRecurring: Boolean
)

data class PaymentReceipt(
    val bookingId: String,
    val isRecurring: Boolean,
    val recurringGroupId: String?,
    val venueName: String,
    val paymentMethod: PaymentMethod,
    val subtotalAmount: Long,
    val discountAmount: Long,
    val depositAmount: Long,
    val remainingAmount: Long,
    val finalPaidAmount: Long,
    val voucherCode: String?,
    val paidAtMillis: Long,
    val mockWarningText: String = "Giao dịch giả lập phục vụ học tập - Không thu tiền thật"
)
```

---

## 4. UI State & Navigation Models (UI Layer)

```kotlin
package com.example.booking_app.feature.payment.ui

import android.os.Parcelable
import com.example.booking_app.feature.payment.domain.model.PaymentBreakdown
import com.example.booking_app.feature.payment.domain.model.PaymentMethod
import com.example.booking_app.feature.payment.domain.model.PaymentReceipt
import com.example.booking_app.feature.payment.domain.model.Voucher
import kotlinx.parcelize.Parcelize

data class PaymentUiState(
    val isLoading: Boolean = false,
    val isProcessingPayment: Boolean = false,
    val bookingId: String = "",
    val venueId: String = "",
    val venueName: String = "",
    val isRecurring: Boolean = false,
    val holdCountdownSeconds: Int = 300,
    val breakdown: PaymentBreakdown? = null,
    val selectedMethod: PaymentMethod = PaymentMethod.MOCK_WALLET,
    val availableMethods: List<PaymentMethod> = emptyList(),
    val appliedVoucher: Voucher? = null,
    val availableVouchers: List<Voucher> = emptyList(),
    val walletBalance: Long = 0L,
    val errorMessage: String? = null
)

sealed interface PaymentUiEffect {
    data class ShowToast(val message: String) : PaymentUiEffect
    data class ShowErrorDialog(val title: String, val message: String) : PaymentUiEffect
    data class NavigateToReceipt(val receipt: PaymentReceipt) : PaymentUiEffect
    object NavigateBackToPrevious : PaymentUiEffect
}

@Parcelize
data class ReceiptToMyBookingsArgs(
    val bookingId: String,
    val isRecurring: Boolean = false,
    val initialTab: String = "PENDING"
) : Parcelable
```
