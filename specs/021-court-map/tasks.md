---

description: "Danh sách task triển khai spec 021 – Bản đồ sân và sân gần bạn"
---

# Tasks: Bản đồ sân và sân gần bạn

**Input**: tài liệu thiết kế trong `specs/021-court-map/`: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/), [quickstart.md](quickstart.md)

**Tests**: Có. Constitution nguyên tắc IV bắt buộc test cho logic trong ViewModel, UseCase, Repository và cho Security Rules. Với spec này:
- hàm thuần: `MapViewport`, `MercatorProjection`, vector geohash, `GeoAreaQuery`, `AreaFilter`, `GridClusterer`, `CardOrder`, `NearbySelector`, `LocationPermissionState`, `MarkerDescriptions`;
- `FirestoreVenueAreaSearch`, `FirestoreNearbyVenues` với fake `VenueDataSource`, kèm test đối chiếu SC-002 và FR-006;
- `SearchMapViewModel`, `NearbyViewModel`, phần thêm của `SearchViewModel` (Turbine);
- quyền đọc của khách cho query theo vùng (M-1…M-6) trên Firebase Emulator.

Viết test trước, chạy thấy **fail**, rồi mới cài đặt.

**Organization**: nhóm theo user story (US1…US4). Phase 2 tách thành PR nhỏ để merge sớm: 2A `core/map` (030 dùng chung), 2B `core/location`, 2C index/test Rules/dữ liệu thử, 2D sửa plan/tasks 020 và 030, 2E nền `feature/search` cho 021.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: làm song song được (file khác nhau, không phụ thuộc task chưa xong)
- **[Story]**: user story của spec (US1…US4)
- **[ĐỘC LẬP]**: không cần code của 020/030, làm được ngay (research R1)
- **Phụ thuộc: 020 Txxx / 030 Txxx**: cần task đó của [tasks 020](../020-court-search/tasks.md) / [tasks 030](../030-court-detail/tasks.md) xong trước
- **[BLOCKED: X]**: cần người phụ trách X làm xong phần của họ mới chạy thật được

## Quy ước đường dẫn

| Viết tắt | Đường dẫn thật |
|---|---|
| `CORE/` | `app/src/main/java/com/example/booking_app/core/` |
| `MAP/` | `app/src/main/java/com/example/booking_app/core/map/` |
| `SEARCH/` | `app/src/main/java/com/example/booking_app/feature/search/` |
| `RES/` | `app/src/main/res/` |
| `TEST/` | `app/src/test/java/com/example/booking_app/` |
| `ATEST/` | `app/src/androidTest/java/com/example/booking_app/` |
| `SEED/` | `scripts/seed/` |
| `FT/` | `firestore-tests/test/` |

**Tình trạng ngày 2026-10-28:** trên `main` mới có `core/session`, `core/network` (`AppError`, `TaskAwait`), `core/util/AppClock` (010) và lưới giờ 040. **Chưa có** gì của 020 (`feature/search`, `core/location`, `core/pricing`, `core/catalog`) và 030 (`feature/courtdetail`, osmdroid). `gradle/libs.versions.toml` **chưa có** osmdroid, Coil, ViewPager2.

---

## Phase 1: Setup

**Purpose**: thư viện và tài nguyên của 021.

- [ ] T001 Kiểm tra thư viện trong `gradle/libs.versions.toml` và `app/build.gradle.kts`: **osmdroid**, **Coil**, **ViewPager2**. Cả ba do 030 T002 thêm (ngày 2026-10-28 chưa có). Đã có thì dùng lại, không thêm trùng. Nếu lúc làm 2A mà 030 T002 chưa merge thì thêm đúng ba dòng như mô tả của 030 T002 trong PR 2A, ghi trong PR "làm thay 030 T002", và đánh dấu 030 T002 xong. osmdroid không build được với AGP 9.4 / `targetSdk 37` thì dừng lại báo nhóm, không tự đổi thư viện (plan Complexity Tracking). **Phụ thuộc: 030 T002**
- [ ] T002 [ĐỘC LẬP] Thêm `com.firebase:geofire-android-common:3.2.0` vào `gradle/libs.versions.toml` (alias `geofire-common`) và `implementation(libs.geofire.common)` vào `app/build.gradle.kts`. Mô tả PR ghi lý do theo [plan Complexity Tracking](plan.md#complexity-tracking): chỉ dùng `GeoFireUtils` (`getGeoHashForLocation`, `getGeoHashQueryBounds`, `getDistanceBetween`), cùng thuật toán với `geofire-common` (npm) mà D dùng ở Edge Function. Chạy `./gradlew assembleDebug`
- [ ] T003 [P] [ĐỘC LẬP] Tạo `RES/values/strings_map.xml` với mọi khóa ở [contracts/ui-screens.md §6](contracts/ui-screens.md#6-chuỗi-stringsmapxml), đúng nội dung tiếng Việt trong bảng (tiền tố `map_`). Không lặp lại chuỗi đã có ở `strings_search.xml` của 020 (`search_offline_stale`, `search_error_retry`, `search_price_*`, `search_no_rating`)
- [ ] T004 [P] [ĐỘC LẬP] Tạo drawable vector, màu lấy từ theme attribute, có bản tối:
  - `RES/drawable/ic_marker_shuttlecock_selected.xml`: bản lớn hơn của `ic_marker_shuttlecock` (030 T004), viền `colorSurface`. Nếu 030 T004 chưa có `ic_marker_shuttlecock.xml` thì tạo luôn file đó theo mô tả của 030 T004 và đánh dấu phần đó xong ở 030;
  - `RES/drawable/ic_my_location.xml`, `RES/drawable/ic_location_off.xml` (icon banner wireframe 2).

**Checkpoint**: `./gradlew assembleDebug` qua.

---

## Phase 2: Foundational (chặn mọi user story)

**⚠️ CRITICAL**: chưa xong phase này thì chưa làm story nào. Riêng các task `[ĐỘC LẬP]` làm ngay, không chờ 020.

### 2A. PR `chore(core): core/map` (dùng chung 030 + 021, [research R4](research.md#r4-osmdroid-coremap-và-rủi-ro-thư-viện-đã-archive))

**Tests (viết trước, phải fail):**

- [ ] T005 [P] [ĐỘC LẬP] Tạo `SEED/geohash.vectors.json`: mảng `{ "id", "lat", "lng", "geohash" }` cho 10 cơ sở trong `SEED/venues.json`, `geohash` độ dài 10 tính bằng hàm `geohash()` của `SEED/seed.mjs` (ví dụ `demo-truong-chinh-909`: `10.8128, 106.6283` → `w3gvd2km5k`), thêm 4 ca biên: `(0, 0)`, `(-33.8688, 151.2093)`, `(51.5074, -0.1278)`, `(10.7769, 106.7009)`
- [ ] T006 [P] [ĐỘC LẬP] Viết `TEST/core/map/GeohashVectorsTest.kt`: đọc `../scripts/seed/geohash.vectors.json` (thư mục làm việc của test Gradle là `app/`), mỗi dòng `GeoFireUtils.getGeoHashForLocation(GeoLocation(lat, lng))` phải bằng `geohash`. Cần T002
- [ ] T007 [P] [ĐỘC LẬP] Viết `TEST/core/map/MapViewportTest.kt`: `contains` (trong, trên biên, ngoài); `halfDiagonalMeters` sai số < 1% so với haversine tay; `differsSignificantlyFrom` = tâm lệch > 25% chiều rộng **hoặc** zoom lệch ≥ 1 ([data-model §3.1](data-model.md#31-coremap-mới-dùng-chung-với-030)); tuần tự hóa kotlinx-serialization qua lại không đổi giá trị
- [ ] T008 [P] [ĐỘC LẬP] Viết `TEST/core/map/MercatorProjectionTest.kt`: `toPixel(0, 0, z, 256)` = giữa thế giới; tăng zoom 1 thì tọa độ pixel gấp đôi; hai điểm cách 1 km ở TP.HCM, zoom 13, tile 256 cách khoảng 53 px (±5%)

**Implementation:**

- [ ] T009 [P] [ĐỘC LẬP] Cài `MAP/MapViewport.kt` (`@Serializable`, `center`, `zoom`, `north`, `south`, `east`, `west`, `contains`, `halfDiagonalMeters`, `differsSignificantlyFrom`), `MAP/MapDefaults.kt` (`CENTER = (10.7769, 106.7009)`, `ZOOM = 12.0`, `MY_LOCATION_ZOOM = 15.0`, `NEARBY_ALL_ZOOM = 14.0`, `NEARBY_WIDER_ZOOM = 12.0`, `MAX_FIT_ZOOM = 16.0`, `MAX_ZOOM = 19.0`), `MAP/MapLimits.kt` (`MAX_SEARCH_RADIUS_M = 15_000`, `MAX_MARKERS = 100`, `GEO_QUERY_LIMIT = 150`, `NEARBY_RADIUS_M = 5_000`, `NEARBY_MAX = 10`, `NEARBY_RELOAD_MOVE_M = 500`, `SETTLE_DEBOUNCE_MS = 300`, `TILE_FAIL_TIMEOUT_MS = 8_000`). `GeoPoint` dùng kiểu của `core/location` nếu 020 T013 đã có, chưa có thì tạo `data class GeoPoint(val lat: Double, val lng: Double)` trong `CORE/location/GeoPoint.kt` đúng như data-model 020 và ghi trong PR. T007 xanh
- [ ] T010 [P] [ĐỘC LẬP] Cài `MAP/MercatorProjection.kt` (`toPixel(lat, lng, zoom, tileSizePx): PointD`, hàm thuần). T008 xanh
- [ ] T011 Cài `MAP/OsmdroidSetup.kt` (`ensure(context)` gọi một lần: `userAgentValue = BuildConfig.APPLICATION_ID`, `osmdroidBasePath` và `osmdroidTileCache` trong `context.cacheDir/osmdroid`, không cần quyền bộ nhớ) và `MAP/MapThemer.kt` (`apply(mapView, isDark)`: `TilesOverlay.INVERT_COLORS` khi tối; thêm `CopyrightOverlay` "© OpenStreetMap contributors"). Theo [030 research R7](../030-court-detail/research.md#r7-bản-đồ-nhúng-và-chỉ-đường-fr-010-fr-011-fr-013). **Phụ thuộc: T001 (osmdroid)**
- [ ] T012 [P] Cài `MAP/TileHealthWatcher.kt`: đăng ký `Handler` vào `mapView.tileProvider.tileRequestCompleteHandlers`, đếm `MapTile.MAPTILE_SUCCESS_ID` / `MAPTILE_FAIL_ID`; phát `failed: StateFlow<Boolean>` khi sau `TILE_FAIL_TIMEOUT_MS` chưa có tile nào xong mà đã có ≥ 4 tile lỗi; gỡ handler khi `stop()` ([research R10](research.md#r10-trạng-thái-lỗi-mất-mạng-và-giữ-trạng-thái-fr-019-đến-fr-021-sc-005-fr-009)). Thời gian lấy qua tham số `clock`/`CoroutineScope` để test được. **Phụ thuộc: T001**
- [ ] T013 [P] Cài `MAP/MarkerIcons.kt`: `venue(selected: Boolean)` từ `ic_marker_shuttlecock` / `ic_marker_shuttlecock_selected`; `cluster(count, selected)` vẽ vòng tròn `colorPrimary` viền `colorSurface` (dày 3dp), chữ `colorOnPrimary` lên `Bitmap`, cache theo `(count, selected, isDark)`. Đường kính 40dp (≤ 9), 48dp (10–99), 56dp (≥ 100). **Phụ thuộc: T001, T004**
- [ ] T014 Sửa tài liệu 030 cho khớp `core/map`: `specs/030-court-detail/tasks.md` T050 (`VenueMapController` gọi `OsmdroidSetup`, `MapThemer`, `TileHealthWatcher`, `MarkerIcons` của `MAP/` thay vì tự cấu hình), T004 (ghi `ic_marker_shuttlecock_selected` do 021 thêm); `specs/030-court-detail/plan.md` (cây thư mục: thêm `core/map`), `specs/030-court-detail/research.md` R14 (danh sách phần đặt ở `core/`)

### 2B. PR `chore(core): core/location cho bản đồ` ([research R7](research.md#r7-vị-trí-và-chấm-vị-trí-fr-011-đến-fr-015-sc-006))

- [ ] T015 [P] [ĐỘC LẬP] Viết `TEST/core/location/LocationPermissionStateTest.kt` cho hàm thuần `LocationPermissionState.from(granted, everAsked, shouldShowRationale)`: chưa hỏi lần nào → `NeedRationale`; đã cấp (COARSE hoặc FINE) → `Granted`; từ chối mà còn hỏi lại được → `DeniedCanAsk`; từ chối và `shouldShowRationale == false` sau khi đã hỏi → `DeniedPermanently` (mở cài đặt)
- [ ] T016 Cài `CORE/location/LocationProvider.kt` + `CORE/location/AndroidLocationProvider.kt`. Nếu 020 T014 **chưa làm**: viết đúng contract 020 (`hasPermission()`, `suspend fun current(): GeoPoint?` qua `LocationManagerCompat.getCurrentLocation`, provider `NETWORK` rồi `GPS`, timeout 10 giây) và đánh dấu 020 T014 xong. Cả hai trường hợp: thêm `fun updates(): Flow<GeoPoint>` (`callbackFlow` quanh `LocationManagerCompat.requestLocationUpdates`, 10 giây hoặc 25 m, `awaitClose` gỡ listener; không có quyền thì flow rỗng, không ném lỗi). Không thêm `play-services-location`. Bind trong `CORE/di/SearchCoreModule.kt` (tạo nếu 020 T016 chưa có). **Phụ thuộc: 020 T013, T014 (nếu đã có)**
- [ ] T017 Cài `CORE/location/LocationPermissionState.kt` (T015 xanh) và `CORE/location/LocationPermissionHelper.kt`: bọc `registerForActivityResult(RequestMultiplePermissions)` cho `ACCESS_COARSE_LOCATION` + `ACCESS_FINE_LOCATION`, hộp giải thích (tham số chuỗi để 020 dùng `search_location_rationale`, 021 dùng `map_location_rationale`, có cờ bỏ qua hộp giải thích cho "Sân gần bạn"), mở `ACTION_APPLICATION_DETAILS_SETTINGS`; lưu `everAsked` trong DataStore tùy chọn (`core/session/SessionPrefs` hoặc DataStore riêng, không lưu vị trí). Nếu 020 T075/T077 đã có `LocationRationaleDialog` và launcher trong `SearchFragment` thì chuyển sang helper này. **Phụ thuộc: 020 T075, T077 (nếu đã có)**
- [ ] T018 Cài `MAP/FlowLocationProvider.kt`: `IMyLocationProvider` của osmdroid nhận vị trí từ `Flow<GeoPoint>` (collect trong scope truyền vào, `startLocationProvider`/`stopLocationProvider` theo vòng đời). **Phụ thuộc: T001, T016**

### 2C. PR `chore(map): index, test Rules và dữ liệu thử` ([contracts/rules-and-indexes.md](contracts/rules-and-indexes.md))

- [ ] T019 [P] [ĐỘC LẬP] Thêm index `venues (status ASC, geohash ASC)` vào `firestore.indexes.json` đúng JSON ở [contract §1](contracts/rules-and-indexes.md#1-index). Không thêm V2 (`status`, `districtId`, `ratingAvg DESC`, `ratingCount DESC`) nếu 020 T020 đã có; chưa có thì thêm cùng và ghi trong PR "làm thay một phần 020 T020"
- [ ] T020 [P] [ĐỘC LẬP] Viết `FT/map.test.ts` M-1…M-6 theo [contract §3](contracts/rules-and-indexes.md#3-bảng-test-firestore-teststestmaptestts) với `@firebase/rules-unit-testing`: seed 2 cơ sở `ACTIVE`, 1 `HIDDEN` cùng vùng bằng `withSecurityRulesDisabled`. M-3, M-4 viết `it.todo` kèm chú thích "chờ D áp Rules venues theo 020 R13". **Không sửa `firestore.rules`** (Rules `venues` là của D). **[BLOCKED: D]** cho M-3, M-4
- [ ] T021 [P] [ĐỘC LẬP] Tạo `SEED/map-demo.json` (cùng schema `venues.json`) với 16 cơ sở giả theo [research R13](research.md#r13-dữ-liệu-seed-và-dữ-liệu-thử-cho-bản-đồ), ID và tọa độ cố định:
  - `mapdemo-q10-1` … `mapdemo-q10-8`: trong bán kính 300 m quanh `(10.7735, 106.6670)`, `districtId: "q10"`;
  - `mapdemo-q3-same-1`, `mapdemo-q3-same-2`: **cùng** tọa độ `(10.7790, 106.6880)`, `districtId: "q3"`;
  - `mapdemo-q3-1` … `mapdemo-q3-3`: quanh `(10.7840, 106.6860)`, `districtId: "q3"`;
  - `mapdemo-no-location`: `location (0, 0)`, `districtId: "q10"`, cờ `"testNoLocation": true` để script bỏ qua kiểm tra vị trí;
  - `mapdemo-hidden`: `status: "HIDDEN"`, Quận 10;
  - `mapdemo-cu-chi`: `(10.9730, 106.4930)`, `districtId: "cu-chi"`.
  Mỗi cơ sở có 2 sân con và 2 dòng giá (thường 80.000, cao điểm 17:00–22:00 120.000), `openTime "0600"`, `closeTime "2200"`; `mapdemo-q10-1`, `mapdemo-q10-2`, `mapdemo-q3-1` có `ratingAvg`/`ratingCount` khác 0 (4,5/12; 3,8/5; 4,9/40). Tên tiếng Việt giả hợp lý, không trùng sân thật, `ownerId "demo-owner-1"`, `photoUrls` rỗng
- [ ] T022 Sửa `SEED/seed.mjs` và `SEED/README.md`: cờ `--with-map-demo` nạp thêm `map-demo.json`, **từ chối** khi thiếu `--emulator` (in lỗi, thoát mã 1); `npm run check` so `geohash()` với `SEED/geohash.vectors.json` và kiểm tra cơ sở `ACTIVE` có `location` khác `(0,0)` trừ cơ sở có `testNoLocation`; `--reset` xóa cả `venues/mapdemo-*`. Cần T005, T021. **Báo Duy (C) review** vì `seed.mjs` do Duy viết; ghi trong mô tả PR

### 2D. Sửa plan/tasks 020 cho 021 ([research R1](research.md#r1-thứ-tự-làm-và-phụ-thuộc-vào-020-030))

- [ ] T023 [ĐỘC LẬP] Sửa `specs/020-court-search/plan.md`, `tasks.md`, `data-model.md`, `contracts/ui-screens.md`, `contracts/search-criteria.md` theo 5 điểm ở research R1, mỗi chỗ ghi "(cho spec 021)":
  1. `SearchViewModel`: `displayMode: LIST | MAP` (lưu `SavedStateHandle`), `onToggleDisplayMode`, `onMapUnavailable`, `dropDistrictForArea`, `onMapIntent` (bổ sung vào 020 T048, ui-screens §3);
  2. `fragment_search.xml`: nút chuyển chế độ, `@+id/map_container` cạnh danh sách (020 T051); `layout-w600dp/fragment_search.xml` hai ô thay cho lưới 2 cột khi có bản đồ, trên `w600dp` chạm thẻ là chọn (020 T093, ui-screens §2);
  3. `LocationProvider.updates()` và `LocationPermissionHelper` (020 T014, T075, T077 trỏ sang 021 T016, T017);
  4. tách `SEARCH/ui/adapter/VenueCardFormatter.kt` khỏi `VenueResultAdapter` (020 T050, T069, T078);
  5. `VenueDataSource.venuesInGeohashRange(start, end, limit)`, `venuesByDistrict(districtId, limit)` (020 T029).
  Không đổi số task của 020; ghi thêm vào mô tả task hiện có

### 2E. Nền `feature/search` cho 021 (cần lõi 020)

- [ ] T024 [P] Tạo model và interface domain theo [data-model §3.3](data-model.md#33-featuresearchdomain-thêm-cho-021): `SEARCH/domain/model/MapLoadMode.kt` (`VIEWPORT`, `DISTRICT`), `LoadRequest.kt` (`criteria`, `mode`, `viewport`, `origin: GeoPoint?`), `AreaResult.kt` (`items: List<VenueResult>`, `capped`, `tooWide`, `isFromCache`, `bounds: MapViewport?`), `MapIntent.kt` (`AroundMe(zoom: Double)`), `MapCluster.kt` (`id`, `center`, `venueIds`, `isSingle`, `containsSelected`, `allSameLocation`); `SEARCH/domain/VenueAreaSearch.kt` (`fun load(request): Flow<AreaResult>`), `SEARCH/domain/NearbyVenues.kt` (`suspend fun around(origin): List<VenueResult>`). **Phụ thuộc: 020 T026, T027**
- [ ] T025 Bổ sung `SEARCH/data/VenueDataSource.kt` + `FirestoreVenueDataSource.kt`: `venuesInGeohashRange(start, end, limit)` = `whereEqualTo("status","ACTIVE").orderBy("geohash").startAt(start).endAt(end).limit(limit)`, `venuesByDistrict(districtId, limit)` = `whereEqualTo("status","ACTIVE").whereEqualTo("districtId", id).orderBy("ratingAvg", DESC).orderBy("ratingCount", DESC).limit(limit)`; trả `VenueDto` kèm `isFromCache`; bọc `awaitWithTimeout(15 s)`. Thêm hai hàm vào fake data source dùng trong test 020. **Phụ thuộc: 020 T029**
- [ ] T026 [P] Tạo `TEST/feature/search/fixtures/MapFixtures.kt`: đọc `../scripts/seed/venues.json` và `../scripts/seed/map-demo.json`, dựng 26 `VenueSummary` (bỏ `mapdemo-hidden` khỏi danh sách `ACTIVE`, giữ để test lọc trạng thái), `priceRules`, `courts`, và 3 bộ `slots` bận cho khung "ngày mai 19:00–21:00" ở `mapdemo-q10-1`, `mapdemo-q3-1`, `demo-k3`; 8 bộ `SearchCriteria` của [contracts/area-search.md §1](contracts/area-search.md#1-venueareasearch) (không lọc; từ khóa "cau long"; quận `q10`; giá ≤ 100.000; tiện ích `parking`; khung giờ mai 19:00–21:00; khung giờ + giá ≤ 100.000; mọi thứ cùng lúc); `fakeLocation` P1 `(10.7990, 106.6480)`, P2 `(10.7769, 106.7009)`, P3 `(10.7735, 106.6670)`. **Phụ thuộc: 020 T027, T021**

### 2F. Phối hợp (theo dõi, không chặn code của A)

- [ ] T027 [P] Theo dõi (**D**, spec 110/111/120): ghi `geohash` độ dài 10 bằng `GeoFireUtils` (111) hoặc `geofire-common` (`review-owner-application`) **cùng lần ghi** với `location`; validate `location` khác `(0,0)` khi `ACTIVE`; test bằng `SEED/geohash.vectors.json` ([contract §5](contracts/area-search.md#5-geohash-dùng-chung-cho-d)). Chưa có: cơ sở do chủ sân tạo không hiện trên bản đồ; demo bằng seed. **[BLOCKED: D]**
- [ ] T028 [P] Theo dõi (**D**): Rules `venues` theo 020 R13 (khách chỉ đọc `ACTIVE`). Hiện `main` đang `allow read: if true` (PR #43). Khi D sửa: bỏ `todo` của M-3, M-4 trong `FT/map.test.ts` và chạy lại. **[BLOCKED: D]**
- [ ] T029 [P] Theo dõi (**B**): 040 dùng `core/pricing` (020 T036) và chốt `daysOfWeek` (020 T037). Giá trên thẻ bản đồ đi theo kết quả chốt, không sửa gì riêng ở 021. **[BLOCKED: B]**
- [ ] T030 [P] Theo dõi (**C – Duy**): review PR T022 (`seed.mjs`). Chưa review thì T022 nằm ở nhánh riêng, test vẫn chạy bằng `MapFixtures` (T026)

**Checkpoint**: 2A, 2B, 2C merge được ngay (trừ phần cần osmdroid). 2E xong khi có Phase 2 của 020. Unit test T006–T008, T015 xanh; `npx vitest run test/map.test.ts` xanh (M-3, M-4 `todo`).

---

## Phase 3: User Story 1 – Xem sân trên bản đồ và mở chi tiết (Priority: P1) 🎯 MVP

**Goal**: nút "Bản đồ" trên màn hình tìm kiếm; marker quả cầu lông cho cơ sở khớp bộ lọc trong vùng đang xem; chạm marker hiện thẻ tóm tắt; chạm thẻ mở chi tiết sân với ngày và khung giờ; giữ vùng, mức phóng, cơ sở đang chọn khi quay lại; bản đồ lỗi thì về danh sách.

**Independent Test**: Emulator có seed + `map-demo`; chuyển sang bản đồ thấy marker đúng vị trí ở Quận 10/Quận 3 (vùng mặc định) và 10 sân seed khi chọn từng quận; chạm marker thấy thẻ đúng cơ sở; chạm thẻ mở đúng chi tiết; quay lại giữ nguyên vùng (quickstart K1, K2, K5, K7–K9, K12, K15, K20–K23, K25).

### Tests cho User Story 1 (viết trước, phải fail)

- [ ] T031 [P] [US1] [ĐỘC LẬP] Viết `TEST/feature/search/data/GeoAreaQueryTest.kt`: `bounds(center = P2, radius = 5_000)` trả 4–9 cặp; mọi cơ sở seed cách tâm ≤ bán kính có `geohash` nằm trong ít nhất một cặp; bán kính 15.000 m vẫn ≤ 9 cặp
- [ ] T032 [P] [US1] Viết `TEST/feature/search/data/AreaFilterTest.kt`: bỏ `location` thiếu hoặc `(0,0)` (`mapdemo-no-location`); bỏ cơ sở ngoài khung dù nằm trong vòng tròn ngoại tiếp; áp `VenueMatcher` với quận/từ khóa/tiện ích; sắp theo khoảng cách tới **tâm vùng**; 150 cơ sở giả → đúng 100 cơ sở gần tâm nhất và `capped = true` (FR-006); ≤ 100 → `capped = false`. **Phụ thuộc: 020 T044, T064; T026**
- [ ] T033 [P] [US1] Viết `TEST/feature/search/data/FirestoreVenueAreaSearchTest.kt` (fake `VenueDataSource`): `VIEWPORT` gọi `venuesInGeohashRange` đúng số cặp của `GeoAreaQuery`, mỗi query có `status == ACTIVE` và `limit = 150`, bỏ trùng theo ID; nửa đường chéo > 15.000 m → một `AreaResult(tooWide = true)` và **không** gọi data source; `DISTRICT` gọi `venuesByDistrict(id, 101)` và `bounds` bao mọi kết quả; `isFromCache = true` chỉ khi **mọi** snapshot từ cache; có khung giờ thì `AvailabilityEvaluator` chạy theo đợt 6 và flow phát nhiều `AreaResult` tăng dần; data source ném timeout → `AppError`; hủy collect thì không gọi thêm. **Phụ thuộc: 020 T063**
- [ ] T034 [P] [US1] Viết `TEST/feature/search/data/AreaListParityTest.kt` (SC-002): với `MapFixtures` và từng bộ trong 8 `SearchCriteria`, tập ID của `FirestoreVenueAreaSearch.load(VIEWPORT, khung chứa Quận 10 + Quận 3)` bằng tập ID của `drainAll(FirestoreVenueSearchEngine, criteria)` lọc theo khung (gọi `firstPage` rồi `nextPage` tới khi `hasMore == false`); với bộ có quận thì so ở `DISTRICT`, không lọc khung. Kiểm tra cả `DisplayPrice` và `freeCourtCount` bằng nhau cho cùng cơ sở (SC-003). **Phụ thuộc: 020 T046, T065**
- [ ] T035 [P] [US1] Viết `TEST/feature/search/ui/adapter/VenueCardFormatterTest.kt`: `DisplayPrice.From(80_000)` → "từ 80.000 đ/giờ"; `ForWindow(95_000, 19:00–21:00)` → "95.000 đ/giờ · 19:00–21:00"; `Contact` → "Liên hệ"; điểm "★ 4,6 (128)" / "Chưa có đánh giá" khi `ratingCount == 0`; khoảng cách qua `DistanceFormatter` ("850 m", "1,2 km"), `null` thì chuỗi rỗng. **Phụ thuộc: 020 T013, T027**
- [ ] T036 [P] [US1] Viết `TEST/feature/search/ui/map/SearchMapViewModelTest.kt` (phần US1, Turbine, fake `VenueAreaSearch`, `SavedStateHandle` thật):
  - vào bản đồ với `criteria` không có quận → một `LoadRequest(VIEWPORT, MapDefaults)`; có quận → `DISTRICT` và sự kiện `FitBounds(bounds)` (lề, tối đa zoom 16);
  - `criteria` đổi (kể cả khác quận) → tải lại ngay trong `camera` hiện tại; yêu cầu mới hủy yêu cầu cũ (`flatMapLatest`), kết quả cũ không ghi đè;
  - `districtId` đổi sang quận khác → `DISTRICT`;
  - chạm marker → `selectedVenueId`; chạm vùng trống → `null` (FR-010);
  - chạm thẻ → `OpenDetail(venueId, window)` có `date/startTime/endTime` khi `criteria.timeWindow != null`, không có khi không;
  - `AreaResult` rỗng → `Empty`; lỗi → `Error` và "Thử lại" gửi lại đúng yêu cầu; `isFromCache` → `staleFromCache`; `capped`, `tooWide`;
  - ViewModel mới dựng từ `SavedStateHandle` có `camera`, `loadedViewport`, `loadMode`, `selectedVenueId` → tải lại **một lần** theo `loadedViewport`, giữ `selectedVenueId` (process death);
  - `onMapFailed()` → sự kiện `MapFailed`.
  **Phụ thuộc: T024**
- [ ] T037 [P] [US1] Bổ sung `TEST/feature/search/ui/SearchViewModelTest.kt` (020): `displayMode` mặc định `LIST`, `onToggleDisplayMode` đổi qua lại không đổi `criteria` (FR-001); lưu và khôi phục qua `SavedStateHandle`; `onMapUnavailable()` → `LIST` và snackbar `map_unavailable`. **Phụ thuộc: 020 T043**

### Implementation cho User Story 1

- [ ] T038 [US1] [ĐỘC LẬP] Cài `SEARCH/data/GeoAreaQuery.kt` (bọc `GeoFireUtils.getGeoHashQueryBounds(GeoLocation, radiusM)` trả `List<Pair<String, String>>`). T031 xanh
- [ ] T039 [US1] Cài `SEARCH/data/AreaFilter.kt` theo bước 2–4 của [contract §1](contracts/area-search.md#1-venueareasearch) (dùng `VenueMatcher` của 020, `GeoDistance` của `core/location`, `MapLimits.MAX_MARKERS`). T032 xanh
- [ ] T040 [US1] Cài `SEARCH/data/FirestoreVenueAreaSearch.kt` theo contract §1 (bước 0–6, chạy song song các query bằng `coroutineScope { async }`, `AvailabilityEvaluator` + `VenueStaticCache` của 020 theo đợt 6 qua `Semaphore`, `channelFlow` phát kết quả từng đợt) và bind `VenueAreaSearch` trong `SEARCH/di/SearchModule.kt`. T033, T034 xanh
- [ ] T041 [US1] Tách `SEARCH/ui/adapter/VenueCardFormatter.kt` (giá, điểm, khoảng cách, mô tả trình đọc màn hình theo thứ tự tên → quận → khoảng cách → giá → điểm). Nếu `VenueResultAdapter` của 020 (T050, T069, T078) đã có thì đổi nó sang gọi formatter; chưa có thì 020 dùng formatter khi làm. T035 xanh. **Phụ thuộc: 020 T013, T027**
- [ ] T042 [US1] Cài `SEARCH/ui/map/MapUiState.kt` theo [data-model §3.5](data-model.md#35-featuresearchuimap-và-uinearby-trạng-thái-màn-hình) và `SEARCH/ui/map/SearchMapViewModel.kt` phần US1: `onCriteriaChanged` (`distinctUntilChanged`), `MutableSharedFlow<LoadRequest>` + `flatMapLatest`, chọn `MapLoadMode`, `onMarkerTapped`, `onMapTapped`, `onCardTapped`, `onRetry`, `onMapFailed`; `SavedStateHandle` chỉ lưu `camera`, `loadedViewport`, `loadMode`, `selectedVenueId`; sự kiện qua `Channel<MapEvent>`. T036 xanh
- [ ] T043 [US1] Bổ sung `SEARCH/ui/SearchViewModel.kt` (020): `displayMode` (lưu `SavedStateHandle`), `onToggleDisplayMode`, `onMapUnavailable`. T037 xanh. **Phụ thuộc: 020 T048**
- [ ] T044 [P] [US1] Tạo `RES/layout/fragment_search_map.xml` (`MapView`, `LinearProgressIndicator`, banner vị trí, banner offline, thẻ nổi Empty/Error/TooWide/capped, FAB "Vị trí của tôi", `ViewPager2` dãy thẻ, nút "Tìm ở khu vực này" ẩn) và `RES/layout/item_map_venue_card.xml` (ảnh trái 96dp, tên 1 dòng, "Quận · 850 m", điểm, giá; cao tối thiểu 112dp) theo [ui-screens §2](contracts/ui-screens.md#2-searchmapfragment-fragmentsearchmapxml) và wireframe `ui/02_court-search_buoc1_ban-do-dang-chon-san.png`
- [ ] T045 [US1] Sửa `RES/layout/fragment_search.xml` (020): nút chuyển Danh sách/Bản đồ cạnh ô tìm kiếm (icon như wireframe, `contentDescription` `map_toggle_map`/`map_toggle_list`), `FragmentContainerView @+id/map_container` cùng chỗ với danh sách, hàng chip nằm chồng lên bản đồ khi `MAP`. **Phụ thuộc: 020 T051**
- [ ] T046 [US1] Cài `SEARCH/ui/map/MapOverlayRenderer.kt` phần US1: mỗi cơ sở một `Marker` (`MarkerIcons.venue(selected)`, neo đáy giữa), marker đang chọn vẽ trên cùng, thay cả lớp `FolderOverlay` khi `venues` đổi; `MapEventsOverlay` cho chạm vùng trống
- [ ] T047 [US1] Cài `SEARCH/ui/map/MapCardAdapter.kt` (`ListAdapter`/`DiffUtil`, `VenueCardFormatter`, Coil qua `PhotoUrlResolver` của 030 nếu có, chưa có thì xử lý `http…` dùng nguyên như seed README) và `SEARCH/ui/map/SearchMapFragment.kt` phần US1:
  - `hiltNavGraphViewModels(R.id.nav_search)` cho `SearchMapViewModel` và `SearchViewModel`; Fragment làm cầu nối (`criteria` → `onCriteriaChanged`, `MapFailed` → `searchVm.onMapUnavailable()`);
  - `OsmdroidSetup.ensure`, `MapThemer.apply` theo `isNightMode`, `TileHealthWatcher` → `onMapFailed`; bắt lỗi khởi tạo `MapView` → `onMapFailed`;
  - `onResume`/`onPause`/`onDetach` của `MapView` theo `viewLifecycleOwner`; dựng lại camera từ `uiState.camera`, không tải lại khi quay về;
  - thu state bằng `repeatOnLifecycle(STARTED)`; "Thu nhỏ bản đồ" (`zoom - 2`, không dưới 11), "Xóa bộ lọc" gọi `searchVm.onClearFilters()`;
  - `OpenDetail` → action của `nav_search` sang `nav_court_detail` (030) với `venueId`, `date`, `startTime`, `endTime`; chưa có 030 thì dùng `CourtDetailPlaceholderFragment` của 020 T030.
  **Phụ thuộc: 020 T030, T052; T011, T012**
- [ ] T048 [US1] Sửa `SEARCH/ui/SearchFragment.kt` (020): thu `displayMode`, hiện/ẩn `results_list` và `map_container`, thêm `SearchMapFragment` vào container lần đầu (không tạo lại khi xoay), snackbar `map_unavailable`. **Phụ thuộc: 020 T052**

**Checkpoint**: US1 chạy độc lập: quickstart K1, K2, K5, K7–K9, K12, K15, K20–K23, K25 đạt; cụm, vuốt, vị trí chưa có.

---

## Phase 4: User Story 2 – Cụm marker, vuốt thẻ, "Tìm ở khu vực này" (Priority: P2)

**Goal**: gộp marker chồng nhau thành cụm có số; chạm cụm phóng vào hoặc hiện dãy thẻ; vuốt thẻ sang cơ sở gần kế tiếp, bản đồ đi theo; kéo/phóng chỉ hiện nút "Tìm ở khu vực này", không tự tải; bỏ chip quận khi tìm theo vùng; vùng quá rộng thì đề nghị phóng gần hơn.

**Independent Test**: `map-demo` có 8 sân sát nhau ở Quận 10: mức 12–13 thấy cụm "8", chạm cụm tách ra; vuốt thẻ qua lại bản đồ đi theo; kéo sang Quận 3 hiện nút, bấm thì có sân Quận 3 (quickstart K3, K4, K6, K10, K11, K13, K14).

### Tests cho User Story 2 (viết trước, phải fail)

- [ ] T049 [P] [US2] [ĐỘC LẬP] Viết `TEST/feature/search/data/GridClustererTest.kt` (`GridClusterer` generic trên `id` + `GeoPoint`, không phụ thuộc `VenueResult`): 8 điểm trong 300 m ở zoom 13 → một cụm 8, ở zoom 17 → 8 marker đơn; kết quả không đổi khi đảo thứ tự đầu vào; 2 điểm cùng tọa độ → `allSameLocation = true` ở mọi zoom; `containsSelected` đúng; `id` cụm ổn định giữa hai lần gọi cùng zoom; zoom làm tròn xuống 0,5 (12,7 và 12,5 cho cùng kết quả)
- [ ] T050 [P] [US2] [ĐỘC LẬP] Viết `TEST/feature/search/data/CardOrderTest.kt`: `build(anchor, visible)` bắt đầu bằng `anchor`, còn lại theo khoảng cách tới `anchor` tăng dần, bằng nhau thì theo `id`; `anchor` không có trong `visible` vẫn đứng đầu; danh sách rỗng → `[anchor]`
- [ ] T051 [P] [US2] Bổ sung `TEST/feature/search/ui/map/SearchMapViewModelTest.kt` (phần US2):
  - **SC-007:** 60 lần `onViewportSettled(viewport khác đáng kể, byUser = true)` cách nhau 1 giây (thời gian ảo) → fake `VenueAreaSearch` **không** được gọi; `showSearchArea = true`;
  - `byUser = false` (app di camera khi vuốt thẻ, fit theo quận, phục hồi) → không hiện nút;
  - vùng lệch nhỏ (tâm < 25% chiều rộng, zoom < 1) → không hiện nút;
  - `onSearchAreaClicked` khi có quận → sự kiện `DropDistrict`, không tự tải; khi không có quận → tải `VIEWPORT` theo `camera` hiện tại, nút ẩn; vùng > 15 km → `TooWide`, không tải;
  - offline khi "Tìm ở khu vực này" (mọi snapshot từ cache và không có kết quả) → giữ marker cũ, `ShowSnackbar` có "Thử lại";
  - chạm marker → `cardOrder = CardOrder.build(...)`; vuốt tới trang i → `selectedVenueId = cardOrder[i]`, sự kiện `MoveCamera(animate = true)`, `cardOrder` **không** đổi;
  - chạm cụm thường → `FitBounds`; cụm `allSameLocation` hoặc zoom ≥ 19 → `cardOrder` = thành viên cụm, không phóng;
  - `AreaResult` mới không còn `selectedVenueId` → bỏ chọn.
- [ ] T052 [P] [US2] Bổ sung `TEST/feature/search/ui/SearchViewModelTest.kt` (020): `dropDistrictForArea()` bỏ `districtId`, giữ các bộ lọc khác, phát snackbar `map_searched_by_area`, **không** gọi `RecordSearch`. **Phụ thuộc: 020 T083**

### Implementation cho User Story 2

- [ ] T053 [US2] [ĐỘC LẬP] Cài `SEARCH/data/GridClusterer.kt` theo [contract §3](contracts/area-search.md#3-gridclusterer) (ô 56dp đổi theo mật độ, `MercatorProjection` của T010, zoom làm tròn xuống 0,5, tâm cụm = trung bình). T049 xanh
- [ ] T054 [US2] [ĐỘC LẬP] Cài `SEARCH/data/CardOrder.kt` theo [contract §4](contracts/area-search.md#4-cardorder). T050 xanh
- [ ] T055 [US2] Bổ sung `SEARCH/ui/map/SearchMapViewModel.kt`: `onViewportSettled(viewport, byUser)`, `showSearchArea`, `onSearchAreaClicked`, `onClusterTapped`, `onCardPageSelected`, gom cụm khi `venues` hoặc `round(zoom, 0.5)` đổi. T051 xanh
- [ ] T056 [US2] Bổ sung `SEARCH/ui/SearchViewModel.kt` (020): `dropDistrictForArea()`. T052 xanh. **Phụ thuộc: 020 T068**
- [ ] T057 [US2] Bổ sung `SEARCH/ui/map/MapOverlayRenderer.kt`: vẽ `MapCluster` (`MarkerIcons.cluster(count, selected)`), cụm chứa cơ sở đang chọn nổi bật, chạm cụm gọi `onClusterTapped`
- [ ] T058 [US2] Bổ sung `SEARCH/ui/map/SearchMapFragment.kt`: `MapListener` (`onScroll`, `onZoom`) debounce `SETTLE_DEBOUNCE_MS` → `onViewportSettled`; `OnTouchListener` của `MapView` đặt cờ `byUser`; camera do app di (`MoveCamera`, `FitBounds`) đặt `byUser = false`; nút "Tìm ở khu vực này"; `DropDistrict` → `searchVm.dropDistrictForArea()`; `ViewPager2` theo `cardOrder` (mép trang kế lộ 24dp, `registerOnPageChangeCallback` → `onCardPageSelected`)

**Checkpoint**: K3, K4, K6, K10, K11, K13, K14 đạt; US1 vẫn đạt.

---

## Phase 5: User Story 3 – Vị trí của tôi (Priority: P2)

**Goal**: nút "Vị trí của tôi" xin quyền đúng lúc (có giải thích), đưa bản đồ về vị trí, hiện chấm vị trí (không kéo camera theo) và khoảng cách trên thẻ; từ chối hoặc không lấy được vị trí thì vẫn dùng được ở khu vực mặc định; vị trí không rời khỏi máy.

**Independent Test**: vị trí giả P1, bấm "Vị trí của tôi" → bản đồ về Tân Bình, có chấm và khoảng cách; từ chối quyền → khu vực mặc định, banner, mọi thứ khác chạy (quickstart K16–K19).

### Tests cho User Story 3 (viết trước, phải fail)

- [ ] T059 [P] [US3] Bổ sung `TEST/feature/search/ui/map/SearchMapViewModelTest.kt` (phần US3, fake `LocationProvider`):
  - mở bản đồ khi chưa có quyền → **không** có sự kiện `RequestLocationPermission` (FR-012, US3-6), `locationBanner = DefaultArea`;
  - đã có quyền từ trước → lấy vị trí ngầm, `userLocation` có giá trị, khoảng cách trên thẻ được tính lại bằng `GeoDistance` **không** tải lại, camera **không** đổi;
  - `onMyLocationClicked` khi chưa có quyền → `RequestLocationPermission`; có quyền và `current()` trả P1 → `MoveCamera(P1, 15, animate)` rồi một `LoadRequest(VIEWPORT, origin = P1)`;
  - kết quả từ chối → `locationBanner = DefaultArea`, `ShowSnackbar(map_location_denied, action = map_settings)`, camera giữ nguyên;
  - `current()` trả `null` sau 10 giây (thời gian ảo) → `locationBanner = Unavailable`, giữ vùng (US3-5);
  - `updates()` phát vị trí mới → `userLocation` đổi, **không** `MoveCamera`, **không** tải lại (Edge case vị trí thay đổi);
  - **SC-006:** sau mọi bước trên, `SavedStateHandle.keys()` không có khóa nào chứa vị trí người chơi (`userLocation`, `origin`), giá trị lưu chỉ là `camera`, `loadedViewport`, `loadMode`, `selectedVenueId`; `criteria` của fake `SearchViewModel` không đổi; fake `SearchHistoryRepository` không nhận lần ghi nào.
  **Phụ thuộc: T016**

### Implementation cho User Story 3

- [ ] T060 [US3] Bổ sung `SEARCH/ui/map/SearchMapViewModel.kt`: `userLocation` chỉ trong `MutableStateFlow` (không vào `SavedStateHandle`), `onMyLocationClicked`, `onLocationPermissionResult(state)`, thu `LocationProvider.updates()` khi có quyền, tính lại `distanceMeters` của `venues` khi vị trí đổi, đưa `origin` vào `LoadRequest`, `locationBanner`. T059 xanh
- [ ] T061 [US3] Bổ sung `SEARCH/ui/map/SearchMapFragment.kt`: `LocationPermissionHelper` (T017) với chuỗi `map_location_rationale`; FAB "Vị trí của tôi" (icon đầy/viền theo có vị trí); `MyLocationNewOverlay(FlowLocationProvider(...))` với `disableFollowLocation()`, chỉ thêm khi có quyền, dừng khi `STOPPED`; banner `map_default_area_banner` (icon `ic_location_off`) và `map_location_unavailable`; snackbar có nút "Cài đặt" mở `ACTION_APPLICATION_DETAILS_SETTINGS`. **Phụ thuộc: T017, T018**

**Checkpoint**: K16–K19 đạt; US1, US2 vẫn đạt.

---

## Phase 6: User Story 4 – Sân gần bạn (Priority: P2)

**Goal**: mục "Sân gần bạn" ngay dưới thanh tìm kiếm khi chưa có điều kiện: tối đa 10 cơ sở trong 5 km theo khoảng cách; "Xem tất cả trên bản đồ"; trạng thái chưa có quyền, từ chối, không có sân, lỗi.

**Independent Test**: vị trí giả P1: bấm "Bật vị trí", thấy Nụ Cười, K3, Trường Chinh 909… (8 sân), không có Quang Vinh, Minh Tâm CN2; "Xem tất cả trên bản đồ" mở bản đồ quanh P1 (quickstart N1–N8).

### Tests cho User Story 4 (viết trước, phải fail)

- [ ] T062 [P] [US4] [ĐỘC LẬP] Viết `TEST/feature/search/data/NearbySelectorTest.kt` (generic trên `id` + `GeoPoint` + `ratingAvg`): với tọa độ 10 sân seed và P1 `(10.7990, 106.6480)` → đúng 8 sân, thứ tự Nụ Cười (≈ 550 m), K3 (≈ 1.050 m), Trường Chinh 909 (≈ 2.640 m), PAM, Minh Tâm CN1, Gia Hân, Tân Thới Nhất, Sơn Tạ; **không** có Quang Vinh (≈ 5.470 m), Minh Tâm CN2 (≈ 5.800 m); với P2 `(10.7769, 106.7009)` → rỗng; 15 sân giả trong 5 km → 10 gần nhất; bằng khoảng cách thì `ratingAvg` giảm dần rồi `id`; `location (0,0)` bị bỏ
- [ ] T063 [P] [US4] Viết `TEST/feature/search/data/FirestoreNearbyVenuesTest.kt` (fake `VenueDataSource`): gọi `venuesInGeohashRange` cho đúng các cặp của `GeoAreaQuery(origin, 5_000)`; kết quả qua `NearbySelector`; `price = DisplayPrice.From(minPricePerHour)` hoặc `Contact` khi `null`; **không** đọc `priceRules`/`slots`; lỗi → `AppError`. **Phụ thuộc: T025**
- [ ] T064 [P] [US4] Viết `TEST/feature/search/ui/nearby/NearbyViewModelTest.kt` (fake `LocationProvider`, `NearbyVenues`, `LocationPermissionState`):
  - chưa hỏi quyền → `NeedPermission`, **không** tự xin quyền; `onEnableLocation()` → sự kiện xin quyền (bỏ hộp giải thích thứ hai);
  - cấp quyền → `Locating` → `Loading` → `Content(8 items)` (P1) hoặc `Empty` (P2);
  - từ chối → `Denied(canAskAgain = true)`; từ chối vĩnh viễn → `Denied(false)` và `onEnableLocation()` mở cài đặt;
  - `current()` trả `null` → `Unavailable`;
  - lỗi engine → `Error`, "Thử lại" gọi lại;
  - quay lại màn hình với vị trí lệch < 500 m → **không** gọi `NearbyVenues` lần nữa; lệch ≥ 500 m → gọi lại;
  - `onSeeAllOnMap()` → `MapIntent.AroundMe(14.0)`; `onWider()` → `AroundMe(12.0)`; `onByDistrict()` → sự kiện mở bảng lọc mục Quận.
- [ ] T065 [P] [US4] Bổ sung `SearchViewModelTest.kt` (020) và `SearchMapViewModelTest.kt`: `onMapIntent(AroundMe(z))` → `displayMode = MAP`, `pendingMapIntent` có giá trị, `criteria` không đổi; `SearchMapViewModel` nhận intent khi có `userLocation` → `MoveCamera(location, z)` + tải `VIEWPORT`, rồi xóa `pendingMapIntent`. **Phụ thuộc: T043**

### Implementation cho User Story 4

- [ ] T066 [US4] [ĐỘC LẬP] Cài `SEARCH/data/NearbySelector.kt`. T062 xanh
- [ ] T067 [US4] Cài `SEARCH/data/FirestoreNearbyVenues.kt` và bind `NearbyVenues` trong `SEARCH/di/SearchModule.kt`. T063 xanh
- [ ] T068 [US4] Cài `SEARCH/ui/nearby/NearbyUiState.kt` (`NeedPermission`, `Denied(canAskAgain)`, `Locating`, `Unavailable`, `Loading`, `Content(items)`, `Empty`, `Error(AppError)`) và `SEARCH/ui/nearby/NearbyViewModel.kt` (scope `nav_search`, `lastOrigin` chỉ trong bộ nhớ). T064 xanh
- [ ] T069 [US4] Bổ sung `SEARCH/ui/SearchViewModel.kt` (`onMapIntent`, `pendingMapIntent: StateFlow<MapIntent?>`) và `SEARCH/ui/map/SearchMapViewModel.kt` (nhận và xóa intent). T065 xanh. **Phụ thuộc: 020 T048**
- [ ] T070 [P] [US4] Tạo `RES/layout/fragment_nearby_venues.xml` (tiêu đề `map_nearby_title`, nút `map_nearby_see_all`, dải `RecyclerView` ngang, vùng thông báo + nút theo trạng thái, khung chờ 3 thẻ) và `RES/layout/item_nearby_venue.xml` (ảnh 16:9 rộng 200dp, tên, khoảng cách, "từ X đ/giờ", điểm) theo [ui-screens §3](contracts/ui-screens.md#3-nearbyvenuesfragment-fragmentnearbyvenuesxml)
- [ ] T071 [US4] Cài `SEARCH/ui/nearby/NearbyAdapter.kt` (`ListAdapter`/`DiffUtil`, `VenueCardFormatter`, mô tả trình đọc màn hình "Tên, 850 m, từ 80.000 đồng mỗi giờ, 4,6 sao") và `SEARCH/ui/nearby/NearbyVenuesFragment.kt` gắn vào `@+id/nearby_container` của 020: `LocationPermissionHelper` (bỏ hộp giải thích), chạm thẻ mở chi tiết qua action của `nav_search` (chỉ `venueId`), "Xem tất cả trên bản đồ"/"Tìm rộng hơn" → `searchVm.onMapIntent`, "Tìm theo quận" mở `searchFilterSheet` cuộn tới mục Quận (như chip "Quận" của 020). **Phụ thuộc: 020 T051, T052, T054**

**Checkpoint**: N1–N8 đạt; US1–US3 vẫn đạt.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [ ] T072 [P] Viết `TEST/feature/search/ui/map/MarkerDescriptionsTest.kt` rồi cài `SEARCH/ui/map/MarkerDescriptions.kt` (hàm thuần): marker "Sân Hòa Bình, 1,2 km, 4,5 sao"; không có vị trí thì bỏ khoảng cách; `ratingCount == 0` → "chưa có đánh giá"; đang chọn thêm ", đang chọn"; cụm "Cụm 8 sân, chạm hai lần để phóng to"
- [ ] T073 Cài `SEARCH/ui/map/MapAccessibilityHelper.kt` (`ExploreByTouchHelper` gắn vào `MapView` qua `ViewCompat.setAccessibilityDelegate`): virtual view cho mỗi marker/cụm trong màn hình, vùng 48dp quanh điểm neo (`Projection.toPixels`), `ACTION_CLICK` gọi đúng handler, thứ tự theo khoảng cách tới tâm màn hình, `invalidateRoot()` sau mỗi lần gom cụm/settle; `announceForAccessibility(map_results_announce)` khi tải xong ([research R12](research.md#r12-trợ-năng-cho-marker-và-cụm-fr-023)). Cần T072
- [ ] T074 Máy tính bảng ([research R11](research.md#r11-máy-tính-bảng-fr-023)): `RES/layout-w600dp/fragment_search.xml` (020) danh sách 40% (tối thiểu 320dp) + `map_container`, ẩn nút chuyển chế độ; `SearchMapViewModel.onListItemSelected(venueId)` (chọn, `MoveCamera`, hiện thẻ; cơ sở ngoài kết quả bản đồ thì chỉ di camera); `VenueResultAdapter` (020) tô nền `colorSecondaryContainer` cho thẻ đang chọn và trên `w600dp` chạm thẻ = chọn; thẻ tóm tắt rộng tối đa 420dp. Thêm test `onListItemSelected` vào `SearchMapViewModelTest.kt`. **Phụ thuộc: 020 T093**
- [ ] T075 [P] Rà chế độ tối: tile đảo màu, icon cụm và marker đọc rõ, banner/thẻ nổi dùng màu theme trong `fragment_search_map.xml`, `item_map_venue_card.xml`, `fragment_nearby_venues.xml`, `item_nearby_venue.xml`; không hardcode màu/chuỗi
- [ ] T076 [P] Rà TalkBack và vùng chạm: FAB, nút "Tìm ở khu vực này", nút chuyển chế độ, thẻ tóm tắt, thẻ "Sân gần bạn" có `contentDescription` và ≥ 48dp; chạy Accessibility Scanner (quickstart T3, T4)
- [ ] T077 Thêm log debug `MapTiming` (từ `LoadRequest` tới marker đầu tiên và tới khi xong) và `MapReads` (cộng `size()` mỗi snapshot, +1 cho query rỗng) trong `SEARCH/data/FirestoreVenueAreaSearch.kt` và `FirestoreNearbyVenues.kt`, chỉ bật khi `BuildConfig.DEBUG`
- [ ] T078 Đo 6 kịch bản [quickstart §5](quickstart.md#5-đo-thời-gian-và-lượt-đọc-sc-001), mỗi kịch bản 20 lần trên 4G giả lập; ghi trung vị, lần chậm thứ 19 và lượt đọc vào `docs/features/02-court-search/README.md`. Kịch bản 2 vượt 3 giây thì kiểm tra marker hiện theo đợt (research R14) và báo trong PR
- [ ] T079 Deploy index lên project thật (`npx firebase-tools@15 deploy --only firestore:indexes --project mobile-22e6c`, hỏi xác nhận trước khi chạy), chờ *Enabled*, chạy [quickstart §4](quickstart.md#4-project-thật-trước-demo) với seed 10 sân (không nạp `map-demo`); Logcat không có `FAILED_PRECONDITION … requires an index`
- [ ] T080 Chạy toàn bộ kịch bản thủ công K1–K26, N1–N8, T1–T4 của [quickstart §3](quickstart.md#3-kịch-bản-thủ-công-trên-máy-ảo) trên Emulator với seed + `map-demo`; chụp ảnh K3, K5, K17, N2, T1 vào `docs/features/02-court-search/screenshots/021/`
- [ ] T081 [P] (Nên có) Viết `ATEST/feature/search/MapFlowTest.kt` (Espresso, Emulator đã seed): chuyển sang bản đồ → chạm marker (qua `MapAccessibilityHelper` hoặc gọi ViewModel) → chạm thẻ → mở chi tiết → quay lại, kiểm tra `selectedVenueId` và camera giữ nguyên
- [ ] T082 [P] Cập nhật `docs/design/database/README.md` theo [danh sách cuối data-model.md](data-model.md#danh-sách-thay-đổi-cần-đưa-vào-docsdesigndatabasereadmemd) (§4.3 `geohash`, `location`; §6 index; ghi chú `geofire-android-common`)
- [ ] T083 [P] Cập nhật `docs/features/02-court-search/README.md` (link spec, plan, tasks 021; bảng đo SC-001; ảnh chụp) và cột *Spec* nhóm 02 trong `docs/features/README.md`; đánh dấu các dòng đã sửa trong bảng [research R16](research.md#r16-những-chỗ-cần-sửa-spec-hoặc-tài-liệu-khác)
- [ ] T084 Kiểm tra cuối: `./gradlew lint testDebugUnitTest assembleDebug`, `npx vitest run` trong `firestore-tests/`, `npm run check` trong `scripts/seed/`; ghi kết quả vào mô tả PR

---

## Dependencies & Execution Order

### Phụ thuộc giữa phase

- **Setup (Phase 1):** T002–T004 làm ngay. T001 chờ 030 T002 (hoặc làm thay trong PR 2A).
- **2A `core/map`:** T005–T010 **làm ngay** (thuần Kotlin, dữ liệu). T011–T013 cần osmdroid (T001). T014 làm ngay.
- **2B `core/location`:** T015 làm ngay. T016, T017 làm được trước 020 (viết đúng contract 020), nhưng nếu 020 đang làm T014/T075/T077 thì phối hợp để không trùng. T018 cần T001, T016.
- **2C:** T019–T021 làm ngay; T022 cần T005, T021.
- **2D:** T023 làm ngay, **nên xong trước khi bắt đầu code 020 US1** để 020 làm luôn phần cho 021.
- **2E:** cần Phase 2 của 020 (T026, T027, T029) và T021.
- **US1 (Phase 3):** cần 2A, 2E và 020 tới US2 (T044, T046, T063, T064, T065) cùng màn hình 020 (T030, T048, T051, T052). T031, T038 làm ngay.
- **US2 (Phase 4):** sau US1. T049, T050, T053, T054 làm ngay.
- **US3 (Phase 5):** sau US1 và 2B. Độc lập với US2.
- **US4 (Phase 6):** sau 2B, 2E và 020 T051, T052, T054. Không cần US1–US3 về logic; T069 (mở bản đồ) cần US1. T062, T066 làm ngay.
- **Polish (Phase 7):** sau các story cần thiết; T074 cần 020 T093.

### Nhóm "độc lập" (làm ngay, không chờ 020/030)

T002, T003, T004, T005–T010, T014, T015, T019–T021, T023, T031, T038, T049, T050, T053, T054, T062, T066 (và T022 sau T005, T021).

### Bảng phụ thuộc 020/030

| Task 021 | Cần task của 020/030 | Nếu chưa có |
|---|---|---|
| T001, T011–T013, T018 | 030 T002 (osmdroid, Coil, ViewPager2) | Thêm thư viện trong PR 2A, đánh dấu 030 T002 |
| T004 | 030 T004 (`ic_marker_shuttlecock`) | Tạo luôn theo mô tả 030 T004 |
| T009 | 020 T013 (`GeoPoint`, `GeoDistance`) | Tạo `GeoPoint` đúng data-model 020 |
| T016, T017 | 020 T014, T075, T077 | Viết theo contract 020 kèm phần mở rộng, đánh dấu task 020 |
| T019 | 020 T020 (V2) | Thêm V2 cùng PR |
| T024, T026 | 020 T026, T027 | Chờ |
| T025 | 020 T029 | Chờ |
| T032, T039 | 020 T044, T064 (`VenueMatcher`) | Chờ |
| T033, T040 | 020 T062, T063 (`VenueStaticCache`, `AvailabilityEvaluator`) | Chờ |
| T034 | 020 T046, T065 (engine S1, S2) | Chờ |
| T035, T041 | 020 T013, T027, T050/T069/T078 | Tạo formatter trước, 020 dùng lại |
| T037, T043, T069 | 020 T043, T048 | Chờ |
| T045, T048 | 020 T051, T052 | Chờ |
| T047 | 020 T030 (placeholder chi tiết), 030 `nav_court_detail` | Dùng `CourtDetailPlaceholderFragment` |
| T052, T056 | 020 T068, T083 | Chờ |
| T071 | 020 T051, T052, T054 | Chờ |
| T074 | 020 T093 | Chờ |

### Phụ thuộc người khác

| Task | Người | Phương án tạm |
|---|---|---|
| T020 (M-3, M-4), T028 | **D**: Rules `venues` theo 020 R13 | `it.todo`; query 021 vẫn có `status == ACTIVE` nên chạy được với Rules hiện tại |
| T027 | **D**: ghi `geohash` cùng `location`, validate `location` khi `ACTIVE` | Demo bằng seed (đã có `geohash` đúng thuật toán) |
| T029 | **B**: 040 dùng `core/pricing`, chốt `daysOfWeek` | Giá theo quy ước ISO như 020 |
| T022, T030 | **C (Duy)**: review `seed.mjs` | Test dùng `MapFixtures` đọc thẳng file JSON |

### Song song trong một story

- **US1:** T031–T037 (test) song song; T044 song song với T038–T043.
- **US2:** T049–T052 song song; T053, T054 song song.
- **US4:** T062–T065 song song; T066, T070 song song.

## Parallel Example: User Story 1

```text
# Viết test cùng lúc:
T031 GeoAreaQueryTest
T032 AreaFilterTest
T033 FirestoreVenueAreaSearchTest
T034 AreaListParityTest
T035 VenueCardFormatterTest
T036 SearchMapViewModelTest (US1)
T037 SearchViewModelTest (displayMode)

# Layout làm song song với logic:
T044 fragment_search_map.xml + item_map_venue_card.xml
```

## Parallel Example: làm ngay khi 020 chưa có

```text
T002 geofire-android-common      T003 strings_map.xml         T004 drawable
T005 geohash.vectors.json        T006–T008 test core/map       T009, T010 MapViewport, MercatorProjection
T015 LocationPermissionStateTest T019 index                    T020 map.test.ts
T021 map-demo.json               T023 sửa plan/tasks 020       T049/T053 GridClusterer
T050/T054 CardOrder              T062/T066 NearbySelector       T031/T038 GeoAreaQuery
```

## Implementation Strategy

### MVP (US1)

1. Làm nhóm độc lập (Phase 1, 2A, 2C, 2D, các hàm thuần) trong lúc 020 đang code.
2. Khi 020 xong Phase 2 + US1 + US2: làm 2E rồi US1.
3. **Dừng và kiểm tra:** K1, K2, K5, K7–K9, K12, K15, K20–K23, K25; demo được "xem sân trên bản đồ, mở chi tiết".

### Giao từng phần

1. US1 → demo bản đồ.
2. US2 (cụm, vuốt, "Tìm ở khu vực này") → demo duyệt nhiều sân.
3. US3 (vị trí) → demo "Vị trí của tôi".
4. US4 ("Sân gần bạn") → demo màn hình chính.
5. Polish (tablet, TalkBack, đo, project thật).

### Thứ tự PR đề xuất

1. **PR `chore(core): core/map + geofire`** (T001–T014, phần cần osmdroid nếu 030 T002 đã có).
2. **PR `chore(core): location cho bản đồ`** (T015–T018).
3. **PR `chore(map): index, test Rules, dữ liệu thử`** (T019–T022), báo Duy review.
4. **PR `docs(search): sửa plan/tasks 020, 030 cho 021`** (T014, T023).
5. **PR `feat(map): bản đồ US1`**, rồi US2, US3, US4, Polish.

## Notes

- Mỗi task xong thì đánh `[x]` và ghi ngắn ghi chú implement (như tasks 010/011).
- Test phải đỏ trước khi cài; không sửa test cho xanh.
- Không sửa `firestore.rules` của D/B; không sửa Rules hay code của người khác ngoài các task đã ghi ("làm thay" phải ghi rõ trong PR).
- Vị trí người chơi không được ghi ra log, Firestore, `SavedStateHandle` hay lịch sử (SC-006).
