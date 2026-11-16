# Contract: Edge Functions của spec 050

Mọi function do app gọi đều nhận `Authorization: Bearer <Firebase ID token>`, xác thực bằng `requireUser` (`_shared/auth.ts`) và trả lỗi theo `errorResponse` (`{ "error": { "code", "message" } }`), như các function khác. Tiền là số nguyên VND. Cấu hình `verify_jwt = false` trong `supabase/config.toml`, vì JWT là token Firebase, không phải token Supabase.

Mã lỗi dùng chung:

| HTTP | `code` | Khi nào |
|---|---|---|
| 401 | `UNAUTHENTICATED` | Thiếu hoặc sai token |
| 403 | `FORBIDDEN` | Không phải chủ đơn, hoặc tài khoản bị khóa |
| 409 | `HOLD_EXPIRED` | Hết hạn giữ chỗ, hoặc slot không còn do mình giữ |
| 409 | `INVALID_STATE` | Đơn không ở trạng thái cho phép |
| 409 | `PRICE_CHANGED` | Giá server tính khác `expectedTotal`; body có `breakdown` mới |
| 422 | `VOUCHER_INVALID` | Kèm `reason`: `NOT_FOUND`, `EXPIRED`, `MIN_ORDER`, `OUT_OF_USES`, `PER_USER_LIMIT` |
| 422 | `TRANSFER_NOT_SUPPORTED` | Cơ sở chưa có `payoutAccount` đủ trường |

---

## 1. `booking-payment-confirm` (POST)

Chốt đơn **trả tại sân** hoặc **miễn phí** (tổng 0đ). Cũng dùng để đổi từ chờ chuyển khoản sang trả tại sân.

```json
// request
{
  "requestId": "uuid",                 // = bookingId (đơn lẻ); idempotent
  "recurringGroupId": null,            // có khi là đơn định kỳ
  "venueId": "v1",
  "slotIds": ["c1_20261120_1800", "c1_20261120_1830"],  // đơn lẻ
  "voucherCode": "SMASH20",            // tùy chọn
  "expectedTotal": 170000
}
// 200
{ "bookingId": "uuid", "status": "PENDING", "paymentStatus": "UNPAID" | "PAID",
  "receipt": { /* PaymentReceipt, xem data-model.md §2 */ } }
```

- Gọi lại cùng `requestId` sau khi đã thành công thì trả lại đúng kết quả cũ.
- Nếu đơn đang `HOLD` + `AWAITING_TRANSFER`: đặt `AT_VENUE`/`UNPAID`, mã thanh toán `active = false`, voucher đang giữ tạm chuyển `USED`.

## 2. `booking-payment-transfer` (POST)

Bắt đầu chuyển khoản: gia hạn giữ chỗ 15 phút, giữ tạm voucher, cấp mã thanh toán.

```json
// request
{
  "requestId": "uuid", "recurringGroupId": null, "venueId": "v1",
  "slotIds": ["..."], "voucherCode": null,
  "option": "TRANSFER_DEPOSIT" | "TRANSFER_FULL",
  "expectedTotal": 200000
}
// 200
{
  "bookingId": "uuid",
  "paymentCode": "SNPK7Q2M9XA",
  "amountDue": 60000,
  "holdExpiresAt": "2026-11-20T11:15:00Z",
  "qrUrl": "https://qr.sepay.vn/img?acc=...&bank=...&amount=60000&des=SNPK7Q2M9XA",
  "payoutAccount": { "bankShortName": "MBBank", "accountNumber": "0123456789", "accountHolderName": "NGUYEN VAN A" },
  "sandbox": true
}
```

- Idempotent theo `requestId`: gọi lại khi đơn đang `AWAITING_TRANSFER` thì trả lại cùng mã và cùng hạn, **không** gia hạn thêm.
- Đơn có `total == 0` thì trả 409 `INVALID_STATE` (phải dùng `booking-payment-confirm`).

## 3. `sepay-webhook` (POST, do SePay gọi)

- Header `X-SePay-Signature: sha256=<hex>`, `X-SePay-Timestamp: <unix giây>`. Secret lấy từ `SEPAY_WEBHOOK_SECRET`. Sai chữ ký hoặc lệch giờ quá 300 giây thì trả 401.
- Body: payload "Tiền vào" của SePay (`id`, `transferType`, `transferAmount`, `code`, `content`, `referenceCode`, `transactionDate`, …).
- Trả 200 `{ "success": true, "outcome": "<OUTCOME>" }` cho mọi giao dịch hợp lệ về chữ ký, kể cả không khớp đơn. Trả 500 khi lỗi tạm thời. Bảng outcome xem [research.md R4](../research.md#r4-webhook-sepay-tái-dùng-_sharedsepayts-đổi-đích-từ-ví-sang-đơn).

## 4. `owner-mark-refunded` (POST, chủ sân)

```json
{ "refundId": "refund_uuid" }
// 200
{ "refundId": "refund_uuid", "status": "DONE", "bookingPaymentStatus": "REFUNDED" | null }
```

Chỉ chủ của `refunds.venueId` gọi được (403 nếu không phải). Gọi lại khi đã `DONE` thì trả lại kết quả cũ.

## 5. `refund-report` (POST, người chơi)

```json
{ "refundId": "refund_uuid", "note": "Đã 4 ngày chưa nhận" }
// 201
{ "reportId": "r123" }
```

Chỉ `refunds.userId` gọi được. Điều kiện: refund `PENDING` đã quá 3 ngày, hoặc `DONE` (người chơi báo chưa nhận được). Không đủ điều kiện thì trả 409 `INVALID_STATE`. Function tạo `reports` với `targetType = VENUE`, `targetPath = venues/{venueId}`, `reason = REFUND_NOT_RECEIVED`, kèm `refundId`, `bookingId`, `amount`; đặt `refunds.reportedAt`. Mỗi refund chỉ tạo một báo cáo.

## 6. `refund-reminders` (cron mỗi giờ)

Header `x-cron-secret` = `CRON_SECRET`. Tìm refund `PENDING` có `createdAt` trước thời điểm hiện tại 24 giờ và `remindedAt == null`, ghi `users/{ownerId}/notifications` loại `REFUND_REMINDER`, rồi đặt `remindedAt`.

## Secrets

`SEPAY_WEBHOOK_SECRET` (đặt bằng `supabase secrets set`). Function dùng service account Firebase sẵn có. Không cần `SEPAY_BANK_ACCOUNT`/`SEPAY_BANK_CODE` nữa, vì tài khoản lấy từ `venues.payoutAccount`.
