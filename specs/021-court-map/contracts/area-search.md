# Contract: Tìm theo vùng, "Sân gần bạn" và phân cụm

**Package:** `com.example.booking_app.feature.search` (cùng nhóm 02 với 020, 022). **Người dùng contract:** `SearchMapViewModel`, `NearbyViewModel` (021); D tham khảo §4 để ghi `geohash`. Lý do thiết kế: [research R2, R3, R5, R6, R9](../research.md).

## 1. `VenueAreaSearch`

```kotlin
interface VenueAreaSearch {
    /**
     * Tải cơ sở cho một yêu cầu. Phát AreaResult nhiều lần khi kiểm tra giờ trống theo đợt
     * (lần cuối có loading = false ở phía ViewModel). Hủy collect = hủy mọi query đang chạy.
     */
    fun load(request: LoadRequest): Flow<AreaResult>
}
```

| Bước | `VIEWPORT` | `DISTRICT` |
|---|---|---|
| 0. Kiểm tra | `viewport.halfDiagonalMeters() > 15_000` → phát một `AreaResult(tooWide = true)` rồi dừng, **không** query | — |
| 1. Query | `GeoAreaQuery.bounds(viewport.center, viewport.halfDiagonalMeters())` → mỗi cặp: `venues.whereEqualTo("status","ACTIVE").orderBy("geohash").startAt(start).endAt(end).limit(150)`, song song, bỏ trùng theo ID | `venues.whereEqualTo("status","ACTIVE").whereEqualTo("districtId", X).orderBy("ratingAvg", DESC).orderBy("ratingCount", DESC).limit(101)` |
| 2. Lọc vị trí | Bỏ `location` thiếu hoặc `(0,0)`; giữ cơ sở có `viewport.contains(location)` | Bỏ `location` thiếu hoặc `(0,0)` |
| 3. Lọc rẻ | `VenueMatcher` của 020 với **toàn bộ** `criteria` (từ khóa, quận, tiện ích, giờ mở cửa, cắt giá) | như trái |
| 4. Sắp, cắt | Theo khoảng cách tới `viewport.center` tăng dần; > 100 thì cắt 100, `capped = true` | Theo khoảng cách tới tâm khung bao; > 100 thì cắt, `capped = true` |
| 5. Giờ trống (khi `criteria.timeWindow != null`) | `AvailabilityEvaluator` của 020 theo đợt 6 cơ sở, theo thứ tự bước 4. Sau mỗi đợt phát `AreaResult` với các cơ sở đã xác nhận còn trống | như trái |
| 6. Kết quả | `VenueResult(price, freeCourtCount, distanceMeters = origin?.let { GeoDistance(it, venue) })` | như trái, thêm `bounds` = khung bao kết quả |

- Mọi lời gọi Firestore đi qua `VenueDataSource` (020), bọc `awaitWithTimeout(15 s)`. Lỗi → `AppError` (ném ra khỏi flow, ViewModel bắt).
- `isFromCache = true` khi **mọi** snapshot của bước 1 có `metadata.isFromCache`.
- **Bất biến kiểm bằng test (SC-002):** với cùng dữ liệu giả, cùng `criteria` và cùng khung `B`:
  `ids(VenueAreaSearch.load(VIEWPORT, B)) == ids(drainAll(VenueSearchEngine, criteria)).filter { B.contains(location) }` (khi tổng ≤ 100), trong đó `drainAll` là hàm test gọi `firstPage` rồi `nextPage` tới khi `hasMore == false`. Với `criteria` có quận thì so ở chế độ `DISTRICT`, không lọc khung. Test chạy trên bộ fixture 26 cơ sở (seed + `map-demo`) với 8 bộ `criteria` (không lọc; từ khóa; quận; giá; tiện ích; khung giờ; khung giờ + giá; mọi thứ).

## 2. `NearbyVenues`

```kotlin
interface NearbyVenues {
    /** ≤ 10 cơ sở ACTIVE có vị trí hợp lệ trong 5 km quanh origin, khoảng cách tăng dần. */
    suspend fun around(origin: GeoPoint): List<VenueResult>
}
```

- Query như §1 bước 1 với bán kính 5.000 m, rồi `NearbySelector`: bỏ vị trí không hợp lệ, `GeoDistance(origin, venue) ≤ 5_000`, sắp tăng dần (bằng nhau thì theo `ratingAvg` giảm dần, rồi theo `id`), lấy 10.
- **Không** dùng `criteria`, **không** kiểm tra giờ trống. `price = DisplayPrice.From(minPricePerHour)` hoặc `Contact`.

## 3. `GridClusterer`

```kotlin
object GridClusterer {
    fun cluster(
        venues: List<VenueResult>,
        zoom: Double,
        cellSizePx: Int,              // 56dp đổi theo mật độ màn hình
        tileSizePx: Int,              // 256 × mật độ (khớp osmdroid)
        selectedId: String?,
    ): List<MapCluster>
}
```

| Quy tắc | Chi tiết |
|---|---|
| Ô | `floor(px.x / cellSizePx)`, `floor(px.y / cellSizePx)` với `px = MercatorProjection.toPixel(lat, lng, zoomRounded, tileSizePx)`; `zoomRounded` = làm tròn xuống tới 0,5 |
| Cụm | Mọi cơ sở cùng ô. `center` = trung bình lat/lng. `id` = `"$zoomRounded:$cx:$cy"` |
| Ổn định | Cùng đầu vào → cùng đầu ra, không phụ thuộc thứ tự `venues` |
| `allSameLocation` | Mọi cặp thành viên lệch nhau < 1 m |
| Chạm cụm | `allSameLocation` hoặc `zoom >= MAX_ZOOM` → hiện dãy thẻ của thành viên; ngược lại `FitBounds(khung bao thành viên)` |

## 4. `CardOrder`

```kotlin
object CardOrder {
    /** [anchor] trước, rồi mọi id còn lại trong `visible` theo khoảng cách tới anchor tăng dần (bằng nhau theo id). */
    fun build(anchor: VenueResult, visible: List<VenueResult>): List<String>
}
```

Dãy giữ nguyên trong lúc vuốt; lập lại khi chạm marker khác, chạm cụm ở mức tối đa (khi đó `visible` = thành viên cụm), hoặc có `AreaResult` mới (nếu `selectedVenueId` không còn trong kết quả thì bỏ chọn).

## 5. Geohash dùng chung (cho D)

- Độ dài **10**, base32 `0123456789bcdefghjkmnpqrstuvwxyz`, xen kẽ bắt đầu bằng **kinh độ**.
- App: `GeoFireUtils.getGeoHashForLocation(GeoLocation(lat, lng))` (mặc định 10). Edge Function: `geohashForLocation([lat, lng])` của `geofire-common` (npm). Seed: hàm `geohash()` trong `scripts/seed/seed.mjs`.
- Cả ba phải ra đúng `scripts/seed/geohash.vectors.json`.
- Ghi `geohash` **cùng lúc** với `location` (một lần `set`/`update`). Đổi `location` mà quên `geohash` thì cơ sở hiện sai vùng trên bản đồ.
