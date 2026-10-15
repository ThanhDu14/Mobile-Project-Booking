# Implementation Plan: [050] Thanh toán và Khuyến mãi giả lập (Payment & Voucher)

**Branch**: `feature/050-payment-voucher` | **Date**: 2026-10-15 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/050-payment-voucher/spec.md`

## 1. Summary (Tóm tắt giải pháp kỹ thuật)
- **Tiếp nhận đơn giữ chỗ (`HOLD`) & Quản lý phiên 5 phút**:
  - Nhận thông tin đơn ngày lẻ (từ `[040]`) hoặc đơn chu kỳ cố định (từ `[041]`) kèm mốc thời gian hết hạn giữ chỗ `holdExpiresAt`.
  - Tiếp tục đếm ngược thời gian còn lại của phiên giữ chỗ 5 phút (không reset lại từ đầu). Nếu chạm mốc `00:00` hoặc người dùng chủ động hủy đơn, tự động vô hiệu hóa nút thanh toán, giải phóng các slot đang `HOLD` và điều hướng quay lại màn hình trước.
- **Áp dụng mã khuyến mãi (Voucher Engine)**:
  - Tra cứu song song 2 nguồn voucher độc lập theo **Phương án 3.B**: (1) Voucher riêng của cơ sở tại `venues/{venueId}/vouchers/{code}` (do chủ sân tạo ở spec 111) và (2) Voucher toàn sàn tại `systemVouchers/{code}` (do Admin tạo ở spec 120). Document ID chính là mã code viết hoa không dấu để chống tạo trùng mã.
  - Kiểm tra các điều kiện: trạng thái kích hoạt (`isActive`), hạn sử dụng (`validFrom`, `validUntil`), giá trị đơn tối thiểu (`minOrderTotal`), tổng lượt dùng (`usedCount < usageLimit`), và giới hạn từng người (`perUserLimit`).
  - Hỗ trợ giảm theo % (có mức giảm tối đa `maxDiscount`) hoặc giảm số tiền cố định (`FIXED_AMOUNT`). Đảm bảo tổng tiền sau giảm $\ge 0$đ.
  - Phản hồi tính toán lại số tiền trong vòng dưới 1 giây. Lượt dùng chỉ được ghi nhận khi thanh toán thành công (vào subcollection `redemptions/{bookingId}` và bộ đếm `userUsage/{userId}`).
- **Lựa chọn phương thức thanh toán (Tùy biến theo loại đơn)**:
  1. *Thanh toán tại sân (`AT_VENUE`)*: Chưa trả tiền, `paymentStatus = UNPAID`. **Lưu ý**: Chỉ áp dụng cho đơn đặt lẻ (`[040]`); tự động ẩn/vô hiệu hóa đối với đơn cố định theo tuần (`[041]`).
  2. *Đặt cọc 30% (`DEPOSIT`)*: Trừ 30% giá trị đơn sau giảm giá từ ví giả lập (làm tròn đến hàng nghìn đồng chẵn gần nhất), phần còn lại trả tại sân, `paymentStatus = DEPOSITED`.
  3. *Ví giả lập 100% (`MOCK_WALLET`)*: Trừ 100% giá trị đơn sau giảm giá từ ví giả lập, `paymentStatus = PAID`.
- **Giao dịch thanh toán an toàn trong Firestore Transaction (Atomic)**:
  - Kiểm tra `holdExpiresAt > now`, kiểm tra số dư ví $\ge$ số tiền cần trừ, kiểm tra tổng lượt voucher (`usedCount < usageLimit`) và lượt dùng của user (`userUsage/{userId}.count < perUserLimit`) bằng Document Reference tất định (chống race condition).
  - Trừ số dư ví tại `users/{userId}.walletBalance`, ghi lịch sử biến động số dư vào `users/{userId}/walletTransactions/{txId}`, tăng `usedCount` của voucher, cập nhật `userUsage/{userId}` và ghi `redemptions/{bookingId}`.
  - Chuyển trạng thái đơn (`bookings/{bookingId}` hoặc `recurringGroups/{recurringGroupId}` cùng các đơn con) sang **`PENDING`** (chờ chủ sân duyệt trong tối đa 60 phút theo **Phương án 2.A**), đồng thời cập nhật các `slots` liên quan từ `HOLD` sang `BOOKED` (xóa `holdExpiresAt` 5 phút để không bị nhả sân).
  - Xuất hóa đơn điện tử / Phiếu đặt sân (E-receipt) có nhãn nổi bật: *"Giao dịch giả lập phục vụ học tập - Không thu tiền thật"* (Constitution VI) kèm nút chuyển sang `[060-my-bookings]`.
- **Tính năng Nạp ví giả lập (Học tập & Thử nghiệm)**:
  - Cho phép người dùng bấm "Nạp ví giả lập" (cộng số dư ảo các mệnh giá 100k, 500k, 1M và ghi log `TOP_UP`) ngay tại màn hình thanh toán để tránh tắc nghẽn luồng demo.

## 2. Technical Context
- **Language/Version**: Kotlin (JVM target 11 - đồng bộ `app/build.gradle.kts`), Android Gradle Plugin 9.4.1.
- **Platform**: Android `minSdk 29`, `targetSdk 37`, Single Activity Architecture (MVVM + Clean Architecture).
- **Primary Dependencies**:
  - Android Jetpack: ViewModel, Lifecycle (`repeatOnLifecycle`), Navigation Component (+ Safe Args), ViewBinding.
  - Concurrency: Kotlin Coroutines & Flow (`StateFlow` cho UI State, `SharedFlow` cho One-time Events, `async/awaitAll` khi đọc 2 nguồn voucher).
  - UI: Material 3 (BottomSheetDialogFragment, MaterialRadioGroup, TextInputLayout cho nhập voucher, Badge/Chip).
  - Backend/Cloud: Firebase BoM 34.19.0 (Firestore, Auth).
- **Storage & Firestore Schema**:
  - `users/{userId}`: Đọc/ghi số dư ví ảo (`walletBalance: Long`).
  - `users/{userId}/walletTransactions/{txId}`: Lưu lịch sử giao dịch ví (`TOP_UP`, `DEPOSIT`, `PAYMENT`).
  - `venues/{venueId}/vouchers/{code}`: Voucher cơ sở (Phương án 3.B).
  - `systemVouchers/{code}`: Voucher toàn hệ thống (Phương án 3.B).
  - `.../vouchers/{code}/userUsage/{userId}`: Document đếm số lần user đã dùng voucher này (`count: Int`) để kiểm tra `perUserLimit` trực tiếp trong `runTransaction`.
  - `.../vouchers/{code}/redemptions/{bookingId}`: Ghi nhận chi tiết lịch sử áp dụng voucher.
  - `bookings/{bookingId}` & `recurringGroups/{recurringGroupId}`: Cập nhật `paymentMethod`, `paymentStatus`, `depositAmount`, `discountAmount`, `finalAmount`, `voucherCode`, `status = PENDING`.
  - `slots/{courtId}_{yyyyMMdd}_{HHmm}`: Cập nhật `status = BOOKED` và xóa `holdExpiresAt` khi thanh toán thành công.
- **Testing**:
  - Unit Test: JUnit4, MockK, Turbine, Kotlinx Coroutines Test (kiểm thử `GetAvailableVouchersUseCase`, `ValidateAndApplyVoucherUseCase`, `CalculatePaymentBreakdownUseCase`, `ProcessMockPaymentUseCase` với các kịch bản race condition số dư âm, hết lượt voucher, hết hạn giữ chỗ).
  - UI Test: Espresso (kiểm thử nhập voucher, ẩn `AT_VENUE` với đơn chu kỳ, đổi phương thức thanh toán, nạp ví giả lập).

## 3. Constitution Check (Kiểm tra quy định dự án)
- [x] **I. Kiến trúc MVVM & Clean Architecture**: Phân tách rành mạch `domain`, `data`, `ui`; `PaymentRepository` interface đặt tại tầng `domain`; ViewModel/Fragment không gọi trực tiếp Firebase SDK.
- [x] **II. Bảo mật kiểm tra phía server & Quota**: Firestore Transaction kiểm tra số dư ví $\ge 0$ và `usedCount <= usageLimit`.
- [x] **III. Toàn vẹn dữ liệu đặt lịch**: Tiền tệ lưu bằng `Long` đơn vị VND (cấm `Double`/`Float`). Giao dịch trừ tiền ví, khóa slot từ `HOLD` sang `BOOKED` và áp voucher chạy trong Firestore Transaction nguyên tử, chống số dư âm và chống vượt hạn mức voucher.
- [x] **IV. Kiểm thử có trọng tâm**: Đặt kế hoạch unit test cho toàn bộ 6 UseCases và ViewModel.
- [x] **V. Giao diện người dùng**: Tuân thủ wireframe SmashNow (Spec 050), hiển thị nhãn học tập phi thương mại trên cả màn hình thanh toán và E-Receipt.
- [x] **VI. Thanh toán giả lập (Bắt buộc)**: Thanh toán thuần túy giả lập, hiển thị nhãn học tập phi thương mại, cấm tích hợp cổng thanh toán thật.

## 4. Project Structure (Cấu trúc thư mục & mã nguồn)

### 4.1. Tài liệu kỹ thuật đi kèm (`specs/050-payment-voucher/`)

```text
specs/050-payment-voucher/
├── spec.md                # Đặc tả nghiệp vụ gốc
├── plan.md                # Kế hoạch kỹ thuật tổng thể (file này)
├── research.md            # Nghiên cứu: Atomic transaction trừ ví, khóa slot & kiểm tra perUserLimit
├── data-model.md          # Chi tiết VoucherDto, RedemptionDto, WalletTransactionDto, PaymentReceipt
├── quickstart.md          # Hướng dẫn kiểm thử Unit Test, UI Test và kịch bản xác minh
└── contracts/             # UI State, Event & Navigation Safe Args từ 040/041 sang 050 và tới 060
    └── ui-contracts.md
```

### 4.2. Cấu trúc Source Code Android (`app/src/main/java/...`)

```text
com.example.booking_app.feature.payment/
├── data/
│   ├── model/
│   │   ├── VoucherDto.kt
│   │   ├── VoucherRedemptionDto.kt
│   │   └── WalletTransactionDto.kt
│   ├── mapper/
│   │   └── PaymentMapper.kt
│   └── repository/
│       └── PaymentRepositoryImpl.kt
├── domain/
│   ├── model/
│   │   ├── Voucher.kt                          # Thông tin voucher (scope: VENUE / SYSTEM)
│   │   ├── VoucherValidationResult.kt          # Sealed class kết quả kiểm tra (Valid, Expired, MinOrderNotMet, LimitReached...)
│   │   ├── PaymentMethod.kt                    # Enum: AT_VENUE, DEPOSIT, MOCK_WALLET, FREE
│   │   ├── PaymentBreakdown.kt                 # Tiền gốc, tiền giảm, payableAmount, depositAmount (30%), remainingAmount
│   │   └── PaymentReceipt.kt                   # Dữ liệu xuất hóa đơn điện tử E-Receipt
│   ├── repository/
│   │   └── PaymentRepository.kt                # Interface đặt tại tầng Domain
│   └── usecase/
│       ├── GetAvailableVouchersUseCase.kt      # Đọc song song voucher từ 2 nguồn (cơ sở & hệ thống)
│       ├── ValidateAndApplyVoucherUseCase.kt   # Kiểm tra điều kiện & tính số tiền được giảm
│       ├── CalculatePaymentBreakdownUseCase.kt # Tính toán phân bổ tiền & làm tròn cọc 30%
│       ├── ProcessMockPaymentUseCase.kt        # Firestore Transaction thực thi thanh toán & cập nhật slot
│       ├── TopUpMockWalletUseCase.kt           # Cộng tiền ảo vào ví giả lập & ghi log giao dịch
│       └── CancelPaymentHoldUseCase.kt         # Giải phóng giữ chỗ khi hết giờ (00:00) hoặc người dùng hủy đơn
└── ui/
    ├── PaymentVoucherFragment.kt               # Màn hình chính: Tóm tắt đơn, chọn voucher, chọn cách trả
    ├── PaymentReceiptFragment.kt               # Màn hình Hóa đơn điện tử / Phiếu đặt sân (E-Receipt)
    ├── PaymentViewModel.kt
    ├── PaymentUiState.kt                       # Chứa UiState (StateFlow) & UiEffect (SharedFlow)
    ├── adapter/
    │   └── VoucherListAdapter.kt               # Adapter hiển thị danh sách voucher trong BottomSheet
    └── bottomsheet/
        ├── VoucherSelectionBottomSheet.kt      # Danh sách voucher gợi ý (cơ sở + hệ thống)
        └── TopUpWalletBottomSheet.kt           # Nạp số dư ảo (100k, 500k, 1M)
```

## 5. Quy tắc thuật toán & Hợp đồng giao tiếp (Core Rules & Contracts)

### 5.1. Công thức tính Đặt cọc 30% và làm tròn an toàn (`CalculatePaymentBreakdownUseCase`)
- Với đơn có tổng tiền gốc là `subtotalAmount` và tiền giảm voucher là `discountAmount`:
  - `val payableAmount = maxOf(0L, subtotalAmount - discountAmount)`
- Nếu `payableAmount == 0L` (do voucher giảm 100%):
  - `depositAmount = 0L`, `remainingAmount = 0L`, phương thức thanh toán tự động chuyển thành `FREE` (`paymentStatus = PAID`).
- Nếu `payableAmount > 0L`:
  - `val rawDeposit = (payableAmount * 30) / 100`
  - Làm tròn đến hàng nghìn đồng chẵn gần nhất nhưng đảm bảo không bằng 0 và không vượt quá `payableAmount`:
    ```kotlin
    val roundedDeposit = ((rawDeposit + 500) / 1000) * 1000
    val depositAmount = if (roundedDeposit == 0L) payableAmount else minOf(roundedDeposit, payableAmount)
    val remainingAmount = payableAmount - depositAmount
    ```

### 5.2. Firestore Transaction thực thi thanh toán (`ProcessMockPaymentUseCase`)
Trong cùng 1 Transaction nguyên tử (thực hiện toàn bộ thao tác `get` trước, `set/update` sau theo quy tắc Firestore):
1. **Đọc & Kiểm tra hạn giữ chỗ**:
   - Đọc document đơn (`bookings/{id}` hoặc `recurringGroups/{id}`). Kiểm tra `status == "HOLD"` và `holdExpiresAt > Timestamp.now()`. Nếu quá hạn $\rightarrow$ ném `HoldExpiredException`.
2. **Đọc & Kiểm tra Voucher (nếu có áp dụng)**:
   - Đọc document voucher (`venues/{venueId}/vouchers/{code}` hoặc `systemVouchers/{code}`) và document đếm lượt dùng của cá nhân tại `.../vouchers/{code}/userUsage/{userId}`.
   - Kiểm tra `isActive == true`, `usedCount < usageLimit`, và `(userUsage.count ?: 0) < perUserLimit`. Nếu vi phạm $\rightarrow$ ném `VoucherInvalidException`.
3. **Đọc & Kiểm tra số dư Ví giả lập**:
   - Xác định `requiredAmount`: bằng `0L` (nếu `AT_VENUE` hoặc `FREE`), bằng `depositAmount` (nếu `DEPOSIT`), hoặc bằng `payableAmount` (nếu `MOCK_WALLET`).
   - Nếu `requiredAmount > 0L`: Đọc `users/{userId}`, kiểm tra `walletBalance >= requiredAmount`. Nếu thiếu tiền $\rightarrow$ ném `InsufficientBalanceException`.
4. **Thực thi Ghi/Cập nhật (Write Phase)**:
   - Nếu `requiredAmount > 0L`: Cập nhật `users/{userId}.walletBalance = walletBalance - requiredAmount` và tạo document log tại `users/{userId}/walletTransactions/{txId}`.
   - Nếu có dùng voucher: Tăng `usedCount + 1`, tăng `userUsage/{userId}.count + 1`, và tạo bản ghi `redemptions/{bookingId}`.
   - Cập nhật document đơn (`bookings` / `recurringGroups`) sang `status = "PENDING"`, ghi nhận `paymentMethod`, `paymentStatus` (`PAID` / `DEPOSITED` / `UNPAID`), `depositAmount`, `discountAmount`, `finalAmount`, `voucherCode`, `paidAt`.
   - Cập nhật toàn bộ các `slots/{slotId}` thuộc đơn từ `HOLD` sang `BOOKED` (xóa `holdExpiresAt` 5 phút).

### 5.3. Hợp đồng chuyển tiếp sang `[060-my-bookings]`
Khi bấm "Xem lịch đặt của tôi" từ màn hình Hóa đơn điện tử (`PaymentReceiptFragment`):

```kotlin
data class ReceiptToMyBookingsArgs(
    val bookingId: String,               // bookingId (đơn lẻ) hoặc recurringGroupId (đơn cố định)
    val isRecurring: Boolean = false,
    val initialTab: String = "PENDING"   // Mở ngay tab "Chờ duyệt" tại [060]
) : Parcelable
```

## 6. Implementation Phases (Các giai đoạn triển khai)

- **Phase 0 (Research & Data Design)**:
  - Soạn thảo `research.md`: Thiết kế luồng đọc-trước-ghi-sau trong Firestore Transaction cho bài toán trừ ví + kiểm tra `perUserLimit` + khóa slot; cơ chế timeout 60 phút duyệt đơn `PENDING` (Phương án 2.A).
  - Soạn thảo `data-model.md`: Định nghĩa Schema 2 nguồn voucher (Phương án 3.B), `userUsage`, `walletTransactions`, DTOs, Domain Entities và UI State.
  - Soạn thảo `contracts/ui-contracts.md`: Hợp đồng Navigation Safe Args từ `040`/`041` sang `050` và `ReceiptToMyBookingsArgs` từ `050` sang `060`.
- **Phase 1 (Domain & Data Layer)**:
  - Xây dựng `PaymentRepository` và 6 UseCases (`GetAvailableVouchers`, `ValidateAndApplyVoucher`, `CalculatePaymentBreakdown`, `ProcessMockPayment`, `TopUpMockWallet`, `CancelPaymentHold`).
  - Viết Unit Test kiểm thử công thức làm tròn cọc 30% (bao gồm các biên giá trị nhỏ), trần giảm giá `maxDiscount`, và rollback Transaction khi thiếu tiền ví, vượt `perUserLimit` hoặc hết hạn giữ chỗ.
- **Phase 2 (UI Layer & ViewModels)**:
  - Thiết kế XML Layout cho `PaymentVoucherFragment`, `PaymentReceiptFragment`, `VoucherSelectionBottomSheet` và `TopUpWalletBottomSheet` (đảm bảo hiển thị rõ nhãn học tập phi thương mại theo Constitution VI).
  - Xây dựng `PaymentViewModel` quản lý đếm ngược thời gian giữ chỗ còn lại, ẩn phương thức `AT_VENUE` nếu là đơn chu kỳ, và xử lý hiệu ứng chuyển màn.
- **Phase 3 (Integration & Verification)**:
  - Kiểm thử tích hợp toàn luồng `040/041 -> 050 -> E-Receipt -> 060` và cập nhật nhật ký `docs/ai-log/AI_log_2.md` (Mục 13).
