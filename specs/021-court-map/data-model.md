# Data Model: Bản đồ sân và sân gần bạn (spec 021)

Spec 021 **chỉ đọc** `venues` (của D), `venues/*/courts`, `venues/*/priceRules` (D), `slots` (B) và `districts` (A, 020). Spec **không sở hữu** collection nào và **không ghi** gì lên Firestore hay Supabase. Lý do các quyết định ở [research.md](research.md).

Quy ước chung theo đặc tả CSDL mục 1: thời gian `Timestamp` UTC, ngày `yyyyMMdd` và giờ `HHmm` theo `Asia/Ho_Chi_Minh`, tiền `Long` VND, 🔒 = chỉ server ghi.

## 1. Dữ liệu Firestore 021 đọc

### 1.1. `venues/{venueId}` (D, đặc tả CSDL §4.3)

Chỉ các trường dưới đây được đọc; ánh xạ vào `VenueSummary` của 020 ([data-model 020 §3.1](../020-court-search/data-model.md)), **không** thêm DTO mới.

| Trường | Kiểu | 021 dùng để | Ràng buộc 021 cần |
|---|---|---|---|
| `status` | String | Điều kiện query `== "ACTIVE"` (Rules, FR-002) | |
| `location` | GeoPoint | Vị trí marker, khoảng cách, lọc trong khung | Bắt buộc hợp lệ khi `ACTIVE`. `(0, 0)` hoặc thiếu = không hiện trên bản đồ, không có trong "Sân gần bạn" (Edge case) |
| `geohash` | String | Khoảng query theo vùng (research R2) | **Độ dài 10**, sinh từ `location` bằng `GeoFireUtils.getGeoHashForLocation` (app) / `geofire-common` (Edge Function). D ghi lại mỗi khi `location` đổi. Sai/thiếu thì cơ sở không hiện trên bản đồ |
| `name`, `nameKeywords` | String, Array | Tên trên thẻ; `VenueMatcher` lọc từ khóa | Như 020 |
| `districtId` | String | Tên quận trên thẻ; query `DISTRICT` (R3); lọc quận trên máy | |
| `photoPaths[0]` | String | Ảnh thẻ (qua `core/media/PhotoUrlResolver` của 030) | |
| `minPricePerHour` 🔒 | Long? | "từ X đ/giờ", cắt giá | Như 020 R7 |
| `ratingAvg` 🔒, `ratingCount` 🔒 | Double, Int | Điểm trên thẻ và mô tả TalkBack | Luôn là số (020 R7) |
| `openTime`, `closeTime`, `amenityIds` | | `VenueMatcher` | |

**Thay đổi cần đưa vào đặc tả CSDL** (cuối file).

### 1.2. `courts`, `priceRules`, `slots`

Chỉ đọc khi bộ lọc có khung giờ, qua `AvailabilityEvaluator` và `VenueStaticCache` của 020 ([research 020 R4](../020-court-search/research.md#r4-lọc-khung-giờ-còn-trống-fr-006-và-giá-theo-khung-fr-005)). 021 không đổi cách đọc.

### 1.3. `districts/{districtId}`

Qua `CatalogRepository` (`core/catalog`, 020) để lấy tên quận. Không đọc trực tiếp.

## 2. Index

| # | Collection | Trường | Query |
|---|---|---|---|
| G1 | `venues` | `status ASC`, `geohash ASC` | Theo vùng và "Sân gần bạn" (`status ==` + `orderBy(geohash)` + `startAt`/`endAt`) |
| (V2 của 020) | `venues` | `status`, `districtId`, `ratingAvg DESC`, `ratingCount DESC` | Chế độ `DISTRICT` (R3), dùng lại |

Chi tiết và cách kiểm: [contracts/rules-and-indexes.md](contracts/rules-and-indexes.md).

## 3. Model trong app

### 3.1. `core/map` (mới, dùng chung với 030)

| Kiểu | Trường / hàm | Ghi chú |
|---|---|---|
| `MapViewport` | `center: GeoPoint`, `zoom: Double`, `north`, `south`, `east`, `west: Double` | `@Serializable` để lưu `SavedStateHandle`. `contains(GeoPoint)`, `halfDiagonalMeters()`, `differsSignificantlyFrom(other)` (tâm lệch > 25% chiều rộng hoặc zoom lệch ≥ 1, research R8) |
| `MapDefaults` | `CENTER = (10.7769, 106.7009)`, `ZOOM = 12.0`, `MY_LOCATION_ZOOM = 15.0`, `NEARBY_ALL_ZOOM = 14.0`, `NEARBY_WIDER_ZOOM = 12.0`, `MAX_FIT_ZOOM = 16.0`, `MAX_ZOOM = 19.0` | research R7, R9 |
| `MapLimits` | `MAX_SEARCH_RADIUS_M = 15_000`, `MAX_MARKERS = 100`, `GEO_QUERY_LIMIT = 150`, `NEARBY_RADIUS_M = 5_000`, `NEARBY_MAX = 10`, `NEARBY_RELOAD_MOVE_M = 500`, `SETTLE_DEBOUNCE_MS = 300`, `TILE_FAIL_TIMEOUT_MS = 8_000` | research R2, R8, R9, R10 |
| `MercatorProjection` | `toPixel(lat, lng, zoom, tileSizePx): PointD` | Hàm thuần cho `GridClusterer` |
| `OsmdroidSetup`, `MapThemer`, `TileHealthWatcher`, `MarkerIcons` | | Lớp Android bọc osmdroid (research R4, R10) |

### 3.2. `core/location` (020, mở rộng)

| Thay đổi | Ghi chú |
|---|---|
| `LocationProvider.updates(): Flow<GeoPoint>` | Mới. `callbackFlow` quanh `LocationManagerCompat.requestLocationUpdates` (10 giây hoặc 25 m), gỡ khi ngừng thu (research R7) |
| `GeoDistance` | Giữ haversine của 020. `GeoFireUtils.getDistanceBetween` chỉ dùng bên trong `GeoAreaQuery`; kết quả hiển thị luôn qua `GeoDistance` để khoảng cách trùng thẻ 020 (SC-003) |

### 3.3. `feature/search/domain` (thêm cho 021)

| Kiểu | Trường | Ghi chú |
|---|---|---|
| `MapLoadMode` | `VIEWPORT`, `DISTRICT` | research R3 |
| `LoadRequest` | `criteria: SearchCriteria`, `mode: MapLoadMode`, `viewport: MapViewport`, `origin: GeoPoint?` | `origin` là vị trí người chơi (nếu có) để tính khoảng cách, **không** lưu |
| `AreaResult` | `items: List<VenueResult>`, `capped: Boolean`, `tooWide: Boolean`, `isFromCache: Boolean`, `bounds: MapViewport?` | `VenueResult` của 020 (có `price`, `freeCourtCount`, `distanceMeters`). `bounds` = khung bao các cơ sở (chế độ `DISTRICT`, để fit camera). Có thể phát nhiều lần cho cùng một yêu cầu khi kiểm tra giờ trống theo đợt (research R14) |
| `VenueAreaSearch` (interface) | `fun load(request: LoadRequest): Flow<AreaResult>` | Cài đặt `FirestoreVenueAreaSearch` ở `data/`. Hủy collect = hủy tải |
| `NearbyVenues` (interface) | `suspend fun around(origin: GeoPoint): List<VenueResult>` | ≤ 10 cơ sở trong 5 km, sắp tăng dần (research R9) |
| `MapIntent` | `AroundMe(zoom: Double)` | "Sân gần bạn" gửi cho `SearchViewModel` để mở bản đồ (research R9) |

### 3.4. `feature/search/data` (thêm cho 021, thuần Kotlin trừ data source)

| Lớp | Việc |
|---|---|
| `GeoAreaQuery` | Từ tâm + bán kính → danh sách `GeoQueryBounds` (bọc `GeoFireUtils.getGeoHashQueryBounds`) |
| `AreaFilter` | Bước 1–4 của research R2: vị trí hợp lệ, trong khung, `VenueMatcher`, sắp theo khoảng cách tới tâm, cắt 100 |
| `NearbySelector` | Lọc ≤ 5.000 m, sắp tăng dần, lấy 10 |
| `FirestoreVenueAreaSearch` | Chọn chế độ, gọi `VenueDataSource` (thêm `venuesInGeohashRange`, `venuesByDistrict`), `AreaFilter`, rồi `AvailabilityEvaluator` theo đợt |
| `FirestoreNearbyVenues` | `GeoAreaQuery` + `VenueDataSource.venuesInGeohashRange` + `NearbySelector` |

### 3.5. `feature/search/ui/map` và `ui/nearby` (trạng thái màn hình)

**`MapUiState`** (`SearchMapViewModel`, scope `nav_search`):

| Trường | Kiểu | Ý nghĩa |
|---|---|---|
| `camera` | `MapViewport` | Vùng đang xem. **Lưu `SavedStateHandle`** |
| `loadedViewport` | `MapViewport?` | Vùng của lần tải gần nhất. **Lưu `SavedStateHandle`** |
| `loadMode` | `MapLoadMode` | **Lưu `SavedStateHandle`** |
| `venues` | `List<VenueResult>` | Kết quả hiện tại (≤ 100) |
| `clusters` | `List<MapCluster>` | Kết quả của `GridClusterer` ở `camera.zoom` |
| `selectedVenueId` | `String?` | **Lưu `SavedStateHandle`**. Nguồn duy nhất cho marker nổi bật, pager và danh sách `w600dp` |
| `cardOrder` | `List<String>` | Dãy ID của pager (research R6) |
| `userLocation` | `GeoPoint?` | Chỉ trong bộ nhớ. **Không** lưu `SavedStateHandle` (FR-015, research R7) |
| `loading` | Boolean | Thanh tiến trình mỏng |
| `content` | `Ready` \| `Empty` \| `Error(AppError)` \| `TooWide` | Thẻ nổi trên bản đồ (FR-019, US2-7) |
| `capped` | Boolean | "Phóng gần hơn để xem thêm sân" (FR-006) |
| `staleFromCache` | Boolean | Banner offline (FR-021) |
| `showSearchArea` | Boolean | Nút "Tìm ở khu vực này" |
| `locationBanner` | `None` \| `DefaultArea` \| `Unavailable` | Banner wireframe 2 / không lấy được vị trí |

**`MapCluster`**: `id` (khóa ô lưới), `center: GeoPoint`, `venueIds: List<String>`, `isSingle`, `containsSelected`, `allSameLocation` (mọi thành viên lệch nhau < 1 m).

**Sự kiện một lần** (`Channel<MapEvent>`): `MoveCamera(target, zoom, animate)`, `FitBounds(bounds)`, `OpenDetail(venueId, window?)`, `RequestLocationPermission`, `ShowSnackbar(textRes, action?)`, `MapFailed`, `DropDistrict`.

**Trạng thái chuyển** (rút gọn):

```text
Idle ──criteria đổi / vào bản đồ──▶ Loading ──AreaResult──▶ Ready | Empty
  ▲                                   │                          │
  │                                   └──lỗi / timeout──▶ Error ─┘ (Thử lại → Loading)
  │
  └── kéo/phóng (byUser) ──▶ Ready + showSearchArea ──bấm──▶ (có quận? DropDistrict → criteria đổi) ──▶ Loading
                                                    └──vùng > 15 km──▶ TooWide
```

**`NearbyUiState`** (`NearbyViewModel`, scope `nav_search`): `NeedPermission`, `Denied(canAskAgain: Boolean)`, `Locating`, `Unavailable`, `Loading`, `Content(items: List<VenueResult>)`, `Empty`, `Error(AppError)`. Giữ `lastOrigin: GeoPoint?` **trong bộ nhớ** để quyết định có tải lại không (lệch > 500 m).

### 3.6. `SearchViewModel` (020, thêm cho 021)

| Thêm | Ghi chú |
|---|---|
| `displayMode: StateFlow<DisplayMode>` (`LIST`, `MAP`), lưu `SavedStateHandle` | FR-001 |
| `onToggleDisplayMode()`, `onMapUnavailable()` | FR-020: về `LIST` kèm snackbar |
| `dropDistrictForArea()` | Bỏ `districtId`, phát snackbar "Đã tìm theo khu vực trên bản đồ"; **không** ghi lịch sử (không phải "một lần tìm" theo 020 R11) |
| `onMapIntent(MapIntent)` | Đổi sang `MAP`, chuyển intent cho bản đồ qua `pendingMapIntent: StateFlow<MapIntent?>` (bản đồ đọc rồi xóa) |

## 4. Dữ liệu thử (chỉ Emulator)

`scripts/seed/map-demo.json`: 16 cơ sở `mapdemo-*` theo [research R13](research.md#r13-dữ-liệu-seed-và-dữ-liệu-thử-cho-bản-đồ). Cùng schema `venues.json` của seed. `seed.mjs` thêm cờ `--with-map-demo`, chỉ nhận khi có `--emulator`.

`scripts/seed/geohash.vectors.json`: `[{ "lat": 10.8128, "lng": 106.6283, "geohash": "w3gvd2km5k" }, …]` lấy từ 10 cơ sở seed; test Kotlin và `npm run check` cùng đọc.

## Danh sách thay đổi cần đưa vào `docs/design/database/README.md`

1. **§4.3 `venues`:** dòng `geohash` đổi ghi chú thành "Độ dài 10, sinh từ `location` bằng `GeoFireUtils.getGeoHashForLocation` (app) hoặc `geofire-common` (Edge Function). D ghi lại mỗi khi `location` đổi. Dùng cho bản đồ theo vùng và 'Sân gần bạn' (021)".
2. **§4.3 `venues`:** dòng `location` thêm "Bắt buộc hợp lệ (khác `0,0`) khi `status = ACTIVE`; thiếu thì cơ sở không hiện trên bản đồ".
3. **§6 Index:** thay dòng `venues | geohash | Gần tôi (02)` bằng `venues | status, geohash | Bản đồ theo vùng, Sân gần bạn (021)`.
4. **§2 hoặc §9:** ghi chú thư viện `geofire-android-common` (chỉ `GeoFireUtils`) là cách sinh và truy vấn geohash chung của nhóm.
