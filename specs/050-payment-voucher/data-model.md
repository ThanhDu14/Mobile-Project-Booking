# Data Model: Thanh toán và Khuyến mãi (spec 050, bản 2026-11-16)

**Ngày**: 2026-11-16 · **Spec**: [spec.md](spec.md) · **Research**: [research.md](research.md)

🔒 = chỉ Edge Function ghi (service account). Tiền luôn là `Long`/số nguyên VND.

---

## 1. Firestore

### 1.1. `bookings/{bookingId}`: trường thanh toán (thay §4.5 đặc tả CSDL)

`bookingId` = `requestId` (UUID app sinh khi bấm xác nhận), như hiện tại.

| Trường | Kiểu | Ghi chú |
|---|---|---|
| `status` 🔒 | String | `HOLD` → `PENDING` → `CONFIRMED` …, nhánh `EXPIRED`, `REJECTED`, `CANCELLED`. Máy trạng thái giữ nguyên |
| `subtotal`, `discount`, `total` 🔒 | Long | Server tính |
| `voucherCode` 🔒 | String? | |
| `voucherScope` 🔒 | String? | `VENUE`, `SYSTEM`: voucher nằm ở collection nào, dùng khi nhả hoặc chốt |
| `paymentMethod` 🔒 | String | `AT_VENUE`, `TRANSFER_DEPOSIT`, `TRANSFER_FULL` |
| `paymentStatus` 🔒 | String | `UNPAID`, `AWAITING_TRANSFER`, `DEPOSITED`, `PAID`, `REFUND_PENDING`, `REFUNDED` |
| `amountDue` 🔒 | Long | Số phải chuyển (cọc hoặc `total`); 0 với `AT_VENUE` |
| `depositAmount`, `paidAmount`, `remainingAmount` 🔒 | Long | Giữ nguyên tên đang dùng ở 060/112 |
| `paymentCode` 🔒 | String? | `SNP` + 8 ký tự; có khi chọn chuyển khoản |
| `holdExpiresAt` 🔒 | Timestamp | Khi chuyển khoản: `now + 15 phút` |
| `paidAt`, `sepayTransactionId` 🔒 | Timestamp?, Long? | Ghi khi webhook xác nhận |
| `pendingAt`, `approvalDeadline` 🔒 | Timestamp | Như hiện tại |
| `refundAmount` 🔒 | Long? | Số chủ sân phải hoàn (không còn là số đã cộng ví) |

Đơn định kỳ: các trường thanh toán nằm ở `recurringGroups/{id}` (thêm `status` 🔒 `HOLD`/`DONE`, `holdExpiresAt`, `paymentCode`, `amountDue`, `paidAt`, `sepayTransactionId`). Từng buổi `bookings` lấy `paymentStatus` theo nhóm, cách phân bổ tiền theo 060.

**Chuyển `paymentStatus` hợp lệ**:

```
(chọn tại sân)        ──▶ UNPAID ─(chủ sân thu khi check-in, 112)──▶ PAID
(tổng = 0)            ──▶ PAID
(chọn chuyển khoản)   ──▶ AWAITING_TRANSFER ─(webhook đúng tiền)──▶ DEPOSITED | PAID
                              └─(đổi sang tại sân)──▶ UNPAID
DEPOSITED | PAID ─(từ chối / quá hạn duyệt / hủy)──▶ REFUND_PENDING ─(chủ sân bấm Đã hoàn)──▶ REFUNDED
```

### 1.2. `slots/{courtId}_{yyyyMMdd}_{HHmm}`

Không thêm trường. Khi bắt đầu chuyển khoản, server ghi `holdExpiresAt = now + 15 phút` và `bookingId`. Code đang dùng `status = HELD` (đặc tả ghi `HOLD`); function mới theo code.

### 1.3. `paymentCodes/{code}` (mới) 🔒

| Trường | Kiểu | Ghi chú |
|---|---|---|
| `code` | String | = ID |
| `bookingId` | String? | Đơn lẻ |
| `recurringGroupId` | String? | Đơn định kỳ |
| `userId`, `venueId` | String | |
| `amount` | Long | = `amountDue` lúc cấp mã |
| `active` | Boolean | `false` khi đã nhận tiền, đổi sang tại sân hoặc đơn hết hạn |
| `createdAt` | Timestamp | |

Rules: app không đọc, không ghi.

### 1.4. `sepayTransactions/{sepayId}` (mới) 🔒

`sepayId`, `transferType`, `transferAmount`, `content`, `referenceCode`, `transactionDate`, `paymentCode?`, `bookingId?`, `recurringGroupId?`, `refundId?`, `outcome` (`CONFIRMED`, `NOT_INCOMING`, `NO_CODE`, `CODE_NOT_FOUND`, `LATE_TRANSFER`, `AMOUNT_MISMATCH`), `receivedAt`. Rules: app không đọc, không ghi.

### 1.5. `refunds/{refundId}`: RefundItem (mở rộng collection đang có)

ID: `refund_{bookingId}` (hoàn theo đơn) hoặc `refund_sepay_{sepayId}` (tiền về muộn hoặc sai số tiền).

| Trường | Kiểu | Ghi chú |
|---|---|---|
| `bookingId` | String? | |
| `recurringGroupId` | String? | |
| `venueId`, `userId` | String | |
| `amount` | Long | Số chủ sân phải trả lại |
| `penaltyAmount` | Long | Giữ từ 060 (hủy muộn); 0 với các lý do khác |
| `reason` | String | `REJECTED`, `APPROVAL_TIMEOUT`, `CANCELLED`, `LATE_TRANSFER`, `AMOUNT_MISMATCH` |
| `status` | String | `PENDING`, `DONE` (thay `refundStatus = COMPLETED` cũ) |
| `sepayTransactionId` | Long? | |
| `createdAt`, `doneAt`, `remindedAt`, `reportedAt` | Timestamp? | |

Rules: người chơi đọc refund có `userId` của mình, chủ sân đọc refund của cơ sở mình; mọi thao tác ghi qua Edge Function.

### 1.6. Voucher: `venues/{venueId}/vouchers/{code}`, `systemVouchers/{code}`

Giữ schema §4.6 (`type` `PERCENT`/`FIXED`, `value`, `maxDiscount`, `minOrderTotal`, `validFrom`, `validTo`, `usageLimit`, `usedCount` 🔒, `perUserLimit`, `isActive`). Thêm:
- `.../userUsage/{uid}` 🔒: `count` (Int), tính cả lượt `RESERVED`.
- `.../redemptions/{bookingId}` 🔒: `userId`, `status` (`RESERVED`, `USED`), `reservedAt`, `usedAt?`. Ở nhóm định kỳ, khóa là `recurringGroupId`.
- Rules: **thêm quyền đọc** `venues/{venueId}/vouchers/{code}` cho người đã đăng nhập (giống `systemVouchers`). `userUsage`, `redemptions`: app không đọc.

### 1.7. `venues/{venueId}.payoutAccount` (mới, spec 111 ghi)

`bankBin` (String, 6 số BIN NAPAS), `bankShortName` (String), `accountNumber` (String, 6–19 chữ số), `accountHolderName` (String, in hoa không dấu). Đủ 4 trường mới được coi là "có tài khoản nhận tiền".

### 1.8. Bỏ

`users.walletBalance`, `users/{uid}/walletTransactions`, `walletTopups` (code chưa commit). Giá trị `paymentMethod` `DEPOSIT`/`MOCK_WALLET`.

---

## 2. Domain (Kotlin, `feature/payment/domain/model`)

```kotlin
enum class PaymentOption { AT_VENUE, TRANSFER_DEPOSIT, TRANSFER_FULL }

enum class PaymentStatus { UNPAID, AWAITING_TRANSFER, DEPOSITED, PAID, REFUND_PENDING, REFUNDED }

data class PaymentBreakdown(
    val subtotal: Long, val discount: Long, val total: Long,
    val depositAmount: Long,  // 30% làm tròn nghìn
    val isFree: Boolean,      // total == 0
) { fun amountDue(option: PaymentOption): Long }

data class AppliedVoucher(val code: String, val scope: VoucherScope, val discount: Long)

sealed interface VoucherCheck {
    data class Valid(val voucher: AppliedVoucher) : VoucherCheck
    data object NotFound : VoucherCheck
    data object Expired : VoucherCheck
    data class MinOrderNotMet(val minOrderTotal: Long) : VoucherCheck
    data object OutOfUses : VoucherCheck
    data object PerUserLimitReached : VoucherCheck
}

data class PayoutAccount(val bankBin: String, val bankShortName: String,
                         val accountNumber: String, val accountHolderName: String)

data class TransferInstruction(
    val bookingId: String, val paymentCode: String, val amountDue: Long,
    val holdExpiresAtMillis: Long, val qrUrl: String, val payoutAccount: PayoutAccount,
)

sealed interface BookingPaymentState {      // từ listener realtime
    data class AwaitingTransfer(val holdExpiresAtMillis: Long) : BookingPaymentState
    data class Confirmed(val receipt: PaymentReceipt) : BookingPaymentState
    data object Expired : BookingPaymentState
}

data class PaymentReceipt(
    val bookingId: String, val recurringGroupId: String?, val venueName: String,
    val items: List<String>, val option: PaymentOption, val status: PaymentStatus,
    val subtotal: Long, val discount: Long, val total: Long,
    val paidAmount: Long, val remainingAmount: Long, val voucherCode: String?,
)
```

`PaymentCalculator` (object thuần) cài công thức ở [research.md R7](research.md#r7-tính-tiền-và-xem-trước-voucher-ở-app-server-tính-lại-khi-chốt). Bản TypeScript `_shared/payment-calc.ts` dùng cùng bảng ca test.

---

## 3. UI state (`feature/payment/ui`)

```kotlin
data class PaymentUiState(
    val loading: Boolean = true,
    val summary: OrderSummary? = null,           // từ PaymentArgs
    val breakdown: PaymentBreakdown? = null,
    val voucher: AppliedVoucher? = null,
    val voucherError: String? = null,
    val transferSupported: Boolean = false,      // venue có payoutAccount
    val selected: PaymentOption = PaymentOption.AT_VENUE,
    val holdRemainingSeconds: Long = 0,
    val submitting: Boolean = false,
    val expired: Boolean = false,
    val error: String? = null,
)

data class TransferUiState(
    val instruction: TransferInstruction? = null,
    val holdRemainingSeconds: Long = 0,
    val expired: Boolean = false,
    val switchingToVenue: Boolean = false,
    val error: String? = null,
)

sealed interface PaymentEffect {
    data class OpenTransfer(val instruction: TransferInstruction) : PaymentEffect
    data class OpenReceipt(val receipt: PaymentReceipt) : PaymentEffect
    data class ShowMessage(val text: String) : PaymentEffect
}
```

Hợp đồng điều hướng xem [contracts/ui-contracts.md](contracts/ui-contracts.md).
