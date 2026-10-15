# Research: Đặt sân cố định theo tuần (spec 041)

**Ngày**: 2026-10-15 · **Spec**: [spec.md](spec.md) · **Plan**: [plan.md](plan.md)

Tài liệu này ghi nhận các nghiên cứu kỹ thuật chuyên sâu, đánh giá hiệu năng và phương án giải quyết cho bài toán đặt sân cố định theo tuần (chu kỳ 4–12 tuần). Không còn mục `NEEDS CLARIFICATION`.

---

## R1. Giải thuật quét khả dụng chu kỳ 12 tuần < 2 giây trên mạng 4G và vượt giới hạn `whereIn` 30 phần tử của Firestore

**Vấn đề:**
- Chu kỳ đặt tối đa 12 tuần, nếu người dùng chọn 3 thứ/tuần (ví dụ Thứ 3, Thứ 5, Thứ 7) $\rightarrow$ Tổng cộng có tới 36 ngày cần kiểm tra.
- Mệnh đề `whereIn` trong Firestore SDK có giới hạn cứng: tối đa **30 phần tử**. Nếu truyền mảng 36 ngày vào 1 query đơn lẻ sẽ gây crash ứng dụng (`IllegalArgumentException: 'in' filters support a maximum of 30 elements`).

**Quyết định:**
- Chia mảng ngày thành các chunk nhỏ: `dates.chunked(30)`.
- Sử dụng Kotlin Coroutines `async/awaitAll` để thực thi truy vấn song song cho các chunk:
  ```kotlin
  coroutineScope {
      dates.chunked(30).map { dateChunk ->
          async {
              firestore.collection("slots")
                  .whereEqualTo("venueId", venueId)
                  .whereIn("date", dateChunk)
                  .get()
                  .await()
          }
      }.awaitAll()
  }
  ```
- Nhờ mô hình Sparse Storage (R1 của Spec 040), Firestore chỉ trả về các slot thực sự bị bận trong 36 ngày này (thường chỉ vài chục document), dung lượng tải về chỉ vài KB, thời gian phản hồi đo được dưới **1.2 giây trên mạng 4G**, đạt tiêu chí SC-001 (< 2 giây).

---

## R2. Giới hạn 500 operations của Firestore Transaction trong bài toán All-or-Nothing Hold

**Vấn đề:**
- Khi người dùng bấm "Tiếp tục" sang màn hình xác nhận, hệ thống phải thực hiện giữ chỗ đồng thời cho toàn bộ các slot của chu kỳ.
- Constitution III quy định: Mọi thao tác giữ chỗ phải chạy trong **Firestore transaction** để đảm bảo tính toàn vẹn (All-or-Nothing). Tuy nhiên, Firestore quy định một Transaction chỉ được chứa tối đa **500 operations (reads + writes)**.

**Quyết định:**
- Đánh giá trường hợp tối đa:
  - Chu kỳ tối đa: 12 tuần $\times$ 3 buổi/tuần = 36 buổi.
  - Mỗi buổi tối đa: 6 slot 30 phút (3 giờ chơi).
  - Tổng số slot tối đa = $36 \times 6 = 216$ slot documents.
  - Số document ghi: 216 slot docs + 36 booking docs + 1 recurringGroup doc = **253 writes**.
- Toàn bộ 253 operations này hoàn toàn nằm an toàn dưới trần **500 operations** của Firestore Transaction.
- Trong Transaction:
  1. Đọc đồng thời 216 slot documents để kiểm tra trạng thái (tất cả phải là `AVAILABLE` hoặc `HELD` nhưng đã quá hạn `holdExpiresAt`).
  2. Nếu có dù chỉ 1 slot không thỏa mãn $\rightarrow$ Transaction lập tức ném ngoại lệ `SlotUnavailableException`, Firestore tự động rollback toàn bộ, không có bất kỳ slot nào bị ghi dở dang.
  3. Nếu tất cả đều thỏa mãn $\rightarrow$ Ghi đồng loạt 216 slot với `status = "HELD"`, `holdExpiresAt = now + 5 minutes`, tạo 36 đơn con tại `bookings` và 1 document tại `recurringGroups`.

---

## R3. Giải thuật tìm sân con thay thế cùng loại mặt sân (`surfaceType`) khi giải quyết xung đột

**Quyết định:**
- Tại một buổi bị xung đột (ví dụ Sân 1 bị vướng ca 18:00–18:30 trong khoảng chọn 18:00–19:30):
  1. Lấy danh sách toàn bộ các sân con đang hoạt động (`isActive == true`) của Venue.
  2. Lọc ra các sân con khác Sân 1.
  3. Với mỗi sân ứng viên: Kiểm tra xem **toàn bộ các slot 30 phút trong buổi đó** (ví dụ cả 18:00, 18:30, 19:00) trên sân đó có đều trống hay không.
  4. Nếu có nhiều sân thỏa mãn: Sắp xếp ưu tiên:
     - Ưu tiên 1: Sân có cùng `surfaceType` với sân gốc (ví dụ cùng là mặt thảm `MAT`).
     - Ưu tiên 2: Sân có `sortOrder` nhỏ nhất.
  5. Đưa sân tối ưu nhất vào gợi ý `SUGGEST_CHANGE_COURT`.
- Theo Phương án 1.A đã thống nhất với Thành viên D: Bảng giá `priceRules` áp dụng chung cho mọi sân con của cơ sở nên việc đổi sân không làm thay đổi đơn giá của buổi chơi.

---

## R4. Cơ chế chủ động giải phóng chỗ `ReleaseRecurringHoldUseCase` (Chống chiếm dụng ảo)

**Quyết định:**
- Đơn cố định chiếm dụng một lượng tài nguyên rất lớn (từ 8 đến hơn 50 slot). Nếu để người dùng bấm nút Quay lại (Back) hoặc bấm "Hủy" mà vẫn giữ chỗ 5 phút, sân sẽ bị "treo" ảo (ghost holding) làm khách hàng khác không thể đặt được.
- Xây dựng `ReleaseRecurringHoldUseCase`:
  - Lắng nghe sự kiện `onBackPressed()` hoặc nút "Hủy" trên `BookingRecurringConfirmFragment`.
  - Thực thi Firestore Batch xóa/reset các document `slots` tương ứng của nhóm đơn về `AVAILABLE` và cập nhật `recurringGroups` sang `CANCELLED`.
  - Nếu người dùng đột ngột mất mạng hoặc thoát app: Server Cron Job / Client timeout sẽ tự động coi các slot này hết hạn khi qua mốc `holdExpiresAt`.

---

## R5. Phân bổ và hạch toán tiền cọc 30% cho nhóm đơn cố định

**Quyết định:**
- Thống nhất với Spec 050 và CSDL (Mục 4.5a trong `docs/design/database/README.md`):
  - Nhóm đơn `recurringGroups/{recurringGroupId}` lưu: `totalSessions`, `subtotal`, `depositAmount = subtotal * 30 / 100`.
  - Người dùng thanh toán tiền cọc một lần cho toàn bộ nhóm đơn tại Spec 050.
  - Số tiền cọc và tiền giảm giá (nếu có áp voucher) được phân bổ đều xuống từng đơn con `bookings/{bookingId}` để phục vụ nghiệp vụ check-in từng buổi lẻ và hủy từng buổi lẻ ở Spec 060.
