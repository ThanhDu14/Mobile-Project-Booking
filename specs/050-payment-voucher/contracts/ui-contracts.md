# UI & Domain Contracts: Thanh toán và Khuyến mãi (spec 050, bản 2026-11-16)

**Ngày**: 2026-11-16 · **Spec**: [spec.md](../spec.md) · **Plan**: [plan.md](../plan.md) · **API**: [edge-functions.md](edge-functions.md)

---

## 1. Điều hướng

### 1.1. Vào màn thanh toán (từ 040, 041, hoặc "Thanh toán tiếp" ở 060)

`HoldToPaymentArgs` hiện nằm ở `feature/booking/ui`. Theo constitution I, một tính năng không được import `ui/` của tính năng khác, nên chuyển lớp này sang `core/navigation/HoldToPaymentArgs.kt` và **giữ nguyên các trường** (`bookingId`, `venueId`, `venueName`, `venueAddress`, `districtName`, `photoUrl`, `isRecurring`, `totalSessions`, `subtotalAmount`, `slotIds`, `holdExpiresAtMillis`, `courtDetails`, `depositAmount`). Với đơn định kỳ, `bookingId` là `recurringGroupId`.

040/041 điều hướng `action_*_to_payment` sang `nav_payment` (graph lồng) thay vì tự gọi `ConfirmHoldToBookingUseCase`. Khi 050 xong thì bỏ `ConfirmHoldToBookingUseCase` cùng transaction ở app.

### 1.2. Các màn trong `nav_payment`

| Màn | Fragment | Vào khi |
|---|---|---|
| Thanh toán | `PaymentFragment` | Từ 040/041/060 với `HoldToPaymentArgs` |
| Chuyển khoản | `TransferFragment` | Sau khi `booking-payment-transfer` trả 200; arg `TransferArgs(bookingId, isRecurring, paymentCode, amountDue, holdExpiresAtMillis, qrUrl, bankShortName, accountNumber, accountHolderName)` |
| Hóa đơn | `PaymentReceiptFragment` | Khi đơn sang `PENDING`; arg `ReceiptArgs(bookingId, isRecurring)`, màn đọc lại đơn để hiển thị |

Back từ `TransferFragment` về `PaymentFragment` để đổi sang trả tại sân. Back từ hóa đơn thì xóa cả `nav_payment` khỏi back stack.

### 1.3. Sang 060

Nút "Xem lịch đặt của tôi" trên hóa đơn điều hướng `ReceiptToMyBookingsArgs(bookingId, isRecurring, initialTab = "PENDING")`, giữ nguyên như bản cũ.

---

## 2. Repository (`feature/payment/domain/repository`)

```kotlin
interface VoucherRepository {
    /** Voucher cơ sở + hệ thống còn hiệu lực, cho danh sách chọn. */
    suspend fun available(venueId: String): Result<List<Voucher>>
    /** Tìm theo mã: cơ sở trước, hệ thống sau. null = không có. */
    suspend fun findByCode(venueId: String, code: String): Result<Voucher?>
}

interface VenuePayoutRepository {
    suspend fun payoutAccount(venueId: String): Result<PayoutAccount?>
}

interface PaymentRepository {
    suspend fun confirmAtVenue(req: ConfirmRequest): Result<PaymentReceipt>      // booking-payment-confirm
    suspend fun startTransfer(req: TransferRequest): Result<TransferInstruction> // booking-payment-transfer
    /** Listener realtime bookings/{id} hoặc recurringGroups/{id}. */
    fun observe(bookingId: String, isRecurring: Boolean): Flow<BookingPaymentState>
}
```

Lỗi từ Edge Function được ánh xạ thành `PaymentError` (sealed): `HoldExpired`, `PriceChanged(breakdown)`, `VoucherInvalid(reason)`, `TransferNotSupported`, `Network`, `Unknown`.

## 3. Use case

| Use case | Việc |
|---|---|
| `PreviewVoucherUseCase(voucher, subtotal)` → `VoucherCheck` | Kiểm tra hạn, đơn tối thiểu, `isActive` ở app (server kiểm lại, kể cả số lượt) |
| `PaymentCalculator.breakdown(subtotal, voucher)` → `PaymentBreakdown` | Công thức R7, dùng chung bảng ca test với TS |
| `ConfirmAtVenueUseCase` | Gọi `confirmAtVenue` với `requestId` cố định của phiên |
| `StartTransferUseCase` | Gọi `startTransfer` |
| `ObserveBookingPaymentUseCase` | Bọc `observe`, cấp trạng thái cho màn chuyển khoản |

`requestId`: lấy `HoldToPaymentArgs.bookingId` nếu có, không thì sinh UUID một lần và giữ trong `SavedStateHandle`, để bấm lại hoặc thử lại không tạo đơn trùng.

## 4. Chuỗi hiển thị bắt buộc

| Key | Nội dung |
|---|---|
| `payment_mock_banner` | "Hệ thống thanh toán giả lập phục vụ học tập - Không thu tiền thật" |
| `payment_sandbox_banner` | "Môi trường thử nghiệm (sandbox) - Không chuyển tiền thật, dùng Mô phỏng giao dịch" |
| `payment_transfer_unsupported` | "Cơ sở chưa hỗ trợ chuyển khoản" |
| `payment_hold_expired` | "Đã hết thời gian giữ chỗ" |
| `receipt_mock_note` | "Giao dịch giả lập phục vụ học tập - Không thu tiền thật" |
| `payment_late_refund` | "Đơn không thành công, chủ sân sẽ hoàn tiền" |
