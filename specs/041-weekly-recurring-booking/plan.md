# Implementation Plan: [041] Đặt sân cố định theo tuần (Weekly Recurring Booking)

**Branch**: `feature/041-weekly-recurring-booking` | **Date**: 2026-10-15 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/041-weekly-recurring-booking/spec.md`

## 1. Summary (Tóm tắt giải pháp kỹ thuật)
- **Mục tiêu nghiệp vụ**: Người chơi (CLB/nhóm) thiết lập lịch đặt sân định kỳ dài hạn:
  - Chọn 1 hoặc nhiều thứ trong tuần (ví dụ: Thứ 3 & Thứ 5).
  - Chọn khung giờ cố định cho mỗi buổi (từ 2 đến 6 slot 30 phút liên tiếp = 1 đến 3 giờ/buổi theo chuẩn Spec 040).
  - Chọn sân con ưu tiên (ví dụ: Sân 1).
  - Chọn chu kỳ từ 4 đến 12 tuần (bắt đầu từ tuần hiện tại hoặc tuần kế tiếp; tự động bỏ qua các thứ đã qua hoặc các ca đã qua giờ trong ngày hiện tại).
- **Thuật toán kiểm tra khả dụng & xử lý xung đột lịch (Conflict Resolution)**:
  - Hệ thống kiểm tra khả dụng toàn bộ các buổi trong chu kỳ trên tất cả các sân con của Venue (chia batch `dates.chunked(30)` chạy song song qua Coroutines `async` để vượt giới hạn `whereIn` 30 phần tử của Firestore và đảm bảo phản hồi dưới 2 giây).
  - Một buổi chỉ được tính là khả dụng trên một sân khi **toàn bộ các slot 30 phút liên tiếp của buổi đó trên cùng một sân con** đều ở trạng thái `AVAILABLE` (hoặc `HELD` nhưng đã quá hạn `holdExpiresAt`).
  - Nếu phát hiện buổi bị trùng lịch tại sân ưu tiên (`BOOKED`, `HELD` còn hạn, hoặc `BLOCKED`), cung cấp 2 phương án:
    1. *Tự động đổi sân khác*: Tìm sân con khác trong cùng Venue còn trống trọn vẹn khung giờ của buổi đó (ưu tiên cùng loại mặt sân `surfaceType`), giữ nguyên giá theo Phương án 1.A.
    2. *Bỏ qua buổi bị trùng*: Giảm tổng số buổi và trừ tiền tương ứng.
  - **Ràng buộc tối thiểu**: Sau khi bỏ các buổi trùng, đơn cố định bắt buộc phải còn **tối thiểu 4 buổi hợp lệ**; nếu ít hơn 4 buổi sẽ chặn luồng và gợi ý chuyển sang đặt sân lẻ (Spec 040).
- **Giữ chỗ tạm thời 5 phút "Tất cả hoặc không" (All-or-Nothing) & Giải phóng chỗ**:
  - Khi người dùng bấm "Tiếp tục" sang màn hình xác nhận: Thực thi Firestore Transaction/Batch (kiểm soát giới hạn dưới 500 operations) để giữ chỗ đồng thời toàn bộ các slot của mọi buổi hợp lệ trong 5 phút (`holdExpiresAt = now + 5 minutes`, kèm `requestId` UUID chống double-submit).
  - Nếu bất kỳ slot nào vừa bị người khác đặt/giữ chỗ trước: Hủy toàn bộ Transaction, thông báo rõ buổi bị mất và đưa người dùng quay lại bước giải quyết xung đột.
  - Lưu thông tin nhóm tại `recurringGroups/{recurringGroupId}` và các đơn con tại `bookings/{bookingId}` (gắn trường `recurringGroupId`).
  - Nếu người dùng bấm Quay lại (Back) từ màn hình xác nhận về màn hình cấu hình để đổi lịch, hệ thống chủ động gọi `ReleaseRecurringHoldUseCase` để giải phóng ngay các slot đang giữ, tránh treo sân suốt 5 phút.
  - Khi xác nhận tại màn hình 2, chuyển tiếp sang màn hình thanh toán/đặt cọc của `[050]`.

## 2. Technical Context
- **Language/Version**: Kotlin (JVM target 11 - đồng bộ `app/build.gradle.kts`), Android Gradle Plugin 9.4.1.
- **Platform**: Android `minSdk 29`, `targetSdk 37`, Single Activity Architecture (MVVM + Clean Architecture).
- **Primary Dependencies**:
  - Android Jetpack: ViewModel, Lifecycle (`repeatOnLifecycle`), Navigation Component (+ Safe Args), ViewBinding.
  - Concurrency: Kotlin Coroutines & Flow (`StateFlow` cho UI State, `SharedFlow` cho One-time Events/Effects, `async/awaitAll` cho Batch Queries).
  - UI: Material 3, RecyclerView (`ListAdapter` + `DiffUtil`), ChipGroup (chọn thứ trong tuần), Material Slider (chọn chu kỳ 4–12 tuần).
  - Backend/Cloud: Firebase BoM 34.19.0 (Firestore, Auth).
- **Storage & Firestore Schema**:
  - `venues/{venueId}/courts`: Đọc danh sách sân con và `surfaceType` để tìm sân thay thế khi có xung đột.
  - `venues/{venueId}/priceRules`: Bảng giá theo block 30 phút (`pricePerSlot = pricePerHour / 2`).
  - `slots/{courtId}_{yyyyMMdd}_{HHmm}`: Collection lưu các ca bận (Sparse Storage). Truy vấn song song theo từng chunk 30 ngày cho toàn bộ các ngày trong chu kỳ.
  - `recurringGroups/{recurringGroupId}`: Lưu thông tin nhóm đơn cố định (`userId`, `venueId`, `daysOfWeek`, `startTime`, `endTime`, `startDate`, `endDate`, `totalSessions`, `subtotal`, `depositAmount` 30%, `status`, `holdExpiresAt`, `requestId`, `createdAt`).
  - `bookings/{bookingId}`: Mỗi buổi hợp lệ là 1 document con có trường `recurringGroupId`.
- **Testing**:
  - Unit Test: JUnit4, MockK, Turbine, Kotlinx Coroutines Test (kiểm thử `GenerateRecurringSessionsUseCase`, `CheckRecurringAvailabilityUseCase`, `ResolveRecurringConflictUseCase`, `CalculateRecurringPriceUseCase`, `HoldRecurringBookingUseCase`).
  - UI Test: Espresso (kiểm thử cấu hình thứ/giờ/tuần, BottomSheet giải quyết xung đột và đếm ngược giữ chỗ).

## 3. Constitution Check (Kiểm tra quy định dự án)
- [x] **I. Kiến trúc MVVM & Clean Architecture**: Phân tách `ui/`, `data/`, `domain/`; Fragment dùng ViewBinding, không gọi trực tiếp Firestore; ViewModel quản lý trạng thái bằng `StateFlow<BookingRecurringUiState>`.
- [x] **II. Tối ưu Quota Spark & Server-side validation**: Áp dụng Sparse Storage từ Spec 040; gom truy vấn kiểm tra khả dụng theo chunk ngày (`chunked(30)`) để giảm thiểu số lần đọc và tránh lỗi giới hạn `whereIn`.
- [x] **III. Toàn vẹn dữ liệu đặt lịch**: Tuân thủ chuẩn ca 30 phút (`HH:00` và `HH:30`), múi giờ `Asia/Ho_Chi_Minh`. Giữ chỗ chu kỳ theo nguyên tắc "Tất cả hoặc không", ID slot tất định `{courtId}_{yyyyMMdd}_{HHmm}`, `holdExpiresAt` 5 phút, `requestId` (UUID) chống double-submit và kiểm soát số lượng operations dưới 500 ops/transaction.
- [x] **IV. Kiểm thử có trọng tâm**: Đặt kế hoạch unit test cho toàn bộ 6 UseCases và ViewModel.
- [x] **V. Giao diện người dùng**: Tuân thủ wireframe SmashNow (Spec 041), ChipGroup chọn thứ, Slider chọn tuần và BottomSheet xử lý xung đột.

## 4. Project Structure (Cấu trúc thư mục & mã nguồn)

### 4.1. Tài liệu kỹ thuật đi kèm (`specs/041-weekly-recurring-booking/`)

```text
specs/041-weekly-recurring-booking/
├── spec.md                # Đặc tả nghiệp vụ gốc
├── plan.md                # Kế hoạch kỹ thuật tổng thể (file này)
├── research.md            # Nghiên cứu: Chunked query chu kỳ 12 tuần < 2s, Firestore Transaction limit & All-or-Nothing
├── data-model.md          # Chi tiết RecurringGroupDto, BookingSessionDto, RecurringSession, ConflictResolution
├── quickstart.md          # Hướng dẫn kiểm thử Unit Test, UI Test và kịch bản xác minh
└── contracts/             # UI State, Event, Side Effect & Navigation Safe Args sang 050
    └── ui-contracts.md
```

### 4.2. Cấu trúc Source Code Android (`app/src/main/java/...`)

```text
com.example.booking_app.feature.booking/
├── data/
│   ├── model/
│   │   ├── RecurringGroupDto.kt
│   │   └── BookingSessionDto.kt
│   ├── mapper/
│   │   └── RecurringBookingMapper.kt
│   └── repository/
│       └── RecurringBookingRepositoryImpl.kt
├── domain/
│   ├── model/
│   │   ├── RecurringConfig.kt                  # Thứ, khung giờ (2-6 slots), sân ưu tiên, số tuần, ngày bắt đầu
│   │   ├── RecurringSession.kt                 # Chi tiết 1 buổi (ngày, courtId, slotIds, giá, status)
│   │   ├── RecurringConflict.kt                # Thông tin buổi trùng & danh sách sân thay thế khả dụng
│   │   └── RecurringSummary.kt                 # Tổng số buổi hợp lệ, tổng tiền, tiền cọc 30%, holdExpiresAt
│   ├── repository/
│   │   └── RecurringBookingRepository.kt       # Interface đặt tại tầng Domain
│   └── usecase/
│       ├── GenerateRecurringSessionsUseCase.kt # Sinh danh sách ngày/buổi theo cấu hình 4-12 tuần
│       ├── CheckRecurringAvailabilityUseCase.kt# Quét Firestore (chunked 30 ngày) kiểm tra slot bận & sân thay thế
│       ├── ResolveRecurringConflictUseCase.kt  # Xử lý đổi sân (trọn vẹn các slot trong buổi) hoặc bỏ buổi trùng
│       ├── CalculateRecurringPriceUseCase.kt   # Tính tổng tiền các buổi hợp lệ & tiền cọc 30%
│       ├── HoldRecurringBookingUseCase.kt      # Transaction giữ chỗ toàn chu kỳ 5 phút (All-or-Nothing)
│       └── ReleaseRecurringHoldUseCase.kt      # Giải phóng các slot đang HELD khi người dùng Back/Hủy
└── ui/
    └── recurring/
        ├── BookingRecurringConfigFragment.kt        # Màn hình 1: Cấu hình thứ, giờ, sân ưu tiên, chu kỳ tuần
        ├── BookingRecurringConflictBottomSheet.kt   # BottomSheet giải quyết xung đột (đổi sân / bỏ buổi)
        ├── BookingRecurringConfirmFragment.kt       # Màn hình 2: Xác nhận danh sách buổi, tổng tiền & đếm ngược 5p
        ├── BookingRecurringViewModel.kt
        ├── BookingRecurringUiState.kt               # Chứa UiState (StateFlow) & UiEffect (SharedFlow)
        └── adapter/
            ├── DayOfWeekChipAdapter.kt
            ├── ConflictResolutionAdapter.kt
            └── RecurringSessionSummaryAdapter.kt
```

## 5. Quy tắc thuật toán & Hợp đồng chuyển tiếp (Core Rules & Navigation)

### 5.1. Thuật toán xử lý xung đột (`ResolveRecurringConflictUseCase`)
1. **Kiểm tra từng buổi trong chu kỳ được sinh ra (mỗi buổi gồm $N$ slot 30 phút liên tiếp, $2 \le N \le 6$)**:
   - Nếu **tất cả $N$ slot** tại sân ưu tiên đều `AVAILABLE` (hoặc `HELD` nhưng đã hết hạn `holdExpiresAt < currentTime`) $\rightarrow$ Giữ nguyên buổi tại sân ưu tiên.
   - Nếu **bất kỳ slot nào** trong $N$ slot tại sân ưu tiên bị `BOOKED`, `BLOCKED`, hoặc `HELD` (còn hạn):
     - Quét các sân con khác cùng Venue trong cùng ngày & khung giờ: Một sân thay thế chỉ hợp lệ khi **cả $N$ slot liên tiếp trên sân đó đều trống**.
     - Nếu tìm thấy $\ge 1$ sân trống $\rightarrow$ Đưa buổi đó vào trạng thái `SUGGEST_CHANGE_COURT` (sắp xếp ưu tiên các sân có cùng `surfaceType` với sân gốc).
     - Nếu toàn bộ các sân con trong Venue đều bị vướng ít nhất 1 slot trong khung giờ đó $\rightarrow$ Đưa vào trạng thái `MUST_SKIP` (bắt buộc bỏ qua buổi này hoặc cấu hình lại giờ).
2. **Sau khi người dùng chọn phương án giải quyết**:
   - **Phương án Đổi sân**: Cập nhật `courtId` và danh sách `slotIds` sang sân thay thế cho buổi đó, giữ nguyên đơn giá của buổi theo Phương án 1.A.
   - **Phương án Bỏ buổi**: Loại bỏ buổi đó khỏi danh sách đặt, gọi `CalculateRecurringPriceUseCase` cập nhật lại tổng số buổi, `subtotalAmount` và `depositAmount` (30%).
   - **Kiểm tra điều kiện tiên quyết**: Nếu số buổi hợp lệ còn lại `validSessions.size < 4` $\rightarrow$ Vô hiệu hóa nút "Tiếp tục", hiển thị cảnh báo yêu cầu tối thiểu 4 buổi cho gói đặt cố định và gợi ý nút chuyển về màn hình đặt sân lẻ `[040]`.

### 5.2. Hợp đồng chuyển tiếp sang `[050-payment-voucher]`
Khi giữ chỗ toàn chu kỳ thành công (`HoldRecurringBookingUseCase`), chuyển sang màn hình thanh toán kèm Bundle Safe Args:

```kotlin
data class RecurringToPaymentArgs(
    val recurringGroupId: String,         // Mã nhóm UUID
    val venueId: String,
    val venueName: String,
    val totalSessions: Int,               // Số lượng buổi hợp lệ (>= 4)
    val subtotalAmount: Long,             // Tổng tiền toàn chu kỳ trước voucher
    val depositAmount: Long,              // Tiền cọc tối thiểu 30% (subtotalAmount * 30 / 100)
    val holdExpiresAtMillis: Long         // Mốc hết hạn giữ chỗ (5 phút kể từ lúc Hold thành công)
) : Parcelable
```

## 6. Implementation Phases (Các giai đoạn triển khai)

- **Phase 0 (Research & Data Design)**:
  - Soạn thảo `research.md`: Giải thuật chia batch `chunked(30)` kết hợp `async/awaitAll` kiểm tra khả dụng chu kỳ 12 tuần dưới 2 giây trên mạng 4G; chiến lược Firestore Transaction đảm bảo không vượt giới hạn 500 operations cho bài toán All-or-Nothing.
  - Soạn thảo `data-model.md`: Định nghĩa Schema `recurringGroups`, `bookings`, `slots`, DTOs và Domain Entities.
  - Soạn thảo `contracts/ui-contracts.md`: Định nghĩa `BookingRecurringUiState`, `BookingRecurringUiEffect` và Navigation Safe Args `RecurringToPaymentArgs` sang `[050]`.
- **Phase 1 (Domain & Data Layer)**:
  - Xây dựng `RecurringBookingRepository` và 6 UseCases (`GenerateRecurringSessions`, `CheckRecurringAvailability`, `ResolveRecurringConflict`, `CalculateRecurringPrice`, `HoldRecurringBooking`, `ReleaseRecurringHold`).
  - Viết Unit Test kiểm thử logic sinh ngày (bỏ qua ngày quá khứ trong tuần hiện tại), tìm sân thay thế cùng `surfaceType`, tính cọc 30% và điều kiện chặn dưới 4 buổi.
- **Phase 2 (UI Layer & ViewModels)**:
  - Thiết kế XML Layout cho `BookingRecurringConfigFragment`, `BookingRecurringConflictBottomSheet` và `BookingRecurringConfirmFragment`.
  - Xây dựng `BookingRecurringViewModel` quản lý luồng trạng thái bằng `StateFlow`, xử lý đếm ngược 5 phút và tự động gọi `ReleaseRecurringHoldUseCase` khi người dùng hủy hoặc thoát màn hình xác nhận.
- **Phase 3 (Integration & Verification)**:
  - Kiểm thử kịch bản giữ chỗ 5 phút thành công và kịch bản rollback All-or-Nothing khi có ít nhất 1 slot trong chu kỳ bị người dùng khác chiếm trước.
  - Kiểm thử số lượng Document Reads/Writes thực tế trên Firebase Console và cập nhật nhật ký `docs/ai-log/AI_log_2.md` (Mục 12).
