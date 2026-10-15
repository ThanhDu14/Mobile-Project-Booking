# Research: Quản lý lịch đặt của tôi (spec 060)

**Ngày**: 2026-10-15 · **Spec**: [spec.md](spec.md) · **Plan**: [plan.md](plan.md)

Tài liệu này ghi nhận các nghiên cứu kỹ thuật chuyên sâu về phân trang 5 tab, xử lý giao dịch hủy đơn nguyên tử, bảo mật chống chỉnh lùi giờ thiết bị, và xuất trình mã QR offline. Không còn mục `NEEDS CLARIFICATION`.

---

## R1. Thiết kế Firestore Composite Index cho 5 Tab và chiến lược truy vấn phân trang

**Vấn đề:**
- Người dùng có 5 tab với các điều kiện lọc và sắp xếp khác nhau. Nếu kết hợp nhiều trường lọc `where` với `orderBy` khác trường mà không cấu hình Composite Index, Firestore sẽ ném lỗi `FirebaseFirestoreException: FAILED_PRECONDITION: The query requires an index`.
- Ngoài ra, Firestore có quy tắc: **Nếu trong truy vấn có điều kiện so sánh bất đẳng thức (`>`, `<`, `>=`, `<=`), trường `orderBy` đầu tiên bắt buộc phải trùng với trường bất đẳng thức đó**.

**Quyết định cấu hình Index (`firestore.indexes.json`) & Truy vấn:**

| Tab | Điều kiện lọc trên Firestore | Sắp xếp | Xử lý tại tầng Kotlin / Client |
|---|---|---|---|
| **1. Chờ thanh toán (`HOLD`)** | `userId == uid` AND `status == "HOLD"` | `createdAt DESC` | Client lọc bỏ các đơn có `holdExpiresAt <= serverNow` |
| **2. Chờ duyệt (`PENDING`)** | `userId == uid` AND `status == "PENDING"` | `createdAt DESC` | Hiển thị đếm ngược 60 phút tới `approvalDeadline` |
| **3. Sắp tới (`UPCOMING`)** | `userId == uid` AND `status == "CONFIRMED"` | `startAt ASC` | ViewModel lọc `endAt > serverNow` (tránh xung đột inequality Firestore) |
| **4. Đã hoàn thành (`COMPLETED`)** | `userId == uid` AND `status IN ["COMPLETED", "NO_SHOW"]` | `startAt DESC` | Phân biệt hiển thị nút "Viết đánh giá" (COMPLETED) hoặc nhãn vi phạm (NO_SHOW) |
| **5. Đã hủy (`CANCELLED`)** | `userId == uid` AND `status IN ["CANCELLED", "REJECTED"]` | `updatedAt DESC` | Hiển thị lý do hủy và số tiền hoàn |

- **Phân trang**: Sử dụng `query.limit(10).startAfter(lastVisibleDocument)` cho mỗi tab để tối ưu Quota Spark.

---

## R2. Thuật toán tính tiền hoàn hủy đơn mốc 240 phút và kẹp biên an toàn chống âm khi có Voucher

**Vấn đề:**
- Người chơi có thể hủy đơn `PENDING` hoặc `CONFIRMED`.
- Với đơn `CONFIRMED` hủy trễ (< 240 phút trước giờ bắt đầu) thanh toán bằng `MOCK_WALLET`: Quy định phạt 30% giá trị đơn hàng (tương đương mức cọc) và hoàn lại 70%.
- Tuy nhiên, nếu đơn ban đầu có áp dụng Voucher giảm giá lớn (ví dụ đơn gốc 500.000đ, voucher giảm 400.000đ, khách chỉ trả 100.000đ):
  - Mức phạt 30% của đơn gốc: $500.000 \times 0.3 = 150.000đ$.
  - Nếu lấy tiền khách đã trả (100.000đ) trừ đi tiền phạt (150.000đ), tiền hoàn sẽ bị **âm (-50.000đ)**.

**Quyết định:**
- Áp dụng thuật toán kẹp biên an toàn (Clamping) trong `CalculateRefundQuoteUseCase`:
  ```kotlin
  val calculatedDepositPenalty = booking.depositAmount.takeIf { it > 0L }
      ?: (((booking.subtotalAmount * 30L) / 100L + 500L) / 1000L) * 1000L

  val penaltyAmount = minOf(booking.paidAmount, calculatedDepositPenalty)
  val refundAmount = maxOf(0L, booking.paidAmount - penaltyAmount)
  ```
- **Hệ quả**: Mức phạt tối đa không bao giờ vượt quá số tiền người chơi thực trả, và số tiền hoàn vào ví không bao giờ bị âm.

---

## R3. Chống gian lận lùi giờ điện thoại (Clock Spoofing) bằng `request.time` trong `firestore.rules`

**Vấn đề:**
- Mốc 240 phút (4 tiếng) quyết định việc người chơi được hoàn 100% hay bị phạt 30%/mất cọc.
- Nếu chỉ tính toán ở Client, kẻ gian có thể chỉnh giờ của điện thoại Android lùi lại 5 tiếng để đánh lừa ứng dụng rằng họ đang hủy sớm, nhằm trục lợi tiền hoàn ví giả lập.

**Quyết định:**
- Cập nhật quy tắc bảo mật `firestore.rules`:
  ```javascript
  match /bookings/{bookingId} {
    allow update: if request.auth != null && request.auth.uid == resource.data.userId
      && request.resource.data.status == "CANCELLED"
      // Kiểm tra mốc 240 phút dựa trên request.time của máy chủ Google:
      && (
        // Nếu claim hoàn 100% thì bắt buộc giờ server Google phải cách giờ chơi >= 240 phút:
        (request.resource.data.refundAmount == resource.data.paidAmount 
          && request.time < resource.data.startAt - duration.value(240, 'm'))
        ||
        // Nếu hủy trễ (< 240 phút) thì refundAmount phải tuân thủ mức phạt:
        (request.time >= resource.data.startAt - duration.value(240, 'm')
          && request.resource.data.refundAmount <= resource.data.paidAmount - resource.data.depositAmount)
      );
  }
  ```
- **Lợi ích**: Triệt tiêu hoàn toàn gian lận giờ giấc từ phía client mà không cần máy chủ riêng (tuân thủ gói Spark).

---

## R4. Kiến trúc Firestore Transaction 2 pha và giải pháp Idempotency

**Vấn đề:**
- Thao tác hủy đơn bao gồm: đổi trạng thái đơn, cộng tiền ví, ghi lịch sử ví, và xóa các slot tương ứng để nhả sân cho người khác.
- Mạng di động có thể chập chờn khiến người dùng bấm hủy nhiều lần.

**Quyết định:**
- **Pha Đọc trước Ghi sau (All Reads Before Writes)**:
  - Pha 1: Đọc `bookings/{bookingId}`, đọc `users/{userId}/walletTransactions/refund_{bookingId}`, đọc `users/{userId}`.
  - Pha 2: Cập nhật `bookings`, cập nhật `users`, tạo `walletTransactions/refund_{bookingId}`, xóa `slots/{slotId}`.
- **Idempotency**: Document ID của lịch sử hoàn tiền được đặt cố định là **`refund_{bookingId}`**. Nếu người dùng bấm 2 lần thật nhanh, lần gọi thứ 2 đọc thấy document này đã tồn tại sẽ trả về kết quả thành công ngay mà không cộng tiền ví lần thứ hai.
- **Xóa Slots**: Gọi `transaction.delete(firestore.collection("slots").document(slotId))` cho mọi slot trong `booking.slotIds`. Việc này giải phóng ngay lập tức các slot trên lưới của Spec 040.

---

## R5. Giải pháp xuất trình mã QR Check-in Offline và Clean Architecture

**Vấn đề:**
- Khi đến sân cầu lông (thường ở tầng hầm hoặc khu vực sóng di động yếu), nếu app không mở được mã QR thì khách không thể check-in.
- Tầng `domain` cần độc lập với Android Framework, không được import `android.graphics.Bitmap`.

**Quyết định:**
- **Clean Architecture cho QR Generator**:
  - Tầng `domain` sử dụng thư viện ZXing Core thuần Java/Kotlin để sinh đối tượng `QrBitMatrix(width: Int, height: Int, bits: BooleanArray)`.
  - Tầng `ui` có file `QrBitmapRenderer.kt` chuyển `QrBitMatrix` sang `android.graphics.Bitmap`, cấu hình kích thước tối thiểu 240dp, nền trắng `#FFFFFF` cố định để máy quét ở Spec 112 nhận diện dễ dàng trong Dark Mode.
- **Offline Cache**:
  - Bật tính năng Firestore Offline Persistence (`persistentCacheSettings`).
  - Khi mở chi tiết đơn lần đầu lúc có mạng, Firestore tự động lưu document vào cache đĩa cục bộ. Khi đến sân mất mạng, Firestore tự động trả dữ liệu từ cache để render mã QR ngay lập tức (đạt tiêu chí SC-003 < 1s offline).
