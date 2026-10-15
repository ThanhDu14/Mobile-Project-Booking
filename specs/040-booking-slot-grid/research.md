# Research: Lưới khung giờ đặt sân và giữ chỗ (spec 040)

**Ngày**: 2026-10-15 · **Spec**: [spec.md](spec.md) · **Plan**: [plan.md](plan.md)

Tài liệu này ghi nhận các quyết định kỹ thuật, phân tích ưu/nhược điểm và phương án đã cân nhắc nhằm phục vụ triển khai feature `040-booking-slot-grid`. Không còn mục `NEEDS CLARIFICATION`.

---

## R1. Tối ưu Quota Firestore gói Spark bằng mô hình Sparse Storage (Lưu trữ thưa)

**Quyết định:**
- Không tạo trước hàng loạt document slot trống trên Firestore.
- Collection `slots/{courtId}_{yyyyMMdd}_{HHmm}` chỉ lưu những slot có trạng thái khác `AVAILABLE` (tức là `HELD`, `BOOKED`, `BLOCKED`).
- **Client (Android app)**: Tự sinh lưới thời gian 30 phút (`HH:00` và `HH:30`) từ `openTime` đến `closeTime` của Venue (ví dụ từ 06:00 đến 22:00 sinh ra 32 slot mặc định `AVAILABLE`). Sau đó, Client lấy danh sách slot bận từ Firestore và hợp nhất (merge) đè lên lưới mặc định.

**Lý do:**
- Nếu một cơ sở có 5 sân con mở từ 06:00 đến 23:00 (34 slot/sân/ngày = 170 slots/ngày), nếu lưu cả slot trống thì chỉ riêng việc xem lịch 1 ngày đã tốn 170 reads. Nếu xem 7 ngày sẽ tốn hơn 1.000 reads, nhanh chóng làm cạn kiệt hạn mức 50.000 reads/ngày của gói Spark miễn phí.
- Với Sparse Storage, nếu ngày hôm đó chỉ có 5 ca được đặt/giữ thì client chỉ tốn đúng 5 reads.

**Đã cân nhắc:**
- *Gộp toàn bộ trạng thái ngày vào 1 document `venues/{venueId}/dailySlots/{yyyyMMdd}`*: Tiết kiệm reads hơn nhưng khi 2 người cùng đặt 2 slot khác nhau trong ngày sẽ gây xung đột transaction trên cùng 1 document (vi phạm Constitution III về ID tất định của từng slot). Do đó giữ mô hình document riêng cho từng slot bận.

---

## R2. Thuật toán chọn slot 30 phút liên tiếp trên cùng 1 sân con

**Quyết định:**
- Đưa toàn bộ logic kiểm tra và xử lý chọn slot vào `ValidateSlotSelectionUseCase` độc lập ở tầng Domain.
- Quy tắc hoạt động:
  1. **Chọn ca đầu tiên**: Cho phép chọn bất kỳ slot nào có trạng thái `AVAILABLE`. Lưu lại `currentCourtId`.
  2. **Chọn ca tiếp theo**:
     - Phải cùng `currentCourtId`. Nếu bấm sang sân khác $\rightarrow$ Reset danh sách cũ, bắt đầu chọn ở sân mới.
     - Kiểm tra tính liên tiếp: Tính khoảng cách thời gian giữa ca mới và dãy ca hiện tại. Nếu liền kề trước (ví dụ đang chọn 08:00–08:30, bấm thêm 07:30–08:00) hoặc liền kề sau (bấm 08:30–09:00) $\rightarrow$ Nối dài danh sách.
     - Nếu bấm ca cách quãng (ví dụ đang chọn 08:00–08:30, bấm 10:00–10:30) $\rightarrow$ Tự động hủy dãy cũ, lấy ca 10:00 làm ca đầu tiên mới.
  3. **Bỏ chọn**:
     - Nếu bấm vào slot ở 2 đầu mút của dãy đang chọn $\rightarrow$ Bỏ chọn slot đó, giữ nguyên phần còn lại.
     - Nếu bấm vào slot ở giữa dãy (ví dụ đang chọn 08:00, 08:30, 09:00, 09:30 mà bấm vào 08:30) $\rightarrow$ Cắt bỏ từ 08:30 đến 09:30 để đảm bảo phần còn lại (08:00) vẫn liên tiếp.
  4. **Giới hạn tối đa**: Tối đa 8 slot liên tiếp (tương đương 4 giờ) theo quy định đã thống nhất.

**Lý do:**
- Giúp người dùng thao tác tự nhiên, không bị văng lỗi dialog gây ức chế, đồng thời đảm bảo không bao giờ sinh ra đơn đặt sân có khoảng trống vô lý ở giữa.

---

## R3. Xử lý xung đột Realtime khi người khác chiếm slot (Race Condition)

**Quyết định:**
- Bọc Firestore `SnapshotListener` bên trong Kotlin `callbackFlow` tại `BookingRepositoryImpl`.
- Khi có bất kỳ thay đổi nào từ Firestore trả về danh sách slot bận mới, `ViewModel` sẽ so sánh danh sách `selectedSlots` hiện tại với danh sách slot bận mới:
  - Nếu bất kỳ slot nào trong `selectedSlots` bị chuyển sang `HELD`, `BOOKED`, hoặc `BLOCKED` $\rightarrow$ Tự động loại bỏ slot đó khỏi `selectedSlots`.
  - Tính lại tổng tiền tạm tính.
  - Bắn một sự kiện `UiEffect.ShowConflictWarning("Ca chơi vừa có người khác đặt/giữ chỗ")` qua `Channel`/`SharedFlow` để Fragment hiển thị Snackbar cảnh báo.

**Lý do:**
- Đảm bảo tính minh bạch theo Acceptance Scenario của US2 trong spec 040. Người dùng nhận biết ngay khi ca chơi họ đang nhắm tới đã bị người khác chọn trước mà không cần đợi bấm "Tiếp tục".

---

## R4. Thuật toán tính giá theo khung giờ 30 phút và bảng giá `priceRules`

**Quyết định:**
- Thành viên B và D đã thống nhất **Phương án 1.A**:
  - Bảng giá `priceRules` có các mốc thời gian bắt đầu và kết thúc là bội số của 30 phút (`HH:00` hoặc `HH:30`).
  - Giá trong `priceRules` được cấu hình theo `pricePerHour` (VNĐ/giờ, chia hết cho 2.000).
  - Đơn giá mỗi slot 30 phút: `pricePerSlot = pricePerHour / 2`.
- `CalculateTotalPriceUseCase` duyệt qua từng slot 30 phút trong dãy đã chọn:
  - Lấy `dayOfWeek` của ngày đang chọn (1 = Chủ nhật, 2 = Thứ hai... 7 = Thứ bảy).
  - Tìm `PriceRule` tương ứng có `daysOfWeek` chứa ngày này và có khoảng `[startTime, endTime)` bao hàm slot 30 phút đó.
  - Cộng dồn `pricePerSlot` vào tổng tiền.

**Lý do:**
- Mọi mốc thời gian đều chẵn 30 phút nên không bao giờ xảy ra trường hợp một ca 30 phút bị chẻ đôi giữa 2 bảng giá khác nhau. Thuật toán tính giá hoàn toàn tất định và chính xác 100%.

---

## R5. Vòng đời lắng nghe Realtime và Quản lý Bộ nhớ (Lifecycle-aware Listener)

**Quyết định:**
- `BookingRepository.getBusySlotsRealtime(venueId: String, date: String): Flow<List<Slot>>` dùng `callbackFlow`.
- Trong khối `callbackFlow`:
  ```kotlin
  val registration = query.addSnapshotListener { snapshot, error -> ... }
  awaitClose { registration.remove() }
  ```
- Trên Fragment: Thu thập Flow thông qua `viewLifecycleOwner.lifecycleScope.launch { viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { ... } }`.
- Khi người dùng chọn ngày khác: `ViewModel` hủy flow cũ và kích hoạt flow mới cho ngày mới.

**Lý do:**
- Tuân thủ nghiêm ngặt Constitution I và II: Ngăn chặn triệt để hiện tượng memory leak và không để listener chạy ngầm tốn reads khi app ở dưới background.
