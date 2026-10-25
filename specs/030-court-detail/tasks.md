---

description: "Danh sách task triển khai spec 030 – Chi tiết sân"
---

# Tasks: Chi tiết sân

**Input**: tài liệu thiết kế trong `specs/030-court-detail/`: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/), [quickstart.md](quickstart.md)

**Tests**: Có. Constitution nguyên tắc IV bắt buộc test cho logic tính tiền, ViewModel, UseCase, Repository và Security Rules. Cụ thể:
- hàm thuần (`SearchWindowArgs`, `OpeningHours`, `CourtSummary`, `PriceTableBuilder`, `PhoneDisplay`, `PendingActionResolver`, `VenueRatingSummary`, `PhotoUrlResolver`, `ZoomMath`);
- giá khung giờ qua **file test vector dùng chung** với 020, 040;
- ViewModel bằng Turbine với repository giả;
- Rules VD-1 → VD-10 trên Firebase Emulator.

Viết test trước, chạy thấy **fail**, rồi mới cài đặt.

**Organization**: nhóm theo user story (US1…US6) để mỗi story làm và kiểm thử độc lập được. Phase 2 tách 3 phần: 2A là PR `chore(core)` dùng chung cả nhóm, 2B là nền của `feature/courtdetail`, 2C là test Rules và dữ liệu.

**Spec đã sửa trước khi chia task (2026-10-25):** US5-7 thành "chủ sân ở chế độ người chơi xem cơ sở của mình" ([research R11](research.md#r11-khách-chưa-đăng-nhập-và-thao-tác-đang-chờ-fr-018-fr-020-sc-004)); FR-005 thêm trạng thái rỗng "Chưa có thông tin tiện ích" ([R13](research.md#r13-dữ-liệu-seed-của-nhóm-scriptsseed-và-constitution-vi)).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: làm song song được (file khác nhau, không phụ thuộc task chưa xong)
- **[Story]**: user story của spec (US1…US6)

## Quy ước đường dẫn

| Viết tắt | Đường dẫn thật |
|---|---|
| `APP/` | `app/src/main/java/com/example/booking_app/` |
| `CORE/` | `app/src/main/java/com/example/booking_app/core/` |
| `CD/` | `app/src/main/java/com/example/booking_app/feature/courtdetail/` |
| `RES/` | `app/src/main/res/` |
| `TEST/` | `app/src/test/java/com/example/booking_app/` |
| `TRES/` | `app/src/test/resources/` |
| `ATEST/` | `app/src/androidTest/java/com/example/booking_app/` |
| `FT/` | `firestore-tests/test/` |

---

## Phase 1: Setup (thư viện, tài nguyên, cấu hình)

**Purpose**: thư viện và tài nguyên chung của spec 030.

- [ ] T001 Kiểm tra nhánh làm việc đã có PR `chore(core)` của 010. **Đã có trên `main` (PR #35, #39):** `core/session`, `core/network`, `core/util/AppClock`, `core/ui/LoginPromptView`, `AuthNavigator`, `nav_player`. Kiểm tra PR `chore(core)` phần 2 của 020 ([tasks 020](../020-court-search/tasks.md) T003–T017: `core/pricing`, `core/schedule`, `core/catalog`, `core/location`):
  - **ngày 2026-10-25 chưa có** trong `CORE/`, nên các task T012–T015 bên dưới phải làm;
  - nếu lúc làm 020 đã merge thì đánh dấu T012–T015 xong và chỉ thêm ca `"2400"` (T007) và `DayConvention`.
- [ ] T002 Thêm thư viện vào `gradle/libs.versions.toml` và `app/build.gradle.kts`, mỗi dòng ghi lý do trong PR:
  - **Coil**: `io.coil-kt.coil3:coil` + `coil-network-okhttp`. Ngày 2026-10-25 **chưa có** trong catalog; nếu PR của 020/070 đã thêm thì dùng lại, không thêm trùng;
  - **osmdroid**: `org.osmdroid:osmdroid-android` (bản ổn định mới nhất), lý do theo [plan Complexity Tracking](plan.md#complexity-tracking): bản đồ nhúng khi không có tài khoản thanh toán Google Maps;
  - **ViewPager2**: `androidx.viewpager2:viewpager2`. Hiện chỉ có gián tiếp qua Material 1.14.0, nên khai báo trực tiếp vì code dùng thẳng;
  - chạy `./gradlew assembleDebug` qua với AGP 9.4. osmdroid không chạy được thì dừng lại báo nhóm, không tự đổi thư viện.
- [ ] T003 [P] Tạo `RES/values/strings_court_detail.xml`: mọi chuỗi tiền tố `court_detail_` theo [contracts/ui-screens.md](contracts/ui-screens.md), gồm:
  - trạng thái mở cửa: "Đang mở cửa", "Đã đóng cửa · Mở lại %1$s", "Mở cả ngày";
  - sân con: "%1$d sân · %2$s", "Tạm thời chưa có sân hoạt động";
  - tiện ích và bảng giá: "Chưa có thông tin tiện ích", "Khung bạn chọn", "Liên hệ", "Liên hệ chủ sân", "Giờ thường", "Giờ cao điểm", "Cuối tuần";
  - đánh giá: "Chưa có đánh giá", "Sân chưa có đánh giá", "Xem tất cả (%1$d)", "Người dùng đã xóa", "Phản hồi của chủ sân", ba tiêu chí "Chất lượng sân", "Vệ sinh", "Phục vụ";
  - trạng thái cơ sở: "Sân tạm ngừng hoạt động", "Sân không còn tồn tại", "Bỏ khỏi yêu thích", "Đây là cơ sở của bạn";
  - mời đăng nhập: "Đăng nhập để đặt sân", "Đăng nhập để lưu sân yêu thích", "Đăng nhập để nhắn tin cho chủ sân", "Để sau";
  - liên hệ, chỉ đường, mạng: "Chỉ đường", "Bấm để gọi", "Đã sao chép số điện thoại", "Không có kết nối, thông tin có thể chưa cập nhật", "Tính năng sắp có";
  - ghi nguồn bản đồ: "© OpenStreetMap contributors";
  - plural "%1$d đánh giá" và các `contentDescription` ở [ui-screens §2](contracts/ui-screens.md#2-các-mục-theo-wireframe-từ-trên-xuống).
- [ ] T004 [P] Tạo drawable vector:
  - `RES/drawable/ic_marker_shuttlecock.xml` (marker quả cầu lông, dùng chung với 021);
  - `RES/drawable/img_venue_placeholder.xml` (ảnh mặc định 16:9);
  - `RES/drawable/ic_amenity_parking.xml`, `ic_amenity_drinks.xml`, `ic_amenity_shower.xml`, `ic_amenity_racket_rental.xml`, `ic_amenity_default.xml`. Tên theo `iconKey` trong `scripts/seed/catalog.json`, dấu `-` đổi thành `_`; `iconKey` lạ dùng `ic_amenity_default`.

  Màu lấy từ theme attribute, có bản tối.
- [ ] T005 [P] Trong `app/src/main/AndroidManifest.xml`:
  - thêm `<nav-graph android:value="@navigation/nav_main" />` vào `MainActivity` để deep link `smashnow://venue/...` mở được từ ngoài app (thông báo, `adb`). Ngày 2026-10-25 manifest chưa có, và deep link `smashnow://auth/...` của 010 chỉ dùng trong app. Lưu ý: sau khi thêm, `smashnow://auth/...` cũng mở được từ ngoài app; kiểm tra `nav_auth` chỉ nhận `returnTo`, `email`, `lockedNotice` và `returnTo` chỉ điều hướng tới URI `smashnow://`;
  - khai báo `ACCESS_NETWORK_STATE` cho osmdroid nếu manifest đã gộp chưa có (`INTERNET` đã có qua Firebase).

**Checkpoint**: `./gradlew assembleDebug` qua.

---

## Phase 2: Foundational (chặn mọi user story)

**⚠️ CRITICAL**: chưa xong phase này thì chưa làm user story nào.

### 2A. PR `chore(core)` phần 3: phần dùng chung cả nhóm ([research R4, R5, R8, R14](research.md))

**Tests (viết trước, phải fail)**

- [ ] T006 [P] Tạo `TRES/pricing/window-price-vectors.json` với 9 ca P1–P9 của [quickstart §3](quickstart.md#3-ca-giá-dùng-chung-sc-002).
  - Mỗi ca có: `id`, `rules` (copy từ `scripts/seed/venues.json`, đổi `days` sang `daysOfWeek` **theo ISO** như `seed.mjs`), `date` (`yyyyMMdd`), `start`, `end`, `expectedTotal`, `expectedPerHour` (`null` cho P3) và `highlightedRuleIndexes`.
  - Ghi trường `dayConvention: "ISO"` ở đầu file. Nhóm chốt khác thì sửa file này cùng `DayConvention` (T013).
- [ ] T007 [P] Viết `TEST/core/schedule/TimeOfDayTest.kt` (nếu 020 T005 đã có thì chỉ thêm ca):
  - `"2400"` hợp lệ khi phân tích **giờ kết thúc** (= 1440 phút), không hợp lệ ở **giờ bắt đầu**;
  - `"0000"` → 0; định dạng in `"24:00"`;
  - khung `[2300, 2400)` liệt kê đúng 2 khung 30 phút.
- [ ] T008 [P] Viết `TEST/core/pricing/SlotPricingVectorTest.kt` (bổ sung cho 020 T006):
  - đọc `TRES/pricing/window-price-vectors.json` bằng `javaClass.classLoader.getResource`;
  - với mỗi ca, `SlotPricing.priceForWindow` khớp `expectedTotal` và `SlotPricing.perHour` khớp `expectedPerHour`;
  - P3 (khung có phần trước 05:00) → `null`.

  Viết thêm `TEST/core/pricing/DayConventionTest.kt`: ISO `1 → MONDAY … 7 → SUNDAY`; giá trị ngoài 1–7 → ném lỗi.
- [ ] T009 [P] Viết `TEST/core/media/PhotoUrlResolverTest.kt`:
  - `https://m-files.alobo.vn/...` và `http://...` → giữ nguyên;
  - `demo-k3/cover.jpg` → `"{SUPABASE_URL}/storage/v1/object/public/venue-photos/demo-k3/cover.jpg"` (`SUPABASE_URL` truyền vào constructor, không đọc `BuildConfig` trong test);
  - path rỗng hoặc chỉ khoảng trắng → `null`;
  - path có `/` ở đầu → không bị hai dấu `/`.
- [ ] T010 [P] Viết `TEST/core/model/VenueRatingSummaryTest.kt`:
  - `fromVenue(ratingAvg = 4.6, ratingCount = 128, ratingSums = {court: 602, cleanliness: 563, service: 589, overall10: …})` → `courtAvg = 4.7`, `cleanlinessAvg = 4.4`, `serviceAvg = 4.6` (làm tròn 1 chữ số thập phân, half-up);
  - `ratingCount == 0` → `count = 0`, các tiêu chí `null`;
  - thiếu `ratingSums` hoặc thiếu một khóa → tiêu chí đó `null`, `average` vẫn lấy `ratingAvg`.
- [ ] T011 [P] Viết `TEST/core/ui/photo/ZoomMathTest.kt` cho hàm thuần `ZoomMath`:
  - tỉ lệ kẹp trong `[1f, 4f]`;
  - phóng quanh tiêu điểm giữ đúng điểm dưới ngón tay;
  - kéo khi ảnh lớn hơn khung bị kẹp không lộ nền;
  - ở tỉ lệ 1× thì `canPanHorizontally == false` để `ViewPager2` chuyển ảnh;
  - chạm hai lần: 1× → 2.5× → 1×.

**Implementation**

- [ ] T012 [P] **Chỉ khi 020 T011 chưa merge:** cài `CORE/schedule/TimeOfDay.kt`, `SlotMath.kt`, `BookingWindow.kt` (`ADVANCE_DAYS = 30`, `ZONE = ZoneId.of("Asia/Ho_Chi_Minh")`), `SlotId.kt` đúng [020 research R5](../020-court-search/research.md#r5-logic-giá-và-lưới-giờ-dùng-chung-với-spec-040-b), cùng test 020 T005. Đã có thì chỉ thêm hàm `TimeOfDay.parseEnd(hhmm)` chấp nhận `"2400"` và `format()` ra `"24:00"`. Cho T007 xanh
- [ ] T013 **Chỉ khi 020 T012 chưa merge:** cài `CORE/pricing/PriceRule.kt`:
  - trường `id`, `daysOfWeek: List<Int>`, `startTime`, `endTime`, `pricePerHour: Long`, `label`; `pricePerSlot = pricePerHour / 2`;
  - **không dùng `Double` cho tiền**.

  Cài `CORE/pricing/SlotPricing.kt` (`priceForWindow(rules, date, start, end): Long?`, `perHour(total, start, end): Long`). Luôn thêm:
  - `CORE/pricing/DayConvention.kt` (`const val CURRENT = ISO`, `toDayOfWeek(Int)`, `fromDayOfWeek(DayOfWeek)`): **chỗ duy nhất** đổi số `daysOfWeek` ([R5](research.md#r5-giờ-2400-và-quy-ước-daysofweek-dữ-liệu-seed));
  - `CORE/pricing/PriceRuleMapper.kt` (`fromFirestore(id, Map<String, Any?>): PriceRule?`, bỏ qua bản ghi thiếu trường), để 020, 030, 040 không mỗi người một DTO.

  Cho T008 xanh
- [ ] T014 [P] **Chỉ khi 020 T013–T015 chưa merge:** cài theo đúng 020:
  - `CORE/location/GeoDistance.kt`, `DistanceFormatter.kt`;
  - `LocationProvider.kt` (`hasPermission()`, `suspend fun current(): GeoPoint?`), `AndroidLocationProvider.kt`;
  - `CORE/catalog/District.kt`, `Amenity.kt`, `CatalogRepository.kt`, `CatalogDataSource.kt`, `FirestoreCatalogRepository.kt` (cache trong bộ nhớ, `awaitWithTimeout`).

  Kèm test 020 T007, T008
- [ ] T015 **Chỉ khi 020 T016 chưa merge:** tạo `CORE/di/SearchCoreModule.kt` bind `LocationProvider`, `CatalogRepository`
- [ ] T016 [P] Cài `CORE/media/PhotoUrlResolver.kt` (`@Singleton`, nhận `supabaseUrl` từ `BuildConfig.SUPABASE_URL` qua Hilt; `venuePhoto(path: String): String?`) và bind trong `CORE/di/MediaModule.kt`. Cho T009 xanh. **Báo 020, 070**: thẻ kết quả và mục yêu thích dùng hàm này
- [ ] T017 [P] Cài `CORE/model/VenueRatingSummary.kt` (`average: Double`, `count: Int`, `courtAvg`, `cleanlinessAvg`, `serviceAvg: Double?`; `companion fun fromVenue(ratingAvg: Double?, ratingCount: Long?, ratingSums: Map<String, Long>?)`). Cho T010 xanh. **Báo C**: `ReviewRepository.observeSummary` của 070 dùng hàm này
- [ ] T018 Cài `CORE/ui/photo/ZoomMath.kt` (thuần) và `CORE/ui/photo/ZoomableImageView.kt`:
  - `AppCompatImageView` + `ScaleGestureDetector` + `GestureDetector`, `scaleType = MATRIX`;
  - khi tỉ lệ > 1× thì gọi `parent.requestDisallowInterceptTouchEvent(true)`;
  - `contentDescription` truyền từ ngoài.

  Cho T011 xanh
- [ ] T019 Cài `CORE/ui/photo/PhotoViewerFragment.kt` + `PhotoPagerAdapter.kt` + `RES/layout/fragment_photo_viewer.xml` + `RES/layout/item_photo_viewer.xml`:
  - Safe Args `photoUrls: string[]`, `startIndex: integer`;
  - `ViewPager2` + `ZoomableImageView`, tải bằng Coil, lỗi → `img_venue_placeholder`;
  - chỉ báo "2/5", nút đóng 48dp, nền tối ở cả hai chế độ lấy từ `RES/values/colors.xml` (`photo_viewer_background`), không hardcode;
  - khai báo destination và `action_global_photoViewer` trong `RES/navigation/nav_player.xml`.

  **Báo C**: 070 T046 dùng màn hình này thay vì viết `PhotoViewerFragment` riêng

**Checkpoint 2A**: T007–T011 xanh. Mở PR `chore(core)` phần 3, nhờ B (dùng `core/pricing`, file vector) và C (dùng `core/media`, `core/model`, `core/ui/photo`) review.

### 2B. Nền của `feature/courtdetail` ([data-model](data-model.md), [contracts/navigation.md](contracts/navigation.md))

**Tests (viết trước, phải fail)**

- [ ] T020 [P] Viết `TEST/feature/courtdetail/data/VenueDetailMapperTest.kt` cho hàm thuần `VenueDetailMapper.map(snapshotData, exists, isFromCache, catalog, resolver)`:
  - `status` `ACTIVE` → `VenueAvailability.ACTIVE`; `HIDDEN`, `LOCKED` → `SUSPENDED`; `DELETED` hoặc `exists == false` → `GONE`;
  - `photoPaths` giữ thứ tự, path rỗng bị bỏ, URL đầy đủ giữ nguyên;
  - `amenityIds` không có trong danh mục bị bỏ (FR-005);
  - `location` thiếu hoặc `0,0` → `null`;
  - `ratingSums` thiếu → tiêu chí `null`;
  - `isFromCache` truyền ra `VenueDetail.isFromCache`.

  Viết thêm `TEST/feature/courtdetail/data/VenueErrorMapperTest.kt`: `FirebaseFirestoreException.Code.PERMISSION_DENIED` → `VenueResult.PermissionDenied` (tầng data **không** biết khách hay đã đăng nhập); `UNAVAILABLE` → `AppError.Network`.

**Implementation**

- [ ] T021 [P] Tạo model trong `CD/domain/model/` đúng [data-model §2](data-model.md#2-mô-hình-phía-app):
  - `CourtDetailArgs`, `SearchWindow`;
  - `VenueDetail`, `VenueAvailability` (`ACTIVE`, `SUSPENDED`, `GONE`, `SUSPENDED_GUEST`), `OpeningStatus` (`AllDay`, `Open(closesAt)`, `Closed(reopensAt)`);
  - `CourtSummary`, `SurfaceType` (`WOOD`, `PU`, `MAT`, `OTHER`), `PhoneDisplay` (`Dialable(e164, display)`, `DisplayOnly(raw)`);
  - `PriceDayGroup`, `DayGroupTitle` (`Single`, `Pair`, `Range`), `PriceRow`, `PriceLabel` (`NORMAL`, `PEAK`, `WEEKEND`; giá trị lạ → `NORMAL`), `SelectedWindowPrice`;
  - `PendingAction` (`BOOK`, `FAVORITE`, `CHAT`), `PendingDecision` (`Run`, `RejectOwnVenue`, `Cancel`, `Wait`), `ReviewPreview`.
- [ ] T022 [P] Tạo `CD/domain/VenueDetailRepository.kt` (interface):
  - `observeVenue(venueId): Flow<VenueResult>`, với `VenueResult` = `Found(VenueDetail)` | `Gone` | `PermissionDenied` | `Failed(AppError)`;
  - `suspend fun loadCourts(venueId): List<Court>`, `loadPriceRules(venueId): List<PriceRule>`, `fetchStatusFromServer(venueId): VenueAvailability?` (`null` khi mất mạng).

  Tạo bản giả `TEST/feature/courtdetail/FakeVenueDetailRepository.kt` (đẩy giá trị bằng `MutableSharedFlow`, đếm số lần gọi để kiểm tra "đọc lại một mục")
- [ ] T023 Cài `CD/data/FirestoreVenueDetailRepository.kt`, `CD/data/VenueDetailMapper.kt`, `CD/data/VenueErrorMapper.kt`, `CD/data/dto/CourtDto.kt`, và `CD/di/CourtDetailModule.kt`:
  - `observeVenue` là `callbackFlow` trên `venues/{id}` với `MetadataChanges.INCLUDE`, dừng khi không còn người thu thập ([R2](research.md#r2-đọc-dữ-liệu-thứ-tự-tải-và-số-lượt-đọc-fr-024-fr-025-sc-001));
  - `loadCourts`, `loadPriceRules` dùng `get()` + `awaitWithTimeout`; `priceRules` qua `PriceRuleMapper`;
  - `fetchStatusFromServer` dùng `get(Source.SERVER)`.

  Cho T020 xanh
- [ ] T076 [P] *(thêm sau `/speckit-analyze` C2, đặt ID nối tiếp để không đánh số lại; làm ngay sau T023)* Test Repository trên Firestore Emulator `ATEST/feature/courtdetail/data/FirestoreVenueDetailRepositoryTest.kt` (constitution IV bắt buộc test Repository). Nạp dữ liệu bằng Admin SDK của Emulator hoặc Firestore client với Rules tắt, không dùng project thật:
  - `observeVenue`: phát `Found` với đúng trường; sửa `status` thành `LOCKED` thì phát lại `Found` với `SUSPENDED`; xóa document thì phát `Gone`; hủy người thu thập thì listener bị gỡ (không còn phát);
  - `loadCourts`, `loadPriceRules`: đọc đủ bản ghi, `priceRules` qua `PriceRuleMapper`, `endTime = "2400"` được giữ;
  - `fetchStatusFromServer`: trả đúng trạng thái; tắt mạng (`disableNetwork()`) thì trả `null`;
  - khách đọc cơ sở `HIDDEN` khi Rules của D đã có → `PermissionDenied` (ghi `[BLOCKED: D]` nếu Rules chưa có);
  - `FirestoreLatestReviewsSource` (T055, bản tạm): tối đa 3 đánh giá `VISIBLE`, mới nhất trước, không có đánh giá `HIDDEN`.

  Test tự bỏ qua khi `BuildConfig.USE_EMULATOR != "true"` như Espresso của 010
- [ ] T024 Tạo `RES/navigation/nav_court_detail.xml` đúng [contracts/navigation.md §1.1](contracts/navigation.md#11-nav-graph-và-tham-số):
  - `courtDetailFragment` với `venueId`, `date?`, `startTime?`, `endTime?` và deep link `smashnow://venue/{venueId}?date={date}&start={startTime}&end={endTime}`;
  - `include` vào `RES/navigation/nav_player.xml`;
  - tạo khung `CD/ui/CourtDetailFragment.kt` (`@AndroidEntryPoint`, ViewBinding) + `RES/layout/fragment_court_detail.xml` rỗng;
  - kiểm tra bằng `adb shell am start -d "smashnow://venue/demo-k3"`.

### 2C. Test Rules và dữ liệu demo

- [ ] T025 [P] Viết `FT/court-detail.test.ts` đủ **VD-1 → VD-10** của [contracts/security-rules.md §2](contracts/security-rules.md#2-bảng-test-firestore-teststestcourt-detailtestts). Dữ liệu `v-active`, `v-hidden`, `v-locked`, `v-deleted`, không có `v-missing`, kèm `courts`, `priceRules`, 2 `reviews`. Trong đó **VD-10** (khách `get venues/v-deleted` → ✅ để app hiện "Sân không còn tồn tại", FR-022) được thêm sau `/speckit-analyze` I1.
  - **Không sửa `firestore.rules`**: `venues` thuộc D, `reviews` thuộc C.
  - Chạy `npm test` trong `firestore-tests/` và ghi kết quả vào PR. Các test VD-1 → VD-4, VD-6, VD-7, VD-10 **sẽ đỏ cho tới khi D và C sửa Rules** theo contract; ghi rõ "[BLOCKED: D]" / "[BLOCKED: C]" trong tên test, không chuyển thành `it.todo`.
  - Test `users` của 010 và test nền vẫn phải xanh.
- [ ] T026 [P] **Có điều kiện, chỉ khi seed đánh giá của 070 (T025 của 070) chưa có:** tạo `scripts/seed/seed-reviews-030.mjs`:
  - chỉ chạy với `--emulator`, thiếu cờ thì thoát kèm thông báo;
  - ghi 12 `reviews` cho `demo-k3` theo [070 data-model](../070-review-favorite/data-model.md): 1 `HIDDEN`, 1 `authorDeleted = true` (`userId = null`, `userSnapshot = { displayName: null, avatarPath: null }`), 1 có `ownerReply`, 2 có `photoPaths`; `createdAt` cách nhau 1 ngày;
  - cập nhật `venues/demo-k3` với `ratingAvg`, `ratingCount = 11`, `ratingSums` tính từ 11 đánh giá `VISIBLE`;
  - ID tất định nên chạy lại không trùng; **không sửa `scripts/seed/seed.mjs`** của Duy.

  Ghi cách chạy vào `scripts/seed/README.md` (một mục mới, không sửa phần của Duy)

**Checkpoint**: nền xong, các user story bắt đầu được.

---

## Phase 3: User Story 1 – Xem thông tin cơ sở và bấm "Đặt sân" (Priority: P1) 🎯 MVP

**Goal**: người chơi mở chi tiết từ tìm kiếm, thấy ảnh, tên, địa chỉ, điểm, sân con, tiện ích, giờ mở cửa; nút "Đặt sân" luôn ở cuối, bấm thì mở lưới giờ đúng cơ sở, mang theo ngày và khung giờ.

**Independent Test**: mở `smashnow://venue/demo-truong-chinh-909?date=20261027&start=1900&end=2100` khi đã đăng nhập. Thấy "10 sân · PU", "Đang mở cửa · 05:00–24:00" (lúc 20:00), mục tiện ích hiện "Chưa có thông tin tiện ích"; bấm "Đặt sân" mở lưới giờ của đúng cơ sở (quickstart Q1, Q2, Q3, Q6, Q7).

### Tests cho User Story 1 ⚠️ viết trước, phải fail

- [ ] T027 [P] [US1] Unit test `TEST/feature/courtdetail/domain/SearchWindowArgsTest.kt`:
  - đủ 3 tham số hợp lệ → `SearchWindow`;
  - thiếu một tham số, sai định dạng, `start >= end`, không phải mốc `:00`/`:30` → `null`;
  - `end = "2400"` hợp lệ;
  - khung đã qua so với `now` (`date + start <= now` theo `Asia/Ho_Chi_Minh`) → `null`.
- [ ] T028 [P] [US1] Unit test `TEST/feature/courtdetail/domain/OpeningHoursTest.kt` với `AppClock` giả:
  - `0500`–`2300`: 20:00 → `Open(23:00)`, 23:30 → `Closed(reopensAt = 05:00)` (US1-4);
  - `0500`–`0100` (qua nửa đêm): 00:30 → `Open`, 02:00 → `Closed(05:00)`;
  - `0500`–`2400`: 00:30 → `Closed(05:00)`, 23:59 → `Open`;
  - `0000`–`2400` hoặc `open == close` → `AllDay`;
  - tính theo `Asia/Ho_Chi_Minh` kể cả khi `AppClock` trả `Instant` UTC.
- [ ] T029 [P] [US1] Unit test `TEST/feature/courtdetail/domain/CourtSummaryTest.kt`:
  - 3 `MAT` hoạt động + 1 `MAT` tạm ngưng → `activeCount = 3`, `[MAT to 3]` (US1-3);
  - 2 `WOOD` + 1 `MAT` → sắp theo số lượng giảm dần `[WOOD to 2, MAT to 1]`;
  - 0 sân hoạt động → `activeCount = 0`;
  - `surfaceType` lạ → `OTHER`.
- [ ] T030 [P] [US1] Unit test `TEST/feature/courtdetail/domain/usecase/CheckVenueBookableUseCaseTest.kt`:
  - server trả `ACTIVE` → `Bookable`;
  - `SUSPENDED`/`GONE` → `NotBookable(availability)`;
  - `fetchStatusFromServer` trả `null` (mất mạng) → dùng trạng thái mới nhất từ listener;
  - hết 15 s → coi như mất mạng.
- [ ] T031 [P] [US1] Unit test `TEST/feature/courtdetail/ui/CourtDetailViewModelUs1Test.kt` (Turbine, `FakeVenueDetailRepository`, `SavedStateHandle` có tham số):
  - lần đầu `availability == null`, `header = Loading`; venue tới → `header = Content` mà **không chờ** `courts`/`priceRules`;
  - `courts` lỗi → chỉ `courts = Error`, `header` vẫn `Content`; `onRetry(COURTS)` chỉ gọi lại `loadCourts`;
  - `amenityIds = []` → `amenities = Empty`;
  - `isFromCache = true` → `isOffline = true`;
  - đã đăng nhập, bấm "Đặt sân" → `CheckVenueBookable` rồi effect `OpenBookingGrid(venueId, window)`; khung đã qua → `window = null`;
  - `onPhotoPageChanged(2)` lưu `photoIndex` vào `SavedStateHandle`;
  - trạng thái mở cửa cập nhật khi `AppClock` giả qua 23:00;
  - khung giờ từ tìm kiếm hết hạn khi màn hình đang mở (`AppClock` giả qua giờ bắt đầu của khung) → `window = null`, bấm "Đặt sân" không mang khung giờ (Edge case, sửa sau `/speckit-analyze` U3);
  - không còn người thu thập `uiState` quá 5 giây → listener venue của repository giả bị hủy (U1).

### Implementation cho User Story 1

- [ ] T032 [P] [US1] Cài `CD/domain/SearchWindowArgs.kt`. Cho T027 xanh
- [ ] T033 [P] [US1] Cài `CD/domain/OpeningHours.kt` (`status(openTime, closeTime, now: ZonedDateTime): OpeningStatus`; dùng `TimeOfDay.parseEnd` cho `closeTime`) và `CD/domain/OpeningTicker.kt` (`Flow<ZonedDateTime>` phát mỗi phút từ `AppClock`). Cho T028 xanh
- [ ] T034 [P] [US1] Cài `CD/domain/CourtSummary.kt` (`from(courts)`). Cho T029 xanh
- [ ] T035 [US1] Cài trong `CD/domain/usecase/`:
  - `ObserveVenueDetailUseCase.kt`: gộp `observeVenue` + `CatalogRepository`; **chỉ ở đây** đổi `PermissionDenied` thành `SUSPENDED_GUEST` khi `SessionManager.isGuest == true`, ngược lại thành `Failed(AppError.Unknown)`;
  - `LoadCourtSummaryUseCase.kt`;
  - `CheckVenueBookableUseCase.kt`.

  Cho T030 xanh
- [ ] T036 [US1] Cài `CD/ui/CourtDetailUiState.kt` (đúng [data-model §2.5](data-model.md#25-trạng-thái-màn-hình), `Section<T> = Loading | Content | Empty | Error(AppError)`, `CourtDetailEffect`) và `CD/ui/CourtDetailViewModel.kt`:
  - phần US1: header, courts, amenities, `isOffline`, `bottomBar`;
  - các hàm `onRetry`, `onBookClicked`, `onPhotoClicked`, `onPhotoPageChanged`;
  - ba luồng venue/courts/priceRules chạy song song bằng `async`;
  - `uiState` dùng `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), …)` để listener venue bị gỡ khi Fragment `STOPPED` ([R2](research.md#r2-đọc-dữ-liệu-thứ-tự-tải-và-số-lượt-đọc-fr-024-fr-025-sc-001));
  - khung giờ đưa vào `OpeningTicker`: qua giờ bắt đầu thì đặt `window = null`.

  Cho T031 xanh
- [ ] T037 [US1] Viết `RES/layout/fragment_court_detail.xml` theo wireframe `03_court-detail_buoc1_chi-tiet-san.png`:
  - `CoordinatorLayout` + `NestedScrollView` (có `id` để tự lưu vị trí cuộn);
  - mục 1 là `ViewPager2` 16:9 kèm chỉ báo "1/5", nút quay lại và trái tim 48dp chồng lên;
  - mục 2 là tên, địa chỉ · quận, điểm, chip trạng thái mở cửa;
  - mục 3 là ba thẻ nhanh; mục 4 là `ChipGroup` tiện ích kèm `TextView` rỗng;
  - chỗ chừa cho mục 5–7 (bảng giá, `ViewStub` bản đồ, đánh giá);
  - mục 8 là thanh dưới cố định (giá, nút chat, nút "Đặt sân");
  - banner offline; mỗi mục có `ProgressBar`/khung chờ và dòng lỗi kèm "Thử lại".

  Kèm `RES/layout/item_court_detail_photo.xml`; mọi màu và kích thước lấy từ resource
- [ ] T038 [US1] Cài `CD/ui/CourtDetailFragment.kt` + `CD/ui/PhotoGalleryAdapter.kt` (`ListAdapter`, Coil, lỗi → `img_venue_placeholder`, `contentDescription` "Ảnh %1$d trên %2$d của %3$s"):
  - thu thập state bằng `repeatOnLifecycle(STARTED)`;
  - chạm ảnh → `action_global_photoViewer`;
  - chip tiện ích dùng `ic_amenity_{iconKey}`;
  - thẻ sân con hiện "N sân · 2 Gỗ, 1 Thảm" hoặc "Tạm thời chưa có sân hoạt động".
- [ ] T039 [US1] Thêm action `action_courtDetail_to_bookingSlotGrid` vào `RES/navigation/nav_court_detail.xml` theo [040 ui-contracts §1.1](../040-booking-slot-grid/contracts/ui-contracts.md):
  - **phụ thuộc B**: truyền `venueId`, và `date`/`startTime`/`endTime` khi `bookingSlotGridFragment` đã có ba tham số này;
  - B chưa thêm thì chỉ truyền `venueId` và ghi `// TODO(B): truyền date/startTime/endTime khi 040 có tham số (research R1)`;
  - 040 chưa có màn hình thì action trỏ tạm `PlayerHomePlaceholderFragment`, kèm `TODO(B)` và snackbar "Tính năng sắp có".

**Checkpoint**: US1 chạy độc lập; quickstart Q1, Q2, Q3, Q6, Q7 đạt.

---

## Phase 4: User Story 2 – Xem bảng giá theo khung giờ (Priority: P1) 🎯 MVP

**Goal**: bảng giá theo nhóm ngày, giá đ/giờ, làm nổi bật dòng của khung đã chọn và hiện "Khung bạn chọn" bằng đúng giá của 020/040.

**Independent Test**: `demo-truong-chinh-909` với T3 17:00–19:00 → "Khung bạn chọn · 110.000 đ/giờ", tô cả hai dòng 16:00–18:00 và 18:00–24:00; `demo-quang-vinh` không qua tìm kiếm → 3 nhóm, không dòng nào được tô (quickstart Q4, Q5, ca P1–P9).

### Tests cho User Story 2 ⚠️ viết trước, phải fail

- [ ] T040 [P] [US2] Unit test `TEST/feature/courtdetail/domain/PriceTableBuilderTest.kt` (dữ liệu lấy từ `scripts/seed/venues.json`, `daysOfWeek` ISO):
  - `demo-quang-vinh` → **3 nhóm** "Thứ 2" / "Thứ 3 – Thứ 6" / "Thứ 7, Chủ nhật";
  - `demo-son-ta` → "Thứ 2 – Thứ 6" / "Thứ 7" / "Chủ nhật";
  - `demo-truong-chinh-909` → 2 nhóm;
  - mỗi ngày nằm trong đúng một nhóm; dòng sắp theo `startTime`;
  - `endTime = "2400"` in "24:00";
  - có `SearchWindow` T3 17:00–19:00 → `highlighted` đúng hai dòng (US2-3); không có `SearchWindow` → không dòng nào (US2-5);
  - không có rule → danh sách rỗng.
- [ ] T041 [P] [US2] Unit test `TEST/feature/courtdetail/domain/usecase/LoadPriceTableUseCaseTest.kt`:
  - đọc `TRES/pricing/window-price-vectors.json`, với mỗi ca P1–P9 `SelectedWindowPrice.perHour == expectedPerHour` và dòng tô khớp `highlightedRuleIndexes` (SC-002);
  - P3 → `perHour = null` ("Liên hệ");
  - `window == null` → không có `SelectedWindowPrice`.
- [ ] T042 [P] [US2] Unit test `TEST/feature/courtdetail/ui/CourtDetailViewModelUs2Test.kt`:
  - `prices` lỗi → chỉ mục giá lỗi, `onRetry(PRICES)` chỉ gọi `loadPriceRules`;
  - rỗng → `prices = Empty`;
  - thanh dưới: có khung → "120.000 đ/giờ" kèm "T3, 27/10 · 19:00 – 21:00"; không có khung → "từ 50.000 đ/giờ" (giá thấp nhất của các rule); không có rule → "Liên hệ";
  - khung giờ hết hạn khi đang mở màn hình → bỏ tô mọi dòng, ẩn thẻ "Khung bạn chọn", thanh dưới về "từ X đ/giờ".

### Implementation cho User Story 2

- [ ] T043 [P] [US2] Cài `CD/domain/PriceTableBuilder.kt` ([R4](research.md#r4-bảng-giá-nhóm-theo-ngày-và-giá-khung-bạn-chọn-fr-008-fr-009-sc-002)): dựng lịch giá từng ngày qua `DayConvention`, gộp ngày liền nhau có danh sách giống hệt, tạo `DayGroupTitle`. Cho T040 xanh
- [ ] T044 [US2] Cài `CD/domain/usecase/LoadPriceTableUseCase.kt`: gọi `SlotPricing.priceForWindow` rồi `SlotPricing.perHour` của `core/pricing`, **không viết lại phép tính**. Cho T041 xanh
- [ ] T045 [US2] Thêm mục `prices` và giá thanh dưới vào `CD/ui/CourtDetailViewModel.kt`. Cho T042 xanh
- [ ] T046 [US2] Cài `RES/layout/item_court_detail_price_group.xml`, `item_court_detail_price_row.xml`, `CD/ui/PriceTableAdapter.kt` (nền tô từ theme attribute), và thẻ "Khung bạn chọn" trong `fragment_court_detail.xml`:
  - định dạng tiền `NumberFormat` locale `vi-VN` + " đ/giờ";
  - mục rỗng hiện "Liên hệ" kèm số điện thoại (`PhoneDisplay`, nối ở US3), hoặc "Liên hệ chủ sân" kèm nút chat khi không có số (Edge case).

**Checkpoint**: US1 + US2 = MVP; quickstart Q4, Q5 và mọi ca P1–P9 đạt.

---

## Phase 5: User Story 3 – Xem vị trí, chỉ đường và liên hệ (Priority: P2)

**Goal**: bản đồ nhúng có marker, "Chỉ đường", bấm số để gọi; bản đồ lỗi không ảnh hưởng phần khác.

**Independent Test**: mở `demo-k3`, thấy marker và địa chỉ; "Chỉ đường" mở ứng dụng bản đồ, máy không có thì mở trình duyệt; số seed bị che không mở trình gọi, sửa thành số hợp lệ thì mở trình gọi đã điền sẵn (quickstart Q8, Q9, Q10).

### Tests cho User Story 3 ⚠️ viết trước, phải fail

- [ ] T047 [P] [US3] Unit test `TEST/feature/courtdetail/domain/PhoneDisplayTest.kt`:
  - `"0901 234 567"`, `"0901.234.567"` → `Dialable("+84901234567", "0901 234 567")`;
  - số bàn 11 chữ số `"028 3812 3456"` → `Dialable("+842838123456", …)` (sửa sau `/speckit-analyze` I2);
  - quá ngắn hoặc quá dài (`"090123"`, `"090123456789"`) → `DisplayOnly`;
  - `"0944xxx486"` (seed bị che) → `DisplayOnly("0944xxx486")`;
  - chuỗi rỗng hoặc `null` → `null`.

  Regex `^(0|\+84)\d{9,10}$` sau khi bỏ khoảng trắng và dấu chấm ([R9](research.md#r9-số-điện-thoại-fr-012)).
- [ ] T048 [P] [US3] Unit test `TEST/feature/courtdetail/ui/CourtDetailViewModelUs3Test.kt`:
  - `Dialable` + bấm → effect `Dial(e164)`; `DisplayOnly` + bấm → không có effect, nhấn giữ → `CopyPhone`;
  - không có `phone` → thẻ số điện thoại ẩn;
  - `location == null` → `map = Empty`;
  - "Chỉ đường" → `OpenDirections(lat, lng, name)`;
  - khoảng cách chỉ có khi `LocationProvider.hasPermission() == true` (giả); không có quyền thì **không gọi** `current()` và không có effect xin quyền.

### Implementation cho User Story 3

- [ ] T049 [P] [US3] Cài `CD/domain/PhoneDisplay.kt`. Cho T047 xanh
- [ ] T050 [US3] Cài `CD/ui/VenueMapController.kt` bọc osmdroid `MapView` theo [R7](research.md#r7-bản-đồ-nhúng-và-chỉ-đường-fr-010-fr-011-fr-013):
  - `Configuration.getInstance().userAgentValue = BuildConfig.APPLICATION_ID`; `osmdroidBasePath`, `osmdroidTileCache` trong `context.cacheDir/osmdroid` (không cần quyền bộ nhớ);
  - tắt multi-touch, cuộn và xoay; zoom 16; marker `ic_marker_shuttlecock` ở giữa;
  - chế độ tối dùng `TilesOverlay.INVERT_COLORS`;
  - ghi nguồn "© OpenStreetMap contributors" bằng `CopyrightOverlay` (hoặc `TextView` chồng lên góc bản đồ, chữ ≥ 10sp, đọc được ở cả hai chế độ), bắt buộc theo giấy phép ODbL và chính sách tile của OSM (sửa sau `/speckit-analyze` C3);
  - `onResume`/`onPause`/`onDetach` theo `viewLifecycleOwner`;
  - tile lỗi (`TileLoadFailed` / `MapListener`) → callback để Fragment ẩn bản đồ, giữ địa chỉ và nút.

  Bản đồ nằm trong `ViewStub` và chỉ inflate khi mục "Vị trí" cách vùng nhìn thấy dưới một chiều cao màn hình (lắng nghe `NestedScrollView.setOnScrollChangeListener`)
- [ ] T051 [US3] Thêm mục `map`, thẻ số điện thoại, thẻ khoảng cách vào `CD/ui/CourtDetailViewModel.kt` (khoảng cách qua `LocationProvider` + `GeoDistance` + `DistanceFormatter` của `core/location`). Cho T048 xanh
- [ ] T052 [US3] Trong `CD/ui/CourtDetailFragment.kt` xử lý các effect:
  - `OpenDirections`: thử `Intent(ACTION_VIEW, Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(name)})"))`, gặp `ActivityNotFoundException` thì mở `https://www.google.com/maps/dir/?api=1&destination=$lat,$lng`; **không** khai báo `<queries>`;
  - `Dial`: kiểm tra `PackageManager.FEATURE_TELEPHONY`; không có, hoặc gặp `ActivityNotFoundException`, thì sao chép vào clipboard và hiện snackbar "Đã sao chép số điện thoại";
  - `CopyPhone`: sao chép.

  Thẻ khoảng cách không có giá trị thì hiện "Chỉ đường" (cùng hành vi)

**Checkpoint**: quickstart Q8, Q9, Q10 đạt.

---

## Phase 6: User Story 4 – Xem đánh giá tổng quan (Priority: P2)

**Goal**: điểm trung bình, số lượt, điểm từng tiêu chí, 3 đánh giá mới nhất, "Xem tất cả (N)".

**Independent Test**: sau T026 (hoặc seed của 070), `demo-k3` hiện điểm và số lượt khớp 11 đánh giá `VISIBLE`; 3 đánh giá mới nhất không có đánh giá bị ẩn; đánh giá ẩn danh hiện "Người dùng đã xóa"; cơ sở chưa có đánh giá hiện "Sân chưa có đánh giá" (quickstart Q11, Q12).

**Phụ thuộc C** (070): interface `ReviewRepository` trong `feature/review/domain` có hàm `latestVisible(venueId, limit)`. Ngày 2026-10-25 `feature/review` **chưa có** trong code.

### Tests cho User Story 4 ⚠️ viết trước, phải fail

- [ ] T053 [P] [US4] Unit test `TEST/feature/courtdetail/domain/usecase/LoadLatestReviewsUseCaseTest.kt` với `LatestReviewsSource` giả:
  - trả tối đa 3 `ReviewPreview` theo thứ tự `createdAt` giảm dần;
  - `authorDeleted == true` hoặc `userSnapshot.displayName == null` → `authorName = null` (UI hiện "Người dùng đã xóa", ảnh mặc định);
  - bình luận giữ nguyên (UI tự rút gọn 3 dòng);
  - có `ownerReply` → `reply` khác `null`;
  - lỗi → `AppError` tương ứng.
- [ ] T054 [P] [US4] Unit test `TEST/feature/courtdetail/ui/CourtDetailViewModelUs4Test.kt`:
  - `reviews` chỉ bắt đầu tải **sau** khi `header = Content` (FR-024);
  - `ratingCount == 0` → `reviews = Empty`, không gọi `LatestReviewsSource`;
  - "Xem tất cả" mang `N = ratingCount` → effect `OpenReviews(venueId, name)`;
  - lỗi → chỉ `reviews = Error`, `onRetry(REVIEWS)` gọi lại đúng mục này;
  - điểm và tiêu chí lấy từ `VenueRatingSummary.fromVenue`, không tính từ danh sách đánh giá.

### Implementation cho User Story 4

- [ ] T055 [US4] Tạo `CD/domain/LatestReviewsSource.kt` (interface `suspend fun latest(venueId, limit = 3): List<ReviewPreview>`). Bản cài đặt:
  - **khi 070 đã có `ReviewRepository.latestVisible`**: `CD/data/ReviewRepositoryLatestReviewsSource.kt` gọi interface của 070 (chỉ import `feature/review/domain`, constitution I);
  - **chưa có**: `CD/data/FirestoreLatestReviewsSource.kt` truy vấn `reviews` where `venueId == X`, `status == "VISIBLE"` order by `createdAt desc` limit 3 (`awaitWithTimeout`), kèm `// TODO(C): thay bằng ReviewRepository.latestVisible (research R3)`.

  Bind trong `CD/di/CourtDetailModule.kt`
- [ ] T056 [US4] Cài `CD/domain/usecase/LoadLatestReviewsUseCase.kt`. Cho T053 xanh
- [ ] T057 [US4] Thêm mục `reviews` vào `CD/ui/CourtDetailViewModel.kt`. Cho T054 xanh
- [ ] T058 [US4] Cài `RES/layout/item_court_detail_review.xml` + `CD/ui/ReviewPreviewAdapter.kt` và khối tổng quan trong `fragment_court_detail.xml`:
  - khối tổng quan: điểm lớn, `RatingBar` chỉ đọc, "N đánh giá", ba `LinearProgressIndicator` cho tiêu chí (ẩn tiêu chí `null`);
  - thẻ đánh giá: avatar Coil hoặc chữ cái đầu, tên hoặc "Người dùng đã xóa", ngày `dd/MM`, số sao, bình luận `maxLines = 3`, ảnh thu nhỏ (chạm mở `action_global_photoViewer`), khối "Phản hồi của chủ sân".

  "Xem tất cả (N)" mở deep link `smashnow://review/venue/{venueId}`. 070 chưa có thì bắt `IllegalArgumentException` của Navigation và hiện "Tính năng sắp có", kèm `// TODO(C)`

**Checkpoint**: quickstart Q11, Q12 đạt.

---

## Phase 7: User Story 5 – Khách chưa đăng nhập và các thao tác cần tài khoản (Priority: P2)

**Goal**: khách xem được toàn bộ thông tin. Bấm "Đặt sân", trái tim hoặc chat thì được mời đăng nhập; đăng nhập xong quay lại đúng màn hình và thao tác tự tiếp tục; hủy thì không làm gì. Chủ sân ở chế độ người chơi không đặt và không chat được với cơ sở của mình.

**Independent Test**: quickstart Q13, Q14, Q15, Q16.

**Phụ thuộc C**: `FavoriteRepository` (070) và deep link chat `smashnow://chat/direct?venueId=` (091). Ngày 2026-10-25 cả hai **chưa có**.

### Tests cho User Story 5 ⚠️ viết trước, phải fail

- [ ] T059 [P] [US5] Unit test `TEST/feature/courtdetail/domain/PendingActionResolverTest.kt`: **đủ 5 dòng** của bảng ở [data-model §2.4](data-model.md#24-thao-tác-đang-chờ-r11):
  - `pending == null` → `Wait`;
  - `SignedIn` + có `pending` + màn hình hiện + `ownerId == uid` + `pending ∈ {BOOK, CHAT}` → `RejectOwnVenue`;
  - `SignedIn` + `FAVORITE` của chủ cơ sở → `Run(FAVORITE)`;
  - `SignedIn` còn lại → `Run(pending)`;
  - `SignedOut` + có `pending` + màn hình hiện → `Cancel`;
  - màn hình không hiện → `Wait`.

  Thêm ca `Loading`/`Initializing`/`NeedsConsent` → `Wait`.
- [ ] T060 [P] [US5] Unit test `TEST/feature/courtdetail/ui/CourtDetailViewModelUs5Test.kt` (`SessionManager` giả, `FavoriteGateway` giả, `SavedStateHandle`):
  - khách bấm "Đặt sân", trái tim, chat → effect `ShowLoginPrompt(action)` tương ứng, không gọi repository nào;
  - `onLoginChosen(BOOK)` → `SavedStateHandle["pendingAction"] == "BOOK"` và effect `OpenLogin`;
  - chuyển `SignedIn` rồi `onScreenVisible()` → chạy **đúng một lần**: `BOOK` → `CheckVenueBookable` rồi `OpenBookingGrid(venueId, window)`; `FAVORITE` → `setFavorite(true)` một lần; `CHAT` → `OpenChat(venueId)`; gọi `onScreenVisible()` lần hai không chạy lại;
  - vẫn là khách mà `onScreenVisible()` → xóa `pendingAction`, không có effect (US5-6);
  - tạo lại ViewModel từ `SavedStateHandle` còn `pendingAction` (giả lập process death) → vẫn chạy được sau khi `SignedIn`;
  - đã đăng nhập, `ownerId == uid`, bấm "Đặt sân" hoặc chat → snackbar "Đây là cơ sở của bạn" (FR-018);
  - khách thấy đủ mọi mục như người đã đăng nhập (FR-019).

### Implementation cho User Story 5

- [ ] T061 [P] [US5] Cài `CD/domain/PendingActionResolver.kt`. Cho T059 xanh
- [ ] T062 [US5] Tạo `CD/domain/FavoriteGateway.kt` (interface: `observeIsFavorite(venueId): Flow<Boolean>`, `suspend fun setFavorite(venue: VenueSummary, favorite: Boolean): Result<Unit>`). Bản cài đặt:
  - **khi 070 đã có `FavoriteRepository`**: `CD/data/ReviewFavoriteGateway.kt` gọi interface của 070;
  - **chưa có**: không bind, nút trái tim ẩn, kèm `// TODO(C): bind FavoriteRepository của 070`.

  `VenueSummary` dùng `CORE/model/VenueSummary.kt` theo [070 ui-screens §2](../070-review-favorite/contracts/ui-screens.md): `venueId`, `name`, `photoPath`, `address`. Tạo file nếu 070 chưa tạo
- [ ] T063 [US5] Thêm vào `CD/ui/CourtDetailViewModel.kt`: `onScreenVisible`, `onLoginChosen`, `onFavoriteClicked` (lạc quan, lỗi thì trả lại và hiện snackbar), `onChatClicked`, kiểm tra chủ cơ sở (`ownerId == sessionManager.currentUid()`), `isFavorite`. Cho T060 xanh
- [ ] T064 [US5] Cài `CD/ui/LoginPromptDialog.kt` (`MaterialAlertDialogBuilder`, tiêu đề theo `PendingAction`, nút "Đăng nhập" / "Để sau") và nối vào `CD/ui/CourtDetailFragment.kt`:
  - `onResume` gọi `viewModel.onScreenVisible()`;
  - `OpenLogin` → `authNavigator.openLogin(findNavController(), returnTo = null)` (010 R15);
  - `OpenChat` → deep link `smashnow://chat/direct?venueId=`; 091 chưa có thì bắt `IllegalArgumentException` và hiện "Tính năng sắp có", kèm `// TODO(C)`;
  - trái tim đổi biểu tượng và `contentDescription` theo `isFavorite`.

**Checkpoint**: quickstart Q13, Q14 (phần đã có của 070/091), Q15, Q16 đạt.

---

## Phase 8: User Story 6 – Cơ sở tạm ngừng hoạt động hoặc không còn tồn tại (Priority: P3)

**Goal**: cơ sở bị ẩn hoặc khóa hiện ghi chú, không cho đặt hay chat, chỉ cho bỏ yêu thích; cơ sở đã xóa hiện màn hình riêng; cơ sở đổi trạng thái khi đang xem thì "Đặt sân" bị từ chối.

**Independent Test**: quickstart Q17, Q18, Q19, Q20.

**Phụ thuộc D**: Rules `venues` theo [research R10](research.md#r10-trạng-thái-cơ-sở-và-rules-đọc-venues-fr-019-fr-021-fr-022-fr-023-sc-005). Chưa có thì người chơi đọc cơ sở `HIDDEN` sẽ nhận `PERMISSION_DENIED`: app hiện lỗi chung, không crash; ghi trong PR.

### Tests cho User Story 6 ⚠️ viết trước, phải fail

- [ ] T065 [P] [US6] Unit test `TEST/feature/courtdetail/ui/CourtDetailViewModelUs6Test.kt`:
  - `SUSPENDED` → `bottomBar = Suspended`, không có "Đặt sân"/chat; bảng giá và đánh giá vẫn tải (US6-3); bấm trái tim khi chưa yêu thích → không gọi `setFavorite`, khi đang yêu thích → `setFavorite(false)` (US6-2);
  - `GONE` → trạng thái "không còn tồn tại", `onRemoveFavoriteFromGone()` gọi `setFavorite(false)` khi đang yêu thích;
  - `SUSPENDED_GUEST` → trạng thái rút gọn, không tải `courts`/`priceRules`/`reviews`;
  - đang `ACTIVE`, listener phát `LOCKED` → state đổi ngay;
  - bấm "Đặt sân" khi `fetchStatusFromServer` trả `LOCKED` → snackbar "Sân tạm ngừng hoạt động", không có `OpenBookingGrid` (FR-023, SC-005);
  - thao tác chờ `BOOK` của khách với cơ sở vừa bị khóa → không mở lưới giờ.

### Implementation cho User Story 6

- [ ] T066 [US6] Thêm xử lý `availability` vào `CD/ui/CourtDetailViewModel.kt` (`SUSPENDED`, `GONE`, `SUSPENDED_GUEST`, đổi trạng thái khi đang xem). Cho T065 xanh
- [ ] T067 [US6] Cài `RES/layout/view_court_detail_gone.xml` ("Sân không còn tồn tại", "Quay lại", "Bỏ khỏi yêu thích" khi `isFavorite == true`), `RES/layout/view_court_detail_suspended_guest.xml` ("Sân tạm ngừng hoạt động", "Quay lại", "Đăng nhập" gọi `AuthNavigator`), và ghi chú nổi bật "Sân tạm ngừng hoạt động" ở mục 2 của `fragment_court_detail.xml`. Nối vào `CD/ui/CourtDetailFragment.kt`

**Checkpoint**: cả 6 user story chạy độc lập; quickstart Q17–Q20 đạt (Q17, Q19 cần Rules của D).

---

## Phase 9: Polish & Cross-Cutting

- [ ] T068 [P] Viết `RES/layout-w600dp/fragment_court_detail.xml` hai cột ([R12](research.md#r12-lỗi-offline-và-giữ-trạng-thái-fr-025-fr-026-sc-007-fr-027)): trái 40% (ảnh, bản đồ), phải 60% (thông tin, bảng giá, đánh giá), thanh "Đặt sân" cố định ở cuối. ID view giống bản điện thoại để Fragment dùng chung ViewBinding
- [ ] T069 [P] Rà FR-027 trên mọi layout của 030 và `fragment_photo_viewer.xml`:
  - chế độ tối dùng màu từ `values-night`, không hardcode;
  - thứ tự TalkBack tên → địa chỉ → điểm → trạng thái mở cửa → giá (`accessibilityTraversalAfter` nếu cần);
  - mọi nút và biểu tượng có `contentDescription`; vùng chạm ≥ 48dp;
  - mỗi mục có đủ 4 trạng thái kèm "Thử lại" (FR-025).
- [ ] T070 [P] Espresso (nên có) `ATEST/feature/courtdetail/CourtDetailFlowTest.kt` trên Auth/Firestore Emulator với seed:
  - mở `smashnow://venue/demo-truong-chinh-909?date=…&start=1900&end=2100` → thấy "Khung bạn chọn" và "130.000 đ/giờ";
  - khách bấm "Đặt sân" → hộp mời đăng nhập → "Để sau" ở lại;
  - mở `smashnow://venue/khong-co` → "Sân không còn tồn tại".

  Tắt autofill trong lúc chạy như 010 T075
- [ ] T071 Chạy quickstart [§4](quickstart.md#4-kịch-bản-thủ-công-trên-máy-ảo-emulator) **Q1–Q24** trên Emulator với seed, ghi kết quả thật vào cuối `specs/030-court-detail/quickstart.md`:
  - đo SC-001 bằng Logcat (mốc bấm thẻ → `header = Content` + ảnh đầu đã vẽ) **20 lần** trên máy ảo giới hạn mạng 4G (`Network type: LTE`), đạt khi ≤ 1 lần vượt 3 giây (≥ 95%);
  - kịch bản nào bị chặn bởi B, C, D thì ghi "[BLOCKED: X]" và lý do.
- [ ] T072 [P] Chụp ảnh Q1, Q11, Q13, Q17 lưu vào `docs/features/03-court-detail/screenshots/` theo tên `03_court-detail_buocX_mo-ta.png` (ảnh đã che số điện thoại nếu là số thật)
- [ ] T073 [P] Cập nhật `docs/features/03-court-detail/README.md`:
  - trạng thái "🟨 Đang làm";
  - link spec, plan, tasks;
  - đánh dấu các mục phạm vi đã xong;
  - bảng ảnh chụp và wireframe;
  - ghi chú quyết định (osmdroid, dữ liệu seed, R13).

  Cập nhật dòng nhóm 03 ở cột *Trạng thái* và *Spec* trong `docs/features/README.md`. Ghi chú ảnh chụp: "Dữ liệu demo lấy từ datlich.alobo.vn, chỉ dùng cho mục đích học tập" (quyết định C1, [research R13](research.md#r13-dữ-liệu-seed-của-nhóm-scriptsseed-và-constitution-vi))
- [ ] T074 [P] Gửi đề xuất sửa đặc tả CSDL ([data-model §4](data-model.md#4-thay-đổi-cần-đưa-vào-docsdesigndatabasereadmemd)) cho người phụ trách bằng comment trong PR. **Không tự sửa** `docs/design/database/README.md` ở các mục của D, C:
  - D: `ratingSums`, `"2400"`, thứ tự `photoPaths`, `phone` công khai, `DELETED`, Rules `venues`;
  - B + D: `daysOfWeek`;
  - C: Rules `reviews`.
- [ ] T075 Chạy `./gradlew lint testDebugUnitTest assembleDebug` (0 lỗi Lint mức Error) và `npm test` trong `firestore-tests/`. Ghi số test xanh/đỏ vào PR, test VD-* còn đỏ thì ghi rõ do chờ D/C. Rà `grep -rn "TODO(B)\|TODO(C)\|TODO(D)" app/src` và liệt kê các chỗ còn lại trong PR. Ghi log AI vào `docs/ai-log/AI_log_1.md`
- [ ] T077 *(thêm sau `/speckit-analyze` C1)* Khi spec 111 (D) đã cho chủ sân tạo cơ sở: đăng nhập một tài khoản chủ sân đã duyệt trên Emulator, tạo 1 cơ sở **bằng giao diện 111** với ảnh tự chụp hoặc miễn phí bản quyền, tiện ích, ít nhất 2 loại mặt sân và số điện thoại giả hợp lệ. Chạy lại Q1, Q6, Q9 trên cơ sở này để kiểm tra 030 đọc đúng dữ liệu do chủ sân ghi (path Supabase thay vì URL đầy đủ, `amenityIds` có giá trị, nhiều `surfaceType`). Ghi kết quả vào `specs/030-court-detail/quickstart.md`. Chưa có 111 thì ghi `[BLOCKED: D]`
- [ ] T078 [P] *(thêm sau `/speckit-analyze` G1)* Thử SC-003 với 5 người (bạn cùng lớp, không phải thành viên nhóm): đưa máy đang mở chi tiết `demo-k3`, yêu cầu tìm giá giờ cao điểm, giờ đóng cửa và số điện thoại; đo thời gian từng người. Đạt khi ≥ 4/5 người xong dưới 30 giây. Ghi kết quả (không ghi tên người thử) vào `specs/030-court-detail/quickstart.md`

---

## Dependencies & Execution Order

### Phụ thuộc giữa các phase

- **Setup (Phase 1)**: T001 xác nhận PR `chore(core)` của 010 (đã có). T002 chặn mọi task dùng Coil/osmdroid/ViewPager2.
- **2A `chore(core)` phần 3**: sau Setup. T012–T015 chỉ làm khi 020 chưa merge phần tương ứng. **Chặn US1–US4** (ảnh, giá, danh mục).
- **2B**: sau T013 (`PriceRuleMapper`) và T014 (`CatalogRepository`). Chặn mọi user story. T076 (test Repository trên Emulator) làm ngay sau T023.
- **2C**: T025 làm song song với 2B, không chặn story. T026 chỉ cần trước US4.
- **US1 (Phase 3)**: sau Phase 2.
- **US2 (Phase 4)**: sau Phase 2; code độc lập với US1 nhưng hiển thị trong cùng Fragment, nên làm sau T037–T038.
- **US3 (Phase 5)**: sau T037–T038.
- **US4 (Phase 6)**: sau T037–T038; cần dữ liệu đánh giá (T026 hoặc seed 070).
- **US5 (Phase 7)**: cần T039 (mở lưới giờ) của US1.
- **US6 (Phase 8)**: cần T035 (`CheckVenueBookableUseCase`) và US5 (bỏ yêu thích qua `FavoriteGateway`).
- **Polish (Phase 9)**: sau các story cần có trong bản demo.

### Phụ thuộc ngoài nhóm (bảng "Phối hợp với nhóm" trong [plan.md](plan.md#phối-hợp-với-nhóm))

| Task | Cần | Nếu chưa có |
|---|---|---|
| T001, T012–T015 | **A** (020): PR `chore(core)` phần 2 (`core/pricing`, `schedule`, `catalog`, `location`) | 030 viết đúng theo contract 020; khi 020 làm thì dùng lại, không viết lần hai |
| T013, T040, T041 | **B + D**: chốt quy ước `daysOfWeek` (đề xuất ISO) | Giữ ISO trong `DayConvention.CURRENT` và file vector; **điểm chặn** trước khi chạy chung dữ liệu thật với 040 |
| T006, T008 | **B** (040): test 040 nạp chung `window-price-vectors.json` | Báo B trong PR `chore(core)` |
| T039 | **B** (040): thêm `date`, `startTime`, `endTime` (nullable) vào `bookingSlotGridFragment` | Chỉ truyền `venueId`, `TODO(B)`; chưa có màn hình 040 thì báo "Tính năng sắp có" |
| T035, T065 | **B** (040): `create-booking` từ chối cơ sở không `ACTIVE` (constitution II) | Ghi trong PR; 030 vẫn kiểm tra ở client |
| T055 | **C** (070): `ReviewRepository.latestVisible(venueId, limit)` | `FirestoreLatestReviewsSource` tạm, `TODO(C)` |
| T062 | **C** (070): `FavoriteRepository` | Ẩn nút trái tim, `TODO(C)` |
| T058 | **C** (070): deep link `smashnow://review/venue/{venueId}` | "Tính năng sắp có", `TODO(C)` |
| T064 | **C** (091): deep link `smashnow://chat/direct?venueId=` | "Tính năng sắp có", `TODO(C)` |
| T025, T066 | **C** (070): Rules `reviews` cho khách đọc `VISIBLE` | VD-6, VD-7 đỏ; khách thấy lỗi ở mục đánh giá (chỉ mục đó) |
| T025, T066, T067 | **D** (111): Rules `venues` theo R10 | VD-1 → VD-4 đỏ; người chơi mở cơ sở `HIDDEN` thấy lỗi chung |
| T017, T074 | **D**: thêm `ratingSums` vào đặc tả CSDL §4.3; **C** ghi `ratingSums` trong Edge Function `review` | Thiếu `ratingSums` thì ẩn thanh tiêu chí |
| T016, T019 | **C** (070), **A** (020): dùng `PhotoUrlResolver`, `PhotoViewerFragment` | Báo trong PR `chore(core)` |
| T077 | **D** (111): chủ sân tạo được cơ sở qua giao diện | Ghi `[BLOCKED: D]`; vẫn demo bằng seed (quyết định C1) |

### Trong mỗi user story

Test (fail) → hàm thuần / UseCase → ViewModel (test xanh) → layout / Fragment → nav graph.

### Cơ hội làm song song

- Phase 1: T003, T004, T005 song song với T002.
- 2A: T006–T011 cùng lúc; T012, T014, T016, T017 khác file nhau; T018 → T019.
- 2B: T020, T021, T022 song song; T023 sau T021–T022.
- 2C: T025, T026 song song với toàn bộ 2B.
- US1: T027–T031 song song; T032, T033, T034 song song.
- US2–US4: tests của từng story song song; US3 và US4 làm song song được sau T038 (khác use case, khác mục layout).
- 2B: T076 song song với T024–T026 sau khi T023 xong.
- Polish: T068, T069, T070, T072, T073, T074, T078 song song.

## Parallel Example: Phase 2A

```bash
# Viết test trước, cùng lúc:
Task: "T006 window-price-vectors.json trong app/src/test/resources/pricing/"
Task: "T007 TimeOfDayTest (ca 2400) trong app/src/test/.../core/schedule/"
Task: "T008 SlotPricingVectorTest, DayConventionTest trong app/src/test/.../core/pricing/"
Task: "T009 PhotoUrlResolverTest trong app/src/test/.../core/media/"
Task: "T010 VenueRatingSummaryTest trong app/src/test/.../core/model/"
Task: "T011 ZoomMathTest trong app/src/test/.../core/ui/photo/"

# Sau đó: T012, T014, T016, T017 song song; T013 sau T012; T018 → T019
```

## Parallel Example: User Story 1

```bash
Task: "T027 SearchWindowArgsTest"
Task: "T028 OpeningHoursTest"
Task: "T029 CourtSummaryTest"
Task: "T030 CheckVenueBookableUseCaseTest"
Task: "T031 CourtDetailViewModelUs1Test"
# Rồi T032, T033, T034 song song → T035 → T036 → T037 → T038 → T039
```

## Implementation Strategy

### MVP (US1 + US2)

1. Phase 1 + **2A → mở PR `chore(core)` phần 3 ngay**, để B dùng file vector và C dùng `core/media`, `core/model`, `core/ui/photo`.
2. 2B, và 2C song song (T025 chạy ra kết quả đỏ là đúng, chờ D/C).
3. US1 → US2.
4. **Dừng và kiểm tra**: quickstart Q1–Q7 và ca P1–P9. Đây là phần demo cho mốc nhóm 03.

### Tăng dần

5. US3 (vị trí, liên hệ) và US4 (đánh giá) song song.
6. US5 (khách) sau US1; phần yêu thích và chat bật khi 070/091 có.
7. US6 (trạng thái cơ sở) sau US5.
8. Polish, đặc biệt T071 (Q1–Q24) trước khi đóng băng tính năng.

### Ghi chú

- Mỗi task hoặc nhóm task liên quan là một commit Conventional Commits:
  - scope `core` cho 2A, `court` cho phần còn lại (ví dụ `feat(court): bảng giá theo nhóm ngày`);
  - không thêm dòng Co-Authored-By.
- Ghi log AI vào `docs/ai-log/AI_log_1.md` cho mỗi lần dùng `/speckit-*`.
- Không commit `app/google-services.json`, `local.properties`, khóa bí mật.
- Không sửa `firestore.rules` của D, C, không sửa `scripts/seed/seed.mjs` của Duy.
