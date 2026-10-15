# Quickstart: Đặt sân cố định theo tuần (spec 041)

**Ngày**: 2026-10-15 · **Spec**: [spec.md](spec.md) · **Plan**: [plan.md](plan.md)

Tài liệu này hướng dẫn cách chạy thử, kiểm thử tự động (Unit Test / UI Test) và kiểm tra xác minh các kịch bản chính của tính năng `041-weekly-recurring-booking`.

---

## 1. Yêu cầu môi trường (Prerequisites)

- **Android Studio**: Ladybug (2024.2+) hoặc Jellyfish.
- **JDK**: Java 11 (khớp `compileOptions` trong `app/build.gradle.kts`).
- **Android SDK**: `minSdk 29`, `targetSdk 37`.
- **Firebase Emulator Suite** (Khuyến nghị để kiểm thử Transaction All-or-Nothing):
  ```bash
  firebase emulators:start --only firestore,auth
  ```

---

## 2. Kiểm thử tự động (Automated Testing)

### 2.1. Chạy Unit Test logic cốt lõi
Kiểm tra thuật toán sinh lịch, xử lý xung đột và giữ chỗ:
```bash
./gradlew testDebugUnitTest --tests "com.example.booking_app.feature.booking.recurring.*"
```

Các ca kiểm thử trọng tâm trong Unit Test:
1. `GenerateRecurringSessionsTest`:
   - Chọn Thứ 2 và Thứ 5, chu kỳ 4 tuần, bắt đầu tuần hiện tại (khi hôm nay là Thứ 4) $\rightarrow$ Tự động bỏ qua Thứ 2 của tuần này, bắt đầu từ Thứ 5, tổng cộng sinh đủ 8 buổi.
   - Chọn bắt đầu từ "Tuần kế tiếp" $\rightarrow$ Sinh đủ $2 \times 4 = 8$ buổi bắt đầu từ Thứ 2 tuần sau.
2. `ResolveRecurringConflictTest`:
   - Chu kỳ 8 tuần bị trùng 1 tuần tại Sân 1 $\rightarrow$ Tìm được Sân 2 cùng loại mặt sân `MAT`, đổi sân thành công, giá giữ nguyên.
   - Bỏ qua buổi bị trùng $\rightarrow$ Giảm từ 8 buổi xuống 7 buổi, tổng tiền và tiền cọc 30% giảm tương ứng.
   - Chu kỳ 4 tuần bị trùng 2 tuần, sau khi bỏ chỉ còn 2 buổi $\rightarrow$ Bị chặn (`ErrorLessThanMinSessions`), không cho tạo đơn cố định.
3. `HoldRecurringBookingTest`:
   - Giữ chỗ 16 slot của 4 buổi $\rightarrow$ Tất cả slot đổi sang `HELD`, sinh `recurringGroupId` và 4 document `bookings`.
   - Giả lập 1 slot bất kỳ bị người khác chiếm trước $\rightarrow$ Transaction tự động rollback hoàn toàn, không có slot nào bị giữ dở dang.

### 2.2. Chạy UI Test (Espresso)
Kiểm tra luồng người dùng cấu hình thứ/giờ và BottomSheet xử lý xung đột:
```bash
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.booking_app.feature.booking.BookingRecurringConfigFragmentTest
```

---

## 3. Các kịch bản kiểm tra thủ công (Manual Verification Scenarios)

### Kịch bản 1: Cấu hình lịch định kỳ thành công không xung đột
1. Mở app, chọn cơ sở sân có bật tính năng đặt cố định.
2. Chọn chế độ "Đặt cố định theo tuần".
3. Chọn Thứ 3 & Thứ 5, khung giờ 18:00–20:00 (4 slot 30 phút), Sân 1, chu kỳ 4 tuần.
4. **Kỳ vọng**:
   - Hệ thống quét nhanh dưới 2 giây.
   - Hiển thị danh sách tổng hợp 8 buổi chơi hợp lệ.
   - Hiển thị tổng tiền và tiền cọc 30%. Nút "Tiếp tục" được kích hoạt.

### Kịch bản 2: Giải quyết xung đột (Đổi sân & Bỏ buổi)
1. Trên Firestore, tạo trước 1 slot bận `court1_20261020_1830` có `status = "BOOKED"`.
2. Trên app, thực hiện cấu hình chu kỳ đi qua ngày 2026-10-20.
3. **Kỳ vọng**:
   - App hiển thị cảnh báo "Phát hiện 1 buổi bị xung đột lịch".
   - Mở BottomSheet đưa ra 2 lựa chọn: Đổi sang Sân 2 (còn trống cả 4 slot) hoặc Bỏ qua buổi này.
   - Chọn "Đổi sân" $\rightarrow$ Buổi đổi sang Sân 2, tổng tiền không đổi.
   - Chọn "Bỏ buổi" $\rightarrow$ Buổi bị loại bỏ, tổng tiền giảm 1 buổi.

### Kịch bản 3: Hủy/Back từ màn hình xác nhận giải phóng slot tức thì
1. Bấm "Tiếp tục" từ màn hình cấu hình sang màn hình xác nhận (đồng hồ đếm ngược 05:00 bắt đầu chạy).
2. Kiểm tra trên Firebase Console: Toàn bộ các slot của chu kỳ đều đang mang trạng thái `HELD`.
3. Bấm nút Quay lại (Back) trên màn hình app để về lại màn hình cấu hình.
4. **Kỳ vọng**: Toàn bộ các slot vừa `HELD` được hệ thống gọi `ReleaseRecurringHoldUseCase` xóa/reset ngay lập tức, không bị treo 5 phút.
