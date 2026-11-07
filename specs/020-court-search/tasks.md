---

description: "Danh sách task triển khai spec 020 – Tìm kiếm sân"
---

# Tasks: Tìm kiếm sân

**Input**: tài liệu thiết kế trong `specs/020-court-search/`: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/), [quickstart.md](quickstart.md)

**Tests**: Có. Constitution nguyên tắc IV bắt buộc test cho:
- Security Rules `districts`, `amenities`, `users/{uid}/searchHistory` (C-1…C-3, H-1…H-15) trên Firebase Emulator, kèm quyền đọc của khách (V-1…V-5, S-1, S-2) khi D, B đã viết Rules;
- logic thuần: chuẩn hóa chữ, `nameKeywords`, `SlotPricing`, lịch 30 phút, khoảng cách, sanitizer, `VenueMatcher`, `AvailabilityEvaluator`, `ResultSorter`;
- engine tìm kiếm (fake data source), repository lịch sử, ViewModel.

Viết test trước, chạy thấy **fail**, rồi mới cài đặt.

**Organization**: nhóm theo user story để mỗi story làm và kiểm thử độc lập được. Phase 2 tách thành 3 PR (2A `chore(core)` phần 2, 2B Rules/index/seed, 2C domain tìm kiếm) để nhóm review và merge sớm.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: làm song song được (file khác nhau, không phụ thuộc task chưa xong)
- **[Story]**: user story của spec (US1…US4)
- **[BLOCKED: X]**: cần người phụ trách X làm xong phần của họ mới chạy thật được

## Quy ước đường dẫn

| Viết tắt | Đường dẫn thật |
|---|---|
| `APP/` | `app/src/main/java/com/example/booking_app/` |
| `CORE/` | `app/src/main/java/com/example/booking_app/core/` |
| `SEARCH/` | `app/src/main/java/com/example/booking_app/feature/search/` |
| `RES/` | `app/src/main/res/` |
| `TEST/` | `app/src/test/java/com/example/booking_app/` |
| `ATEST/` | `app/src/androidTest/java/com/example/booking_app/` |
| `FN/` | `supabase/functions/` |
| `SEED/` | `scripts/seed/` |

---

## Phase 1: Setup

**Purpose**: kiểm tra phần nền đã có. Spec 020 **không thêm thư viện mới** ([plan](plan.md#technical-context)).

- [ ] T001 Kiểm tra PR `chore(core)` của spec 010 đã merge vào nhánh làm việc: Phase 1 và 2A của [tasks 010](../010-account-auth/tasks.md) (T001–T018: Hilt, KSP, Safe Args, kotlinx-serialization, Coil, `CORE/network/AppError.kt`, `CORE/network/TaskAwait.kt` với `awaitWithTimeout(15s)`, `CORE/session/SessionManager.kt`, `CORE/di/FirebaseModule.kt`, `RES/navigation/nav_player.xml`), **gồm cả chế độ khách** (tasks 010 T080–T083: `SessionRouter`, `AuthNavigator`, `LoginPromptView`; khách vào thẳng `nav_player`). **Chưa merge thì mọi task từ T003 trở đi bị chặn**: chờ, hoặc rebase lên nhánh của PR đó và ghi rõ trong mô tả PR
- [ ] T002 [P] Tạo `SEED/package.json` (Node 20, dependency `firebase-admin`, script `seed`) và `SEED/README.md` ghi cách chạy với `--emulator` / `--project=<id>` và `--today=yyyyMMdd`

**Checkpoint**: `./gradlew assembleDebug` chạy được trên nhánh có `chore(core)` của 010.

---

## Phase 2: Foundational (chặn mọi user story)

**⚠️ CRITICAL**: chưa xong phase này thì chưa làm story nào.

### 2A. PR `chore(core)` phần 2: phần dùng chung cả nhóm ([research R5, R6, R9, R14](research.md))

**Tests (viết trước, phải fail):**

- [ ] T003 [P] Tạo `FN/_shared/search-keywords.vectors.json` theo [contract keyword §5](contracts/keyword-normalization.md#5-vector-test-trích-file-json-là-bản-đầy-đủ): mảng `normalize` (`input`, `expected`), mảng `generate` (`input`, `expected` đã sắp xếp), mảng `match` (`query`, `name`, `matches`, `serverToken`). Đủ mọi dòng của hai bảng trong contract, thêm: tên > 60 tiền tố (kiểm tra cắt còn 60), từ > 15 ký tự, chữ số, `Đ` đầu từ, chuỗi rỗng
- [ ] T004 [P] Viết `TEST/core/text/VietnameseNormalizerTest.kt`, `KeywordGeneratorTest.kt`, `KeywordQueryTest.kt`: đọc file vector ở T003 bằng đường dẫn `../supabase/functions/_shared/search-keywords.vectors.json` (thư mục làm việc của test Gradle là `app/`). `KeywordQuery.parse` trả `null` khi tổng ký tự < 2 hoặc chỉ ký tự đặc biệt; `serverToken` là token dài nhất cắt 15 ký tự, `null` khi token dài nhất chỉ 1 ký tự
- [ ] T005 [P] Viết `TEST/core/schedule/TimeOfDayTest.kt`, `SlotMathTest.kt`, `BookingWindowTest.kt`, `SlotIdTest.kt`: `HHmm` ⇄ phút; chỉ nhận mốc `:00`/`:30`; liệt kê khung 30 phút của `[start, end)`; giờ bắt đầu sớm nhất hôm nay là mốc 30 phút kế tiếp sau `now` (FR-007); ngày hợp lệ từ hôm nay tới hôm nay + 30 theo `Asia/Ho_Chi_Minh`; `SlotId.of("c1", 2026-10-20, 19:30) == "c1_20261020_1930"`
- [ ] T006 [P] Viết `TEST/core/pricing/SlotPricingTest.kt`: US2-2 (thường 80k, cao điểm 19:00–22:00 150k: khung 15:00–17:00 → 160.000, `perHour` 80.000; khung 19:00–21:00 → `perHour` 150.000); khung 17:00–19:00 với cao điểm từ 18:00 → `perHour` 115.000 (Edge case "đổi mức giá giữa chừng"); thiếu rule cho một khung 30 phút → `null`; rule không áp dụng cho thứ đó → `null`; `daysOfWeek` theo quy ước đã chốt ở T037 (tạm dùng ISO 1 = Thứ hai, đặt hằng số một chỗ để đổi)
- [ ] T007 [P] Viết `TEST/core/location/GeoDistanceTest.kt`, `DistanceFormatterTest.kt`: haversine sai số < 1% với hai điểm biết trước ở TP.HCM; theo FR-013: làm tròn tới 10 m trước, kết quả < 1.000 m hiện mét (`849 → "850 m"`), ≥ 1.000 m hiện km một chữ số thập phân dấu phẩy (`996 → "1,0 km"`, `1234 → "1,2 km"`)
- [ ] T008 [P] Viết `TEST/core/catalog/FirestoreCatalogRepositoryTest.kt` với fake `CatalogDataSource`: đọc một lần mỗi phiên rồi giữ trong `StateFlow`; sắp xếp theo `sortOrder`; `isActiveDistrict(id)` / `isActiveAmenity(id)` trả `false` khi không có hoặc `isActive == false`; lỗi mạng → `AppError`, gọi lại được

**Implementation:**

- [ ] T009 [P] Cài đặt `CORE/text/VietnameseNormalizer.kt` theo [contract §2](contracts/keyword-normalization.md#2-normalizes): `đ`/`Đ` → `d`, NFD bỏ `\p{Mn}`, `lowercase(Locale.ROOT)`, ký tự ngoài `[a-z0-9]` → khoảng trắng, gộp khoảng trắng
- [ ] T010 Cài đặt `CORE/text/KeywordGenerator.kt` (tiền tố 2..min(len, 15) mỗi từ, từ 1 ký tự giữ nguyên, bỏ trùng, sắp xếp, tối đa 60) và `CORE/text/KeywordQuery.kt` (`tokens`, `serverToken`, `matches(nameWords)`) theo [contract §3–4](contracts/keyword-normalization.md#3-generatename). T004 xanh
- [ ] T011 [P] Cài đặt `CORE/schedule/TimeOfDay.kt`, `SlotMath.kt`, `BookingWindow.kt` (`ADVANCE_DAYS = 30`, `ZONE = ZoneId.of("Asia/Ho_Chi_Minh")`), `SlotId.kt`. T005 xanh
- [ ] T012 Cài đặt `CORE/pricing/PriceRule.kt` (`id`, `daysOfWeek: List<Int>`, `startTime`, `endTime`, `pricePerHour: Long`, `label`; `pricePerSlot = pricePerHour / 2`) và `CORE/pricing/SlotPricing.kt` (`priceForWindow(rules, date, start, end): Long?`, `perHour(total, start, end): Long`). Không dùng `Double` cho tiền. T006 xanh
- [ ] T013 [P] Cài đặt `CORE/location/GeoDistance.kt`, `CORE/location/DistanceFormatter.kt`. T007 xanh
- [ ] T014 [P] Cài đặt `CORE/location/LocationProvider.kt` (interface: `hasPermission()`, `suspend fun current(): GeoPoint?`) và `CORE/location/AndroidLocationProvider.kt` dùng `LocationManagerCompat.getCurrentLocation` (provider `NETWORK` rồi `GPS`, timeout 10 giây, trả `null` khi tắt vị trí hoặc quá hạn); thêm `ACCESS_COARSE_LOCATION`, `ACCESS_FINE_LOCATION` vào `app/src/main/AndroidManifest.xml` ([research R9](research.md#r9-sắp-xếp-gần-nhất-và-vị-trí-fr-011-đến-fr-014))
- [ ] T015 [P] Cài đặt `CORE/catalog/District.kt`, `Amenity.kt` (trường theo [data-model §2.2](data-model.md#22-districtsdistrictid-và-amenitiesamenityid)), `CatalogRepository.kt` (interface), `CatalogDataSource.kt`, `FirestoreCatalogRepository.kt` (một query mỗi collection, cache trong bộ nhớ, `awaitWithTimeout`). T008 xanh
- [ ] T016 Tạo `CORE/di/SearchCoreModule.kt`: bind `LocationProvider`, `CatalogRepository`, cung cấp `Clock` theo `BookingWindow.ZONE`
- [ ] T017 [P] Cài đặt `FN/_shared/search-keywords.ts` (`normalize`, `generateKeywords`) và `FN/_shared/search-keywords.test.ts` đọc cùng file vector T003; `deno test` xanh. D dùng file này trong `review-owner-application`

### 2B. PR `feat(search)`: Rules, index, dữ liệu demo

- [ ] T018 [P] Viết `firestore-tests/test/search.test.ts` theo [bảng test](contracts/security-rules-and-indexes.md#4-bảng-test): C-1…C-3, H-1…H-15 (phải fail trước T019). V-1…V-5 để `it.todo` ghi `[BLOCKED: D]`, S-1, S-2 để `it.todo` ghi `[BLOCKED: B]`; không tự viết Rules `venues`/`slots` thay D, B
- [ ] T019 Thêm match `districts`, `amenities`, `users/{uid}/searchHistory` vào `firestore.rules` đúng [contract §1](contracts/security-rules-and-indexes.md#1-match-do-a-viết) (dùng `isSelf`, `isInitialized`, `isAdmin`, `hasServerTimestamps` của Rules nền). Ràng buộc trường lịch sử: `keyword` "≤ 100 ký tự"; `districtId` ≤ 40 ký tự; `priceMin`, `priceMax` "≥ 0, bội số 10.000; `priceMin ≤ priceMax` khi có cả hai"; `date`, `startTime`, `endTime` "cùng có hoặc cùng không", giờ "bội số 30 phút, `endTime > startTime`"; `amenityIds` "≤ 20 phần tử"; `sort` ∈ `RATING`, `PRICE_ASC`, `NEAREST`; `searchedAt == request.time`; cập nhật giữ `createdAt`. T018 xanh (trừ `it.todo`)
- [ ] T020 [P] Ghi đủ 13 index V1–V12, S1 vào `firestore.indexes.json` theo [research R8](research.md#r8-index-cần-tạo) (`nameKeywords` dùng `"arrayConfig": "CONTAINS"`; V5–V8 thêm `minPricePerHour ASC` ở cuối). Báo B trong PR: index `slots (venueId, date, startAt)` thay index `(venueId, date)` của 040
- [ ] T021 [P] Tạo `SEED/catalog.json`: 24 quận/huyện TP.HCM (ID slug như `q1`, `q10`, `tan-binh`, `binh-thanh`, `thu-duc`; `cityName` "TP. Hồ Chí Minh"; `sortOrder`; `isActive: true`) và 4 tiện ích `parking` "Gửi xe", `drinks` "Nước uống", `shower` "Phòng tắm", `racket-rental` "Cho thuê vợt" (`iconKey` cùng tên)
- [ ] T022 [P] Tạo `SEED/search-demo.json` theo [quickstart §0](quickstart.md#0-chuẩn-bị): 30 cơ sở ở `q10`, `tan-binh`, `binh-thanh` (2 `HIDDEN`, 1 `LOCKED`), đủ `courts`, `priceRules`, `nameKeywords` sinh bằng T017, `ratingAvg`/`ratingCount`/`minPricePerHour` đúng; các cơ sở đặc biệt "Sân cầu lông Hòa Bình", A, B, C (US2-4), D (`HOLD` hết hạn), chưa có bảng giá, chưa có đánh giá, không có ảnh. `slots` ghi ngày dạng tương đối (`+1` = ngày mai) để seed theo `--today`
- [ ] T023 [P] Tạo `SEED/search-demo.expected.json`: 12 bộ điều kiện (`SearchCriteria` dạng JSON, ngày tương đối) kèm danh sách `venueId` đúng thứ tự: tên "hoa binh"; "oa bi" (rỗng); quận `q10`; tên + quận; giá 50k–100k; Q10 + Mai 19:00–21:00 + ≤100k + Gửi xe; khung 19:00–21:00 (loại B, C, gồm D); Gửi xe + Phòng tắm; sắp xếp giá không khung (cơ sở "Liên hệ" cuối); sắp xếp giá có khung; sắp xếp đánh giá (chưa có đánh giá cuối); tên của cơ sở `HIDDEN` (rỗng, SC-004)
- [ ] T024 Viết `SEED/seed.mjs`: đọc T021–T022, đổi ngày tương đối theo `--today`, ghi bằng `firebase-admin` vào Emulator (`--emulator`, đặt `FIRESTORE_EMULATOR_HOST`) hoặc project (`--project=<id>`, dùng Application Default Credentials, **hỏi xác nhận** trước khi ghi project thật); `--reset` xóa dữ liệu demo cũ theo tiền tố ID `demo-`

### 2C. PR `feat(search)`: domain dùng chung cho các story

- [ ] T025 [P] Viết `TEST/feature/search/domain/model/SearchCriteriaTest.kt`: `hasAnyCondition()` (sắp xếp đơn thuần không tính; từ khóa < 2 ký tự không tính), `clearFilters()` giữ `keyword`, `districtId`, `sort` và bỏ giá, khung giờ, tiện ích (US2-8), `canonicalKey()` ổn định khi `amenityIds` khác thứ tự và khi từ khóa khác hoa/thường/dấu, round-trip JSON qua kotlinx-serialization
- [ ] T026 Cài đặt `SEARCH/domain/model/SearchCriteria.kt`, `TimeWindow.kt`, `SortOption.kt` (`RATING` mặc định, `PRICE_ASC`, `NEAREST`) và `SEARCH/domain/model/serializer/LocalDateAsStringSerializer.kt`, `TimeOfDayAsStringSerializer.kt` (chuỗi `yyyyMMdd`, `HHmm`) theo [contract §1](contracts/search-criteria.md#1-kiểu-dữ-liệu). `SearchCriteria` là `@Serializable` ngay từ đầu vì US1 lưu vào `SavedStateHandle` và 021/022 dùng lại. T025 xanh
- [ ] T027 [P] Cài đặt các model còn lại trong `SEARCH/domain/model/`: `VenueSummary.kt` (kèm `nameWords`), `VenueResult.kt`, `DisplayPrice.kt` (`From`, `ForWindow`, `Contact`), `SearchPage.kt` (`items`, `hasMore`, `totalCount: Int?`, `isFromCache`, `capped`), `SearchSession.kt` (`id`, `criteria`, `strategy: SERVER_ORDERED | CLIENT_ORDERED`, `cursor: SearchCursor` mờ với domain, không lộ kiểu Firestore), `SearchHistoryEntry.kt`, `SanitizeResult.kt`, `DroppedField.kt` theo [data-model §3.1](data-model.md#31-featuresearchdomainmodel)
- [ ] T028 [P] Tạo interface `SEARCH/domain/VenueSearchEngine.kt` (`firstPage`, `nextPage`, `count`) theo [contract §4](contracts/search-criteria.md#4-engine-tìm-kiếm) và `SEARCH/domain/SearchHistoryRepository.kt` (`observe(): Flow<List<SearchHistoryEntry>>`, `record(criteria)`, `delete(id)`, `clear()`)
- [ ] T029 Tạo `SEARCH/data/dto/VenueDto.kt`, `PriceRuleDto.kt`, `CourtDto.kt`, `SlotDto.kt` và `SEARCH/data/VenueDataSource.kt` (interface) + `FirestoreVenueDataSource.kt`: chạy `Query.get()` bọc `awaitWithTimeout`, trả `(docs, lastSnapshot, isFromCache)`; map DTO → `VenueSummary` (chỉ trường công khai, `nameWords` = `normalize(name).split(" ")`, ảnh đầu tiên của `photoPaths`); lỗi đổi thành `AppError`. Đây là lớp duy nhất gọi Firestore cho tìm kiếm (constitution I)
- [ ] T030 Tạo `RES/navigation/nav_search.xml` (start `searchFragment`, có `searchInputFragment`, `searchFilterSheet`) và include vào `RES/navigation/nav_player.xml` ở tab "Trang chủ"; nếu `nav_court_detail` của spec 030 chưa có thì tạo `RES/navigation/nav_court_detail.xml` với `CourtDetailPlaceholderFragment` (`APP/feature/venue/ui/CourtDetailPlaceholderFragment.kt`, chỉ hiện `venueId`) nhận args `venueId: String`, `date: String?`, `startTime: String?`, `endTime: String?` ([contract UI §1](contracts/ui-screens.md#1-nav-graph-nav_search-lồng-trong-nav_player-tab-trang-chủ)); tạo `RES/values/strings_search.xml` với các khóa ở [contract UI §4](contracts/ui-screens.md#4-chuỗi-chính-strings_searchxml)

### 2D. Việc phối hợp (theo dõi, người khác làm)

Theo bảng "Phối hợp với nhóm" của [plan.md](plan.md#phối-hợp-với-nhóm). A mở issue/comment trong PR của người phụ trách và đánh dấu xong khi họ xác nhận.

- [ ] T031 [P] Theo dõi (**D**, spec 111/110/120): ghi `nameKeywords` bằng `KeywordGenerator` (T010) hoặc `FN/_shared/search-keywords.ts` (T017) khi tạo/đổi tên cơ sở; Rules `nameKeywords is list && size() <= 60`. Ghi kết quả vào `specs/020-court-search/plan.md` mục Phối hợp
- [ ] T032 [P] Theo dõi (**D**): khi tạo cơ sở luôn ghi `ratingAvg = 0.0`, `ratingCount = 0`, `minPricePerHour = null` ([research R7](research.md#r7-sắp-xếp-theo-đánh-giá-và-giá-khi-thiếu-trường-fr-011)); ghi trong `specs/020-court-search/plan.md`
- [ ] T033 [P] Theo dõi (**D**): chốt bên cập nhật `minPricePerHour` 🔒 khi chủ sân sửa bảng giá (Edge Function), về `null` khi hết rule; ghi trong `specs/020-court-search/plan.md`
- [ ] T034 [P] Theo dõi (**D**): Rules `venues` (đọc công khai khi `ACTIVE`, chủ cơ sở và admin đọc mọi trạng thái), `courts`, `priceRules` (đọc công khai) theo [contract §2](contracts/security-rules-and-indexes.md#2-yêu-cầu-rules-gửi-người-phụ-trách-d-cho-venues-courts-pricerules-b-cho-slots). Khi xong: bỏ `it.todo` của V-1…V-5 trong `firestore-tests/test/search.test.ts`
- [ ] T035 [P] Theo dõi (**B**): Rules `slots` cho mọi người đọc; quyết định có bỏ `holdBy`/`bookingId` khỏi document không ([research R13](research.md#r13-quyền-đọc-cho-khách-chưa-đăng-nhập-fr-026-fr-027)). Khi xong: bỏ `it.todo` của S-1, S-2 trong `firestore-tests/test/search.test.ts`
- [ ] T036 [P] Theo dõi (**B**): 040 dùng `CORE/pricing/SlotPricing.kt`, `CORE/schedule/*` thay `CalculateTotalPriceUseCase`, `GenerateDailySlotsUseCase` tự viết; B review PR 2A
- [ ] T037 Theo dõi (**B + D**): chốt quy ước `daysOfWeek` (đề xuất ISO 1 = Thứ hai); sửa hằng số trong `CORE/pricing/SlotPricing.kt` và test T006 nếu khác đề xuất
- [ ] T038 [P] Theo dõi (**C**, spec 070): giữ `ratingAvg`, `ratingCount` luôn là số; thống nhất cách gắn nút trái tim vào `RES/layout/item_search_venue.xml` (interface ở `CORE/`)
- [ ] T039 [P] Theo dõi (**D**, spec 120): màn hình quản lý `districts`, `amenities` theo schema [data-model §2.2](data-model.md#22-districtsdistrictid-và-amenitiesamenityid), ẩn bằng `isActive = false`; ghi trong `specs/020-court-search/plan.md`

**Checkpoint**: PR 2A, 2B, 2C merge; `npm test` trong `firestore-tests` xanh (trừ `it.todo`); `./gradlew testDebugUnitTest` xanh; seed được dữ liệu demo lên Emulator.

---

## Phase 3: User Story 1 – Tìm sân theo tên hoặc quận (Priority: P1) 🎯 MVP

**Goal**: người chơi (kể cả khách) gõ tên hoặc chọn quận, thấy danh sách cơ sở `ACTIVE` sắp xếp theo đánh giá, tải thêm khi cuộn, mở chi tiết sân và quay lại giữ nguyên trạng thái.

**Independent Test**: với dữ liệu demo, gõ "hoa" thấy các sân có từ bắt đầu bằng "Hòa"; chọn "Quận 10" chỉ thấy cơ sở ở Quận 10; bấm một thẻ mở đúng chi tiết sân; bộ 1–4 và 12 của `search-demo.expected.json` đúng.

### Tests cho User Story 1 ⚠️ viết trước, phải fail

- [ ] T040 [P] [US1] Viết `TEST/feature/search/data/VenueMatcherTest.kt` (phần từ khóa và quận): US1-1, US1-2, US1-4; nhiều token đều phải là tiền tố của một từ trong tên; từ khóa < 2 ký tự không lọc theo tên
- [ ] T041 [P] [US1] Viết `TEST/feature/search/data/VenueQueryBuilderTest.kt` (phần đánh giá): `sort == RATING` → chiến lược S1, luôn có `status == ACTIVE`, thêm `districtId ==` khi có quận, `nameKeywords array-contains serverToken` khi có, `orderBy(ratingAvg DESC).orderBy(ratingCount DESC)`, `limit(25)` (khớp V1–V4)
- [ ] T042 [P] [US1] Viết `TEST/feature/search/data/FirestoreVenueSearchEngineTest.kt` với fake `VenueDataSource` (phần S1): đọc tiếp tới khi đủ 20 kết quả; dừng sau 4 lô (100 document) dù chưa đủ 20, `hasMore = true`; `nextPage` đọc tiếp từ con trỏ; hết dữ liệu → `hasMore = false`; `isFromCache` truyền lên `SearchPage`; `count()` trả số khi không có lọc trên máy và `null` khi có ([research R3](research.md#r3-hai-chiến-lược-phân-trang-fr-016-khi-có-lọc-trên-máy))
- [ ] T043 [P] [US1] Viết `TEST/feature/search/ui/SearchViewModelTest.kt` (Turbine, fake engine): gõ "ho" rồi "hoa" trong 300 ms chỉ chạy một lần tìm; kết quả của "ho" trả về muộn không ghi đè "hoa" (FR-018); 4 trạng thái Loading/Content/Empty/Error (FR-019); `isFromCache` → `staleBanner = true`, lỗi khi chưa có kết quả → `Error` có "Thử lại" (FR-020); `onLoadMore` của phiên cũ bị bỏ; `SearchCriteria` khôi phục từ `SavedStateHandle` (FR-017); khách vẫn tìm được (FR-026); `onVenueClicked` phát sự kiện điều hướng với `venueId`

### Implementation cho User Story 1

- [ ] T044 [P] [US1] Cài đặt `SEARCH/data/VenueMatcher.kt` (từ khóa qua `KeywordQuery.matches(nameWords)`, quận). T040 xanh
- [ ] T045 [P] [US1] Cài đặt `SEARCH/data/VenueQueryBuilder.kt` cho `sort == RATING` theo [contract §4](contracts/search-criteria.md#4-engine-tìm-kiếm). T041 xanh
- [ ] T046 [US1] Cài đặt `SEARCH/data/FirestoreVenueSearchEngine.kt` chiến lược S1 (lô 25, đủ 20, tối đa 4 lô mỗi lần tải, con trỏ trong `SearchSession`) và `count()` dùng `AggregateQuery` `count()` khi không có lọc trên máy; bind trong `SEARCH/di/SearchModule.kt`. T042 xanh
- [ ] T047 [P] [US1] Cài đặt `SEARCH/domain/usecase/SearchVenues.kt` (gọi engine, chuyển `SearchPage` lên; `DisplayPrice` do engine gắn sẵn, use case không tính lại)
- [ ] T048 [US1] Cài đặt `SEARCH/ui/SearchUiState.kt` theo [data-model §3.3](data-model.md#33-trạng-thái-màn-hình) và `SEARCH/ui/SearchViewModel.kt`: `criteria` trong `MutableStateFlow` lưu JSON vào `SavedStateHandle`; ô gõ `debounce(300)`; `distinctUntilChanged().flatMapLatest { firstPage }`; `onLoadMore` kiểm `sessionId`; `onRetry`; `onDistrictPicked`; `onRemoveChip` (chip quận); sự kiện một lần qua `Channel`. T043 xanh
- [ ] T049 [P] [US1] Tạo `RES/layout/item_search_venue.xml` (ảnh 16:9, tên, quận, khoảng cách ẩn, dòng "N sân trống" ẩn, giá, điểm "★ 4,6 (128)" hoặc "Chưa có đánh giá"; chừa chỗ nút trái tim cho C) và `RES/drawable/img_venue_placeholder.xml`
- [ ] T050 [US1] Cài đặt `SEARCH/ui/adapter/VenueResultAdapter.kt` (`ListAdapter` + `DiffUtil`; Coil tải ảnh từ `{SUPABASE_URL}/storage/v1/object/public/venue-photos/{path}`, lỗi thì ảnh mặc định; định dạng giá theo `DisplayPrice` và `strings_search.xml`; `contentDescription` đọc tên → quận → giá → điểm)
- [ ] T051 [US1] Tạo `RES/layout/fragment_search.xml`: ô tìm kiếm chỉ đọc; `FragmentContainerView` trống `@+id/ai_entry_container` (022) và `@+id/nearby_container` (021); tiêu đề "Tất cả sân · N" / "N sân phù hợp" / "Kết quả phù hợp"; hàng chip nhanh "Bộ lọc", "Quận", "Giờ trống", "Giá"; `ChipGroup` chip đang áp dụng; nút sắp xếp (US3 gắn hành vi); `RecyclerView`; view trạng thái đang tải, rỗng (`search_empty_title`, `search_empty_hint`, nút "Xóa bộ lọc"), lỗi ("Thử lại"); banner `search_offline_stale`; nút quay lại khi có điều kiện
- [ ] T052 [US1] Cài đặt `SEARCH/ui/SearchFragment.kt`: `hiltNavGraphViewModels(R.id.nav_search)`; thu `uiState` bằng `repeatOnLifecycle(STARTED)`; hai chế độ (trống: hiện vùng 021/022; có điều kiện: ẩn, hiện nút quay lại xóa điều kiện); `StateRestorationPolicy.PREVENT_WHEN_EMPTY`; tải thêm khi cuộn cuối; chạm ô tìm kiếm → `searchInputFragment`; chạm thẻ → `nav_court_detail` với `venueId` (FR-017)
- [ ] T053 [US1] Cài đặt `SEARCH/ui/input/SearchInputFragment.kt` + `RES/layout/fragment_search_input.xml` phần gõ: ô nhập có focus, nút tìm trên bàn phím (`IME_ACTION_SEARCH`) → `SearchViewModel.onSubmitKeyword` rồi quay về; khi đang gõ hiện gợi ý quận khớp tên từ `CatalogRepository` (lọc trên máy bằng `VietnameseNormalizer`), chọn gợi ý → `onDistrictPicked`. Phần lịch sử làm ở US4
- [ ] T054 [US1] Cài đặt `SEARCH/ui/filter/SearchFilterBottomSheet.kt` + `RES/layout/bottom_sheet_search_filter.xml` + `SEARCH/ui/filter/SearchFilterViewModel.kt` phần **quận** (dropdown danh mục, "Xóa bộ lọc" giữ từ khóa và quận theo FR-009, nút "Xem kết quả"); danh mục đang tải → khung chờ, lỗi → "Không tải được danh mục" + "Thử lại" tại mục, không chặn phần khác; bản nháp chỉ áp dụng khi bấm nút chính; mở từ chip "Bộ lọc"/"Quận" và chip "Sửa". Các mục khác thêm ở US2
- [ ] T055 [US1] Cài đặt `SEARCH/ui/adapter/FilterChipBinder.kt` cho chip quận (nhãn theo danh mục, nút "x", `contentDescription` "Bỏ bộ lọc Quận 10", vùng chạm 48dp)

**Checkpoint**: US1 chạy độc lập; quickstart §5 bước 1, 8, 9, 10 đạt.

---

## Phase 4: User Story 2 – Lọc theo giá, khung giờ còn trống và tiện ích (Priority: P1) 🎯 MVP

**Goal**: lọc khoảng giá, khung giờ còn trống (ít nhất một sân con trống trọn khung), tiện ích "đủ tất cả"; chip có thể bỏ từng cái hoặc xóa hết; giá trên thẻ khớp điều kiện lọc.

**Independent Test**: với dữ liệu demo, lọc 19:00–21:00 ngày mai chỉ thấy A (không B, C; có D); thêm "Gửi xe" chỉ còn cơ sở có gửi xe; bỏ chip khung giờ thì B xuất hiện lại; bộ 5–8 và 10 của `search-demo.expected.json` đúng.

### Tests cho User Story 2 ⚠️ viết trước, phải fail

- [ ] T056 [P] [US2] Viết `TEST/feature/search/data/AvailabilityEvaluatorTest.kt` (fake data source cho `priceRules`, `courts`, `slots`): US2-3; US2-4 (hai sân trống nửa khung → không khớp); US2-5 (`HOLD` có `holdExpiresAt <= now` coi là trống, còn hạn là bận); `BOOKED`, `BLOCKED` bận; US2-6 (khung ngoài `openTime`/`closeTime` → loại, không đọc slots); sân `isActive == false` không tính; `freeCourtCount` đúng; giá khung ngoài khoảng → loại và **không** đọc `courts`/`slots`; bảng giá không phủ hết khung → `DisplayPrice.Contact`, không khớp lọc giá; tối đa 6 cơ sở chạy cùng lúc
- [ ] T057 [P] [US2] Bổ sung `TEST/feature/search/data/VenueMatcherTest.kt`: tiện ích "đủ tất cả" (US2-7); cắt giá khi có khung giờ (`minPricePerHour > priceMax` → loại); `minPricePerHour == null` không khớp khi có lọc giá
- [ ] T058 [P] [US2] Bổ sung `TEST/feature/search/data/VenueQueryBuilderTest.kt` và `FirestoreVenueSearchEngineTest.kt`: lọc giá không khung giờ → `minPricePerHour >= / <=` (V5–V8 khi sắp xếp đánh giá); `PRICE_ASC` + khung giờ → S2, đọc tối đa 150 ứng viên (`limit(151)`), vượt thì `capped = true`; S2 sắp theo cận dưới `minPricePerHour` và chỉ gọi `AvailabilityEvaluator` tới khi đủ 20 (đếm số lần gọi trên fake), thứ tự trang giống hệt cách kiểm tra hết rồi sắp xếp; quá 4 đợt mà chưa đủ 20 thì trả trang hiện có; S1 có khung giờ chỉ kiểm tra giờ trống cho lô đang cần; `totalCount` chính xác ở S2 không khung giờ, `null` khi có khung giờ
- [ ] T059 [P] [US2] Viết `TEST/feature/search/ui/SearchFilterViewModelTest.kt`: chọn hôm nay không chọn được giờ bắt đầu đã qua, giờ kết thúc luôn sau giờ bắt đầu (FR-007, US2-9); ngày từ hôm nay tới +30; khoảng giá ngược không áp dụng được và báo lỗi tại bộ lọc; đầu phải 200.000 = không giới hạn trên; `previewCount` debounce 400 ms, `null` → nút "Xem kết quả"
- [ ] T060 [P] [US2] Bổ sung `TEST/feature/search/ui/SearchViewModelTest.kt`: bỏ một chip chỉ bỏ bộ lọc đó; "Xóa bộ lọc" giữ từ khóa và quận (US2-8); thứ tự chip quận → giá → khung giờ → từng tiện ích; `capped` → hiện `search_capped`

### Implementation cho User Story 2

- [ ] T061 [P] [US2] Bổ sung `SEARCH/data/VenueDataSource.kt` / `FirestoreVenueDataSource.kt`: `priceRules(venueId)`, `activeCourts(venueId)`, `busySlots(venueId, date, windowStart, windowEnd)` (query `venueId ==`, `date ==`, `startAt >= windowStart`, `startAt < windowEnd`, index S1)
- [ ] T062 [P] [US2] Cài đặt `SEARCH/data/VenueStaticCache.kt`: cache `priceRules` và `courts` theo `venueId`, hết hạn sau 10 phút, an toàn khi gọi song song (`Mutex`)
- [ ] T063 [US2] Cài đặt `SEARCH/data/AvailabilityEvaluator.kt` theo [research R4](research.md#r4-lọc-khung-giờ-còn-trống-fr-006-và-giá-theo-khung-fr-005): thứ tự giờ mở cửa → giá khung (`SlotPricing`) → sân con → slot bận; `Semaphore(6)`; trả `VenueResult` với `DisplayPrice.ForWindow` và `freeCourtCount`. T056 xanh
- [ ] T064 [US2] Bổ sung `SEARCH/data/VenueMatcher.kt` (tiện ích `containsAll`, giờ mở cửa, cắt giá) và `SEARCH/data/VenueQueryBuilder.kt` (lọc khoảng giá, chọn S2 khi `PRICE_ASC` có khung giờ, `limit(151)`). T057 xanh
- [ ] T065 [US2] Bổ sung `SEARCH/data/FirestoreVenueSearchEngine.kt`: chiến lược S2 (đọc document ứng viên ≤ 150, lọc rẻ, sắp theo cận dưới, kiểm tra giờ trống lười theo đợt 6 tới khi đủ 20 hoặc 4 đợt, sắp theo giá khung, giữ phần còn lại trong `SearchCursor`, `capped`; xem [research R3](research.md#r3-hai-chiến-lược-phân-trang-fr-016-khi-có-lọc-trên-máy), R4 mục tệ nhất); S1 gọi `AvailabilityEvaluator` lười theo lô; `count()` theo [research R3](research.md#r3-hai-chiến-lược-phân-trang-fr-016-khi-có-lọc-trên-máy). T058 xanh
- [ ] T066 [US2] Bổ sung `SEARCH/ui/filter/SearchFilterViewModel.kt` + `RES/layout/bottom_sheet_search_filter.xml`: `RangeSlider` 0–200.000 bước 10.000 (đầu phải "200.000 đ+"); 31 ô ngày (Hôm nay, Mai, T2…CN + ngày); "Từ"/"Đến" bước 30 phút dùng `SlotMath`; ghi chú "Chỉ hiện sân còn ít nhất một sân con trống suốt khung này."; tiện ích dạng chip chọn nhiều từ `CatalogRepository` (icon `ic_amenity_<iconKey>`); nút "Xem N sân" / "Xem kết quả" theo `previewCount`; chip nhanh "Giờ trống", "Giá" mở sheet cuộn tới mục tương ứng. T059 xanh
- [ ] T067 [P] [US2] Tạo `RES/drawable/ic_amenity_parking.xml`, `ic_amenity_drinks.xml`, `ic_amenity_shower.xml`, `ic_amenity_racket_rental.xml` và icon mặc định `ic_amenity_default.xml`
- [ ] T068 [US2] Bổ sung `SEARCH/ui/adapter/FilterChipBinder.kt` và `SEARCH/ui/SearchViewModel.kt`: chip giá ("≤100k/giờ", "≥50k/giờ", "50k–100k/giờ"), chip khung giờ ("Mai 19:00–21:00"), mỗi tiện ích một chip; `onApplyFilters(draft)`, `onClearFilters` (`SearchCriteria.clearFilters()`); banner `search_capped`. T060 xanh
- [ ] T069 [US2] Bổ sung `SEARCH/ui/adapter/VenueResultAdapter.kt`: giá "X đ/giờ · HH:mm–HH:mm" khi có khung (FR-015), dòng `search_free_courts`; điều hướng sang chi tiết sân mang thêm `date`, `startTime`, `endTime` của khung (trong `SEARCH/ui/SearchFragment.kt`)

**Checkpoint**: US1 + US2 là MVP; quickstart §5 bước 2–4 đạt; bộ điều kiện 1–8, 10, 12 đúng trên Emulator.

---

## Phase 5: User Story 3 – Sắp xếp kết quả và xem khoảng cách (Priority: P2)

**Goal**: ba cách sắp xếp đúng FR-011; "Gần nhất" xin quyền vị trí đúng lúc, có giải thích; thẻ hiện khoảng cách khi có vị trí; từ chối thì mọi chức năng khác vẫn chạy.

**Independent Test**: với 3 cơ sở có giá, điểm, vị trí khác nhau, đổi lần lượt ba cách sắp xếp và kiểm tra thứ tự; từ chối quyền vị trí thì "Gần nhất" không áp dụng, hai cách kia vẫn chạy; bộ 9 và 11 của `search-demo.expected.json` đúng.

### Tests cho User Story 3 ⚠️ viết trước, phải fail

- [ ] T070 [P] [US3] Viết `TEST/feature/search/data/ResultSorterTest.kt`: `RATING` (điểm giảm dần, cùng điểm nhiều lượt trước, chưa có đánh giá cuối); `PRICE_ASC` theo giá đang hiển thị (giá khung nếu có), "Liên hệ" cuối (US3-1, FR-011); `NEAREST` theo khoảng cách tăng dần, cùng khoảng cách thì theo đánh giá
- [ ] T071 [P] [US3] Bổ sung `TEST/feature/search/data/VenueQueryBuilderTest.kt` và `FirestoreVenueSearchEngineTest.kt`: `PRICE_ASC` không khung giờ → S1 hai pha (pha 1 `minPricePerHour >= priceMin ?: 0` `orderBy ASC`, V9–V12; hết pha 1 mới sang pha 2 `minPricePerHour == null` theo đánh giá; có lọc giá thì không chạy pha 2); `NEAREST` → S2 với `origin`, thẻ có `distanceMeters`
- [ ] T072 [P] [US3] Bổ sung `TEST/feature/search/ui/SearchViewModelTest.kt` (fake `LocationProvider`): chọn "Gần nhất" khi chưa có quyền → sự kiện hiện giải thích, "Để sau" giữ sắp xếp cũ; quyền bị từ chối → sắp xếp trở về lựa chọn trước, sự kiện snackbar có hành động "Cài đặt" (US3-5); có quyền nhưng `current()` trả `null` → giữ sắp xếp cũ, báo "Không lấy được vị trí" (US3-6); đã có quyền thì mọi cách sắp xếp đều có khoảng cách (FR-013); vị trí không nằm trong `SearchCriteria` (FR-014)

### Implementation cho User Story 3

- [ ] T073 [P] [US3] Cài đặt `SEARCH/data/ResultSorter.kt`. T070 xanh
- [ ] T074 [US3] Bổ sung `SEARCH/data/VenueQueryBuilder.kt` và `SEARCH/data/FirestoreVenueSearchEngine.kt`: hai pha cho `PRICE_ASC` (pha lưu trong `SearchSession`), `NEAREST` dùng S2 + `GeoDistance` từ `origin` (khoảng cách là khóa chính xác nên có khung giờ cũng kiểm tra lười theo thứ tự khoảng cách), mọi kết quả có `distanceMeters` khi có `origin`. T071 xanh
- [ ] T075 [P] [US3] Cài đặt `SEARCH/ui/sort/SortDialog.kt` + `RES/layout/dialog_search_sort.xml` (3 lựa chọn, mặc định "Đánh giá cao nhất") và `SEARCH/ui/location/LocationRationaleDialog.kt` (`search_location_rationale`, "Tiếp tục" / "Để sau")
- [ ] T076 [US3] Bổ sung `SEARCH/ui/SearchViewModel.kt`: `onSortSelected`, `onLocationPermissionResult`, lấy vị trí qua `LocationProvider`, giữ vị trí chỉ trong bộ nhớ, `locationAvailable` trong `SearchUiState`. T072 xanh
- [ ] T077 [US3] Bổ sung `SEARCH/ui/SearchFragment.kt`: `registerForActivityResult(RequestMultiplePermissions)` cho `ACCESS_COARSE_LOCATION` + `ACCESS_FINE_LOCATION` (một trong hai được cấp là đủ); hiện `LocationRationaleDialog` trước; snackbar từ chối có nút mở `Settings.ACTION_APPLICATION_DETAILS_SETTINGS`; nhãn nút sắp xếp theo lựa chọn hiện tại
- [ ] T078 [US3] Bổ sung `SEARCH/ui/adapter/VenueResultAdapter.kt`: hiện khoảng cách bằng `DistanceFormatter` khi `distanceMeters != null`, ẩn khi không có

**Checkpoint**: quickstart §5 bước 5–7 đạt; từ chối quyền không làm hỏng chức năng nào (SC-005).

---

## Phase 6: User Story 4 – Lịch sử tìm kiếm (Priority: P3)

**Goal**: người đã đăng nhập thấy 10 lần tìm gần nhất của mình, bấm để chạy lại (bỏ phần không còn hợp lệ), xóa từng mục hoặc xóa hết; khách không có lịch sử. Phase này cũng làm **cửa vào cho spec 022/021** (`applyExternal` + sanitizer) vì dùng chung sanitizer với khôi phục lịch sử.

**Independent Test**: tìm 3 lần với từ khóa/bộ lọc khác nhau, mở lại ô tìm kiếm thấy đủ 3 mục với lần mới nhất ở đầu; bấm một mục thì từ khóa và chip được khôi phục; xóa một mục và xóa hết đều còn hiệu lực sau khi mở lại app; tài khoản khác không thấy lịch sử này.

### Tests cho User Story 4 ⚠️ viết trước, phải fail

- [ ] T079 [P] [US4] Viết `TEST/feature/search/domain/SearchCriteriaSanitizerTest.kt` theo mọi dòng [bảng sanitizer](contracts/search-criteria.md#2-sanitizer): `keyword` > 100 ký tự cắt còn 100 (`KEYWORD_TRUNCATED`); quận không có/ẩn (`UNKNOWN_DISTRICT`); từng tiện ích không có/ẩn bị bỏ riêng (`UNKNOWN_AMENITY`); giá âm hoặc ngược (`INVALID_PRICE`); làm tròn bội số 10.000; `priceMax ≥ 200.000` → `null`; `priceMin == 0` → `null`; ngày đã qua (`PAST_DATE`, US4-3); quá 30 ngày (`DATE_OUT_OF_RANGE`); giờ lệch mốc, kết thúc ≤ bắt đầu, hôm nay đã qua giờ (`INVALID_TIME`); bỏ một phần không làm hỏng phần còn lại (FR-010)
- [ ] T080 [P] [US4] Viết `TEST/feature/search/data/FirestoreSearchHistoryRepositoryTest.kt` với fake `SearchHistoryDataSource`: ID = 20 ký tự hex đầu của SHA-256 `canonicalKey()`; tìm lại cùng tổ hợp → cập nhật đúng document, giữ `createdAt`, không thêm mục (US4-4); sau khi ghi mục thứ 11 thì mục cũ nhất bị xóa (US4-5); dọn lỗi thì lần ghi sau dọn tiếp; `observe()` chỉ trả tối đa 10 mục mới nhất trước; không ghi khi `hasAnyCondition() == false`; không có trường vị trí
- [ ] T081 [P] [US4] Viết `TEST/feature/search/ui/SearchHistoryViewModelTest.kt`: khách → `Guest` và không gọi repository (US4-8, FR-027); `Empty`, `Content`, `Error`; `onDelete`, `onDeleteAll` (sau xác nhận); bấm mục → chạy sanitizer và phát sự kiện `RestoreCriteria(sanitizeResult)` (không giữ tham chiếu tới `SearchViewModel`, xem [contracts/ui-screens.md §3](contracts/ui-screens.md#3-viewmodel))
- [ ] T082 [P] [US4] Viết `TEST/feature/search/ui/HistorySummaryFormatterTest.kt`: dòng 1 = từ khóa, không có thì tên quận, không có thì "Tất cả sân"; dòng 2 = nhãn bộ lọc nối " · " cộng cách sắp xếp khác mặc định, hoặc "Không có bộ lọc" (đúng các ví dụ trong wireframe bước 4); nhãn "Mai" tính lại theo ngày hiện tại
- [ ] T083 [P] [US4] Bổ sung `TEST/feature/search/ui/SearchViewModelTest.kt`: ghi lịch sử khi bấm tìm trên bàn phím, áp dụng bảng lọc, chọn quận, bỏ chip, đổi sắp xếp, chọn mục lịch sử, mở thẻ; **không** ghi theo từng ký tự gõ (FR-021); khách không ghi; `applyExternal` chạy sanitizer, thay toàn bộ `criteria`, có `dropped` thì phát snackbar (US4-3 "Ngày đã qua, vui lòng chọn lại"); `applyExternal` với `sort = NEAREST` chạy luồng quyền vị trí của US3; khôi phục `SavedStateHandle` có ngày đã qua thì sanitize

### Implementation cho User Story 4

- [ ] T084 [US4] Cài đặt `SEARCH/domain/SearchCriteriaSanitizer.kt` (inject `CatalogRepository`, `Clock`). T079 xanh
- [ ] T085 [P] [US4] Tạo `SEARCH/data/dto/SearchHistoryDto.kt` theo [data-model §2.1](data-model.md#21-usersuidsearchhistoryhistoryid) (`keyword` "≤ 100 ký tự"; `districtId?`; `priceMin?`, `priceMax?` "≥ 0, bội số 10.000"; `date?`, `startTime?`, `endTime?` "cùng có hoặc cùng không"; `amenityIds` "≤ 20 phần tử, đã sắp xếp"; `sort`; `searchedAt`, `createdAt`, `updatedAt` bằng `serverTimestamp()`) và `SEARCH/data/SearchHistoryDataSource.kt` + `FirestoreSearchHistoryDataSource.kt` (transaction `get` → `set` đủ trường hoặc `update` trừ `createdAt`; query `orderBy(searchedAt DESC).limit(10)`; query dọn `startAfter(mục thứ 10).limit(5)`; `delete`; `WriteBatch` xóa hết)
- [ ] T086 [US4] Cài đặt `SEARCH/data/FirestoreSearchHistoryRepository.kt` (lấy `uid` qua `SessionManager`, không gọi `FirebaseAuth`) và bind trong `SEARCH/di/SearchModule.kt`. T080 xanh
- [ ] T087 [P] [US4] Cài đặt `SEARCH/domain/usecase/RecordSearch.kt`, `ObserveHistory.kt`, `DeleteHistory.kt`, `ClearHistory.kt`
- [ ] T088 [US4] Bổ sung `SEARCH/ui/SearchViewModel.kt`: `applyExternal(criteria, source)` theo [contract §3](contracts/search-criteria.md#3-cửa-vào-cho-spec-022-và-021) (cửa vào cho 022 và 021, ghi KDoc trỏ tới contract); gọi `RecordSearch` ở đúng các thao tác của [research R11](research.md#r11-lịch-sử-tìm-kiếm-fr-021-đến-fr-025); sanitize khi khôi phục `SavedStateHandle`. T083 xanh
- [ ] T089 [P] [US4] Cài đặt `SEARCH/ui/input/HistorySummaryFormatter.kt`. T082 xanh
- [ ] T090 [US4] Cài đặt `SEARCH/ui/input/SearchHistoryViewModel.kt`; `SearchInputFragment` thu `RestoreCriteria` và gọi `SearchViewModel.applyExternal(criteria, source = HISTORY)`. T081 xanh
- [ ] T091 [P] [US4] Tạo `RES/layout/item_search_history.xml` (icon lịch sử, 2 dòng, nút "x" có `contentDescription`) và `SEARCH/ui/adapter/HistoryAdapter.kt`
- [ ] T092 [US4] Bổ sung `SEARCH/ui/input/SearchInputFragment.kt` + `RES/layout/fragment_search_input.xml`: khi ô trống hiện "Tìm gần đây", "Xóa tất cả" (hộp xác nhận), danh sách, ghi chú `search_history_note`; khách thấy `search_history_guest` kèm `LoginPromptView` (010 T083) mở đăng nhập qua `AuthNavigator` với `returnTo` là màn hình tìm kiếm; bấm mục → `applyExternal(HISTORY)` rồi quay về `searchFragment`

**Checkpoint**: quickstart §5 bước 11–14 đạt; H-1…H-15 xanh (SC-006).

---

## Phase 7: Polish & Cross-Cutting

- [ ] T093 [P] Lưới 2 cột cho `w600dp`: `RES/values-w600dp/integers.xml` (`search_grid_span = 2`) và `GridLayoutManager` trong `SEARCH/ui/SearchFragment.kt`; kiểm tra bảng lọc trên máy tính bảng
- [ ] T094 [P] Rà chế độ tối và màu theo theme trong `RES/layout/fragment_search.xml`, `item_search_venue.xml`, `bottom_sheet_search_filter.xml`, `fragment_search_input.xml`; không hardcode màu/chuỗi (constitution V); ảnh chụp sáng/tối cho PR
- [ ] T095 [P] Rà TalkBack: `contentDescription` cho chip, thẻ, nút sắp xếp, icon tiện ích; vùng chạm ≥ 48dp; thứ tự đọc của thẻ (tên → quận → khoảng cách → giá → điểm) trong `RES/layout/item_search_venue.xml`
- [ ] T096 Thêm log debug `SearchTiming` (từ lúc đổi điều kiện tới khi hiện trang đầu) và `SearchReads` (cộng `QuerySnapshot.size()`, +1 cho query rỗng) trong `SEARCH/data/FirestoreVenueSearchEngine.kt`, chỉ bật ở `BuildConfig.DEBUG`
- [ ] T097 Chạy đo theo [quickstart §4](quickstart.md#4-đo-thời-gian-và-lượt-đọc-sc-001-quota) cho 6 kịch bản × 20 lần trên 4G (kịch bản 6 là S2 tệ nhất với seed `--stress`, thêm cờ này vào `scripts/seed/seed.mjs`); ghi bảng kết quả vào mô tả PR; quá 3 giây hoặc lượt đọc vượt ước lượng nhiều thì ghi vào `specs/020-court-search/research.md` R4 và đề xuất chỉnh (cỡ lô, số luồng)
- [ ] T098 Deploy `firestore.indexes.json` lên project thật, seed `search-demo.json` (có xác nhận), chạy lại 12 bộ điều kiện theo [quickstart §3](quickstart.md#3-kiểm-thử-với-dữ-liệu-demo); có lỗi `requires an index` thì thêm index vào `firestore.indexes.json` và ghi vào research R8
- [ ] T099 [P] (Nên có) Viết `ATEST/feature/search/VenueSearchEngineEmulatorTest.kt`: seed Emulator bằng `SEED/seed.mjs`, chạy 12 bộ điều kiện của `SEED/search-demo.expected.json` qua `FirestoreVenueSearchEngine` thật, so đúng thứ tự (SC-003, SC-004)
- [ ] T100 [P] (Nên có) Viết `ATEST/feature/search/SearchFlowTest.kt` (Espresso): gõ tên → áp dụng bộ lọc → mở chi tiết sân → quay lại giữ chip và vị trí cuộn
- [ ] T101 Chạy toàn bộ [quickstart.md](quickstart.md) §1–§5a (gồm bước 14: xóa tài khoản xong `searchHistory` không còn); `./gradlew lint testDebugUnitTest assembleDebug` không lỗi; ktlint sạch
- [ ] T102 [P] Cập nhật `docs/design/database/README.md` theo [danh sách cuối data-model.md](data-model.md#danh-sách-thay-đổi-cần-đưa-vào-docsdesigndatabasereadmemd) (mục 4.3, thêm mục `searchHistory`/`districts`/`amenities`, mục 5, 6, 7, 9)
- [ ] T103 [P] Rà lại `specs/020-court-search/spec.md` và `specs/020-court-search/ui/README.md` theo [bảng R15](research.md#r15-những-chỗ-cần-sửa-spec-hoặc-tài-liệu-khác) (dòng 1, 2, 3, 6, 7 đã sửa ngày 19/10/2026); nếu khi làm có thay đổi giới hạn 150, cỡ trang hay cách đếm thì cập nhật lại
- [ ] T104 [P] Ghi log AI cho `/speckit-plan` và `/speckit-tasks` của spec 020 vào `docs/ai-log/AI_log_1.md` (chỉ ghi các lệnh `/speckit-*`, theo mẫu các mục trước)
- [ ] T105 Cập nhật trạng thái spec 020 trong `docs/features/README.md` và `docs/features/02-court-search/README.md`
- [ ] T106 Thử với 5 người dùng theo [quickstart §5a](quickstart.md#5a-thử-với-người-dùng-sc-002-sc-007) (SC-002 dưới 1 phút, SC-007 ≥ 4/5); ghi kết quả và đề xuất sửa vào mô tả PR polish

---

## Dependencies & Execution Order

### Phụ thuộc giữa các phase

- **Setup (Phase 1)**: T001 phụ thuộc PR `chore(core)` của 010 (Phase 1 + 2A của tasks 010). **Chưa merge thì chặn toàn bộ.**
- **2A `chore(core)` phần 2**: sau Setup. **Chặn mọi user story**, và B (040), D (111) cũng cần → mở PR sớm nhất.
- **2B Rules/index/seed**: làm song song với 2A; T022, T023 cần T017 (bản TS sinh `nameKeywords`).
- **2C domain**: sau 2A (dùng `TimeOfDay`, `AppError`); T029 cần T009 (`normalize`).
- **2D phối hợp**: theo dõi song song, không chặn code của A; chỉ chặn việc bỏ `it.todo` (T034, T035) và quy ước ngày (T037 → có thể phải sửa T012).
- **US1 (Phase 3)**: sau Phase 2.
- **US2 (Phase 4)**: sau US1 (mở rộng `VenueMatcher`, `VenueQueryBuilder`, engine, bảng lọc, chip).
- **US3 (Phase 5)**: sau US1; độc lập với US2 về logic, nhưng sắp xếp giá có khung giờ (S2) cần T065 của US2.
- **US4 (Phase 6)**: sau US1; sanitizer (T084) cần `CatalogRepository` (T015). Spec 022 cần `applyExternal` (T088) nên **022 bắt đầu sau US4**.
- **Polish (Phase 7)**: sau các story cần có trong bản demo.

```text
Phase 1 ──▶ 2A ─┬─▶ 2C ──▶ US1 ──┬──▶ US2 ──┐
               │                 ├──▶ US3 ──┼──▶ Polish
     2B ───────┘ (song song)     └──▶ US4 ──┘
     2D (theo dõi, song song suốt quá trình)
```

### Phụ thuộc ngoài nhóm (bảng "Phối hợp với nhóm" trong [plan.md](plan.md#phối-hợp-với-nhóm))

| Task | Cần | Nếu chưa có |
|---|---|---|
| T001 | **A** (chính mình): PR `chore(core)` của 010, gồm chế độ khách (010 T080–T083) | Làm 010 Phase 1 + 2A + 2D trước; thiếu chế độ khách thì khách không vào được tìm kiếm (FR-026) |
| T018, T034 | **D**: Rules `venues`, `courts`, `priceRules` | V-1…V-5 để `it.todo`. Rules nền chặn mọi collection chưa có match, nên app không đọc được `venues` trên Emulator: thử bằng Rules tạm `allow read: if true` **chỉ ở máy mình**, không commit |
| T018, T035 | **B**: Rules `slots` | S-1, S-2 để `it.todo`; thử như dòng trên |
| T012, T037 | **B + D**: quy ước `daysOfWeek` | Dùng ISO, để hằng số một chỗ, sửa khi chốt |
| T022, T032, T033 | **D**: giá trị mặc định khi tạo cơ sở, cập nhật `minPricePerHour` | Dữ liệu demo tự ghi đúng; dữ liệu do app của D tạo có thể thiếu trường → cơ sở không hiện khi sắp xếp |
| T049, T038 | **C**: nút trái tim | Thẻ chừa chỗ, không hiện nút |
| T053, T052 | **A** (spec 030): màn hình chi tiết sân | `CourtDetailPlaceholderFragment` (T030) |

### Trong mỗi user story

Test (fail) → data/domain (test xanh) → ViewModel (test xanh) → layout / Fragment → nav graph.

### Cơ hội làm song song

- Phase 1: T002 song song với T001.
- 2A: T003–T008 cùng lúc; T009, T011, T013, T014, T015, T017 khác file nhau.
- 2B làm song song toàn bộ với 2A; trong 2B: T018, T020, T021 cùng lúc.
- 2C: T025, T027, T028 song song.
- 2D: mọi task theo dõi song song.
- US1: T040–T043 song song; T044, T045, T047, T049 song song.
- US2: T056–T060 song song; T061, T062, T067 song song.
- US3 và US4 làm song song được sau US1 (khác file, trừ `SearchViewModel.kt` và `SearchViewModelTest.kt`: gộp tuần tự).

## Parallel Example: Phase 2A

```bash
# Viết test trước, cùng lúc:
Task: "T003 Vector test trong supabase/functions/_shared/search-keywords.vectors.json"
Task: "T005 Test core/schedule trong app/src/test/.../core/schedule/"
Task: "T006 SlotPricingTest trong app/src/test/.../core/pricing/SlotPricingTest.kt"
Task: "T007 Test core/location trong app/src/test/.../core/location/"
Task: "T008 FirestoreCatalogRepositoryTest trong app/src/test/.../core/catalog/"
# T004 sau T003 (đọc file vector)

# Sau đó: T009 → T010; T011 → T012; T013, T014, T015, T017 song song; T016 cuối
```

## Parallel Example: User Story 1

```bash
Task: "T040 VenueMatcherTest"
Task: "T041 VenueQueryBuilderTest"
Task: "T042 FirestoreVenueSearchEngineTest"
Task: "T043 SearchViewModelTest"
# Rồi T044, T045, T047, T049 song song → T046 → T048 → T050–T055
```

## Implementation Strategy

### Chia PR

1. **PR `chore(core)` phần 2** (2A: T003–T017): B, D review vì dùng chung. Merge sớm nhất.
2. **PR `feat(search): rules, index, seed`** (2B: T018–T024): song song với PR 1. Báo B về index `slots`.
3. **PR `feat(search): domain`** (2C: T025–T030).
4. **PR `feat(search): tìm theo tên và quận`** (US1).
5. **PR `feat(search): bộ lọc giá, giờ trống, tiện ích`** (US2).
6. **PR `feat(search): sắp xếp và khoảng cách`** (US3) và **PR `feat(search): lịch sử tìm kiếm`** (US4), song song.
7. **PR `chore(search): polish`** (Phase 7, gồm T106 thử người dùng) và PR `docs` (T102–T105).

### MVP (US1 + US2)

1. Phase 1 → 2A + 2B song song → 2C.
2. US1 → **dừng và kiểm tra**: quickstart §5 bước 1, 8, 9, 10.
3. US2 → **dừng và kiểm tra**: bước 2–4; bộ điều kiện 1–8, 10, 12 đúng trên Emulator. Đây là phần demo cho mốc nhóm 02.

### Tăng dần

4. US3 và US4 song song (gộp thay đổi `SearchViewModel` cẩn thận).
5. Báo người làm 022 bắt đầu sau khi US4 merge (`applyExternal`).
6. Polish, đặc biệt T097 (đo SC-001) và T098 (index trên project thật) trước buổi demo.

### Ghi chú

- Mỗi task hoặc nhóm task liên quan: một commit Conventional Commits, scope `core` cho 2A, `search` cho phần còn lại (ví dụ `feat(search): lọc giờ trống theo khung`).
- Không ghi project Firebase thật khi chưa xác nhận (T024, T098).
- Không commit `app/google-services.json`, `local.properties`, service account.
