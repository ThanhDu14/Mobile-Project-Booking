# Quickstart: Quản lý lịch đặt của tôi (spec 060)

**Ngày**: 2026-10-15 · **Spec**: [spec.md](spec.md) · **Plan**: [plan.md](plan.md)

Tài liệu này hướng dẫn cách chạy thử nghiệm, thực thi kiểm thử tự động (Unit Test / UI Test) và kiểm tra xác minh các kịch bản chính của tính năng `060-my-bookings`.

---

## 1. Yêu cầu môi trường (Prerequisites)

- **Android Studio**: Ladybug (2024.2+) hoặc Jellyfish.
- **JDK**: Java 11 (khớp `compileOptions` trong `app/build.gradle.kts`).
- **Android SDK**: `minSdk 29`, `targetSdk 37`.
- **Firebase Emulator Suite** (Khuyến nghị để kiểm thử Transaction hủy đơn, hoàn tiền ví và xóa slots):
  ```bash
  firebase emulators:start --only firestore,auth
  ```

---

## 2. Kiểm thử tự động (Automated Testing)

### 2.1. Chạy Unit Test logic cốt lõi
Kiểm tra thuật toán tính toán hoàn tiền mốc 240 phút, kẹp biên an toàn khi có voucher, và sinh ma trận QR:
```bash
./gradlew testDebugUnitTest --tests "com.example.booking_app.feature.booking_management.*"
```

Các ca kiểm thử trọng tâm trong Unit Test:
1. `CalculateRefundQuoteUseCaseTest`:
   - **Đơn PENDING hủy**: `penaltyAmount = 0L`, `refundAmount = paidAmount` (Hoàn 100%).
   - **Đơn CONFIRMED hủy sớm ($\ge 240$ phút)**: Đặt lúc 18:00, hủy lúc 13:00 (cách 300 phút) $\rightarrow$ Hoàn 100%.
   - **Đơn CONFIRMED hủy tại mốc biên (đúng 240 phút)**: Cách đúng 240 phút $\rightarrow$ Hoàn 100%.
   - **Đơn CONFIRMED hủy trễ ($< 240$ phút) trả bằng Ví giả lập**: Đơn 200.000đ, đã trả 200.000đ, hủy trước 120 phút $\rightarrow$ Phạt 30% cọc = 60.000đ, hoàn 70% = 140.000đ vào ví.
   - **Đơn CONFIRMED hủy trễ trả bằng Đặt cọc 30%**: Đơn 200.000đ, đã cọc 60.000đ $\rightarrow$ Phạt 60.000đ, hoàn 0đ.
   - **Đơn CONFIRMED hủy trễ trả tại sân (AT_VENUE)**: Đã trả 0đ $\rightarrow$ Hoàn 0đ, phạt 0đ.
   - **Biên an toàn có Voucher**: Đơn gốc 200k áp voucher giảm 160k, thực trả `paidAmount = 40.000đ`. Mức phạt tính theo đơn gốc là 60.000đ $\rightarrow$ Thuật toán kẹp biên: `penaltyAmount = minOf(40.000, 60.000) = 40.000đ`, `refundAmount = maxOf(0, 40.000 - 40.000) = 0đ` (Không bị âm tiền).
2. `CancelBookingUseCaseTest`:
   - Kiểm tra tính Idempotent: Gọi hủy 2 lần trên cùng 1 đơn không bị cộng tiền ví 2 lần nhờ ID `refund_{bookingId}`.
   - Kiểm tra quy tắc xóa slot: Toàn bộ document `slots/{slotId}` của đơn được xóa trong Transaction.
3. `GenerateCheckInQrUseCaseTest`:
   - Truyền chuỗi `qrToken` ngẫu nhiên $\rightarrow$ Trả về `QrBitMatrix` kích thước $512 \times 512$ pixel với các bit boolean chính xác, không gây crash JVM thuần.

### 2.2. Chạy UI Test (Espresso)
Kiểm tra giao diện 5 Tab, hiển thị QR code, khóa nút đánh giá đơn NO_SHOW:
```bash
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.booking_app.feature.booking_management.MyBookingsFragmentTest
```

---

## 3. Các kịch bản kiểm tra thủ công (Manual Verification Scenarios)

### Kịch bản 1: Điều hướng 5 Tab và đồng bộ đồng hồ đếm ngược
1. Mở màn hình "Lịch đặt của tôi".
2. Chuyển đổi qua lại giữa 5 tab: **Chờ thanh toán**, **Chờ duyệt**, **Sắp tới**, **Đã hoàn thành**, **Đã hủy**.
3. **Kỳ vọng**:
   - Ở Tab "Chờ thanh toán": Các đơn hiển thị đếm ngược 5 phút từ `holdExpiresAt`. Khi chạm 00:00, thẻ tự động biến mất khỏi danh sách. Nút "Thanh toán tiếp" mở lại Spec 050 kế thừa thời gian còn lại.
   - Ở Tab "Chờ duyệt": Hiển thị đồng hồ đếm ngược 60 phút hạn chủ sân duyệt (Phương án 2.A).
   - Thao tác kéo xuống để làm mới (`SwipeRefreshLayout`) mượt mà, phân trang tải tiếp 10 đơn khi cuộn tới cuối.

### Kịch bản 2: Hiển thị mã QR Check-in và cập nhật Realtime
1. Vào Tab "Sắp tới", chọn một đơn `CONFIRMED`.
2. Mở màn hình Chi tiết đơn đặt sân.
3. **Kỳ vọng**:
   - Mã QR hiển thị rõ nét trên nền trắng `#FFFFFF` với kích thước tối thiểu 240dp (kể cả khi điện thoại đang bật Dark Mode).
   - Sử dụng một thiết bị khác (hoặc Firebase Console) cập nhật trường `checkedInAt` của đơn này.
   - **Kỳ vọng Realtime**: Ngay lập tức UI cập nhật sang trạng thái *"Đã check-in lúc HH:mm"*, mã QR mờ đi/ẩn nút hủy đơn.

### Kịch bản 3: Hủy đơn và hoàn tiền ví giả lập theo mốc 240 phút
1. **Ca 1 (Hủy sớm $\ge 240$ phút)**:
   - Chọn đơn cách giờ chơi > 4 tiếng, bấm "Hủy lịch đặt".
   - Hộp thoại xác nhận hiển thị: *"Bạn hủy sớm trước 4 tiếng, sẽ được hoàn 100% (XXX.000đ) vào ví"*.
   - Xác nhận hủy $\rightarrow$ Đơn chuyển sang Tab "Đã hủy", số dư ví giả lập được hoàn 100%, các slot được nhả trống trên màn hình đặt sân (040).
2. **Ca 2 (Hủy trễ $< 240$ phút)**:
   - Chọn đơn cách giờ chơi 2 tiếng (trả ví 100%), bấm "Hủy lịch đặt".
   - Hộp thoại xác nhận hiển thị cảnh báo phạt 30% tiền cọc và chỉ hoàn 70% số tiền.
   - Xác nhận hủy $\rightarrow$ Ví giả lập chỉ được cộng 70% số tiền, tab "Đã hủy" ghi rõ lý do và số tiền đã hoàn.

### Kịch bản 4: Kiểm tra hiển thị QR Offline khi mất kết nối mạng
1. Mở màn hình chi tiết đơn `CONFIRMED` khi đang có mạng để dữ liệu được lưu vào Firestore Cache.
2. Bật chế độ máy bay (Airplane Mode) để ngắt hoàn toàn Wi-Fi và 4G.
3. Mở lại ứng dụng và vào chi tiết đơn đó.
4. **Kỳ vọng**:
   - Ứng dụng đọc dữ liệu từ cache cục bộ và render mã QR bình thường.
   - Hiển thị thanh thông báo nhỏ: *"Đang ở chế độ ngoại tuyến - Mã QR vẫn có hiệu lực check-in tại sân"*.

### Kịch bản 5: Kiểm tra khóa quyền đánh giá đơn NO_SHOW
1. Vào Tab "Đã hoàn thành".
2. Quan sát các đơn có trạng thái `NO_SHOW` (khách không đến sân).
3. **Kỳ vọng (Phương án 5.A)**:
   - Thẻ đơn hiển thị tag màu đỏ/xám "Vắng mặt (No-show)".
   - Không xuất hiện nút "Viết đánh giá" (nút bị ẩn hoặc vô hiệu hóa hoàn toàn kèm tooltip giải thích).
