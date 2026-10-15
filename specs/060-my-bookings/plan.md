# Implementation Plan: [060] Quản lý lịch đặt của tôi (My Bookings)

**Branch**: `feature/060-my-bookings` | **Date**: 2026-10-15 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/060-my-bookings/spec.md`

## 1. Summary (Tóm tắt giải pháp kỹ thuật)
- **Quản lý danh sách lịch đặt theo 5 Tab trạng thái & Phân trang**:
  - Giao diện `TabLayout` + `ViewPager2` gồm 5 tab với `SwipeRefreshLayout` và phân trang `limit(10)` + `startAfterDocument`:
    1. *Chờ thanh toán (`HOLD`)*: Lọc `status == "HOLD"`, đếm ngược thời gian giữ chỗ còn lại theo giờ server. Tự động ẩn ngay các đơn đã quá hạn (`holdExpiresAt <= serverNow`). Nút "Thanh toán tiếp" mở lại `[050]` kế thừa thời gian còn lại (không reset 5 phút).
    2. *Chờ duyệt (`PENDING`)*: Lọc `status == "PENDING"`, hiển thị đồng hồ đếm ngược 60 phút hạn chủ sân duyệt (Phương án 2.A).
    3. *Sắp tới (`UPCOMING`)*: Lọc `status == "CONFIRMED"`, sắp xếp theo giờ chơi gần nhất (`startAt ASC`), tầng ViewModel lọc `endAt > serverNow`.
    4. *Đã hoàn thành (`COMPLETED`)*: Lọc `status IN ["COMPLETED", "NO_SHOW"]`, sắp xếp `startAt DESC`. Đơn `COMPLETED` (chưa đánh giá) hiện nút "Viết đánh giá" sang `[070]`; đơn `NO_SHOW` bị khóa tuyệt đối quyền đánh giá theo Phương án 5.A.
    5. *Đã hủy (`CANCELLED`)*: Lọc `status IN ["CANCELLED", "REJECTED"]`, sắp xếp `updatedAt DESC`, hiển thị lý do hủy/từ chối và số tiền đã hoàn vào ví giả lập (`refundAmount`).
  - **Quản lý Countdown Timer trên RecyclerView**: Sử dụng luồng thời gian chuẩn từ ViewModel (`serverNowMillis` ticker Flow) để đồng bộ hiển thị đếm ngược trên Tab 1 và Tab 2, không tạo `CountDownTimer` rời rạc trong từng item view gây rò rỉ bộ nhớ.
- **Hiển thị Mã QR Check-in & Hỗ trợ Offline (US2)**:
  - Với đơn `CONFIRMED`, sinh mã QR từ chuỗi `qrToken` bí mật của đơn hàng (kích thước tối thiểu 240dp, khung nền trắng `#FFFFFF` kèm padding để máy quét của chủ sân ở Spec `[112]` quét tốt trong Dark Mode).
  - Tầng `domain` trả về model thuần `QrBitMatrix` (không import Android `Bitmap`); tầng UI render sang `Bitmap` qua `QrBitmapRenderer.kt`.
  - **Offline Support**: Tận dụng Firestore Offline Persistence kết hợp cache cục bộ để xuất trình mã QR khi mất sóng 4G/Wi-Fi tại sân.
  - **Realtime Check-in Listener**: Lắng nghe realtime `bookings/{bookingId}`. Khi chủ sân quét QR thành công (`checkedInAt != null`), UI chuyển sang "Đã check-in lúc HH:mm" và ẩn nút "Hủy lịch đặt".
- **Thuật toán Tính tiền hoàn mốc 240 phút (`CalculateRefundQuoteUseCase`)**:
  - Đơn `PENDING`: Hoàn **100%**.
  - Đơn `CONFIRMED` hủy sớm ($\ge 240$ phút = 4 tiếng trước giờ chơi): Hoàn **100%**.
  - Đơn `CONFIRMED` hủy trễ ($< 240$ phút):
    + Trả qua ví giả lập (100%): Phạt 30% tiền cọc chuẩn hóa, hoàn **70%** còn lại. Kẹp biên an toàn chống âm khi có áp voucher: `penaltyAmount = minOf(booking.paidAmount, calculatedDepositPenalty)`, `refundAmount = maxOf(0L, booking.paidAmount - penaltyAmount)`.
    + Đặt cọc 30%: Mất 100% tiền cọc (hoàn 0đ).
    + Trả tại sân (`AT_VENUE`): Hoàn 0đ.
- **Firestore Transaction Hủy đơn & Nhả Slot Nguyên tử (`CancelBookingUseCase`)**:
  - Tuân thủ nghiêm ngặt quy tắc **All Reads Before All Writes**: Đọc `bookings/{bookingId}`, tính quote hoàn tiền, kiểm tra tính Idempotent qua document `users/{userId}/walletTransactions/refund_{bookingId}`, đọc `users/{userId}`.
  - Thực thi ghi: Chuyển đơn sang `CANCELLED`, cộng tiền hoàn vào `walletBalance`, ghi log giao dịch `refund_{bookingId}`, và **xóa toàn bộ document `slots/{slotId}`** thuộc đơn để giải phóng sân ngay lập tức trên lưới `[040]`.
- **Quản lý và Hủy chuỗi đơn cố định tuần (`recurringGroupId`)**:
  - Cho phép hủy 1 buổi lẻ (`CancelRecurringSessionUseCase`) hoặc hủy toàn bộ các buổi chưa diễn ra còn lại bằng cách truy vấn danh sách `DocumentReference` bên ngoài rồi truyền vào Transaction xử lý nguyên tử.

## 2. Technical Context
- **Language/Version**: Kotlin (JVM target 11 - đồng bộ `app/build.gradle.kts`), Android Gradle Plugin 9.4.1.
- **Platform**: Android `minSdk 29`, `targetSdk 37`, Single Activity Architecture (MVVM + Clean Architecture).
- **Primary Dependencies**:
  - Android Jetpack: ViewModel, Lifecycle (`repeatOnLifecycle`), Navigation Component (+ Safe Args), ViewBinding.
  - Concurrency: Kotlin Coroutines & Flow (`StateFlow` cho UI State, `SharedFlow` cho One-time Events).
  - UI: Material 3 (TabLayout, ViewPager2, MaterialCardView, Chip, SwipeRefreshLayout, MaterialAlertDialogBuilder).
  - QR Code: ZXing Core (`com.google.zxing:core`) tạo `BitMatrix`.
  - Backend/Cloud: Firebase BoM 34.19.0 (Firestore, Auth).
- **Storage & Firestore Schema**:
  - `bookings/{bookingId}`: Quản lý trạng thái đơn, `userId`, `qrToken`, `checkedInAt`, `status`, `refundAmount`, `cancelReason`.
  - `recurringGroups/{recurringGroupId}`: Quản lý nhóm đơn chu kỳ tuần.
  - `slots/{courtId}_{yyyyMMdd}_{HHmm}`: Xóa document slot khi đơn bị hủy (Constitution III).
  - `users/{userId}`: Đọc/ghi số dư ví ảo khi hoàn tiền (`walletBalance: Long`).
  - `users/{userId}/walletTransactions/refund_{bookingId}`: Document ID tất định chống hoàn tiền trùng lặp (Idempotent).
- **Testing**:
  - Unit Test: JUnit4, MockK, Turbine, Kotlinx Coroutines Test (kiểm thử `CalculateRefundQuoteUseCase`, `CancelBookingUseCase`, `CancelRecurringSessionUseCase`, thuật toán sinh QR).
  - UI Test: Espresso (kiểm thử chuyển 5 tab, hiển thị QR code, hộp thoại hủy đơn và khóa nút đánh giá đơn NO_SHOW).

## 3. Constitution Check (Kiểm tra quy định dự án)
- [x] **Constitution I (Clean Architecture)**: Tầng `domain` chỉ chứa pure Kotlin, model `QrBitMatrix` độc lập với `android.graphics.Bitmap`. UI render qua `QrBitmapRenderer.kt`.
- [x] **Constitution II (Security & Server Validation)**: Security Rules đảm bảo chỉ chủ đơn mới đọc được `qrToken` và hủy đơn; sử dụng `request.time` trong `firestore.rules` chặn hành vi chỉnh lùi giờ điện thoại nhằm trục lợi hoàn tiền 100% khi hủy trễ.
- [x] **Constitution III (Money, Time & Atomic Transaction)**: Mọi số tiền dùng kiểu `Long` VND (cấm `Double`/`Float`). Transaction hủy đơn tuân thủ All Reads Before All Writes, giải phóng `slots` và đảm bảo Idempotency qua ID `refund_{bookingId}`.
- [x] **Thỏa thuận liên thành viên**: Phương án 2.A (hiển thị đơn `REJECTED` khi quá 60m duyệt), Phương án 5.A (khóa quyền đánh giá đơn `NO_SHOW`).

## 4. Project Structure (Cấu trúc thư mục & mã nguồn)

### 4.1. Tài liệu kỹ thuật đi kèm (`specs/060-my-bookings/`)

```text
specs/060-my-bookings/
├── spec.md                # Đặc tả nghiệp vụ gốc
├── plan.md                # Kế hoạch kỹ thuật tổng thể (file này)
├── research.md            # Nghiên cứu: Bảng Composite Index, xử lý biên Voucher, chống chỉnh giờ, Transaction 2 pha, QR Offline
├── data-model.md          # Chi tiết DTOs, Domain Entities, QrBitMatrix, UI States
├── quickstart.md          # Hướng dẫn kiểm thử Unit Test, UI Test và kịch bản xác minh
└── contracts/             # UI State, Event & Navigation Safe Args sang 040, 050, 070 và Security Rules
    └── ui-contracts.md
```

### 4.2. Cấu trúc Source Code Android (`app/src/main/java/...`)

```text
com.example.booking_app.feature.booking_management/
├── data/
│   ├── model/
│   │   ├── BookingDto.kt
│   │   └── CancelBookingRequestDto.kt
│   ├── mapper/
│   │   └── BookingManagementMapper.kt
│   └── repository/
│       └── BookingManagementRepositoryImpl.kt
├── domain/
│   ├── model/
│   │   ├── BookingTab.kt                       # Enum: HOLD, PENDING, UPCOMING, COMPLETED, CANCELLED
│   │   ├── BookingSummaryItem.kt               # Dữ liệu hiển thị thẻ đơn trên danh sách
│   │   ├── BookingDetail.kt                    # Dữ liệu đầy đủ màn hình chi tiết đơn
│   │   ├── RefundPolicyQuote.kt                # Báo giá hoàn tiền: refundAmount, penaltyAmount, isEarlyCancel
│   │   ├── RecurringBookingGroupDetail.kt      # Chi tiết nhóm đơn cố định & danh sách các buổi
│   │   └── QrBitMatrix.kt                      # Model pure Kotlin ma trận bit QR (width, height, bits)
│   ├── repository/
│   │   └── BookingManagementRepository.kt      # Interface đặt tại tầng Domain
│   └── usecase/
│       ├── GetMyBookingsByTabUseCase.kt        # Đọc danh sách đơn phân trang theo Tab
│       ├── GetBookingDetailUseCase.kt          # Đọc chi tiết 1 đơn (kèm listener realtime)
│       ├── CalculateRefundQuoteUseCase.kt      # Tính toán số tiền hoàn dựa trên mốc 240 phút & phương thức
│       ├── CancelBookingUseCase.kt             # Transaction hủy đơn lẻ, hoàn ví & xóa slot (All Reads Before Writes)
│       ├── CancelRecurringSessionUseCase.kt    # Hủy riêng lẻ 1 buổi trong chuỗi định kỳ
│       └── GenerateCheckInQrUseCase.kt         # Sinh dữ liệu QrBitMatrix từ qrToken (ZXing Core)
└── ui/
    ├── MyBookingsFragment.kt                   # Màn hình chính chứa TabLayout & ViewPager2
    ├── BookingTabListFragment.kt               # Fragment con hiển thị danh sách đơn của từng Tab
    ├── BookingDetailFragment.kt                # Màn hình chi tiết đơn & xuất trình QR check-in
    ├── RecurringGroupDetailFragment.kt         # Màn hình quản lý chuỗi đơn cố định tuần
    ├── MyBookingsViewModel.kt
    ├── BookingDetailViewModel.kt
    ├── adapter/
    │   ├── MyBookingsPagerAdapter.kt           # Adapter quản lý 5 tab
    │   ├── BookingListAdapter.kt               # ListAdapter hiển thị danh sách thẻ đơn (kèm DiffUtil)
    │   └── RecurringSessionAdapter.kt          # Adapter hiển thị các buổi trong chuỗi cố định
    ├── dialog/
    │   └── CancelBookingConfirmationDialog.kt  # Hộp thoại cảnh báo tiền hoàn/phạt & chọn lý do hủy
    └── util/
        └── QrBitmapRenderer.kt                 # Chuyển QrBitMatrix sang android.graphics.Bitmap (nền trắng #FFFFFF)
```

## 5. Quy tắc thuật toán & Hợp đồng giao tiếp (Core Rules & Contracts)

### 5.1. Thuật toán Tính tiền hoàn hủy đơn (`CalculateRefundQuoteUseCase`)
- Đầu vào: `booking: BookingDetail`, `serverCurrentTimeMillis: Long`.
- Khoảng cách thời gian: `minutesUntilStart = (booking.startAtMillis - serverCurrentTimeMillis) / 60_000L`.
- Điều kiện tiên quyết: Chỉ cho phép hủy khi `status IN [PENDING, CONFIRMED]`, `checkedInAt == null`, và `serverCurrentTimeMillis < booking.startAtMillis`.
- Quy tắc hoàn tiền:
  1. **Đơn `PENDING`**: `penaltyAmount = 0L`, `refundAmount = booking.paidAmount` (Hoàn 100%).
  2. **Đơn `CONFIRMED` hủy sớm (`minutesUntilStart >= 240`)**: `penaltyAmount = 0L`, `refundAmount = booking.paidAmount` (Hoàn 100%).
  3. **Đơn `CONFIRMED` hủy trễ (`0 < minutesUntilStart < 240`)**:
     - `paymentMethod == MOCK_WALLET`:
       + `val calculatedDepositPenalty = booking.depositAmount.takeIf { it > 0 } ?: (((booking.subtotalAmount * 30) / 100 + 500) / 1000) * 1000`
       + `penaltyAmount = minOf(booking.paidAmount, calculatedDepositPenalty)`
       + `refundAmount = maxOf(0L, booking.paidAmount - penaltyAmount)` (Hoàn 70% sau khi trừ phạt cọc 30%, chống âm).
     - `paymentMethod == DEPOSIT`: `penaltyAmount = booking.paidAmount` (Mất 100% cọc), `refundAmount = 0L`.
     - `paymentMethod == AT_VENUE`: `penaltyAmount = 0L`, `refundAmount = 0L`.

### 5.2. Firestore Transaction Hủy đơn & Nhả Slot Nguyên tử (`CancelBookingUseCase`)
Trong cùng 1 Transaction nguyên tử (`firestore.runTransaction`):
- **Pha 1 — ALL READS**:
  1. Đọc `bookings/{bookingId}`: Xác thực `userId`, trạng thái `PENDING`/`CONFIRMED`, `checkedInAt == null`.
  2. Tính toán `RefundPolicyQuote`.
  3. Đọc `users/{userId}/walletTransactions/refund_{bookingId}`: Nếu đã tồn tại $\rightarrow$ trả về kết quả ngay (Idempotency).
  4. Nếu `refundAmount > 0L`: Đọc `users/{userId}` lấy `walletBalance`.
- **Pha 2 — ALL WRITES & DELETES**:
  1. Cập nhật `bookings/{bookingId}` sang `CANCELLED`, ghi lý do hủy, mốc hủy và số tiền hoàn/phạt.
  2. Nếu `refundAmount > 0L`: Cập nhật `walletBalance` và tạo bản ghi `walletTransactions/refund_{bookingId}`.
  3. **Xóa toàn bộ các document `slots/{slotId}`** thuộc `booking.slotIds` bằng `transaction.delete(slotRef)` để nhả sân ngay lập tức trên lưới `[040]`.

### 5.3. Hợp đồng điều hướng (Navigation Safe Args)
- **Từ `050` sang `060`**: Nhận `ReceiptToMyBookingsArgs` $\rightarrow$ Tự động mở đúng tab "Chờ duyệt" (`PENDING`).
- **Từ `060` sang `040`**: Bấm "Đặt lại sân này" $\rightarrow$ Điều hướng sang `BookingSlotGridFragment` với tham số `venueId`.
- **Từ `060` sang `070`**: Với đơn `COMPLETED` (không phải `NO_SHOW`), hiển thị nút "Viết đánh giá" điều hướng sang màn hình Review của Spec 070.

## 6. Implementation Phases (Các giai đoạn triển khai)

- **Phase 0 (Research & Data Design)**:
  - Soạn thảo `research.md`: Bảng Composite Index 5 tab, xử lý biên Voucher khi hủy trễ, cơ chế chống chỉnh lùi giờ điện thoại bằng `firestore.rules`, Transaction 2 pha, QR Offline.
  - Soạn thảo `data-model.md`: Định nghĩa DTOs, Domain Entities, `QrBitMatrix`, UI States.
  - Soạn thảo `contracts/ui-contracts.md`: Hợp đồng Navigation Safe Args và Domain `BookingManagementRepository` interface.
- **Phase 1 (Domain & Data Layer)**:
  - Xây dựng Repository và 6 UseCases (`GetMyBookingsByTab`, `GetBookingDetail`, `CalculateRefundQuote`, `CancelBooking`, `CancelRecurringSession`, `GenerateCheckInQr`).
  - Viết Unit Test kiểm thử công thức hoàn tiền (mốc 240m, hủy trễ cọc vs ví 70%, đơn PENDING), và phân bổ tiền chuỗi đơn cố định.
- **Phase 2 (UI Layer & ViewModels)**:
  - Thiết kế XML Layout cho `MyBookingsFragment` (TabLayout + ViewPager2), `BookingTabListFragment`, `BookingDetailFragment`, dialog xác nhận hủy, và `QrBitmapRenderer`.
  - Xây dựng `MyBookingsViewModel` và `BookingDetailViewModel` kết hợp `StateFlow` và `serverNowMillis` ticker.
- **Phase 3 (Integration & Verification)**:
  - Kiểm thử hiển thị QR offline khi ngắt mạng, kiểm thử hủy đơn hoàn ví realtime trên Firebase Emulator Suite và cập nhật nhật ký `docs/ai-log/AI_log_2.md` (Mục 14).
