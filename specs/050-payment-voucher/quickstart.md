# Quickstart: Thanh toán và Khuyến mãi (spec 050, bản 2026-11-16)

**Ngày**: 2026-11-16 · **Spec**: [spec.md](spec.md) · **Plan**: [plan.md](plan.md) · **API**: [contracts/edge-functions.md](contracts/edge-functions.md)

Hướng dẫn chạy và kiểm chứng. Không chứa code cài đặt.

---

## 1. Chuẩn bị

- JDK 11, Android SDK (minSdk 29), Deno 2, Node 20, Firebase CLI, Supabase CLI.
- Tài khoản **SePay Test mode** của nhóm (không bao giờ dùng Live):
  1. Trên my.sepay.vn (chế độ Test), thêm tài khoản ngân hàng test. Ở mục "Cấu trúc mã thanh toán", khai tiền tố `SNP` và 8 ký tự.
  2. Tạo webhook "Tiền vào" trỏ tới `https://<supabase-ref>.functions.supabase.co/sepay-webhook`, bật chữ ký HMAC, chép secret.
  3. `supabase secrets set SEPAY_WEBHOOK_SECRET=<secret>`. Không đưa secret vào app hay repo.
- Seed một cơ sở demo có `payoutAccount` là số tài khoản test ở bước 1, và một cơ sở **không có** `payoutAccount`.

## 2. Test tự động

| Lệnh | Kiểm gì |
|---|---|
| `./gradlew testDebugUnitTest --tests "com.example.booking_app.feature.payment.*"` | `PaymentCalculator` (bảng ca test chung), `PreviewVoucherUseCase`, các ViewModel (đếm ngược, đổi cách trả, chuyển màn khi listener báo `PENDING`/`EXPIRED`, ánh xạ lỗi) |
| `cd supabase/functions && deno test -A _shared/payment-calc.test.ts` | Bản TS chạy **cùng bảng ca test** với Kotlin |
| `deno test -A booking-payment-confirm/ booking-payment-transfer/ sepay-webhook/ owner-mark-refunded/ refund-report/ refund-reminders/ expire-holds/` | Handler với Firestore giả: idempotent theo `requestId`, gia hạn 15 phút, giữ/nhả voucher, đủ 7 outcome của webhook, webhook gửi lại 3 lần chỉ ghi nhận 1, quyền chủ sân/chủ đơn |
| `cd firestore-tests && npm test` (Firebase Emulator) | Rules: app không ghi được `bookings` `PENDING`, `paymentCodes`, `sepayTransactions`, `refunds`, `userUsage`, `redemptions`; đọc được voucher cơ sở; chỉ chủ venue ghi `payoutAccount` |

Bảng ca test chung tối thiểu (spec SC, edge cases):

| subtotal | voucher | total | cọc 30% | còn lại |
|---|---|---|---|---|
| 200.000 | – | 200.000 | 60.000 | 140.000 |
| 175.000 | – | 175.000 | 53.000 | 122.000 |
| 300.000 | 10%, không trần | 270.000 | 81.000 | 189.000 |
| 150.000 | 50%, trần 40.000 | 110.000 | 33.000 | 77.000 |
| 80.000 | cố định 100.000 | 0 | 0 (miễn phí) | 0 |
| 1.000 | – | 1.000 | 1.000 (không cọc 0đ) | 0 |

## 3. Kịch bản thủ công (máy ảo + Supabase cloud + SePay Test)

1. **Trả tại sân**: giữ chỗ một đơn 200.000đ, chọn "Thanh toán tại sân", bấm xác nhận. Kỳ vọng phiếu đặt sân "Chưa thanh toán", đơn ở `PENDING`, slot `BOOKED`.
2. **Chuyển khoản cọc**: đơn 175.000đ, chọn chuyển khoản cọc 30%. Kỳ vọng màn QR hiện 53.000đ, mã `SNP…`, tên ngân hàng, số tài khoản, tên chủ tài khoản của cơ sở, banner sandbox, đồng hồ khoảng 15:00. Trên my.sepay.vn chọn "Mô phỏng giao dịch" tiền vào 53.000đ với nội dung là mã đó. Kỳ vọng trong vòng 10 giây app tự sang hóa đơn "Đã cọc 53.000đ, còn 122.000đ".
3. **Webhook gửi lại**: trên SePay bấm gửi lại webhook của giao dịch ở bước 2. Kỳ vọng `sepayTransactions/{id}` có một bản ghi, đơn không đổi.
4. **Sai số tiền**: đơn mới, mô phỏng 50.000đ thay vì 60.000đ. Kỳ vọng đơn vẫn chờ, có refund `PENDING` lý do `AMOUNT_MISMATCH` trong danh sách cần hoàn của chủ sân. Mô phỏng thêm 60.000đ đúng mã thì đơn sang `PENDING`.
5. **Tiền về muộn**: đơn mới, chờ hết 15 phút (đơn sang `EXPIRED`, slot trống, voucher được nhả), rồi mới mô phỏng tiền vào. Kỳ vọng refund `LATE_TRANSFER`, người chơi nhận "Đơn không thành công, chủ sân sẽ hoàn tiền".
6. **Đổi ý**: đang ở màn QR thì bấm back, chọn "Thanh toán tại sân". Kỳ vọng đơn `PENDING`/`UNPAID`. Mô phỏng tiền theo mã cũ thì ra refund `LATE_TRANSFER`.
7. **Voucher lượt cuối**: voucher `usageLimit = 1`. Tài khoản A bắt đầu chuyển khoản với mã này. Tài khoản B áp cùng mã rồi bấm chuyển khoản. Kỳ vọng B bị báo hết lượt và giá được tính lại. A để hết hạn thì B dùng lại được.
8. **Cơ sở chưa có tài khoản**: kỳ vọng chỉ còn "Thanh toán tại sân" với ghi chú "Cơ sở chưa hỗ trợ chuyển khoản".
9. **Hoàn tiền thủ công**: chủ sân từ chối đơn đã cọc (112). Kỳ vọng `paymentStatus = REFUND_PENDING` và có refund `PENDING`. Chủ sân bấm "Đã hoàn tiền" thì đơn chuyển `REFUNDED`.
10. **Nhắc và báo cáo**: sửa `createdAt` của refund lùi 25 giờ rồi chạy `refund-reminders`: chủ sân nhận nhắc một lần. Lùi 4 ngày: người chơi thấy nút "Báo cáo chưa nhận được tiền hoàn", bấm thì Admin thấy báo cáo ở màn 120.
