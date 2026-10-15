# Quickstart: Thanh toán và Khuyến mãi giả lập (spec 050)

**Ngày**: 2026-10-15 · **Spec**: [spec.md](spec.md) · **Plan**: [plan.md](plan.md)

Tài liệu này hướng dẫn cách chạy thử, kiểm thử tự động (Unit Test / UI Test) và kiểm tra xác minh các kịch bản chính của tính năng `050-payment-voucher`.

---

## 1. Yêu cầu môi trường (Prerequisites)

- **Android Studio**: Ladybug (2024.2+) hoặc Jellyfish.
- **JDK**: Java 11 (khớp `compileOptions` trong `app/build.gradle.kts`).
- **Android SDK**: `minSdk 29`, `targetSdk 37`.
- **Firebase Emulator Suite** (Khuyến nghị để kiểm thử Transaction thanh toán & trừ ví):
  ```bash
  firebase emulators:start --only firestore,auth
  ```

---

## 2. Kiểm thử tự động (Automated Testing)

### 2.1. Chạy Unit Test logic cốt lõi
Kiểm tra tính toán cọc 30%, điều kiện áp dụng voucher và Transaction thanh toán:
```bash
./gradlew testDebugUnitTest --tests "com.example.booking_app.feature.payment.*"
```

Các ca kiểm thử trọng tâm trong Unit Test:
1. `CalculatePaymentBreakdownTest`:
   - Đơn 200.000đ $\rightarrow$ Cọc 30% = 60.000đ, còn lại tại sân = 140.000đ.
   - Đơn 175.000đ $\rightarrow$ Cọc $175.000 \times 0.3 = 52.500đ \rightarrow$ Làm tròn thành 53.000đ, còn lại = 122.000đ.
   - Đơn áp voucher giảm 100% (`payableAmount == 0đ`) $\rightarrow$ Cọc 0đ, phương thức tự chuyển sang `FREE`.
2. `ValidateAndApplyVoucherTest`:
   - Voucher yêu cầu đơn tối thiểu 300.000đ, áp vào đơn 200.000đ $\rightarrow$ Bị từ chối (`MinOrderNotMet`).
   - Voucher giảm 50% trần 40.000đ áp vào đơn 150.000đ $\rightarrow$ Giảm đúng 40.000đ.
   - Voucher đã dùng hết lượt cá nhân (`userUsageCount >= perUserLimit`) $\rightarrow$ Bị từ chối (`PerUserLimitReached`).
3. `ProcessMockPaymentTest`:
   - Số dư ví < tiền cần trả $\rightarrow$ Ném `InsufficientBalanceException`, Transaction rollback, số dư ví không đổi.
   - Hết hạn giữ chỗ (`holdExpiresAt < now`) $\rightarrow$ Ném `HoldExpiredException`, không trừ tiền ví.
   - Thanh toán thành công $\rightarrow$ Số dư ví bị trừ chính xác, đơn chuyển sang `PENDING`, slot chuyển sang `BOOKED`.

### 2.2. Chạy UI Test (Espresso)
Kiểm tra tương tác chọn phương thức, áp voucher và nạp ví giả lập:
```bash
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.booking_app.feature.payment.PaymentVoucherFragmentTest
```

---

## 3. Các kịch bản kiểm tra thủ công (Manual Verification Scenarios)

### Kịch bản 1: Áp dụng voucher cơ sở và thanh toán ví giả lập
1. Từ màn hình đặt sân (040), bấm "Tiếp tục" sang màn hình thanh toán đơn 200.000đ.
2. Bấm "Chọn mã khuyến mãi", chọn voucher `GIAM20` (giảm 20%).
3. **Kỳ vọng**: Mức giảm 40.000đ, tổng tiền hiển thị giảm còn 160.000đ.
4. Chọn phương thức "Ví giả lập", bấm "Xác nhận thanh toán".
5. **Kỳ vọng**:
   - Trừ 160.000đ từ ví giả lập.
   - Chuyển sang màn hình Hóa đơn điện tử (E-Receipt) ghi nhận trạng thái `PENDING`.
   - Có dòng chữ nổi bật: "Giao dịch giả lập phục vụ học tập - Không thu tiền thật".
   - Bấm nút "Xem lịch đặt của tôi" $\rightarrow$ Điều hướng sang Spec 060 tại tab "Chờ duyệt".

### Kịch bản 2: Đặt cọc 30% và kiểm tra làm tròn
1. Tạo đơn có tiền tạm tính 175.000đ.
2. Chọn phương thức "Đặt cọc".
3. **Kỳ vọng**:
   - Hiển thị tiền cọc: 53.000đ.
   - Hiển thị số tiền còn lại trả tại sân: 122.000đ.
4. Bấm "Xác nhận đặt cọc" $\rightarrow$ Ví chỉ bị trừ 53.000đ, hóa đơn ghi rõ đã cọc 53.000đ.

### Kịch bản 3: Xử lý hết hạn 5 phút giữ chỗ
1. Mở màn hình thanh toán, chờ đồng hồ đếm ngược chạm mốc 00:00.
2. **Kỳ vọng**:
   - Nút "Xác nhận thanh toán" tự động bị vô hiệu hóa (disabled).
   - Hiển thị thông báo: "Đã hết thời gian giữ chỗ. Đơn đặt sân đã tự động hủy".
   - Hệ thống tự động giải phóng các slot đang `HOLD` về `AVAILABLE`.
