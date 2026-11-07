# Implementation Plan: [040] Lưới chọn sân và ca chơi (Slot Grid)

**Branch**: `feature/040-booking-slot-grid` | **Date**: 2026-10-15 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/040-booking-slot-grid/spec.md`

## 1. Summary (Tóm tắt giải pháp kỹ thuật)
- **Mục tiêu nghiệp vụ**: Người dùng chọn ngày chơi (từ hôm nay đến 30 ngày tới), chọn sân con (Courts) và xem lưới thời gian theo từng block 30 phút (`HH:00` và `HH:30`).
- **Hiển thị trực quan 6 trạng thái ca chơi**: 
  - Còn trống (`AVAILABLE` - mặc định)
  - Đang chọn (`SELECTED` - Trạng thái tạm thời ở Local UI)
  - Đã được đặt (`BOOKED`)
  - Đang tạm giữ chờ thanh toán (`HELD`)
  - Khóa sân / Buổi vãng lai (`BLOCKED` với lý do `DROP_IN` hoặc bảo trì)
  - Đã qua giờ (`PAST` - Client tự động tính toán so với thời gian hiện tại nếu chọn ngày hôm nay).
- **Luồng tương tác**: Người dùng chọn 1 hoặc nhiều slot 30 phút **liên tiếp nhau trên cùng 1 sân con** (tối đa 8 slot = 4 giờ) $\rightarrow$ Hệ thống tính tổng tiền realtime theo cấu hình `priceRules` của cơ sở $\rightarrow$ Bấm "Tiếp tục" để truyền dữ liệu sang màn hình tóm tắt đặt sân `[041]`.
- **Giải pháp kỹ thuật cốt lõi**:
  - **UI Layer**: Sử dụng `RecyclerView` kết hợp `GridLayoutManager` (cho lưới ca chơi 30 phút) và `CourtTabAdapter` (chuyển đổi sân con) thông qua `ViewBinding` và `ListAdapter`/`DiffUtil`.
  - **Tối ưu Quota Firestore gói Spark (Sparse Storage)**: 
    - Client tự sinh danh sách các ca 30 phút mặc định `AVAILABLE` dựa theo `openTime` và `closeTime` của `venues`.
    - Firestore chỉ lưu và lắng nghe (`SnapshotListener`) trên collection gốc `slots` các ca có trạng thái bận (`BOOKED`, `HELD`, `BLOCKED`) trong ngày đang chọn với điều kiện `venueId == currentVenueId` và `date == yyyyMMdd`.
    - Tự động hủy `SnapshotListener` khi `onStop()`, khi đổi ngày hoặc khi thoát khỏi màn hình (qua `callbackFlow` với `awaitClose`).
  - **State Machine & Xử lý xung đột Realtime**: Quản lý trạng thái chọn qua `StateFlow` trong `ViewModel`. Đảm bảo ràng buộc chọn liên tiếp; tự động loại bỏ các slot đang chọn (kèm thông báo Snackbar qua `SharedFlow`) nếu có người dùng khác vừa chiếm chỗ qua Realtime.

## 2. Technical Context
- **Language/Version**: Kotlin (JVM target 11 - đồng bộ với `app/build.gradle.kts`), Android Gradle Plugin 9.4.1.
- **Platform**: Android `minSdk 29`, `targetSdk 37`, Single Activity Architecture (MVVM + Clean Architecture).
- **Primary Dependencies**:
  - Android Jetpack: ViewModel, Lifecycle (`repeatOnLifecycle`), Navigation Component (+ Safe Args), ViewBinding.
  - Concurrency: Kotlin Coroutines & Flow (`StateFlow` cho UI State, `SharedFlow` cho One-time UI Events/Effects).
  - UI: Material 3, RecyclerView (`ListAdapter` + `DiffUtil`, `GridLayoutManager`).
  - Backend/Cloud: Firebase BoM 34.19.0 (Firestore, Auth).
- **Storage & Firestore Schema**:
  - `venues/{venueId}`: Lấy giờ mở/đóng cửa (`openTime`, `closeTime` dạng `HHmm`).
  - `venues/{venueId}/courts/{courtId}`: Danh sách sân con (`name`, `surfaceType`, `isActive`).
  - `venues/{venueId}/priceRules/{id}`: Bảng giá (`daysOfWeek`, `startTime`, `endTime`, `pricePerHour`, tính giá 30p = `pricePerHour / 2`).
  - `slots/{courtId}_{yyyyMMdd}_{HHmm}`: **Collection cấp cao nhất (Root collection)**. Query có điều kiện composite index: `venueId` và `date == yyyyMMdd`. Document chỉ tồn tại khi slot bị bận (`BOOKED`, `HELD`, `BLOCKED`).
- **Testing**:
  - Unit Test: JUnit4, MockK, Turbine, Kotlinx Coroutines Test (kiểm thử `ValidateSlotSelectionUseCase`, `CalculateTotalPriceUseCase` và `BookingSlotGridViewModel`).
  - UI Test: Espresso (kiểm thử tương tác chọn/bỏ chọn ca liên tiếp và cập nhật bottom bar).

## 3. Constitution Check (Kiểm tra quy định dự án)
- [x] **I. Kiến trúc MVVM & Clean Architecture**: Tách biệt rõ `ui/`, `data/`, `domain/`; Fragment dùng ViewBinding, không gọi trực tiếp Firestore; ViewModel quản lý trạng thái bằng `StateFlow<BookingSlotUiState>`.
- [x] **II. Tối ưu Quota Spark & Server-side validation**: Áp dụng Sparse Storage (chỉ truy vấn slot bận theo composite index `venueId`, `date`), giải phóng `SnapshotListener` khi rời màn hình.
- [x] **III. Toàn vẹn dữ liệu đặt lịch**: Tuân thủ quy chuẩn slot 30 phút (`HH:00` và `HH:30`), múi giờ `Asia/Ho_Chi_Minh`. ID slot tất định `{courtId}_{yyyyMMdd}_{HHmm}`.
- [x] **IV. Kiểm thử có trọng tâm**: Đặt kế hoạch unit test cho toàn bộ UseCase và ViewModel.
- [x] **V. Giao diện người dùng**: Tuân thủ wireframe SmashNow (Spec 040), hỗ trợ màu sắc trực quan theo token Material 3 cho 6 trạng thái ca.

## 4. Project Structure (Cấu trúc thư mục & mã nguồn)

### 4.1. Tài liệu kỹ thuật đi kèm (`specs/040-booking-slot-grid/`)

```text
specs/040-booking-slot-grid/
├── spec.md                # Đặc tả nghiệp vụ gốc
├── plan.md                # Kế hoạch kỹ thuật tổng thể (file này)
├── research.md            # Nghiên cứu: Thuật toán chọn slot liên tiếp, tối ưu Firestore reads
├── data-model.md          # Chi tiết Kotlin Data Classes, Domain Models & Firestore Schema
├── quickstart.md          # Hướng dẫn chạy thử và kiểm thử tính năng
└── contracts/             # Định nghĩa UI State, UI Event, Safe Args & Repository Interface
    └── ui-contracts.md
```

### 4.2. Cấu trúc Source Code Android (`app/src/main/java/...`)

```text
com.example.booking_app.feature.booking/
├── data/
│   ├── model/
│   │   ├── CourtDto.kt
│   │   ├── PriceRuleDto.kt
│   │   └── SlotDto.kt
│   ├── mapper/
│   │   └── BookingSlotMapper.kt
│   └── repository/
│       └── BookingRepositoryImpl.kt
├── domain/
│   ├── model/
│   │   ├── Court.kt
│   │   ├── PriceRule.kt
│   │   ├── TimeSlot.kt              # Giờ, giá, trạng thái (AVAILABLE, BOOKED, HELD, BLOCKED, PAST, SELECTED)
│   │   └── SlotSelectionState.kt
│   ├── repository/
│   │   └── BookingRepository.kt     # Interface đặt tại tầng Domain
│   └── usecase/
│       ├── GenerateDailySlotsUseCase.kt       # Sinh lưới 30p từ giờ mở/đóng cửa + gộp slot bận từ Firestore
│       ├── ValidateSlotSelectionUseCase.kt    # Thuật toán kiểm tra & xử lý chọn/bỏ chọn ca liên tiếp
│       └── CalculateTotalPriceUseCase.kt      # Tính tổng tiền theo khung giờ thường / giờ vàng (PriceRule)
└── ui/
    ├── BookingSlotGridFragment.kt
    ├── BookingSlotGridViewModel.kt
    ├── BookingSlotUiState.kt        # UiState (StateFlow) & UiEffect (SharedFlow báo lỗi/chuyển màn)
    └── adapter/
        ├── DateSelectorAdapter.kt   # Thanh chọn ngày (7-14 ngày tới)
        ├── CourtTabAdapter.kt       # Chọn sân con (Sân 1, Sân 2...)
        └── SlotGridAdapter.kt       # Lưới ca chơi 30 phút
```

## 5. Quy tắc thuật toán & Hợp đồng giao tiếp (Core Rules & Contracts)

### 5.1. Thuật toán chọn ca liên tiếp (`ValidateSlotSelectionUseCase`)
- **Chọn ca đầu tiên**: Cho phép chọn bất kỳ ca nào có trạng thái `AVAILABLE`.
- **Chọn ca tiếp theo (cùng sân)**:
  - Nếu liền kề ngay trước hoặc ngay sau dãy đang chọn $\rightarrow$ Nối dài dãy chọn (`SELECTED`).
  - Nếu nằm cách quãng với dãy đang chọn $\rightarrow$ Tự động hủy dãy cũ, lấy ca vừa bấm làm mốc bắt đầu mới.
- **Bỏ chọn (Click vào ca đang `SELECTED`)**:
  - Nếu nằm ở 2 đầu mút của dãy $\rightarrow$ Bỏ chọn ca đó, giữ nguyên phần còn lại.
  - Nếu nằm ở giữa dãy đang chọn $\rightarrow$ Tự động cắt bỏ từ vị trí ca đó về phía cuối dãy để đảm bảo các ca còn lại vẫn liên tiếp.
- **Đổi sân con khác hoặc đổi ngày**: Reset toàn bộ danh sách đang chọn.

### 5.2. Xử lý xung đột Realtime (Race Condition)
- Khi `SnapshotListener` bắn dữ liệu các slot bận mới: Nếu có bất kỳ slot nào người dùng đang chọn (`SELECTED`) bị chuyển sang `HELD`, `BOOKED`, hoặc `BLOCKED` $\rightarrow$ Tự động xóa slot đó khỏi danh sách chọn, tính lại tổng tiền và bắn `UiEffect.ShowConflictWarning("Ca chơi vừa có người đặt/giữ chỗ")` hiển thị Snackbar.

### 5.3. Hợp đồng chuyển màn hình sang thanh toán `[050]` (Navigation Safe Args Contract)
Khi người dùng bấm "Tiếp tục" và giữ chỗ 5 phút thành công, Fragment điều hướng sang màn hình thanh toán `[050]` kèm theo Bundle Safe Args `HoldToPaymentArgs`:
```kotlin
data class HoldToPaymentArgs(
    val bookingId: String,
    val venueId: String,
    val venueName: String,
    val isRecurring: Boolean = false,
    val totalSessions: Int = 1,
    val subtotalAmount: Long,
    val slotIds: Array<String>,
    val holdExpiresAtMillis: Long
) : Parcelable
```

## 6. Implementation Phases (Các giai đoạn triển khai)

- **Phase 0 (Research & Contracts)**:
  - Soạn thảo `research.md`: Bài toán tối ưu query `slots` với composite index `(venueId, date)` và cơ chế `callbackFlow`.
  - Soạn thảo `data-model.md`: Định nghĩa chi tiết Kotlin Data Classes và Firestore Schema.
  - Soạn thảo `contracts/ui-contracts.md`: Hợp đồng UI State, Event và Navigation Safe Args.
- **Phase 1 (Domain & Data Layer)**:
  - Xây dựng Domain Models, `BookingRepository` interface và 3 UseCases cốt lõi (`GenerateDailySlots`, `ValidateSlotSelection`, `CalculateTotalPrice`).
  - Implement `BookingRepositoryImpl` kết nối Firestore với `callbackFlow` (tự động gọi `remove()` listener trong khối `awaitClose`).
- **Phase 2 (UI & ViewModel Integration)**:
  - Thiết kế XML Layout cho `BookingSlotGridFragment` và các item con (`item_date_pill.xml`, `item_court_tab.xml`, `item_time_slot.xml`, bottom bar hiển thị tổng tiền + nút Tiếp tục).
  - Xây dựng `BookingSlotGridViewModel` kết nối `StateFlow` (UI State) và `SharedFlow` (Navigation, Conflict Snackbar).
- **Phase 3 (Testing & Verification)**:
  - Viết Unit Test cho toàn bộ các trường hợp biên của `ValidateSlotSelectionUseCase` và `CalculateTotalPriceUseCase` (ca nằm vắt qua giữa 2 khung giờ trong `priceRules`).
  - Kiểm thử thực tế số lượng Document Reads trên Firebase Console khi chuyển ngày/chuyển sân để đảm bảo tối ưu Quota Spark.
