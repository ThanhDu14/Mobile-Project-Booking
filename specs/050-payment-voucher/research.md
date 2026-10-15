# Research: Thanh toán và Khuyến mãi giả lập (spec 050)

**Ngày**: 2026-10-15 · **Spec**: [spec.md](spec.md) · **Plan**: [plan.md](plan.md)

Tài liệu này ghi nhận các nghiên cứu kỹ thuật chuyên sâu về tính toàn vẹn giao dịch (Atomic Transaction), xử lý đa nguồn voucher, và chính sách cọc/hoàn tiền giả lập. Không còn mục `NEEDS CLARIFICATION`.

---

## R1. Thiết kế giao dịch nguyên tử trong Firestore Transaction (Luồng Đọc-trước-Ghi-sau)

**Vấn đề:**
- Khi người dùng bấm "Xác nhận thanh toán", hệ thống cần:
  1. Kiểm tra đơn giữ chỗ còn hạn không (`holdExpiresAt > now`).
  2. Kiểm tra voucher còn lượt không (`usedCount < usageLimit` và `perUserLimit`).
  3. Kiểm tra ví giả lập có đủ tiền không (`walletBalance >= requiredAmount`).
  4. Trừ tiền ví, tăng lượt voucher, chuyển đơn sang `PENDING` và cập nhật slot từ `HOLD` sang `BOOKED`.
- Firestore Transaction có quy tắc nghiêm ngặt: **Mọi thao tác đọc (`get`) phải thực hiện trước mọi thao tác ghi (`set`, `update`, `delete`)**. Nếu vi phạm, Transaction sẽ bị hủy ngay lập tức.

**Quyết định:**
- Thiết kế `ProcessMockPaymentUseCase` tuân thủ chặt chẽ 2 pha:
  - **Pha 1 (Read Phase)**:
    - Đọc document đơn (`bookings/{id}` hoặc `recurringGroups/{id}`).
    - Đọc document voucher (`vouchers/{code}`) và document đếm lượt cá nhân (`.../vouchers/{code}/userUsage/{userId}`).
    - Đọc document người dùng (`users/{userId}`).
    - Thực hiện kiểm tra toàn bộ điều kiện logic (hạn giữ chỗ, voucher, số dư ví).
  - **Pha 2 (Write Phase)**:
    - Cập nhật số dư ví và tạo bản ghi log giao dịch `walletTransactions/{txId}`.
    - Cập nhật `usedCount + 1`, `userUsage/{userId}.count + 1`, và ghi `redemptions/{bookingId}`.
    - Cập nhật trạng thái đơn sang `PENDING`.
    - Cập nhật toàn bộ các `slots` liên quan sang `status = "BOOKED"` và xóa trường `holdExpiresAt`.
- Nếu có bất kỳ lỗi nào ở Pha 1, Transaction ném ngoại lệ tương ứng (`HoldExpiredException`, `VoucherInvalidException`, `InsufficientBalanceException`) $\rightarrow$ Firestore tự động hủy bỏ, không có bất kỳ dữ liệu nào bị ghi dở dang.

---

## R2. Xử lý 2 nguồn voucher độc lập (Cơ sở & Toàn sàn - Phương án 3.B)

**Quyết định:**
- Theo kết quả thống nhất liên thành viên (Phương án 3.B):
  - Voucher cơ sở lưu tại: `venues/{venueId}/vouchers/{code}` (do chủ sân tạo ở spec 111).
  - Voucher toàn sàn lưu tại: `systemVouchers/{code}` (do Admin tạo ở spec 120).
- **Quy ước Document ID**: Sử dụng chính mã code viết hoa không dấu (ví dụ `SMASH50K`, `HELLOSMASH`) làm ID của document. Điều này giúp Firestore tự động đảm bảo tính duy nhất (Unique constraint), không thể tạo 2 voucher cùng mã trong cùng một phạm vi.
- **Thứ tự tra cứu**:
  - Khi người dùng nhập mã: Truy vấn song song bằng Coroutines `async` vào cả 2 collection.
  - Nếu trùng mã ở cả 2 nơi: Ưu tiên áp dụng voucher của cơ sở sân hiện tại trước, sau đó mới đến voucher toàn sàn.
- **Kiểm tra `perUserLimit` trong Transaction**:
  - Tách subcollection `.../vouchers/{code}/userUsage/{userId}` lưu trường `count: Int`.
  - Thay vì query collection `redemptions` (tốn chi phí và khó kiểm tra số lượng trong transaction), việc đọc thẳng document tất định `userUsage/{userId}` cho phép kiểm tra số lần người dùng đã dùng voucher cực kỳ nhanh gọn và chính xác.

---

## R3. Công thức tính Đặt cọc 30% và làm tròn an toàn

**Quyết định:**
- Tỷ lệ đặt cọc cố định **30%** trên tổng giá trị đơn hàng sau khi đã trừ giảm giá (`payableAmount`).
- Công thức làm tròn đến hàng nghìn đồng chẵn gần nhất:
  ```kotlin
  val rawDeposit = (payableAmount * 30) / 100
  val roundedDeposit = ((rawDeposit + 500) / 1000) * 1000
  val depositAmount = if (roundedDeposit == 0L) payableAmount else minOf(roundedDeposit, payableAmount)
  val remainingAmount = payableAmount - depositAmount
  ```
- **Xử lý các ca biên**:
  - Đơn 175.000đ: Cọc $175.000 \times 0.3 = 52.500đ \rightarrow$ làm tròn thành **53.000đ**. Phần còn lại tại sân: **122.000đ**.
  - Đơn có giá trị rất nhỏ (ví dụ 1.000đ hoặc 2.000đ do voucher): Thuật toán đảm bảo không bị cọc 0đ và không vượt quá tổng tiền.
  - Đơn được giảm 100% (`payableAmount == 0đ`): Phương thức thanh toán tự động chuyển thành `FREE`, không yêu cầu ví ảo hay đặt cọc.

---

## R4. Xử lý timeout 60 phút duyệt đơn `PENDING` (Phương án 2.A)

**Quyết định:**
- Sau khi thanh toán thành công, đơn chuyển sang trạng thái **`PENDING`** (chờ chủ sân duyệt).
- Thiết lập mốc thời hạn duyệt:
  `approvalDeadline = min(thời điểm sang PENDING + 60 phút, giờ bắt đầu buổi chơi)`.
- Server Cron Job / Supabase Edge Function `booking-timeout` (do Thành viên D vận hành) quét định kỳ mỗi 1–2 phút:
  - Nếu đơn `PENDING` đã quá `approvalDeadline` mà chưa được duyệt $\rightarrow$ Chuyển đơn sang `REJECTED`, xóa document `slots` của đơn để giải phóng sân.
  - Tự động cộng hoàn trả **100% số tiền đã trừ** (tiền cọc hoặc tiền ví) vào `walletBalance` của người chơi tại `users/{userId}`.
  - Bắn thông báo FCM `BOOKING_REJECTED` kèm số tiền đã hoàn trả.

---

## R5. Tuân thủ cam kết học tập phi thương mại (Constitution VI)

**Quyết định:**
- Mọi màn hình thanh toán, BottomSheet nạp ví và Hóa đơn điện tử (E-Receipt) đều hiển thị rõ ràng nhãn cảnh báo:
  > *"Giao dịch giả lập phục vụ học tập - Không thu tiền thật"*.
- Tính năng "Nạp ví giả lập" (`TopUpMockWalletUseCase`) cho phép người dùng tự cộng số dư ảo (100.000đ, 500.000đ, 1.000.000đ) không giới hạn để phục vụ kiểm thử và chấm điểm đồ án mượt mà.
- Tuyệt đối không tích hợp bất kỳ SDK cổng thanh toán thật nào (VNPay, MoMo, ZaloPay, Stripe) theo quy định của Constitution VI.
