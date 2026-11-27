# Research: Thanh toán và Khuyến mãi (spec 050, bản 2026-11-16)

**Ngày**: 2026-11-16 · **Spec**: [spec.md](spec.md) · **Plan**: [plan.md](plan.md)

Bản này thay hoàn toàn bản 2026-10-15 (ví giả lập, app tự chạy Firestore transaction). Không còn mục `NEEDS CLARIFICATION`.

---

## R1. Xác nhận đơn chạy ở Edge Function, app không tự ghi đơn nữa

**Hiện trạng**: `FirestoreBookingRemoteDataSource.confirmHoldToBooking` (040) chạy Firestore transaction ở app để tạo `bookings/{id}` và chuyển slot `HELD → BOOKED`. Rules đang mở tạm "lát cắt test 050" cho player tạo đơn `PENDING`.

**Quyết định**: Mọi thao tác chốt tiền và chuyển trạng thái đơn đi qua Edge Function (service account, transaction qua Firestore REST như `expire-holds`, `player-cancel-booking`):
- `booking-payment-confirm`: trả tại sân, hoặc đơn miễn phí (tổng 0đ).
- `booking-payment-transfer`: bắt đầu chuyển khoản (tạo đơn chờ chuyển khoản, gia hạn giữ chỗ, giữ tạm voucher, cấp mã thanh toán).
- `sepay-webhook`: ghi nhận tiền về.

Sau khi các function chạy, thu hồi nhánh `status == 'PENDING'` trong rule `bookings` create và bỏ đường ghi đơn ở app.

**Lý do**: Constitution II (người dùng không được tự ghi trạng thái đơn đã thanh toán và số tiền) và III (transaction ở server). Chuyển khoản bắt buộc có server vì webhook chỉ gọi được server.

**Phương án đã loại**: Giữ transaction ở app cho trả tại sân, chỉ đưa chuyển khoản lên server. Bị loại vì có hai đường ghi đơn khác nhau, Rules phải mở cho app ghi `paymentStatus`.

---

## R2. Đơn chờ chuyển khoản vẫn ở `HOLD`, gia hạn giữ chỗ 15 phút

**Quyết định**: `booking-payment-transfer` trong một transaction:
1. Kiểm tra mọi slot của đơn đang `HELD`, `holdBy == uid`, chưa hết hạn (đơn lẻ 040), hoặc nhóm `recurringGroups/{id}` đang `HOLD` (041).
2. Tạo (đơn lẻ) hoặc cập nhật (nhóm) đơn với `status = HOLD`, `paymentStatus = AWAITING_TRANSFER`, `holdExpiresAt = now + 15 phút`.
3. Ghi `holdExpiresAt` mới và `bookingId` vào từng slot.

`expire-holds` (đã có, chạy mỗi phút) tiếp tục là nơi duy nhất chuyển `HOLD → EXPIRED` và xóa slot. Chỉ cần bổ sung bước nhả lượt voucher giữ tạm và vô hiệu mã thanh toán (R5, R6).

**Lý do**: Không thêm trạng thái mới vào máy trạng thái của đặc tả CSDL §4.5. Rules của `slots` giới hạn client chỉ giữ tối đa 5 phút, server dùng service account nên gia hạn được mà không phải nới Rules.

**Lưu ý**: Code đang dùng `HELD` cho slot, đặc tả CSDL §4.4 ghi `HOLD`. Function mới đọc theo code (`HELD`). Việc thống nhất tên ghi ở mục "Việc liên spec".

---

## R3. Mã thanh toán (`paymentCode`) và bảng tra `paymentCodes`

**Quyết định**:
- Mã = `SNP` + 8 ký tự từ bảng `ABCDEFGHJKLMNPQRSTUVWXYZ23456789` (bỏ 0/O, 1/I). Đây là hàm `generateTopupCode` đã có, chỉ đổi tiền tố. Trên my.sepay.vn khai "Cấu trúc mã thanh toán" là `SNP` + 8 ký tự.
- Document `paymentCodes/{code}` gồm `bookingId` hoặc `recurringGroupId`, `userId`, `venueId`, `amount`, `active`, `createdAt`. Document này tạo bằng precondition `exists = false`, nên mã trùng thì sinh lại (tối đa 3 lần, như `wallet-topup-create`).
- Webhook tìm đơn qua `paymentCodes/{code}` trong transaction, không quét `bookings`.

**Lý do**: `bookingId` là UUID có chữ thường và dấu gạch, ngân hàng có thể đổi hoa/thường hoặc cắt ký tự. Mã ngắn, in hoa thì dò được trong nội dung chuyển khoản (`findCodeInContent`). Một bảng tra dùng chung cho cả đơn lẻ và nhóm định kỳ.

---

## R4. Webhook SePay: tái dùng `_shared/sepay.ts`, đổi đích từ ví sang đơn

**Tái dùng nguyên**: `verifySepaySignature` (HMAC-SHA256 trên `{timestamp}.{rawBody}`, lệch giờ tối đa 300 giây), `parseSepayTransaction`, `findCodeInContent`, `topupQrUrl` (đổi tên thành `vietQrUrl`), sổ chống trùng `sepayTransactions/{id}` ghi trong cùng transaction.

**Đổi**: thay `walletTopups/{code}` bằng `paymentCodes/{code}` cùng đơn/nhóm. Bỏ `wallet-topup-create` (chưa commit) và rule `walletTopups`.

**Thứ tự kiểm tra trong transaction**:

| Bước | Điều kiện | Kết quả `outcome` |
|---|---|---|
| 1 | Đã có `sepayTransactions/{id}` | `DUPLICATE` (không ghi gì) |
| 2 | `transferType != in` | `NOT_INCOMING` |
| 3 | Không dò được mã | `NO_CODE` |
| 4 | Không có `paymentCodes/{code}` | `CODE_NOT_FOUND` |
| 5 | Đơn không còn `HOLD` + `AWAITING_TRANSFER`, hoặc mã `active = false`, hoặc có slot không còn `HELD` cho đơn này | `LATE_TRANSFER`: tạo `RefundItem` |
| 6 | `transferAmount != amountDue` | `AMOUNT_MISMATCH`: tạo `RefundItem`, đơn vẫn chờ (người chơi chuyển lại đúng số tiền vẫn được) |
| 7 | Hợp lệ | `CONFIRMED`: đơn sang `PENDING`, slot `BOOKED`, voucher `RESERVED → USED`, mã `active = false`, ghi thông báo `BOOKING_NEW` |

Mọi kết quả từ bước 2 đến 7 trả HTTP 200 `{"success": true}` để SePay ngừng gửi lại. Lỗi tạm thời (Firestore, mạng) trả 500 để SePay thử lại; nhờ sổ chống trùng nên thử lại vẫn an toàn.

**Lý do cho bước 5**: không so `transactionDate` với `holdExpiresAt`. Thay vào đó coi đơn còn sống khi `expire-holds` chưa dọn **và** slot vẫn đang giữ cho đơn này. Như vậy có thêm tối đa khoảng 1 phút ân hạn mà không bao giờ cướp slot người khác đã giữ lại.

---

## R5. Giữ tạm lượt voucher

**Quyết định**:
- `booking-payment-transfer` (và `booking-payment-confirm` khi có voucher) đọc voucher (cơ sở trước, rồi `systemVouchers`) cùng `.../userUsage/{uid}`, kiểm tra hiệu lực, `usedCount < usageLimit`, `count < perUserLimit`.
- **Giữ tạm**: tăng `usedCount` và `userUsage.count`, tạo `redemptions/{bookingId}` với `status = RESERVED`.
- **Chính thức**: webhook (hoặc confirm tại sân) đổi `status = USED`, ghi `usedAt`.
- **Nhả**: `expire-holds` khi đơn hết hạn thì giảm `usedCount`, `userUsage.count` và xóa redemption `RESERVED`, trong cùng transaction với việc chuyển `EXPIRED`.

**Lý do**: Giới hạn luôn được tính trên số đã giữ cộng đã dùng, nên không bao giờ vượt `usageLimit` (SC-005). Không ai trả tiền xong mới biết voucher hết lượt.

**Phương án đã loại**: Chỉ đếm khi tiền về (spec đã chọn A ở câu 2).

---

## R6. Đổi sang trả tại sân khi đang chờ chuyển khoản

**Quyết định**: `booking-payment-confirm` nhận đơn đang `HOLD` + `AWAITING_TRANSFER`, đặt `paymentMethod = AT_VENUE`, `paymentStatus = UNPAID`, đơn sang `PENDING`, slot `BOOKED`, `paymentCodes/{code}.active = false`. Nếu sau đó tiền vẫn về theo mã cũ thì webhook xử lý như `LATE_TRANSFER` (bước 5 của R4) và tạo `RefundItem`.

---

## R7. Tính tiền và xem trước voucher ở app, server tính lại khi chốt

**Quyết định**:
- Công thức thuần (không I/O) có hai bản dùng **chung bộ ca test**: Kotlin `PaymentCalculator` ở `feature/payment/domain` và TypeScript `_shared/payment-calc.ts`.
  - `discount`: `PERCENT` thì `min(subtotal * value / 100, maxDiscount ?: ∞)`, `FIXED` thì `value`. Sau đó `discount = min(discount, subtotal)`.
  - `total = subtotal - discount` (≥ 0).
  - Cọc: `raw = total * 30 / 100`, `rounded = ((raw + 500) / 1000) * 1000`, `deposit = if (rounded == 0) total else min(rounded, total)`.
  - `amountDue = deposit` (cọc) hoặc `total` (trả đủ). `total == 0` thì không chọn cách trả, `paymentStatus = PAID`.
- App xem trước giá ngay khi áp mã (SC-001 < 1 giây) bằng cách đọc voucher từ Firestore. Cần **thêm rule đọc** `venues/{venueId}/vouchers/{code}` cho người đã đăng nhập, giống `systemVouchers`. Hiện chưa có rule này nên app không đọc được voucher cơ sở.
- Server luôn tính lại từ voucher và giá trong slot/đơn, không tin số tiền app gửi lên. Nếu khác số app hiển thị thì trả `PRICE_CHANGED` kèm bảng giá mới để app hiện lại.

**Phương án đã loại**: Gọi Edge Function `quote` mỗi lần áp mã. Bị loại vì cold start dễ vượt 1 giây và tốn lượt gọi.

---

## R8. Màn hình người chơi tự chuyển sang hóa đơn

**Quyết định**: App nghe realtime `bookings/{bookingId}` (đơn lẻ) hoặc `recurringGroups/{id}` (nhóm) bằng `callbackFlow`. Khi `status` chuyển `PENDING` thì điều hướng sang hóa đơn, khi chuyển `EXPIRED` thì hiện "Đã hết thời gian giữ chỗ". Rules hiện đã cho chủ đơn đọc đơn của mình.

**Lý do**: SC-002 (dưới 10 giây sau webhook) đạt được mà không cần polling. Firestore listener đẩy thay đổi gần như tức thì.

---

## R9. Hoàn tiền thủ công: dùng lại collection `refunds`

**Hiện trạng**: `player-cancel-booking` và `owner-cancel-booking` ghi `refunds/refund_{bookingId}` với `refundStatus = COMPLETED`, rồi cộng ví (`users.walletBalance`).

**Quyết định**: `RefundItem` chính là document trong `refunds`:
- ID `refund_{bookingId}` cho hoàn theo đơn (từ chối, quá hạn duyệt, hủy). ID `refund_sepay_{sepayId}` cho tiền về muộn hoặc sai số tiền, vì một đơn có thể có nhiều khoản như vậy.
- Trường mới: `status` (`PENDING`, `DONE`), `reason`, `venueId`, `userId`, `amount`, `sepayTransactionId?`, `doneAt?`, `remindedAt?`, `reportedAt?`.
- Bỏ việc cộng ví ở mọi function. Đơn đã trả bị từ chối, quá hạn hoặc hủy thì ghi `paymentStatus = REFUND_PENDING` và tạo refund `PENDING`. Số tiền hoàn khi người chơi hủy vẫn theo `RefundPolicy` của 060.
- Function mới `owner-mark-refunded`: chủ sân của `venueId` bấm "Đã hoàn tiền" thì refund chuyển `DONE` và đơn chuyển `paymentStatus = REFUNDED` (nếu refund gắn với đơn).
- Cron mới `refund-reminders` (mỗi giờ): refund `PENDING` quá 24 giờ, chưa nhắc lần nào thì ghi thông báo nhắc chủ sân, đặt `remindedAt`.
- Function mới `refund-report`: người chơi báo cáo khi refund chờ quá 3 ngày, hoặc đã `DONE` mà chưa nhận. Function tạo `reports/{id}` với `targetType = VENUE` (Rules chặn app ghi `reports`, và hiện chưa có đường tạo báo cáo cho người chơi).

---

## R10. Thông tin nhận tiền của cơ sở và VietQR

**Quyết định**:
- `venues/{venueId}.payoutAccount` (Map): `bankBin` (mã BIN NAPAS, dùng cho QR), `bankShortName` (ví dụ `MBBank`), `accountNumber`, `accountHolderName`. Chủ sân nhập ở spec 111. Rules: chỉ chủ cơ sở ghi, ai cũng đọc được như các trường khác của venue (số tài khoản vốn là để người khác chuyển vào).
- Cơ sở có `payoutAccount` đủ 4 trường mới hiện chuyển khoản (spec câu 3). Server kiểm tra lại và trả `TRANSFER_NOT_SUPPORTED` nếu thiếu.
- Ảnh QR lấy từ `https://qr.sepay.vn/img?acc=…&bank=…&amount=…&des={paymentCode}`. Server trả URL trong response, app tải bằng Coil (đã có trong `libs.versions.toml`).

**Rủi ro (Constitution VI)**: QR trỏ tới tài khoản ngân hàng thật của chủ sân, nên người chơi **có thể chuyển tiền thật** bằng app ngân hàng. Tiền đó không đi qua SePay sandbox, hệ thống không biết. Cách giảm rủi ro:
- (a) Màn chuyển khoản ghi rõ "Môi trường thử nghiệm (sandbox) - Không chuyển tiền thật, dùng Mô phỏng giao dịch" (FR-032).
- (b) Dữ liệu demo dùng tài khoản test của SePay.
- (c) Câu chữ constitution VI cần sửa cho khớp (việc liên spec).

---

## R11. Tác động lên phần đã có (ngoài phạm vi code của 050 nhưng phải sửa cùng lúc)

| Chỗ | Thay đổi | Phụ trách |
|---|---|---|
| `on-signup` | Bỏ `walletBalance: 0` | A |
| `core/session/MyProfile`, `FirestoreUserProfileReader`, `DeleteAccountFragment` | Bỏ số dư ví | A |
| `player-cancel-booking`, `RefundPolicy` (060) | Bỏ `MOCK_WALLET`/`DEPOSIT`, dùng `TRANSFER_*`; tạo refund `PENDING` thay vì cộng ví | B |
| `owner-cancel-booking`, `owner-reject-booking` (112) | Đơn đã trả → `REFUND_PENDING` + refund `PENDING` | D |
| `booking-timeout` (112, chưa có) | Khi làm: theo cách trên, không hoàn ví | D |
| Spec 111 | Form nhập `payoutAccount` | D |
| Spec 112 | Màn "Cần hoàn tiền" + nút "Đã hoàn tiền" (gọi `owner-mark-refunded`) | D |
| `expire-holds` | Nhả voucher `RESERVED`, đặt `paymentCodes.active = false` | B |
| Đặc tả CSDL §4.1, §4.4, §4.5, §4.6, §8a | Cập nhật theo [data-model.md](data-model.md) | B |
