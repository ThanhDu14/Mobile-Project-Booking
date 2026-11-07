# Implementation Plan: Tìm kiếm sân

**Branch**: `feature/02-court-search-plan` | **Date**: 2026-10-19 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/020-court-search/spec.md`

## Summary

Người chơi, kể cả khách chưa đăng nhập, tìm cơ sở sân theo tên (không dấu, khớp phần đầu của từ) và quận. Bộ lọc gồm khoảng giá, khung giờ còn trống và tiện ích; sắp xếp theo đánh giá, giá hoặc khoảng cách. Người đã đăng nhập có lịch sử 10 lần tìm gần nhất. Bộ lọc là đầu vào dùng chung với spec 022 (AI) và 021 (bản đồ).

**Cách làm:**
- **Query một điều kiện chính, lọc phần còn lại trên máy** ([R2](research.md#r2-cách-chạy-tìm-kiếm-a-query-một-điều-kiện-chính--lọc-trên-máy)).
  - Firestore giới hạn một `array-contains` mỗi query, và `array-contains-any` là phép OR, nên tên và tiện ích không lọc chung được ([R1](research.md#r1-giới-hạn-truy-vấn-firestore-áp-vào-bộ-lọc-của-spec-020)).
  - Firestore lọc `status`, quận, **một** token tên (`nameKeywords`) và khoảng `minPricePerHour`. Máy lọc các token còn lại, tiện ích "đủ tất cả", giờ mở cửa và giờ trống.
  - Đã so sánh với đổi `amenityIds` thành map (số index tăng theo tổ hợp, phải đổi schema của D) và Edge Function `search-venues` (lượt đọc như cũ, thêm độ trễ, khách phải gọi được). Cả hai bị loại.
- **Hai chiến lược phân trang** ([R3](research.md#r3-hai-chiến-lược-phân-trang-fr-016-khi-có-lọc-trên-máy)):
  - Sắp xếp theo đánh giá, hoặc theo giá khi không có khung giờ: Firestore `orderBy`, đọc lô 25 tới khi đủ 20 kết quả (tối đa 4 lô mỗi lần tải).
  - "Gần nhất", hoặc giá khi có khung giờ: đọc tối đa 150 ứng viên, sắp theo cận dưới (khoảng cách hoặc `minPricePerHour`), kiểm tra giờ trống lười tới khi đủ 20, chia trang trong bộ nhớ.
- **Giờ trống và giá theo khung** ([R4](research.md#r4-lọc-khung-giờ-còn-trống-fr-006-và-giá-theo-khung-fr-005)): mỗi cơ sở ứng viên đọc `priceRules`, `courts` (cache 10 phút) và các `slots` bận trong khung (query `venueId`, `date`, `startAt`). `HOLD` quá `holdExpiresAt` coi là trống.
  - Ước lượng 35–105 lượt đọc mỗi lần tìm có khung giờ khi đã chọn quận, khoảng 1,2–1,8 giây trên 4G.
  - Logic giá 30 phút và lưới giờ đưa lên `core/pricing`, `core/schedule` để dùng chung với 040 ([R5](research.md#r5-logic-giá-và-lưới-giờ-dùng-chung-với-spec-040-b)).
- **Chốt mục 9 điểm 6 của đặc tả CSDL: dùng `nameKeywords`** ([R6](research.md#r6-tìm-theo-tên-dùng-namekeywords-chốt-mục-9-điểm-6-đặc-tả-csdl)). Tiền tố 2–15 ký tự của từng từ đã bỏ dấu, tối đa 60 phần tử. Có bản Kotlin (`core/text`) và TypeScript (`_shared/search-keywords.ts`) chạy chung một file vector test.
- **Thiếu trường làm mất cơ sở khỏi kết quả sắp xếp** ([R7](research.md#r7-sắp-xếp-theo-đánh-giá-và-giá-khi-thiếu-trường-fr-011)). D phải ghi sẵn `ratingAvg = 0`, `ratingCount = 0`, `minPricePerHour = null`. Sắp xếp giá chạy hai pha để cơ sở "Liên hệ" đứng cuối. Cần 13 composite index ([R8](research.md#r8-index-cần-tạo)).
- **"Gần nhất"** ([R9](research.md#r9-sắp-xếp-gần-nhất-và-vị-trí-fr-011-đến-fr-014)): haversine trên máy, `LocationManagerCompat` (không thêm thư viện). Vị trí chỉ nằm trong bộ nhớ. Query geohash để spec 021 dùng cho bản đồ.
- **FR-017, FR-018, FR-020** ([R10](research.md#r10-cập-nhật-theo-điều-kiện-mới-nhất-fr-018-giữ-trạng-thái-fr-017-mất-mạng-fr-020)): debounce 300 ms và `flatMapLatest`; ViewModel scope theo nav graph; cache offline của Firestore với `isFromCache`.
- **Lịch sử** ([R11](research.md#r11-lịch-sử-tìm-kiếm-fr-021-đến-fr-025)): ID tất định từ bộ lọc đã chuẩn hóa nên tìm lại không tạo mục trùng; app dọn còn 10 mục; `delete-account` của 011 đã xóa subcollection này.
- **Quyền của khách** ([R13](research.md#r13-quyền-đọc-cho-khách-chưa-đăng-nhập-fr-026-fr-027)): đọc công khai `venues` khi `ACTIVE`, `courts`, `priceRules`, `slots` và danh mục. `slots` lộ uid/bookingId dạng ID mờ (không tra ra được thông tin cá nhân), nên chấp nhận. Có đề xuất sửa câu chữ FR-026.
- **Danh mục** `districts`, `amenities` (A phụ trách): spec 020 định nghĩa schema, `CatalogRepository` ở `core/catalog` ([R14](research.md#r14-danh-mục-quậnhuyện-và-tiện-ích)).

## Technical Context

**Language/Version**: Kotlin (tích hợp trong AGP 9.4), JVM target 11; TypeScript/Deno cho `_shared/search-keywords.ts`; Node 20 cho script seed

**Primary Dependencies**:
- Đã có hoặc đã chốt ở plan 070 R12 và 010 R13: Fragment/Navigation và Safe Args, ViewBinding, Material 3, Lifecycle ViewModel/StateFlow, Coroutines, Hilt (KSP), Firebase BoM (Firestore, Auth), kotlinx-serialization, Coil.
- Dùng thêm từ thư viện có sẵn: `RangeSlider` của Material, `LocationManagerCompat` của `androidx.core`, `java.time` (minSdk 29).
- **Không thêm thư viện mới.** Không dùng `play-services-location`, GeoFire, hay dịch vụ tìm kiếm ngoài ([R6](research.md#r6-tìm-theo-tên-dùng-namekeywords-chốt-mục-9-điểm-6-đặc-tả-csdl), [R9](research.md#r9-sắp-xếp-gần-nhất-và-vị-trí-fr-011-đến-fr-014)).
- `LocalDate` và `TimeOfDay` trong `SearchCriteria` dùng serializer tự viết dạng chuỗi `yyyyMMdd` / `HHmm`.

**Storage**:
- Firestore (đọc): `venues`, `venues/*/courts`, `venues/*/priceRules`, `slots`.
- Firestore (sở hữu): `users/{uid}/searchHistory`, `districts`, `amenities`.
- Trên máy: cache offline của Firestore; cache trong bộ nhớ cho danh mục và `priceRules`/`courts` (10 phút); `SavedStateHandle` cho `SearchCriteria`.
- Không dùng Supabase cho tìm kiếm.

**Testing**:
- Vitest và `@firebase/rules-unit-testing` cho Rules: C-1…C-3, H-1…H-15, V-1…V-5, S-1, S-2.
- `deno test` cho `_shared/search-keywords.ts` với file vector chung.
- JUnit4, MockK, Turbine, coroutines-test cho `core/text`, `core/pricing`, `core/schedule`, `core/location`, sanitizer, matcher, evaluator, sorter, engine (fake data source), ViewModel.
- Bộ dữ liệu demo có đáp án (SC-003), chạy trên Emulator và một lần trên project thật để bắt lỗi thiếu index.
- Espresso (nên có): tìm theo tên → lọc → mở chi tiết → quay lại giữ trạng thái.

**Target Platform**: Android `minSdk 29`, `targetSdk 37`

**Project Type**: Mobile app (Android). Không có Edge Function mới.

**Performance Goals**:
- Trang đầu ≤ 3 giây ở 95% số lần trên 4G, kể cả có khung giờ (SC-001). Ước lượng 1,2–1,8 giây ([R4](research.md#r4-lọc-khung-giờ-còn-trống-fr-006-và-giá-theo-khung-fr-005)), đo theo [quickstart §4](quickstart.md#4-đo-thời-gian-và-lượt-đọc-sc-001-quota).
- Tìm được sân theo yêu cầu mẫu trong dưới 1 phút (SC-002).

**Constraints**:
- Spark 50.000 lượt đọc/ngày dùng chung cả app. Ước lượng 24.000/ngày cho 300 lần tìm.
- Không listener realtime cho kết quả tìm kiếm. Timeout 15 giây (`core/network`).
- Vị trí không rời khỏi máy (FR-014).
- Tối đa 150 ứng viên với chiến lược sắp xếp trên máy.

**Scale/Scope**: Quy mô đồ án, dữ liệu demo 30 cơ sở. 3 màn hình và 1 bottom sheet trong `nav_search`. 2 match Rules mới của A cùng yêu cầu Rules gửi D, B. 13 index. 5 package `core/` mới, dùng chung với 021, 022, 030, 040, 111, 120.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Nguyên tắc | Đánh giá | Ghi chú |
|---|---|---|
| I. MVVM, package theo tính năng | ✅ | Fragment → `SearchViewModel` → UseCase → `VenueSearchEngine` / `SearchHistoryRepository` (interface) → Firestore data source. ViewModel không gọi Firestore. `StateFlow<SearchUiState>` có đủ loading/nội dung/rỗng/lỗi; sự kiện một lần qua `Channel`. 021, 022 cùng nhóm 02 nên cùng package `feature/search`. Phần dùng với nhóm khác (giá, lịch, vị trí, danh mục, chuẩn hóa chữ) đặt ở `core/` |
| II. Bảo mật phía server | ✅ | `searchHistory` chỉ chủ đọc/ghi; Rules kiểm tra danh sách trường, kiểu, độ dài, `searchedAt == request.time` (H-1…H-15). Danh mục chỉ admin ghi. Khách chỉ đọc dữ liệu công khai, query luôn có `status == ACTIVE` để Rules chứng minh. Mọi Rules mới có test Emulator. Không có khóa bí mật mới |
| III. Toàn vẹn dữ liệu đặt lịch | ✅ (chỉ đọc) | Không ghi slot. Coi `HOLD` hết hạn là trống theo `holdExpiresAt`, đúng quy tắc của nguyên tắc này. Tiền là `Long` VND. Ngày giờ theo `Asia/Ho_Chi_Minh`. Kiểm tra cuối cùng vẫn ở transaction của 040 |
| IV. Kiểm thử có trọng tâm | ✅ | Test Rules (SC-006), unit test logic lọc/giá/sắp xếp với dữ liệu giả, test ViewModel cho FR-018, bộ dữ liệu demo có đáp án (SC-003, SC-004). Không test trên project thật, trừ bước kiểm tra index trước demo (chỉ đọc, dữ liệu giả) |
| V. Giao diện XML nhất quán | ✅ | XML + Material 3 DayNight, `strings_search.xml` tiền tố `search_`, `ListAdapter`/`DiffUtil`, đủ 4 trạng thái (FR-019), mất mạng không crash (FR-020), `w600dp` lưới 2 cột, `contentDescription` cho chip và thẻ, vùng chạm 48dp, danh sách < 3 giây |
| VI. Quyền riêng tư | ✅ | Quyền vị trí xin lúc chọn "Gần nhất", có giải thích trước, từ chối vẫn dùng được (FR-012, FR-013). Vị trí không lưu và không gửi đi (FR-014). Khách không có lịch sử (FR-027). Lịch sử xóa cùng tài khoản |
| VII. Đơn giản, minh bạch AI | ✅ | Không thêm thư viện, module Gradle hay Edge Function. Hai chiến lược phân trang là mức tối thiểu để giữ FR-011 và FR-016 với giới hạn của Firestore, có lý do ở R3. Log AI ở `docs/ai-log/` |

**Kết quả:** đạt, không có vi phạm cần ghi vào Complexity Tracking.

**Re-check sau Phase 1:** vẫn đạt. Có ba thay đổi ảnh hưởng nhóm khác, đều không nới lỏng nguyên tắc nào:
1. Mở đọc công khai `courts`, `priceRules`, `slots` (R13). Đây là dữ liệu không có thông tin cá nhân; trước đây chỉ người đã đăng nhập đọc được.
2. Thêm trường bắt buộc khi tạo cơ sở (R7).
3. Gom logic giá/lịch của 040 vào `core/` (R5).

## Phối hợp với nhóm

| Với | Việc | Hạn |
|---|---|---|
| **D** (spec 111, 110/120) | 1. Ghi `nameKeywords` bằng `KeywordGenerator` (app) hoặc `_shared/search-keywords.ts` (Edge Function `review-owner-application`) mỗi khi tạo/đổi tên cơ sở ([contract](contracts/keyword-normalization.md)).<br>2. Khi tạo cơ sở luôn ghi `ratingAvg = 0.0`, `ratingCount = 0`, `minPricePerHour = null` ([R7](research.md#r7-sắp-xếp-theo-đánh-giá-và-giá-khi-thiếu-trường-fr-011)).<br>3. **Chốt bên cập nhật `minPricePerHour` 🔒** khi chủ sân sửa bảng giá (Edge Function, vì Spark không có trigger), kể cả khi xóa hết rule thì về `null`.<br>4. Rules `venues` (đọc công khai khi `ACTIVE`), `courts`, `priceRules` (đọc công khai) theo [contract §2](contracts/security-rules-and-indexes.md#2-yêu-cầu-rules-gửi-người-phụ-trách-d-cho-venues-courts-pricerules-b-cho-slots).<br>5. Spec 120: màn hình quản lý `districts`, `amenities` theo schema ở [data-model §2.2](data-model.md#22-districtsdistrictid-và-amenitiesamenityid); ẩn bằng `isActive = false`, không xóa cứng. | Trước khi D code 111 |
| **B** (spec 040) | 1. Dùng chung `core/pricing` (`PriceRule`, `SlotPricing`) và `core/schedule` thay cho `CalculateTotalPriceUseCase` tự viết ([R5](research.md#r5-logic-giá-và-lưới-giờ-dùng-chung-với-spec-040-b)). A viết phần này kèm test, B review.<br>2. **Chốt quy ước `daysOfWeek`** (040 R4 đang ghi 1 = Chủ nhật; đề xuất ISO 1 = Thứ hai) cùng D.<br>3. Rules `slots`: cho mọi người đọc (kể cả khách). Cân nhắc bỏ `holdBy`/`bookingId` khỏi document nếu muốn chặt hơn ([R13](research.md#r13-quyền-đọc-cho-khách-chưa-đăng-nhập-fr-026-fr-027)).<br>4. Index `slots` đổi thành `venueId, date, startAt` (phục vụ cả lưới giờ 040). | Họp tuần 3 |
| **C** (spec 070) | 1. `ratingAvg`, `ratingCount` do Edge Function `review` cập nhật (plan 070 R1); giữ hai trường luôn là số, không xóa trường.<br>2. Nút trái tim trên thẻ kết quả: thẻ của 020 chừa chỗ, C cung cấp cách gắn (ví dụ một interface ở `core/` trả trạng thái yêu thích theo `venueId`). | Khi C làm UI 070 |
| **A** (spec 010, của mình) | Chế độ khách của 010 (010 research R15, tasks 010 T080–T083): khách vào thẳng `nav_player`, đăng nhập mở `nav_auth` chồng lên rồi `popBackStack` để giữ `nav_search`. **Chặn FR-026 của 020**, làm cùng PR `chore(core)` của 010. | Trước khi code 020 |
| **A** (spec 021, 022, 030 cũng của mình) | 1. 021 dùng `SearchCriteria`, `VenueSearchEngine`, `core/location`; geohash do 021 quyết định.<br>2. 022 gọi `SearchViewModel.applyExternal(criteria, AI)` ([contract](contracts/search-criteria.md#3-cửa-vào-cho-spec-022-và-021)).<br>3. 030 nhận `venueId` và tùy chọn `date`, `startTime`, `endTime` qua Safe Args. | Khi plan 021, 022, 030 |
| **Cả nhóm** | 1. Review PR `chore(core)` phần 2 của A: `core/text`, `core/pricing`, `core/schedule`, `core/location`, `core/catalog`.<br>2. Dùng `CatalogRepository` để lấy tên quận/tiện ích, không tự query `districts`, `amenities`. | Trước khi code |
| A (mình) | 1. Cập nhật đặc tả CSDL theo [danh sách cuối data-model.md](data-model.md#danh-sách-thay-đổi-cần-đưa-vào-docsdesigndatabasereadmemd).<br>2. Sửa spec theo [R15](research.md#r15-những-chỗ-cần-sửa-spec-hoặc-tài-liệu-khác).<br>3. Viết `scripts/seed/catalog.json`, `search-demo.json`, `search-demo.expected.json` và script seed. | Trước họp tuần 3 |

## Project Structure

### Documentation (this feature)

```text
specs/020-court-search/
├── spec.md
├── plan.md                                  # file này
├── research.md                              # Phase 0 (R1–R15)
├── data-model.md                            # Phase 1
├── quickstart.md                            # Phase 1
├── contracts/
│   ├── search-criteria.md                   # SearchCriteria, sanitizer, cửa vào cho 021/022, engine
│   ├── keyword-normalization.md             # chuẩn hóa tên, nameKeywords (Kotlin + TS), vector test
│   ├── security-rules-and-indexes.md        # Rules searchHistory/danh mục, yêu cầu Rules cho D/B, 13 index, test
│   └── ui-screens.md                        # nav_search, 3 màn hình + bottom sheet, ViewModel, chuỗi
├── ui/                                      # wireframe (đã có)
├── checklists/requirements.md
└── tasks.md                                 # Phase 2 (/speckit-tasks)
```

### Source Code (repository root)

```text
app/src/main/java/com/example/booking_app/
├── core/
│   ├── text/VietnameseNormalizer.kt, KeywordGenerator.kt, KeywordQuery.kt
│   ├── pricing/PriceRule.kt, SlotPricing.kt                      # dùng chung với 040
│   ├── schedule/TimeOfDay.kt, SlotMath.kt, BookingWindow.kt, SlotId.kt
│   ├── location/LocationProvider.kt, AndroidLocationProvider.kt, GeoDistance.kt, DistanceFormatter.kt
│   ├── catalog/CatalogRepository.kt, FirestoreCatalogRepository.kt, District.kt, Amenity.kt
│   └── di/SearchCoreModule.kt                                    # bind LocationProvider, CatalogRepository, Clock
└── feature/search/
    ├── domain/
    │   ├── model/ (SearchCriteria, TimeWindow, SortOption, VenueSummary, VenueResult, DisplayPrice,
    │   │           SearchPage, SearchSession, SearchHistoryEntry, SanitizeResult, DroppedField)
    │   ├── SearchCriteriaSanitizer.kt
    │   ├── VenueSearchEngine.kt, SearchHistoryRepository.kt       # interface
    │   └── usecase/ (SearchVenues, RecordSearch, ObserveHistory, DeleteHistory, ClearHistory)
    ├── data/
    │   ├── FirestoreVenueSearchEngine.kt
    │   ├── VenueQueryBuilder.kt                                  # chọn query/index theo criteria
    │   ├── VenueMatcher.kt, AvailabilityEvaluator.kt, ResultSorter.kt   # thuần Kotlin
    │   ├── VenueStaticCache.kt                                   # priceRules, courts 10 phút
    │   ├── VenueDataSource.kt                                    # bọc Firestore, awaitWithTimeout
    │   ├── dto/ (VenueDto, PriceRuleDto, CourtDto, SlotDto, SearchHistoryDto)
    │   └── FirestoreSearchHistoryRepository.kt
    └── ui/
        ├── SearchFragment.kt, SearchViewModel.kt, SearchUiState.kt
        ├── input/SearchInputFragment.kt, SearchHistoryViewModel.kt
        ├── filter/SearchFilterBottomSheet.kt, SearchFilterViewModel.kt
        ├── sort/SortDialog.kt, location/LocationRationaleDialog.kt
        └── adapter/VenueResultAdapter.kt, FilterChipBinder.kt, HistoryAdapter.kt
    └── di/SearchModule.kt                                        # bind VenueSearchEngine, SearchHistoryRepository, VenueDataSource

app/src/main/res/
├── navigation/nav_search.xml                                     # include vào nav_player
├── layout/fragment_search.xml, fragment_search_input.xml, bottom_sheet_search_filter.xml,
│          item_search_venue.xml, item_search_history.xml, dialog_search_sort.xml
├── layout-w600dp/ (hoặc dimens w600dp cho lưới 2 cột)
├── drawable/ic_amenity_*.xml, img_venue_placeholder.xml
└── values/strings_search.xml

app/src/test/java/com/example/booking_app/
├── core/ (text, pricing, schedule, location)
└── feature/search/ (domain, data, ui)

app/src/androidTest/java/com/example/booking_app/feature/search/VenueSearchEngineEmulatorTest.kt   # nên có

supabase/functions/_shared/search-keywords.ts, search-keywords.test.ts, search-keywords.vectors.json
firestore.rules                                                   # match districts, amenities, searchHistory
firestore.indexes.json                                            # 13 index
firestore-tests/test/search.test.ts                               # C-*, H-*, V-*, S-*
scripts/seed/catalog.json, search-demo.json, search-demo.expected.json, seed.mjs
```

**Structure Decision**:
- **App:**
  - Package `feature/search` cho cả nhóm 02 (020, 021, 022), nên `SearchCriteria` dùng chung không vi phạm quy tắc import chéo.
  - Phần các nhóm khác cần (chuẩn hóa chữ, giá 30 phút, lịch, vị trí, danh mục) đặt ở `core/`, làm trong PR `chore(core)` phần 2 trước khi code màn hình.
- **Server:** không có Edge Function mới. Chỉ có `_shared/search-keywords.ts` để D dùng khi tạo cơ sở từ hồ sơ chủ sân.

## Complexity Tracking

Không có vi phạm constitution cần giải thích.
