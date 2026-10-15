# Quickstart: Lưới khung giờ đặt sân và giữ chỗ (spec 040)

**Ngày**: 2026-10-15 · **Spec**: [spec.md](spec.md) · **Plan**: [plan.md](plan.md)

Tài liệu này hướng dẫn cách chạy thử, kiểm thử tự động (Unit Test / UI Test) và kiểm tra xác minh các kịch bản chính của tính năng `040-booking-slot-grid`.

---

## 1. Yêu cầu môi trường (Prerequisites)

- **Android Studio**: Ladybug (2024.2+) hoặc Jellyfish.
- **JDK**: Java 11 (khớp `compileOptions` trong `app/build.gradle.kts`).
- **Android SDK**: `minSdk 29`, `targetSdk 37`.
- **Firebase Emulator Suite** (Khuyến nghị để test mà không tốn Quota):
  ```bash
  firebase emulators:start --only firestore,auth
  ```

---

## 2. Kiểm thử tự động (Automated Testing)

### 2.1. Chạy Unit Test logic cốt lõi
Kiểm tra thuật toán chọn slot liên tiếp và tính giá 30 phút:
```bash
./gradlew testDebugUnitTest --tests "com.example.booking_app.feature.booking.*"
```

Các ca kiểm thử trọng tâm trong Unit Test:
1. `ValidateSlotSelectionTest`:
   - Chọn ca đầu tiên còn trống $\rightarrow$ Thành công.
   - Chọn ca thứ 2 liền kề trước/sau $\rightarrow$ Nối dài chuỗi ca chọn.
   - Chọn ca thứ 2 cách quãng $\rightarrow$ Reset chuỗi cũ, bắt đầu ca mới.
   - Bỏ chọn slot ở đầu/đuôi chuỗi $\rightarrow$ Bỏ slot đó, giữ các slot còn lại.
   - Bỏ chọn slot ở giữa chuỗi $\rightarrow$ Cắt toàn bộ từ slot đó về sau.
   - Chọn quá 8 slot 30 phút (4 tiếng) $\rightarrow$ Bị chặn kèm thông báo lỗi.
2. `CalculateTotalPriceTest`:
   - Slot 30 phút nằm trọn trong khung giờ thường $\rightarrow$ Giá = `pricePerHour / 2`.
   - Slot 30 phút nằm trọn trong khung giờ cao điểm $\rightarrow$ Giá = `peakPricePerHour / 2`.
   - Đặt 4 slot vắt qua thời điểm giao thoa giữa giờ thường và giờ cao điểm (ví dụ 16:30 đến 18:30 với mốc cao điểm từ 17:00) $\rightarrow$ Tính đúng từng slot riêng biệt theo rule của nó.

### 2.2. Chạy UI Test (Espresso)
Kiểm tra tương tác trên màn hình lưới:
```bash
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.booking_app.feature.booking.BookingSlotGridFragmentTest
```

---

## 3. Các kịch bản kiểm tra thủ công (Manual Verification Scenarios)

### Kịch bản 1: Mở màn hình và hiển thị lưới 30 phút
1. Vào ứng dụng, mở chi tiết một cơ sở sân (`venueId`).
2. Bấm "Đặt sân".
3. **Kỳ vọng**:
   - Thanh chọn ngày hiển thị hôm nay và các ngày tiếp theo.
   - Tab sân con hiển thị danh sách sân (`Sân 1`, `Sân 2`...).
   - Lưới giờ chia theo từng block 30 phút từ giờ mở đến giờ đóng cửa.
   - Các ca trước giờ hiện tại hiển thị mờ (`PAST`).
   - Bottom bar ở trạng thái ẩn hoặc vô hiệu hóa nút "Tiếp tục".

### Kịch bản 2: Chọn slot và tính tiền tạm tính
1. Chọn ca 18:00–18:30 trên Sân 1.
2. **Kỳ vọng**: Ca đổi sang màu xanh (`SELECTED`), bottom bar hiện `1 khung giờ (30p)` và số tiền chính xác.
3. Chọn tiếp ca 18:30–19:00 trên Sân 1.
4. **Kỳ vọng**: Ca thứ 2 cũng đổi sang màu xanh, bottom bar hiện `2 khung giờ (1h)` và tổng tiền của 2 ca.
5. Bấm chọn ca 21:00–21:30 trên Sân 1.
6. **Kỳ vọng**: 2 ca trước bị hủy chọn, chỉ còn ca 21:00 được chọn.

### Kịch bản 3: Xung đột Realtime (Race Condition)
1. Người dùng A đang chọn ca 19:00–19:30.
2. Trên Firebase Console hoặc thiết bị B, cập nhật slot `court1_20261015_1900` có `status = "HELD"`.
3. **Kỳ vọng**: Trên màn hình của người dùng A, ca 19:00 tự động chuyển sang màu vàng (`HELD`), bị loại khỏi vùng chọn, tổng tiền trở về 0đ và Snackbar hiện thông báo "Ca chơi vừa có người đặt/giữ chỗ".
