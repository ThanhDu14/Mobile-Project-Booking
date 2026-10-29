# Implementation Plan: Bản đồ sân và sân gần bạn

**Branch**: `feature/02-court-map-plan` | **Date**: 2026-10-28 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/021-court-map/spec.md`

## Summary

Người chơi, kể cả khách, xem cơ sở cầu lông trên bản đồ và tìm sân gần mình. Chế độ bản đồ nằm trên màn hình tìm kiếm của 020 và dùng chung bộ lọc với danh sách. Các phần chính:
- marker quả cầu lông, gộp cụm khi chồng nhau;
- thẻ tóm tắt vuốt được, đồng bộ với marker;
- nút "Tìm ở khu vực này" thay cho tự tải lại;
- "Vị trí của tôi";
- mục "Sân gần bạn" (10 sân trong 5 km) dưới thanh tìm kiếm.

Spec chỉ đọc dữ liệu, không ghi gì lên server.

**Cách làm:**
- **Truy vấn theo vùng bằng geohash** ([R2](research.md#r2-truy-vấn-theo-vùng-geohash-giới-hạn-firestore-cắt-100)):
  - Vùng đang xem đổi thành vòng tròn ngoại tiếp. `GeoFireUtils.getGeoHashQueryBounds` cho 4–9 query `status == ACTIVE` + khoảng `geohash`, chạy song song.
  - Mọi bộ lọc khác chạy trên máy bằng **đúng** `VenueMatcher` và `AvailabilityEvaluator` của 020. Nhờ vậy tập kết quả của bản đồ trùng danh sách (SC-002), có test đối chiếu.
  - Cắt 100 cơ sở gần tâm vùng nhất. Vùng rộng hơn 15 km thì đề nghị phóng gần hơn.
  - Thêm một index `venues (status, geohash)`.
- **Hai chế độ tải** ([R3](research.md#r3-hai-chế-độ-tải-theo-vùng-và-theo-quận)): bộ lọc có quận thì tải theo quận và đưa camera bao các sân. Còn lại tải theo vùng. "Tìm ở khu vực này" khi có quận thì bỏ chip quận qua `SearchCriteria`.
- **Không tự tải lại** ([R8](research.md#r8-không-tự-tải-lại-khi-kéophóng-fr-004-sc-007)): `MapListener` debounce 300 ms, chỉ hiện nút khi người chơi tự kéo và vùng lệch đáng kể. Đổi bộ lọc (kể cả từ 022) thì tải ngay, qua `flatMapLatest`.
- **Bản đồ osmdroid** như 030 ([R4](research.md#r4-osmdroid-coremap-và-rủi-ro-thư-viện-đã-archive)). Phần cấu hình đưa lên `core/map` để 030 và 021 dùng chung. osmdroid đã archive từ 2024-11-20; ghi rủi ro, MapLibre là phương án thay thế.
- **Phân cụm tự viết theo lưới pixel** ([R5](research.md#r5-phân-cụm-marker-fr-003)). Không dùng osmbonuspack: chỉ có trên JitPack, có báo lỗi resolve, và kéo theo cả một thư viện lớn chỉ để dùng một lớp.
- **Thẻ tóm tắt** ([R6](research.md#r6-thẻ-tóm-tắt-vuốt-và-đồng-bộ-marker-fr-007-đến-fr-010-sc-003)): `ViewPager2` với dãy thẻ cố định `[X, gần X nhất, …]`. Giá, điểm, khoảng cách dùng chung `VenueCardFormatter` với thẻ 020.
- **Vị trí** ([R7](research.md#r7-vị-trí-và-chấm-vị-trí-fr-011-đến-fr-015-sc-006)): dùng `LocationProvider` của 020, thêm `updates()` cho chấm vị trí, không follow camera, không thêm Play Services. Vị trí người chơi chỉ nằm trong bộ nhớ, có test SC-006.
- **"Sân gần bạn"** ([R9](research.md#r9-sân-gần-bạn-fr-015a-đến-fr-018)): cùng query geohash bán kính 5 km. Không tự xin quyền khi màn hình vừa hiện mà chờ người chơi bấm "Bật vị trí" (đề nghị sửa câu chữ US4-1).
- **Lỗi, offline, giữ trạng thái, máy tính bảng, TalkBack** ([R10](research.md#r10-trạng-thái-lỗi-mất-mạng-và-giữ-trạng-thái-fr-019-đến-fr-021-sc-005-fr-009)–[R12](research.md#r12-trợ-năng-cho-marker-và-cụm-fr-023)):
  - tile lỗi thì về danh sách;
  - mất mạng thì hiện dữ liệu cache kèm banner;
  - ViewModel scope `nav_search` cộng `SavedStateHandle` để giữ trạng thái;
  - `w600dp` hiện hai ô;
  - TalkBack đọc marker qua `ExploreByTouchHelper` gắn trên `MapView`.
- **Dữ liệu thử** ([R13](research.md#r13-dữ-liệu-seed-và-dữ-liệu-thử-cho-bản-đồ)): seed 10 sân đủ cho "Sân gần bạn" (vị trí giả ở Tân Bình), nhưng thiếu cụm 8 sân, sân cùng tọa độ, sân thiếu vị trí. Thêm `map-demo.json` 16 sân, chỉ nạp vào Emulator.

## Technical Context

**Language/Version**: Kotlin (bản đi kèm AGP 9.4), JVM target 11; Node 20 cho script seed; TypeScript (Vitest) cho test Rules.

**Primary Dependencies**:
- Đã có từ 010: Fragment/Navigation + Safe Args, ViewBinding, Material 3, Lifecycle ViewModel/StateFlow, Coroutines, Hilt (KSP), Firebase BoM (Auth, Firestore), kotlinx-serialization.
- Đã chốt ở 020/030 (chưa cài): Coil, ViewPager2, osmdroid (`org.osmdroid:osmdroid-android`), `LocationManagerCompat` của `androidx.core`.
- **Mới:** `com.firebase:geofire-android-common:3.2.0`, chỉ dùng `GeoFireUtils` ([R2](research.md#r2-truy-vấn-theo-vùng-geohash-giới-hạn-firestore-cắt-100), Complexity Tracking).
- `ExploreByTouchHelper` trong `androidx.customview` (đã có qua AndroidX).

**Storage**:
- Firestore (chỉ đọc): `venues`; `courts`, `priceRules`, `slots` khi có khung giờ (qua 020); `districts` qua `CatalogRepository`.
- Trên máy: cache offline của Firestore; cache `priceRules`/`courts` 10 phút của 020; tile osmdroid trong `cacheDir/osmdroid`; `SavedStateHandle` cho camera, cơ sở đang chọn, chế độ tải.
- Không ghi Firestore/Supabase. Không lưu vị trí người chơi.

**Testing**:
- JUnit4, MockK, Turbine, `kotlinx-coroutines-test`:
  - các hàm thuần: `MapViewport`, `MercatorProjection`, `GridClusterer`, `CardOrder`, `AreaFilter`, `NearbySelector`, `GeoAreaQuery`;
  - vector geohash;
  - `FirestoreVenueAreaSearch` với fake `VenueDataSource`, gồm test đối chiếu SC-002 và FR-006 (150 cơ sở);
  - `SearchMapViewModel` (SC-006, SC-007, chế độ tải, bỏ quận, lỗi, khôi phục `SavedStateHandle`) và `NearbyViewModel`.
- Vitest + `@firebase/rules-unit-testing` trên Emulator: M-1 → M-6.
- Kịch bản thủ công K1–K26, N1–N8, T1–T4 ([quickstart](quickstart.md)).
- Espresso (nên có): chuyển chế độ → chạm marker → mở chi tiết → quay lại giữ trạng thái.

**Target Platform**: Android `minSdk 29`, `targetSdk 37`; điện thoại và `w600dp`; sáng và tối.

**Project Type**: mobile-app, một module `app`, package theo tính năng. Không có Edge Function mới.

**Performance Goals**:
- Marker trong vùng hiện đủ ≤ 3 giây ở ≥ 95% lần thử trên 4G (SC-001). Ước lượng 0,4–1,4 giây cho vùng thường. Có khung giờ thì hiện marker theo đợt ([R14](research.md#r14-lượt-đọc-và-thời-gian-sc-001-hạn-mức-spark)).
- Gom cụm dưới 1 ms cho 100 cơ sở; kéo bản đồ không giật.

**Constraints**:
- Spark 50.000 lượt đọc/ngày dùng chung. Ước lượng 021 khoảng 7.200/ngày, cộng 020 là ~31.000 (R14).
- Tối đa 100 marker mỗi lần tải; bán kính tìm tối đa 15 km.
- Không tự xin quyền vị trí khi mở bản đồ. Vị trí không rời khỏi máy.
- Timeout 15 giây (`core/network`). Không có listener realtime: kết quả bản đồ là ảnh chụp lúc tải, như 020.
- Ghi nguồn "© OpenStreetMap contributors"; `userAgent` theo chính sách tile của OSM.

**Scale/Scope**:
- 2 Fragment con trong `SearchFragment` (bản đồ, "Sân gần bạn") và 2 ViewModel.
- 1 package `core/map` dùng chung với 030; ~10 lớp thuần Kotlin.
- 1 index, 6 test Rules, 1 file dữ liệu thử Emulator.
- Dữ liệu demo 26 cơ sở trên Emulator, 10 trên project thật.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Nguyên tắc | Đánh giá | Ghi chú |
|---|---|---|
| I. MVVM, package theo tính năng | ✅ | Fragment → `SearchMapViewModel` / `NearbyViewModel` → `VenueAreaSearch` / `NearbyVenues` (interface ở `domain`) → `VenueDataSource` → Firestore. ViewModel không gọi Firebase. `StateFlow<MapUiState>`, sự kiện qua `Channel`. Thuộc nhóm 02, cùng package `feature/search` với 020/022. Phần dùng với 030 (osmdroid) đưa lên `core/map`, không import `feature/courtdetail` |
| II. Bảo mật phía server | ✅ | Chỉ đọc. Không có Rules mới; query luôn có `status == ACTIVE` để Rules của D chứng minh được. Test M-1 → M-6. Không có khóa bí mật (osmdroid, GeoFireUtils không cần khóa) |
| III. Toàn vẹn dữ liệu đặt lịch | ✅ (chỉ đọc) | Không ghi slot. Giờ trống dùng `AvailabilityEvaluator` của 020 (HOLD hết hạn coi là trống). Kiểm tra cuối vẫn ở transaction của 040. Tiền `Long`, giờ `Asia/Ho_Chi_Minh` |
| IV. Kiểm thử có trọng tâm | ✅ | Unit test cho logic vùng, cụm, thứ tự thẻ, chọn sân gần, ViewModel. Test đối chiếu tập kết quả với 020 (SC-002). Test Rules trên Emulator. Không test trên project thật, trừ bước kiểm tra index trước demo (chỉ đọc, dữ liệu seed) |
| V. Giao diện XML nhất quán | ✅ | XML + Material 3 DayNight; `strings_map.xml` tiền tố `map_`. Đủ trạng thái tải/rỗng/lỗi trên bản đồ (FR-019) và "Sân gần bạn"; mất mạng không crash. `layout-w600dp` hai ô. Marker/cụm có mô tả TalkBack, vùng chạm 48dp. Tile đảo màu khi tối |
| VI. Quyền riêng tư | ✅ | Quyền vị trí chỉ xin khi bấm "Vị trí của tôi" hoặc "Bật vị trí", có giải thích trước. Từ chối vẫn dùng được (khu vực mặc định, danh sách). Vị trí chỉ trong bộ nhớ, không lưu server/lịch sử/`SavedStateHandle` (FR-015, test SC-006). Dữ liệu thử `map-demo` là dữ liệu giả, chỉ nạp Emulator |
| VII. Đơn giản, minh bạch AI | ⚠️ có lý do | Thêm 1 thư viện nhỏ (`geofire-android-common`) và dùng osmdroid đã archive, ghi ở Complexity Tracking. Không dùng osmbonuspack; phân cụm tự viết (~80 dòng, có test). Không thêm module Gradle hay Edge Function. Log AI ở `docs/ai-log/AI_log_1.md` |

**Kết quả:** đạt; hai mục ghi ở Complexity Tracking.

**Re-check sau Phase 1:** vẫn đạt. Thay đổi ảnh hưởng nhóm khác:
1. Thêm `LocationProvider.updates()` và `LocationPermissionHelper` vào phần của 020 (A tự sửa).
2. Đưa cấu hình osmdroid từ `feature/courtdetail` lên `core/map` (030, A tự sửa).
3. D phải ghi `geohash` cùng thuật toán và cùng lúc với `location`.

Không mục nào nới lỏng nguyên tắc.

## Phối hợp với nhóm

| Với | Việc | Hạn |
|---|---|---|
| **D** (spec 110, 111, 120) | 1. Ghi `geohash` **độ dài 10** bằng `GeoFireUtils.getGeoHashForLocation` (111, app) hoặc `geofire-common` (`review-owner-application`, Edge Function), **cùng lần ghi** với `location` mỗi khi tạo cơ sở hoặc chủ sân ghim lại vị trí ([contract §5](contracts/area-search.md#5-geohash-dùng-chung-cho-d)). Test với `scripts/seed/geohash.vectors.json`.<br>2. Không cho `ACTIVE` khi `location` thiếu hoặc `(0,0)` (validate ở 110/111).<br>3. **Rules `venues`:** hiện trên `main` là `allow read: if true` (PR #43). Đề nghị chốt theo 020 R13 (khách đọc `ACTIVE`, chủ cơ sở và admin đọc mọi trạng thái). 021 không phụ thuộc vào việc này vì query luôn có `status == ACTIVE`; M-3, M-4 để `it.todo` tới khi D sửa.<br>4. Giữ `minPricePerHour`, `ratingAvg`, `ratingCount` luôn có giá trị (đã có ở 020, T032–T033 của 020). | Trước khi D code ghi `venues` ở 111 |
| **B** (spec 040) | 1. Giá theo khung và giờ trống trên bản đồ đi qua `core/pricing` (`SlotPricing`) và `AvailabilityEvaluator` của 020. B dùng cùng `core/pricing` cho 040 (020 T036) để giá trên thẻ bản đồ bằng giá ở lưới giờ.<br>2. Rules `slots` cho mọi người đọc (hiện đã là `read: if true`). Quy ước `daysOfWeek` (020 T037) ảnh hưởng giá trên thẻ bản đồ như thẻ danh sách. | Khi B sửa 040 theo 020 T036 |
| **C** (spec 070) | `ratingAvg`, `ratingCount` luôn là số (đã có ở 020); mô tả TalkBack của marker dùng hai trường này. | — |
| **A** (spec 020, của mình) | Sửa plan/tasks 020 theo [R1](research.md#r1-thứ-tự-làm-và-phụ-thuộc-vào-020-030):<br>1. `SearchViewModel`: `displayMode`, `onToggleDisplayMode`, `onMapUnavailable`, `dropDistrictForArea`, `onMapIntent`.<br>2. `fragment_search.xml`: nút chuyển chế độ, `map_container`; `layout-w600dp` hai ô; trên `w600dp` chạm thẻ danh sách là chọn.<br>3. `LocationProvider.updates()`; tách `LocationPermissionHelper`.<br>4. Tách `VenueCardFormatter` khỏi `VenueResultAdapter`.<br>5. `VenueDataSource`: `venuesInGeohashRange`, `venuesByDistrict`. | Trước khi code 020 US1 (T048–T052) |
| **A** (spec 030, của mình) | Đưa `OsmdroidSetup`, `MapThemer`, `TileHealthWatcher`, `MarkerIcons` lên `core/map`; 030 T050 `VenueMapController` gọi lại. `ic_marker_shuttlecock.xml` thêm bản nổi bật. | Trước khi code 030 T050 |
| **A** (spec 022, của mình) | 022 gọi `SearchViewModel.applyExternal` như cũ; bản đồ tự tải lại khi `criteria` đổi, đổi quận thì chuyển chế độ `DISTRICT`. Không cần API riêng. | Khi plan 022 |
| **Cả nhóm** | Review PR `core/map` + `geofire-android-common`. Kiểm tra osmdroid build với AGP 9.4/`targetSdk 37` (030 T002); lỗi thì báo nhóm, không tự đổi thư viện. | Trước khi code màn hình bản đồ |
| A (mình) | 1. Cập nhật `docs/design/database/README.md` theo [danh sách cuối data-model.md](data-model.md#danh-sách-thay-đổi-cần-đưa-vào-docsdesigndatabasereadmemd).<br>2. Sửa spec theo [R16](research.md#r16-những-chỗ-cần-sửa-spec-hoặc-tài-liệu-khác).<br>3. Viết `map-demo.json`, `geohash.vectors.json`, cờ `--with-map-demo` trong `seed.mjs` (báo Duy vì `seed.mjs` là của C). | Trước khi code |

## Project Structure

### Documentation (this feature)

```text
specs/021-court-map/
├── spec.md
├── plan.md                         # file này
├── research.md                     # Phase 0 (R1–R16)
├── data-model.md                   # Phase 1
├── quickstart.md                   # Phase 1
├── contracts/
│   ├── area-search.md              # VenueAreaSearch, NearbyVenues, GridClusterer, CardOrder, geohash cho D
│   ├── rules-and-indexes.md        # index G1, Rules dựa vào, test M-1…M-6, kiểm tra seed
│   └── ui-screens.md               # Fragment con trong nav_search, trạng thái, thao tác, w600dp, TalkBack, chuỗi
├── ui/                             # wireframe (đã có)
├── checklists/requirements.md
└── tasks.md                        # Phase 2 (/speckit-tasks)
```

### Source Code (repository root)

```text
app/src/main/java/com/example/booking_app/
├── core/
│   ├── map/                                           # mới, dùng chung 030 + 021
│   │   ├── MapViewport.kt, MapDefaults.kt, MapLimits.kt, MercatorProjection.kt   # thuần Kotlin
│   │   ├── OsmdroidSetup.kt, MapThemer.kt, TileHealthWatcher.kt, MarkerIcons.kt
│   │   └── FlowLocationProvider.kt                    # IMyLocationProvider từ Flow<GeoPoint>
│   └── location/                                      # của 020, thêm updates()
│       ├── LocationProvider.kt, AndroidLocationProvider.kt
│       └── LocationPermissionHelper.kt                # tách từ 020 T075/T077
└── feature/search/                                    # của 020, thêm phần 021
    ├── domain/
    │   ├── model/ MapLoadMode.kt, LoadRequest.kt, AreaResult.kt, MapIntent.kt, MapCluster.kt
    │   ├── VenueAreaSearch.kt, NearbyVenues.kt        # interface
    ├── data/
    │   ├── GeoAreaQuery.kt, AreaFilter.kt, NearbySelector.kt, GridClusterer.kt, CardOrder.kt   # thuần Kotlin
    │   ├── FirestoreVenueAreaSearch.kt, FirestoreNearbyVenues.kt
    │   └── VenueDataSource.kt (+ venuesInGeohashRange, venuesByDistrict)
    ├── ui/
    │   ├── adapter/VenueCardFormatter.kt              # tách từ VenueResultAdapter (020)
    │   ├── map/ SearchMapFragment.kt, SearchMapViewModel.kt, MapUiState.kt,
    │   │        MapCardAdapter.kt, MapOverlayRenderer.kt, MapAccessibilityHelper.kt
    │   └── nearby/ NearbyVenuesFragment.kt, NearbyViewModel.kt, NearbyUiState.kt, NearbyAdapter.kt
    └── di/SearchModule.kt (+ bind VenueAreaSearch, NearbyVenues)

app/src/main/res/
├── layout/fragment_search_map.xml, item_map_venue_card.xml, fragment_nearby_venues.xml, item_nearby_venue.xml
├── layout-w600dp/fragment_search.xml                  # của 020, hai ô
├── drawable/ic_marker_shuttlecock.xml (030), ic_marker_shuttlecock_selected.xml, ic_my_location.xml, ic_location_off.xml
└── values/strings_map.xml

app/src/test/java/com/example/booking_app/
├── core/map/ (MapViewportTest, MercatorProjectionTest, GeohashVectorsTest)
└── feature/search/ (data/GeoAreaQueryTest, AreaFilterTest, GridClustererTest, CardOrderTest, NearbySelectorTest,
                     FirestoreVenueAreaSearchTest, AreaListParityTest; ui/map/SearchMapViewModelTest; ui/nearby/NearbyViewModelTest)

app/src/androidTest/java/com/example/booking_app/feature/search/MapFlowTest.kt   # nên có

firestore.indexes.json                                 # + venues (status, geohash)
firestore-tests/test/map.test.ts                       # M-1…M-6
scripts/seed/map-demo.json, geohash.vectors.json, seed.mjs (+ --with-map-demo, kiểm tra vector)
gradle/libs.versions.toml                              # + geofire-android-common
```

**Structure Decision**:
- **App:** 021 nằm trong `feature/search` cùng 020, 022 (nhóm 02), dùng lại `SearchCriteria`, `VenueMatcher`, `AvailabilityEvaluator`, `SearchViewModel` mà không vi phạm quy tắc import chéo. Bản đồ và "Sân gần bạn" là Fragment con trong `SearchFragment`, không thêm destination. Phần bọc osmdroid dùng chung với 030 đưa lên `core/map`.
- **Server:** không có Edge Function, không có Rules mới. Chỉ có một index và test Rules kiểm chứng.

## Complexity Tracking

| Vi phạm / bổ sung | Vì sao cần | Phương án đơn giản hơn bị loại vì |
|---|---|---|
| Thư viện **`geofire-android-common`** (chỉ `GeoFireUtils`) | Tính khoảng geohash phủ một vòng tròn (ô kề, biên, độ dài tiền tố) đúng mẫu tài liệu Firestore; cùng thuật toán với `geofire-common` mà D dùng ở Edge Function ([R2](research.md#r2-truy-vấn-theo-vùng-geohash-giới-hạn-firestore-cắt-100)) | Tự viết khoảng 100 dòng, dễ sai ở biên, phải tự giữ đồng bộ với bản TS. Đọc mọi `venues` rồi lọc trên máy thì không dùng index, lượt đọc tăng theo tổng số cơ sở |
| Dùng **osmdroid** dù repo đã archive (2024-11-20) | Thư viện bản đồ mà constitution nêu tên khi không có tài khoản thanh toán Google Maps; 030 đã chốt; không cần khóa ([R4](research.md#r4-osmdroid-coremap-và-rủi-ro-thư-viện-đã-archive)) | MapLibre: nặng hơn nhiều, cần style và nguồn tile vector, hai thư viện bản đồ trong một app. Google Maps: cần thanh toán |
| Package **`core/map`** mới | 030 và 021 cùng cần cấu hình osmdroid, theme tối, theo dõi tile, icon marker; constitution I cấm 021 import `feature/courtdetail/ui` | Chép code sang 021: hai bản lệch nhau (user agent, ghi nguồn OSM) |
