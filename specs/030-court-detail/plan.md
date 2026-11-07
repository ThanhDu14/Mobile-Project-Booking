# Implementation Plan: Chi tiết sân

**Branch**: `feature/03-court-detail-plan` | **Date**: 2026-10-25 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/030-court-detail/spec.md`

## Summary

Màn hình chi tiết một cơ sở cầu lông cho người chơi và khách. Màn hình hiển thị:
- thư viện ảnh có xem toàn màn hình và phóng to;
- thông tin chính, trạng thái mở cửa theo giờ Việt Nam;
- sân con theo loại mặt sân và tiện ích;
- bảng giá theo nhóm ngày, có làm nổi bật khung giờ đến từ tìm kiếm;
- bản đồ nhúng, "Chỉ đường" và số điện thoại;
- đánh giá tổng quan với 3 đánh giá mới nhất;
- thanh "Đặt sân" và chat cố định ở cuối.

**Cách làm:**
- Spec chỉ đọc Firestore. Có một snapshot listener cho `venues/{id}` để bắt kịp đổi trạng thái (FR-023) và điểm mới. `priceRules`, `courts` và 3 đánh giá được đọc bằng `get()` một lần, mỗi mục có trạng thái riêng ([R2](research.md#r2-đọc-dữ-liệu-thứ-tự-tải-và-số-lượt-đọc-fr-024-fr-025-sc-001)).
- Không có Edge Function hay transaction mới.
- Dùng lại nhiều phần đã có:
  - giá khung giờ gọi đúng `core/pricing` của 020 và 040, có test vector dùng chung ([R4](research.md#r4-bảng-giá-nhóm-theo-ngày-và-giá-khung-bạn-chọn-fr-008-fr-009-sc-002));
  - khách bấm thao tác cần tài khoản thì dùng cơ chế `nav_auth` chồng lên của 010 R15 cùng `pendingAction` trong `SavedStateHandle` ([R11](research.md#r11-khách-chưa-đăng-nhập-và-thao-tác-đang-chờ-fr-018-fr-020-sc-004));
  - yêu thích và danh sách đánh giá đi qua interface domain của 070.
- Bản đồ dùng osmdroid, vì nhóm không có tài khoản thanh toán Google Maps ([R7](research.md#r7-bản-đồ-nhúng-và-chỉ-đường-fr-010-fr-011-fr-013)).
- Dữ liệu demo là seed 10 sân của nhóm (`scripts/seed/`). Plan xử lý các điểm riêng của seed: ảnh là URL đầy đủ, giờ `"2400"`, số điện thoại bị che, tọa độ gần đúng, chưa có tiện ích và đánh giá ([R13](research.md#r13-dữ-liệu-seed-của-nhóm-scriptsseed-và-constitution-vi)).

## Technical Context

**Language/Version**: Kotlin (bản đi kèm AGP 9.4), `minSdk 29`, `compileSdk`/`targetSdk 37`

**Primary Dependencies**:
- Đã có từ 010: Fragment/Navigation + Safe Args, ViewBinding, Material 3, Lifecycle ViewModel/StateFlow, Coroutines, Hilt (KSP), Firebase BoM (Auth, Firestore).
- Đã chốt ở 020 và 070: Coil, ViewPager2.
- **Mới:** osmdroid ([R14](research.md#r14-thư-viện-và-cấu-trúc-code)).

**Storage**: Cloud Firestore, chỉ đọc: `venues` kèm `courts`, `priceRules`; `reviews`; `districts`, `amenities`; `users/{uid}/favorites` qua 070. Ảnh: Supabase Storage bucket `venue-photos` (public) hoặc URL đầy đủ của dữ liệu demo. Cache offline của Firestore khi mất mạng. Tile bản đồ cache trong `cacheDir`.

**Testing**:
- JUnit4, MockK, Turbine, `kotlinx-coroutines-test` cho các hàm thuần và ViewModel:
  - `SearchWindowArgs`, `OpeningHours`, `CourtSummary`;
  - `PriceTableBuilder`, `PhoneDisplay`, `PendingActionResolver`;
  - `VenueRatingSummary`, `PhotoUrlResolver`, ma trận zoom;
  - use case và `CourtDetailViewModel`.
- Test vector giá dùng chung với 020 và 040.
- `@firebase/rules-unit-testing` trên Firestore Emulator cho VD-1 → VD-10.
- Espresso trên Emulator (nên có).

**Target Platform**: Android điện thoại và máy tính bảng (`w600dp`), sáng và tối.

**Project Type**: mobile-app (một module `app`, package theo tính năng).

**Performance Goals**: phần đầu màn hình hiện < 3 giây trên 4G ở ≥ 95% lần thử (SC-001), ước lượng 0,7–1,5 giây (R2). Bản đồ và đánh giá tải sau.

**Constraints**:
- Firebase Spark: khoảng 19–26 lượt đọc mỗi lần mở (R2), gỡ listener khi `STOPPED`.
- Không xin quyền vị trí.
- Không dịch vụ trả phí (không dùng Google Maps SDK).
- Tiền `Long` VND; giờ theo `Asia/Ho_Chi_Minh`.
- Offline: hiện dữ liệu cache kèm thông báo.

**Scale/Scope**: dữ liệu demo 10 cơ sở (seed), quy mô đồ án dưới 100 cơ sở. Phạm vi:
- 1 Fragment chính có 3 trạng thái toàn màn hình;
- 1 Fragment xem ảnh dùng chung;
- 5 use case;
- 1 repository;
- 3 thành phần `core/` mới (`core/media`, `core/ui/photo`, `core/model/VenueRatingSummary`);
- không có Rules hay index do 030 sở hữu; đề nghị sửa Rules cho D và C, kèm 9 test.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Nguyên tắc | Đánh giá | Ghi chú |
|---|---|---|
| I. MVVM, package theo tính năng | ✅ | Package `feature/courtdetail/{ui,domain,data}`. Fragment → `CourtDetailViewModel` → use case → `VenueDetailRepository` (interface) → Firestore. Một `StateFlow<CourtDetailUiState>`, sự kiện qua `Channel`. Listener venue bọc trong `callbackFlow`. Không import `ui/`/`data/` của tính năng khác: yêu thích và đánh giá qua interface trong `feature/review/domain`; điều hướng sang 040, 070, 091 bằng action hoặc deep link ([contracts/navigation.md](contracts/navigation.md)). Phần dùng chung mới đặt ở `core/` |
| II. Bảo mật phía server | ✅ | Chỉ đọc. Quyền đọc do Rules quy định ([contracts/security-rules.md](contracts/security-rules.md), test VD-1 → VD-10). Ẩn "Đặt sân" ở cơ sở tạm ngừng và chặn chủ cơ sở chỉ là trải nghiệm: `create-booking` (B) và chat (C) phải kiểm tra ở server, đã ghi trong contract. Không thêm khóa bí mật (osmdroid không cần khóa) |
| III. Toàn vẹn đặt lịch | ✅ | Không ghi slot hay đơn. "Đặt sân" chỉ điều hướng sang 040; giá hiển thị dùng cùng `core/pricing` với 040. Thời gian theo `Asia/Ho_Chi_Minh`, tiền `Long` |
| IV. Kiểm thử có trọng tâm | ✅ | Unit test cho mọi hàm thuần, use case, ViewModel; test vector giá dùng chung (logic tính tiền bắt buộc có test); test Rules trên Emulator; quickstart chạy trên Emulator với seed, không dùng project thật |
| V. Giao diện XML | ✅ | XML + ViewBinding + Material 3 DayNight; chuỗi `court_detail_*`; mỗi mục đủ 4 trạng thái kèm "Thử lại"; `layout-w600dp` hai cột; `contentDescription`, vùng chạm 48dp. Phần đầu < 3 giây |
| VI. Quyền riêng tư | ⚠️ Dữ liệu demo | **Code** đạt: không xin quyền vị trí (chỉ dùng vị trí đã được cấp trước đó, không gửi đi), `ACTION_DIAL` không cần quyền, số điện thoại cơ sở là thông tin công khai đã chốt. **Dữ liệu seed** lấy tên, địa chỉ, số điện thoại (đã che) và ảnh của sân thật từ Alobo, là app tham khảo của nhóm. Constitution VI yêu cầu dữ liệu demo giả hợp lý và ảnh tự chụp hoặc miễn phí bản quyền. **Đã quyết (C1, 2026-10-25):** seed chỉ dùng để demo đồ án, có ghi nguồn; cơ sở thật do chủ sân tạo qua spec 111 (T077). Ghi là ngoại lệ ở Complexity Tracking ([R13](research.md#r13-dữ-liệu-seed-của-nhóm-scriptsseed-và-constitution-vi)). Code 030 không phụ thuộc dữ liệu này |
| VII. Đơn giản, minh bạch AI | ✅ | Một thư viện mới (osmdroid, có trong constitution) ghi ở Complexity Tracking. Không thêm thư viện phóng to ảnh; tự viết `ZoomableImageView` nhỏ có test (R8). Log AI ghi ở `docs/ai-log/AI_log_1.md` |

**Kết quả gate:** đạt có ngoại lệ. Mục VI: dữ liệu demo lấy từ Alobo, đã quyết chỉ dùng cho demo đồ án và ghi ở Complexity Tracking. Mục IV: thêm test Repository trên Emulator (T076) sau `/speckit-analyze` C2.

**Re-check sau Phase 1:** vẫn đạt. Thiết kế không thêm collection, Edge Function hay module Gradle. Ba thành phần `core/` mới đều có ít nhất hai nhóm dùng (020, 070, 021).

## Project Structure

### Documentation (this feature)

```text
specs/030-court-detail/
├── plan.md                      # File này
├── research.md                  # R1–R14 + bảng phối hợp
├── data-model.md                # Trường được đọc, mô hình phía app, đề xuất sửa đặc tả CSDL
├── quickstart.md                # Chuẩn bị Emulator + seed, ca giá P1–P9, kịch bản Q1–Q24
├── contracts/
│   ├── navigation.md            # Tham số vào/ra, deep link, interface dùng chung
│   ├── ui-screens.md            # Các mục theo wireframe, trạng thái, ViewModel
│   └── security-rules.md        # Rules đề nghị D, C và test VD-1 → VD-10
├── ui/                          # Wireframe (đã có)
├── checklists/requirements.md
└── tasks.md                     # Phase 2 (/speckit-tasks)
```

### Source Code (repository root)

```text
app/src/main/java/com/example/booking_app/
├── core/
│   ├── media/PhotoUrlResolver.kt                              # MỚI, dùng chung 020/021/070/111
│   ├── model/VenueRatingSummary.kt                            # MỚI, dùng chung 070
│   ├── ui/photo/PhotoViewerFragment.kt, PhotoPagerAdapter.kt,
│   │            ZoomableImageView.kt, ZoomMath.kt             # MỚI, dùng chung 070
│   ├── pricing/ (PriceRule, SlotPricing, DayConvention)       # của 020, dùng lại
│   ├── schedule/ (TimeOfDay chấp nhận "2400" ở giờ kết thúc)  # của 020, bổ sung ca 2400
│   ├── catalog/, location/                                    # của 020, dùng lại
│   └── session/, network/, util/AppClock.kt                   # của 010, dùng lại
└── feature/courtdetail/
    ├── domain/
    │   ├── model/ (CourtDetailArgs, SearchWindow, VenueDetail, VenueAvailability,
    │   │           OpeningStatus, CourtSummary, PhoneDisplay, PriceDayGroup, PriceRow,
    │   │           SelectedWindowPrice, PendingAction, PendingDecision)
    │   ├── SearchWindowArgs.kt, OpeningHours.kt, PriceTableBuilder.kt,
    │   │   PendingActionResolver.kt                           # thuần Kotlin
    │   ├── VenueDetailRepository.kt                           # interface
    │   └── usecase/ (ObserveVenueDetail, LoadPriceTable, LoadCourtSummary,
    │                 LoadLatestReviews, CheckVenueBookable)
    ├── data/
    │   ├── FirestoreVenueDetailRepository.kt                  # awaitWithTimeout, callbackFlow
    │   └── dto/ (VenueDetailDto, CourtDto)                    # priceRules dùng mapper của core/pricing
    ├── ui/
    │   ├── CourtDetailFragment.kt, CourtDetailViewModel.kt, CourtDetailUiState.kt
    │   ├── PhotoGalleryAdapter.kt, PriceTableAdapter.kt, ReviewPreviewAdapter.kt
    │   ├── VenueMapController.kt                              # bọc osmdroid MapView theo vòng đời
    │   └── LoginPromptDialog.kt
    └── di/CourtDetailModule.kt

app/src/main/res/
├── navigation/nav_court_detail.xml                            # include vào nav_player; action_global_photoViewer
├── layout/fragment_court_detail.xml, view_court_detail_gone.xml,
│          view_court_detail_suspended_guest.xml, item_court_detail_photo.xml,
│          item_court_detail_price_group.xml, item_court_detail_price_row.xml,
│          item_court_detail_review.xml, fragment_photo_viewer.xml
├── layout-w600dp/fragment_court_detail.xml
├── drawable/ic_marker_shuttlecock.xml, img_venue_placeholder.xml, ic_amenity_*.xml
└── values/strings_court_detail.xml

app/src/test/java/com/example/booking_app/
├── core/ (media, model, ui/photo/ZoomMath, schedule ca "2400")
└── feature/courtdetail/ (domain, ui)
app/src/test/resources/pricing/window-price-vectors.json       # dùng chung 020/030/040

app/src/androidTest/java/com/example/booking_app/feature/courtdetail/CourtDetailFlowTest.kt   # nên có

firestore-tests/test/court-detail.test.ts                      # VD-1 → VD-10
scripts/seed/seed-reviews-030.mjs                              # chỉ Emulator, nếu 070 chưa có seed đánh giá
```

**Structure Decision**: Một module `app`, package `feature/courtdetail` theo constitution I. Phần nhiều nhóm dùng đặt ở `core/`. Spec không có code server; Rules của `venues`, `reviews` thuộc D và C, 030 chỉ viết test kiểm chứng.

## Phối hợp với nhóm

| Ai | Việc | Hạn đề xuất | Nếu chưa xong |
|---|---|---|---|
| **B** (040) | Thêm `date`, `startTime`, `endTime` (nullable) vào `bookingSlotGridFragment`; `create-booking` từ chối cơ sở không `ACTIVE`; test nạp `window-price-vectors.json` | Trước khi 030 làm US1 | Chỉ truyền `venueId`, `TODO(B)` |
| **B + D** | Chốt `daysOfWeek` (đề xuất ISO), ghi vào đặc tả CSDL §4.3 | **Điểm chặn**, họp tuần này | 030 giữ một hằng số trong `DayConvention` |
| **C** (070) | `ReviewRepository.latestVisible`; dùng `core/model/VenueRatingSummary` và `core/ui/photo/PhotoViewerFragment`; Rules `reviews` cho khách đọc `VISIBLE`; ánh xạ `DELETED` thành "không còn tồn tại" | Trước US4 | 030 truy vấn tạm, `TODO(C)` |
| **C** (091) | Deep link `smashnow://chat/direct?venueId=` | Trước US5 | Nút chat báo "Tính năng sắp có" |
| **D** (111) | Rules `venues` theo R10; thêm `ratingSums`, `"2400"`, thứ tự `photoPaths`, `phone` công khai, `DELETED` vào §4.3 | Trước khi merge | Test VD-* đỏ, ghi trong PR |
| **A** (020) | `core/pricing`, `core/schedule`, `core/catalog`, `core/location` trong PR `chore(core)` phần 2 | Trước 030 | 030 viết theo contract 020 |
| **D** (111) | Chủ sân tạo cơ sở qua giao diện để kiểm 030 với dữ liệu thật của app (T077, quyết định C1) | Sau 111 | Demo bằng seed, ghi nguồn |

## Complexity Tracking

| Thêm vào | Vì sao cần | Phương án đơn giản hơn bị loại vì |
|---|---|---|
| Thư viện **osmdroid** | Bản đồ nhúng có marker (FR-010); constitution ghi đây là lựa chọn khi không có tài khoản thanh toán. Phải hiện "© OpenStreetMap contributors" (R7) | Google Maps SDK / Static Maps cần tài khoản thanh toán; chỉ hiện địa chỉ không đạt FR-010 |
| `ZoomableImageView` tự viết (~150 dòng, `core/ui/photo`) | Phóng to ảnh ở chế độ toàn màn hình (FR-004) | Thư viện cho View đều ngừng bảo trì (PhotoView, subsampling-scale-image-view); thư viện còn bảo trì chỉ có cho Compose |
| 3 thành phần `core/` mới | 020, 021, 070, 111 cùng cần giải URL ảnh, tính điểm tiêu chí, xem ảnh | Để trong `feature/courtdetail` thì nhóm khác phải import chéo (vi phạm constitution I) hoặc viết lại |
| Dùng seed Alobo (tên, địa chỉ, ảnh sân thật) làm dữ liệu demo, **ngoại lệ constitution VI** | Nhóm cần dữ liệu trông thật để demo đồ án; chủ sân thật chưa có (quyết định C1, R13) | Tự nghĩ 10 cơ sở giả và chụp ảnh tốn thời gian trước buổi demo. Giới hạn: chỉ demo/học tập, ghi nguồn trên ảnh chụp; cơ sở thật tạo qua 111 (T077); nếu giữ tới báo cáo cuối kỳ thì sửa constitution |
| `scripts/seed/seed-reviews-030.mjs` (có điều kiện) | US4 và SC-006 cần đánh giá để kiểm thử; seed hiện chưa có | Chỉ làm khi seed đánh giá của 070 chưa có; không sửa `seed.mjs` của Duy |
