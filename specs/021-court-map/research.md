# Research: Bản đồ sân và sân gần bạn (spec 021)

**Ngày:** 2026-10-28 · **Người làm:** A · **Nguồn kiểm chứng:** tài liệu Firestore "Geo queries" (`firebase.google.com/docs/firestore/solutions/geoqueries`), README `firebase/geofire-android`, README osmdroid (repo archive ngày 2024-11-20), trang JitPack của `mkergall/osmbonuspack`; plan/research 020, 030, 040; đặc tả CSDL; `scripts/seed/` (PR #41).

Mỗi mục ghi: quyết định, lý do, phương án đã loại.

---

## R1. Thứ tự làm và phụ thuộc vào 020, 030

**Hiện trạng (28/10):** code của 020 và 030 **chưa có dòng nào** (tasks 0/106 và 0/78). Trong code chỉ có `core/session`, `core/network` (`AppError`, `TaskAwait`), `core/util/AppClock` của 010 và phần lưới giờ của 040. 021 dựa trên `feature/search` (020) và cấu hình osmdroid (030), nên **không thể làm trọn trước 020**.

**Quyết định:** chia việc của 021 làm ba nhóm.

| Nhóm | Gồm | Cần gì trước | Làm khi nào |
|---|---|---|---|
| **Độc lập** | `GeoBounds`/`MapViewport` (R2), `GridClusterer` (R5), `CardOrder` (R6), `NearbySelector` (R9), `core/map` (R4, chuyển cấu hình osmdroid từ 030 lên), dữ liệu `map-demo.json` (R13), index `status, geohash` | Không. Đều là Kotlin thuần hoặc file dữ liệu, test bằng JUnit | Ngay, song song với 020 |
| **Cần lõi 020** | `VenueAreaSearch` (R2, R3) | `core/location` (020 T013, T014), `core/pricing`, `core/schedule` (T011, T012), `SearchCriteria` (T026), `VenueSummary`/`VenueResult`/`DisplayPrice` (T027), `VenueDataSource` (T029), `VenueMatcher` (T044, T064), `AvailabilityEvaluator` (T063), `VenueStaticCache` (T062) | Sau Phase 2 của 020 và US2 của 020 (lọc khung giờ) |
| **Cần màn hình 020** | `SearchMapFragment`, nút Danh sách/Bản đồ, `NearbyVenuesFragment`, bố cục `w600dp` | `SearchFragment`, `SearchViewModel` (T048, T052), slot `nearby_container` (T051), luồng quyền vị trí (T075, T077), chip (T055, T068) | Sau US1 + US3 của 020 |

**Chỗ cần sửa plan/tasks của 020** (A tự sửa, ghi ở [plan §Phối hợp](plan.md#phối-hợp-với-nhóm)):
1. `SearchViewModel` thêm `displayMode: LIST | MAP` (lưu `SavedStateHandle`), `dropDistrictForArea()`, và nhận `MapIntent` từ "Sân gần bạn" (R9).
2. `fragment_search.xml` (020 T051): nút chuyển chế độ, `FragmentContainerView` `@+id/map_container` cạnh `RecyclerView`; bản `layout-w600dp` hai ô (R11).
3. `LocationProvider` (020 T014) thêm `fun updates(): Flow<GeoPoint>` cho chấm vị trí (R7). Hộp giải thích và trình xin quyền (020 T075, T077) tách thành `LocationPermissionHelper` dùng chung cho thẻ kết quả, bản đồ và "Sân gần bạn".
4. `VenueResultAdapter` (020 T050, T069, T078): tách phần định dạng giá/điểm/khoảng cách thành `VenueCardFormatter` để thẻ tóm tắt bản đồ dùng lại (R6, SC-003).
5. `VenueDataSource` (020 T029) thêm `venuesInGeohashRange(start, end, limit)` và `venuesByDistrict(districtId, limit)`.

**Chỗ cần sửa plan/tasks của 030** (A): `VenueMapController` của 030 nằm ở `feature/courtdetail/ui`, 021 không được import (constitution I). Đưa phần cấu hình osmdroid lên `core/map` (R4); 030 T050 gọi lại `core/map`.

## R2. Truy vấn theo vùng: geohash, giới hạn Firestore, cắt 100

**Quyết định:**
- **Dùng thư viện `com.firebase:geofire-android-common` (3.2.0)**, chỉ lớp `GeoFireUtils` (`getGeoHashForLocation`, `getGeoHashQueryBounds`, `getDistanceBetween`). Không dùng phần realtime của GeoFire.
- Vùng bản đồ (`MapViewport`: tâm, mức phóng, khung `north/south/east/west`) đổi thành **vòng tròn ngoại tiếp**: tâm là tâm khung, bán kính là nửa đường chéo. `getGeoHashQueryBounds(tâm, bán kính)` trả 4–9 cặp khoảng geohash (thường là 4, theo tài liệu Firestore).
- Mỗi cặp là một query: `venues.where(status == "ACTIVE").orderBy("geohash").startAt(b.start).endAt(b.end).limit(150)`. Chạy song song, bỏ trùng theo ID.
- Lọc trên máy, theo thứ tự:
  1. Vị trí hợp lệ (bỏ `location` thiếu hoặc `0,0`, Edge case "Cơ sở thiếu vị trí").
  2. **Nằm trong khung chữ nhật** đang xem. Vòng tròn rộng hơn khung, nên bước này loại phần thừa ở góc.
  3. `VenueMatcher` của 020: từ khóa, **quận**, tiện ích "đủ tất cả", giờ mở cửa, cắt giá theo `minPricePerHour`.
  4. Sắp theo khoảng cách tới **tâm vùng**, lấy tối đa **100** (FR-006). Nếu sau bước 3 còn hơn 100 thì `capped = true` và hiện "Phóng gần hơn để xem thêm sân".
  5. Có khung giờ: chạy `AvailabilityEvaluator` của 020 **lười theo khoảng cách tới tâm**, mỗi đợt 6 cơ sở, dừng khi đủ 100 cơ sở còn trống hoặc hết ứng viên. Giá theo khung và số sân trống lấy từ kết quả đó.
- **Vùng quá rộng:** nửa đường chéo > **15 km** (khoảng mức phóng ≤ 11 trên điện thoại) thì không tải, hiện "Phóng gần hơn để tìm sân" (US2-7, FR-005). Hằng số `MapLimits.MAX_SEARCH_RADIUS_M = 15_000`.
- **Ghép với bộ lọc của 020:** Firestore chỉ lọc `status` và **một** khoảng trên `geohash`. Firestore cho phép lọc khoảng trên nhiều trường, nhưng ghép `minPricePerHour` thì kết quả phải `orderBy(geohash)` trước nên trường giá không giúp cắt bớt lượt đọc. Mọi bộ lọc khác chạy trên máy bằng **đúng** các lớp của 020. Như vậy 021 không thêm quy tắc khớp nào của riêng mình.
- **Index:** một composite index mới `venues (status ASC, geohash ASC)`. Xem [contracts/rules-and-indexes.md](contracts/rules-and-indexes.md).
- **Thuật toán geohash dùng chung:** `GeoFireUtils.getGeoHashForLocation` mặc định độ dài 10, trùng với hàm `geohash()` trong `scripts/seed/seed.mjs` (đã đối chiếu: cùng base32, xen kẽ kinh độ trước). D dùng `GeoFireUtils` trong app (111) hoặc `geofire-common` (npm, cùng tác giả, cùng thuật toán) trong Edge Function `review-owner-application`. Có file vector `geohash.vectors.json` để ba bản (Kotlin, seed JS, TS) chạy chung (R13).

**Lý do chọn thư viện thay vì tự viết:**
- Tính khoảng geohash phủ một vòng tròn phải xử lý ô kề, ô vượt kinh tuyến 180°, chọn độ dài tiền tố theo bán kính. Đây là phần dễ sai và khó test hết. Thư viện là bản chính thức của Firebase và đúng mẫu trong tài liệu Firestore.
- Thư viện nhỏ (Java thuần, không kéo Firebase Database), có trên Maven Central, không cần khóa.
- Tự viết tốn khoảng 100 dòng kèm test, đổi lại không phải thêm phụ thuộc. Lợi ích đó nhỏ hơn rủi ro sai ở biên. Ghi vào Complexity Tracking.

**Phương án đã loại:**
- **Tự viết `GeoHashBounds`:** như trên.
- **Lọc theo hai khoảng `location.latitude`/`longitude`:** Firestore cho lọc khoảng trên nhiều trường, nhưng `GeoPoint` không tách được thành hai trường lọc; phải thêm hai trường `lat`, `lng` do D ghi, và index sẽ quét theo một trường rồi lọc trường kia nên lượt đọc không giảm. Bị loại.
- **Đọc mọi `venues` ACTIVE một lần rồi lọc vùng trên máy:** đơn giản nhất với 10–30 cơ sở demo, nhưng mỗi lần tải tốn đúng số cơ sở trong cả hệ thống. Trái ràng buộc "dùng truy vấn có index" của constitution khi dữ liệu tăng. Bị loại.
- **Ghép bộ lọc quận vào query theo vùng (`districtId ==` + khoảng `geohash`):** được, nhưng thêm index và khi tìm theo vùng thì bộ lọc quận bị bỏ (FR-005), nên chỉ có ích ở chế độ tải theo quận. Chế độ đó đã có query riêng (R3).

## R3. Hai chế độ tải: theo vùng và theo quận

**Vấn đề:** bộ lọc có quận (ví dụ người chơi chọn Quận 12 ở danh sách rồi bấm "Bản đồ"). Nếu chỉ tải theo vùng đang xem, mà vùng mặc định ở trung tâm TP.HCM, thì không có marker nào dù danh sách có 5 sân. Spec cũng giả định không có tọa độ tâm cho từng quận (Assumptions).

**Quyết định:** `MapLoadMode` có hai giá trị.

| Chế độ | Khi nào | Query | Camera |
|---|---|---|---|
| `DISTRICT` | Vào bản đồ khi `criteria.districtId != null`, hoặc `districtId` đổi sang một quận khác (người chơi chọn, 022 điền) | `venues.where(status == ACTIVE).where(districtId == X).orderBy(ratingAvg desc).limit(101)` (dùng lại index V2 của 020), rồi lọc trên máy như R2 bước 1, 3–5 | Đưa camera bao mọi marker (`zoomToBoundingBox`, lề 48dp, mức phóng tối đa 16). Không có marker thì giữ vùng hiện tại và hiện trạng thái rỗng |
| `VIEWPORT` | Mọi trường hợp còn lại: đổi bộ lọc khác quận, "Tìm ở khu vực này", "Vị trí của tôi", "Xem tất cả trên bản đồ" | R2 | Giữ nguyên (hoặc về vị trí khi bấm "Vị trí của tôi") |

- **"Tìm ở khu vực này" khi đang có quận:** gọi `SearchViewModel.dropDistrictForArea()`. Hàm này bỏ `districtId` khỏi `criteria` (chip quận biến mất) và phát snackbar "Đã tìm theo khu vực trên bản đồ" (US2-6). Vì `criteria` đổi, bản đồ tải lại theo `VIEWPORT`. Không cần một luồng tải riêng.
- **Ở chế độ `DISTRICT`, kéo bản đồ** vẫn hiện nút "Tìm ở khu vực này" như thường (FR-004).

**Phương án đã loại:** thêm tọa độ tâm quận vào `districts`: spec đã ghi là không cần (Assumptions), và tâm quận không bảo đảm thấy hết sân của quận. Dùng lại các trang đã tải của danh sách: danh sách chia trang 20 và có thể đang sắp theo giá, nên tập marker sẽ thiếu.

## R4. osmdroid, `core/map` và rủi ro thư viện đã archive

**Quyết định:**
- Dùng **osmdroid** như 030 R7 (constitution: không có tài khoản thanh toán Google Maps). Một thư viện bản đồ cho cả app.
- Phần dùng chung đưa lên `core/map` (030 đang để trong `feature/courtdetail/ui`):
  - `OsmdroidSetup.ensure(context)`: `userAgentValue = BuildConfig.APPLICATION_ID`, `osmdroidBasePath`/`osmdroidTileCache` trong `cacheDir/osmdroid`, gọi một lần.
  - `MapThemer.apply(mapView, isDark)`: `TilesOverlay.INVERT_COLORS` khi tối, `CopyrightOverlay` "© OpenStreetMap contributors".
  - `TileHealthWatcher`: theo dõi tile tải xong/lỗi (R10).
  - `MarkerIcons`: `ic_marker_shuttlecock` thường và nổi bật, icon cụm có số (vẽ lên `Bitmap`, cache theo số và theme).
  - 030 T050 (`VenueMapController`) gọi lại các lớp này.
- **Rủi ro:** repo osmdroid đã **archive ngày 2024-11-20**, không còn bản mới. Bản cuối vẫn chạy trên Android hiện tại. 030 T002 đã có bước kiểm tra build với AGP 9.4 và `targetSdk 37`. Nếu lỗi thì dừng lại báo nhóm, không tự đổi thư viện. Ghi vào Complexity Tracking.

**Phương án đã loại:**
- **MapLibre Native Android:** còn bảo trì và vẽ vector, nhưng nặng hơn nhiều (thư viện native vài MB mỗi ABI), cần style JSON và nguồn tile vector miễn phí, và 030 đã chốt osmdroid. Ghi làm hướng thay thế nếu osmdroid hỏng trên SDK mới.
- **Google Maps SDK:** cần tài khoản thanh toán (030 R7).

## R5. Phân cụm marker (FR-003)

**Quyết định: tự viết `GridClusterer` thuần Kotlin**, không dùng `RadiusMarkerClusterer` của osmbonuspack.

- **Thuật toán:** chiếu mỗi cơ sở sang tọa độ pixel Web Mercator ở mức phóng hiện tại (`MercatorProjection.toPixel(lat, lng, zoom)`, hàm thuần, kích thước tile 256 × mật độ màn hình). Chia lưới ô **56dp**. Mọi cơ sở cùng ô gộp thành một cụm. Vị trí cụm là trung bình tọa độ các thành viên. Ô có 1 cơ sở là marker đơn.
- Gom lại khi **mức phóng đổi** (làm tròn tới 0,5) hoặc khi danh sách cơ sở đổi. Kéo ngang thì không gom lại, vì lưới cố định theo tọa độ thế giới nên cụm không nhảy. Tối đa 100 cơ sở nên mỗi lần gom dưới 1 ms.
- **Chạm cụm:** `zoomToBoundingBox` của các thành viên. Nếu đã ở mức phóng tối đa của bản đồ (19), hoặc mọi thành viên trùng tọa độ (sai khác dưới 1 m), thì không phóng nữa. Khi đó hiện dãy thẻ của đúng các thành viên để vuốt (FR-003, Edge case "Nhiều cơ sở cùng tọa độ").
- **Vẽ:** mỗi cụm/marker là một `org.osmdroid.views.overlay.Marker`, đặt trong một `FolderOverlay` và thay cả lớp mỗi lần gom lại. Marker nổi bật dùng icon lớn hơn và vẽ trên cùng.

**Lý do:**
- osmbonuspack chỉ phát hành qua **JitPack** (bản 6.9.0). Phải thêm kho JitPack vào `settings.gradle.kts`, đã có báo lỗi "Failed to resolve" với bản này, và có issue hỏi lớp `RadiusMarkerClusterer` bị mất ở bản mới. Thư viện cũng phụ thuộc osmdroid đã archive. Thêm cả một thư viện lớn (routing, KML, POI) chỉ để dùng một lớp là trái YAGNI (nguyên tắc VII).
- Gom theo lưới đủ cho tối đa 100 điểm, dễ unit test (cho tọa độ, mức phóng, kiểm tra nhóm), dễ giải thích khi bảo vệ đồ án.

**Phương án đã loại:** osmbonuspack (như trên); thuật toán khoảng cách (gom theo bán kính từ từng điểm): cụm phụ thuộc thứ tự duyệt và nhảy khi kéo bản đồ; không cần cho 100 điểm.

## R6. Thẻ tóm tắt, vuốt và đồng bộ marker (FR-007 đến FR-010, SC-003)

**Quyết định:**
- Thẻ là `ViewPager2` ở cuối màn hình, mỗi trang là `item_map_venue_card.xml` (ảnh trái 96dp, tên, quận · khoảng cách, điểm, giá) theo wireframe. Trang kế lộ mép 24dp để gợi ý vuốt.
- **Thứ tự thẻ (`CardOrder`, hàm thuần):** khi chạm marker X, dãy thẻ là `[X, n1, n2, …]`, trong đó n1, n2 là các cơ sở **đang hiện trên bản đồ** (sau lọc), sắp theo khoảng cách tới X tăng dần. Dãy này **giữ cố định** trong lúc vuốt và chỉ lập lại khi chạm marker khác hoặc tải kết quả mới. Nếu lập lại mỗi lần vuốt thì trang trước sẽ đổi chỗ và vuốt ngược không về được cơ sở cũ. Vuốt sang trái là cơ sở gần kế tiếp, vuốt phải là quay lại. Cụm ở mức phóng tối đa: dãy chỉ gồm thành viên cụm.
- **Đồng bộ hai chiều:** trạng thái duy nhất là `selectedVenueId` trong `SearchMapViewModel`. Chạm marker → đặt `selectedVenueId`, lập dãy, pager về trang 0. Vuốt pager → đặt `selectedVenueId` theo trang, marker tương ứng nổi bật, camera `animateTo` cơ sở đó (giữ mức phóng). Lần di camera này **do app** nên không làm hiện nút "Tìm ở khu vực này" (R8).
- Cơ sở đang chọn nằm trong cụm sau khi gom lại: cụm đó nổi bật. Khi phóng tách ra thì marker của cơ sở nổi bật.
- **Chạm vùng trống** (`MapEventsOverlay.singleTapConfirmedHelper`): `selectedVenueId = null`, ẩn pager (FR-010).
- **Chạm thẻ:** mở `nav_court_detail` với `venueId` và `date`/`startTime`/`endTime` khi bộ lọc có khung giờ (030 contract §1.1, FR-009).
- **Giá, điểm, khoảng cách (SC-003):** thẻ bản đồ và thẻ danh sách cùng gọi `VenueCardFormatter` (tách từ `VenueResultAdapter` của 020): `DisplayPrice` → "từ X đ/giờ" / "X đ/giờ · HH:mm–HH:mm" / "Liên hệ"; điểm "★ 4,6 (128)" / "Chưa có đánh giá"; `DistanceFormatter`. `DisplayPrice` và `freeCourtCount` do `AvailabilityEvaluator` gắn trong cùng pipeline (R2), nên hai chế độ ra cùng một giá. Hai thẻ khác bố cục (wireframe), nên không dùng chung layout, chỉ dùng chung formatter.

**Phương án đã loại:** sắp lại dãy thẻ theo khoảng cách tới cơ sở đang chọn sau mỗi lần vuốt (thứ tự không ổn định như trên); `RecyclerView` + `PagerSnapHelper` thay `ViewPager2` (phải tự xử lý trang hiện tại và callback, `ViewPager2` đã có trong app cho 030).

## R7. Vị trí và chấm vị trí (FR-011 đến FR-015, SC-006)

**Quyết định:**
- Dùng `LocationProvider` của 020 (`LocationManagerCompat.getCurrentLocation`, timeout 10 giây). **Không thêm** `play-services-location`. Bản đồ cần độ chính xác vài trăm mét, provider mạng đủ dùng.
- **Chấm vị trí cập nhật khi di chuyển** (Edge case "Vị trí thay đổi"): thêm `LocationProvider.updates(): Flow<GeoPoint>` (`LocationManagerCompat.requestLocationUpdates`, 10 giây hoặc 25 m, `callbackFlow`, gỡ khi ngừng thu). Flow chỉ chạy khi bản đồ đang `STARTED` và đã có quyền.
- Vẽ chấm bằng `MyLocationNewOverlay` của osmdroid, cấp vị trí qua `FlowLocationProvider` (adapter `IMyLocationProvider` nhận từ flow trên), **tắt** `enableFollowLocation`. Như vậy chỉ có một nguồn vị trí trong app, và camera không tự đi theo.
- **"Vị trí của tôi" (US3):**
  - Chưa có quyền: `LocationPermissionHelper` (tách từ 020 T075/T077) hiện hộp giải thích rồi hộp xin quyền của hệ thống, xin cả `COARSE` và `FINE` (chấp nhận "gần đúng", FR-012).
  - Có quyền: `current()` → camera `animateTo` vị trí, mức phóng 15, tải `VIEWPORT`.
  - Từ chối: giữ vùng, banner "Đang hiện khu vực trung tâm TP.HCM. Bật vị trí để xem khoảng cách." (wireframe 2) kèm snackbar có nút "Cài đặt" (`ACTION_APPLICATION_DETAILS_SETTINGS`).
  - Hết 10 giây hoặc định vị tắt: "Không lấy được vị trí. Hãy bật định vị rồi thử lại.", giữ vùng (US3-5).
- **Mở bản đồ không xin quyền** (FR-012, US3-6). Nếu đã có quyền từ trước thì lấy vị trí ngầm để hiện chấm và khoảng cách, nhưng **không** di camera.
- **Khu vực mặc định** (FR-013, đã chốt ở Clarifications): `MapDefaults.CENTER = (10.7769, 106.7009)` (trung tâm Quận 1), mức phóng **12**, giống nhau cho mọi người. Ở mức 12 màn hình điện thoại rộng khoảng 13 km, thấy được Quận 1, 3, 4, 5, 10, Bình Thạnh, Phú Nhuận. **Lưu ý seed:** 10 sân của seed nằm ở Tân Bình, Tân Phú, Gò Vấp, Quận 12, cách tâm này 6,8–11 km, nên vùng mặc định chỉ thấy vài marker ở rìa. Quickstart dùng thêm `map-demo.json` (R13). Không dời tâm theo seed vì Clarifications đã chốt "trung tâm TP.HCM".
- **Vị trí không rời khỏi máy (FR-015, SC-006):**
  - Vị trí người chơi (`userLocation`) chỉ nằm trong `StateFlow` của `SearchMapViewModel` và `NearbyViewModel` (bộ nhớ). **Không** ghi vào `SearchCriteria`, lịch sử, Firestore, log, và **không** ghi vào `SavedStateHandle`. Sau khi tiến trình bị hủy, chấm vị trí được lấy lại nếu còn quyền.
  - Camera (tâm, mức phóng) **được** lưu vào `SavedStateHandle` để giữ vùng đang xem (FR-009). Sau "Vị trí của tôi", tâm camera trùng vị trí người chơi. Dữ liệu này vẫn chỉ nằm trên máy (Bundle của hệ thống), đúng FR-015 ("chỉ dùng trên thiết bị"). Prompt plan yêu cầu không đưa vị trí vào `SavedStateHandle`; plan hiểu là không lưu trường vị trí người chơi, còn tâm camera là trạng thái hiển thị. Nếu nhóm muốn chặt hơn thì sau "Vị trí của tôi" lưu tâm camera đã làm tròn 3 chữ số (~100 m).
  - Test SC-006: (1) `SearchMapViewModelTest`: sau khi fake `LocationProvider` trả vị trí, mọi khóa trong `SavedStateHandle` không chứa `userLocation`, `criteria` không đổi; (2) fake `SearchHistoryRepository` ghi nhận mọi `RecordSearch` không có trường vị trí (kiểu `SearchCriteria` không có trường này); (3) Rules `searchHistory` của 020 chặn trường lạ (H-* của 020). Không có đường ghi nào khác trong `feature/search`.

**Phương án đã loại:** `play-services-location` (Fused): chính xác và tiết kiệm pin hơn nhưng thêm phụ thuộc Google Play Services; 020 R9 đã loại. `MyLocationNewOverlay` với `GpsMyLocationProvider` mặc định: tạo nguồn vị trí thứ hai ngoài `LocationProvider`, khó thay bằng bản giả khi test.

## R8. Không tự tải lại khi kéo/phóng (FR-004, SC-007)

**Quyết định:**
- Gắn `MapListener` (`onScroll`, `onZoom`) và **debounce 300 ms** sau sự kiện cuối. Khi hết debounce, gửi `onViewportSettled(viewport, byUser)` cho ViewModel.
- `byUser` lấy từ `OnTouchListener` của `MapView`: `true` nếu có thao tác chạm (kéo, chụm) từ lần settle trước. Camera di do app (vuốt thẻ, "Vị trí của tôi", fit theo quận, phục hồi trạng thái) có `byUser = false`.
- ViewModel hiện nút "Tìm ở khu vực này" khi `byUser` và vùng mới **khác đáng kể** vùng đã tải: tâm lệch hơn 25% chiều rộng khung, hoặc mức phóng lệch ≥ 1. Không gọi engine. Marker cũ giữ nguyên (US2-4).
- Chỉ ba thứ gây tải: `criteria` đổi (FR-004, gồm cả 022 gọi `applyExternal`), bấm "Tìm ở khu vực này", và "Vị trí của tôi" / "Xem tất cả trên bản đồ" / "Tìm rộng hơn".
- `criteria` từ `SearchViewModel` đi qua `distinctUntilChanged()`; mọi yêu cầu tải đi qua một `MutableSharedFlow<LoadRequest>` rồi `flatMapLatest { areaSearch.load(it) }`. Yêu cầu mới hủy yêu cầu cũ nên kết quả cũ không ghi đè (như 020 R10).
- SC-007 có unit test: phát 60 sự kiện `onViewportSettled(byUser = true)` trong 1 phút (thời gian ảo của `runTest`), kiểm tra fake engine không được gọi lần nào.

**Phương án đã loại:** tự tải sau khi dừng kéo (trái FR-004); bỏ debounce và so sánh mỗi sự kiện (osmdroid gọi `onScroll` liên tục khi kéo, nút sẽ nhấp nháy).

## R9. "Sân gần bạn" (FR-015a đến FR-018)

**Quyết định:**
- `NearbyVenuesFragment` đặt vào `@+id/nearby_container` mà 020 T051 đã chừa ngay dưới thanh tìm kiếm. 020 đã quy định vùng này chỉ hiện khi `criteria` trống và ẩn khi có điều kiện. Ô nhập/lịch sử là màn hình riêng (`SearchInputFragment`), nên khi mở lịch sử thì vùng này tự khuất (FR-015a). Xóa hết điều kiện thì vùng hiện lại. 021 không phải tự viết luật thu gọn.
- **Truy vấn:** `getGeoHashQueryBounds(vị trí, 5_000)` → 4–9 query `status == ACTIVE` + khoảng `geohash` (R2), lọc vị trí hợp lệ, `GeoFireUtils.getDistanceBetween ≤ 5.000 m`, sắp tăng dần, lấy **10** (`NearbySelector`, hàm thuần). Không kiểm tra giờ trống. Giá là "từ X đ/giờ" theo `minPricePerHour` (FR-016).
- **Trạng thái (`NearbyUiState`):** `NeedPermission` (lần đầu, chưa từng hỏi), `Denied` (đã từ chối), `Locating`, `Unavailable` (không lấy được vị trí), `Loading`, `Content(items)`, `Empty` (không có sân trong 5 km), `Error`.
- **"Mở Sân gần bạn lần đầu" (US4-1, FR-012):** vùng này hiện ngay khi vào màn hình tìm kiếm, nên nếu tự hiện hộp xin quyền thì người chơi bị hỏi ngay khi mở app, trái tinh thần "xin lúc cần dùng" (constitution VI). Plan hiểu "mở lần đầu" là **lần đầu người chơi bấm vào mục**: trạng thái `NeedPermission` hiện một dòng giải thích ngắn kèm nút "Bật vị trí". Bấm nút thì mới hiện hộp xin quyền của hệ thống (qua `LocationPermissionHelper`, bỏ hộp giải thích thứ hai vì dòng giải thích đã có trên thẻ). Ghi vào [bảng sửa spec](#r16-những-chỗ-cần-sửa-spec-hoặc-tài-liệu-khác) để chốt câu chữ US4-1.
- **Nút:**
  - "Xem tất cả trên bản đồ": gửi `MapIntent.AroundMe(zoom = 14)` cho `SearchViewModel`. ViewModel đổi `displayMode = MAP`; bản đồ đưa camera về vị trí ở mức 14 (thấy bán kính khoảng 5 km) rồi tải `VIEWPORT`. Bộ lọc lúc này đã trống, đúng "không mang theo bộ lọc" (US4-4).
  - "Tìm rộng hơn": như trên với mức phóng 12 (US4-5).
  - "Tìm theo quận": mở `searchFilterSheet` cuộn tới mục Quận (như chip "Quận" của 020).
  - "Bật vị trí": trạng thái `NeedPermission` thì xin quyền; trạng thái `Denied` mà hệ thống không cho hỏi lại (`shouldShowRequestPermissionRationale == false` sau khi từ chối) thì mở cài đặt.
- **Lượt đọc:** khoảng 4 query + số cơ sở trong vòng 5 km (seed: 8 cơ sở quanh Tân Bình) ≈ 12–40 lượt, mỗi lần vào màn hình chính. Kết quả giữ trong `NearbyViewModel` (scope `nav_search`) và **chỉ tải lại khi vị trí lệch hơn 500 m** so với lần tải trước hoặc người chơi kéo để làm mới, để không đọc lại mỗi lần quay về từ chi tiết sân.
- Thẻ dải ngang `item_nearby_venue.xml` (ảnh, tên, khoảng cách, "từ X đ/giờ", điểm) dùng `VenueCardFormatter` (R6).

**Phương án đã loại:** tự xin quyền khi vùng hiện lần đầu (như trên); chạy `VenueSearchEngine` của 020 với `sort = NEAREST` (020 S2 đọc tới 150 cơ sở toàn hệ thống, không giới hạn bán kính).

## R10. Trạng thái, lỗi, mất mạng và giữ trạng thái (FR-019 đến FR-021, SC-005, FR-009)

**Quyết định:**
- `MapUiState` có: `markers` (cụm và marker đã gom), `cards`, `selectedVenueId`, `loading` (thanh tiến trình mỏng dưới hàng chip, **không** che bản đồ), `empty` (thẻ nổi "Không có sân phù hợp trong khu vực này" + "Thu nhỏ bản đồ", "Xóa bộ lọc"), `error` (thẻ nổi + "Thử lại"), `capped`, `tooWide`, `showSearchArea`, `staleFromCache`, `locationBanner`.
- **Bản đồ không tải được (FR-020):**
  - `MapView` ném lỗi khi khởi tạo: bắt trong `SearchMapFragment`, phát `MapFailed`.
  - Tile: `TileHealthWatcher` (`core/map`) đăng ký `Handler` của `MapTileProviderBase` để đếm tile xong và tile lỗi. Nếu **8 giây** đầu không có tile nào xong mà có ít nhất 4 tile lỗi, hoặc máy không có mạng và cache tile trống, thì phát `MapFailed`.
  - `MapFailed` → `SearchViewModel.onMapUnavailable()`: đổi `displayMode = LIST`, snackbar "Không tải được bản đồ, đã chuyển về danh sách". Bộ lọc giữ nguyên, nút "Bản đồ" vẫn còn để thử lại.
- **Mất mạng (FR-021):** query dùng `get()` mặc định. Firestore trả dữ liệu cache khi không có mạng, `metadata.isFromCache` → `staleFromCache = true` → banner "Không có kết nối, kết quả có thể chưa cập nhật" (`search_offline_stale` của 020). Riêng "Tìm ở khu vực này": nếu **mọi** query trả từ cache và không có kết quả nào, coi là lỗi: giữ marker cũ, snackbar lỗi kèm "Thử lại" (Edge case). Mọi lời gọi bọc `awaitWithTimeout(15 s)` của `core/network`, lỗi đổi thành `AppError`.
- **Giữ trạng thái:**

| Tình huống | Cách giữ |
|---|---|
| Xoay màn hình, đổi sáng/tối | `SearchMapViewModel` scope `nav_search` sống qua thay đổi cấu hình; `MapView` dựng lại từ `camera` và `selectedVenueId` trong ViewModel |
| Mở chi tiết sân (030) rồi quay lại | ViewModel scope nav graph còn nguyên; `MapView` dựng lại như trên, **không** tải lại (FR-009) |
| Tiến trình bị hủy | `SavedStateHandle` lưu `camera` (lat, lng, zoom), `selectedVenueId`, `loadedViewport`, `loadMode`. `criteria` đã do `SearchViewModel` lưu (020 R10). Mở lại thì tải lại một lần theo `loadedViewport` |
| Khách đăng nhập giữa chừng | `nav_auth` mở chồng lên rồi `popBackStack` (010 R15), ViewModel còn nguyên |

- `MapView.onResume()/onPause()/onDetach()` theo `viewLifecycleOwner`, như 030 R7.

## R11. Máy tính bảng (FR-023)

**Quyết định:**
- `layout-w600dp/fragment_search.xml` (020) đặt danh sách (40%, tối thiểu 320dp) và `map_container` (phần còn lại) cạnh nhau. Nút chuyển chế độ ẩn. `displayMode` không dùng trên `w600dp`.
- **Đồng bộ cơ sở đang chọn:** `selectedVenueId` nằm trong `SearchMapViewModel`. Danh sách nhận nó (qua `SearchFragment`) để tô nền thẻ tương ứng và cuộn tới nếu thẻ đã tải.
  - Trên `w600dp`, chạm thẻ danh sách = **chọn** (marker nổi bật, camera di tới, hiện thẻ tóm tắt). Chạm thẻ tóm tắt mới mở chi tiết. Lý do: nếu chạm danh sách mở luôn chi tiết thì người chơi không bao giờ thấy phần đồng bộ mà spec yêu cầu.
  - Cơ sở có trong danh sách nhưng không có trên bản đồ (ngoài vùng, hoặc chưa tải trang đó): camera di tới cơ sở, không tải lại vùng.
- Thẻ tóm tắt trên `w600dp` rộng tối đa 420dp, căn trái của ô bản đồ.

**Phương án đã loại:** hai màn hình riêng trên máy tính bảng như điện thoại (không đạt "hiện cạnh nhau").

## R12. Trợ năng cho marker và cụm (FR-023)

**Quyết định:**
- `MapAccessibilityHelper : ExploreByTouchHelper` gắn vào `MapView` (`ViewCompat.setAccessibilityDelegate`). Mỗi marker/cụm **đang nằm trong màn hình** là một virtual view:
  - `contentDescription`: marker "Sân Hòa Bình, 1,2 km, 4,5 sao" (bỏ phần khoảng cách nếu không có vị trí, "chưa có đánh giá" nếu `ratingCount == 0`); cụm "Cụm 8 sân, chạm hai lần để phóng to"; marker đang chọn thêm "đang chọn".
  - Vùng chạm là hình vuông 48dp quanh điểm neo (`Projection.toPixels`), không theo kích thước icon.
  - Hành động `ACTION_CLICK` gọi đúng handler chạm marker/cụm.
  - Gọi `invalidateRoot()` sau mỗi lần gom lại hoặc settle camera.
- Thứ tự duyệt theo khoảng cách tới tâm màn hình, để TalkBack đọc cơ sở gần giữa trước.
- Các nút "Vị trí của tôi", "Tìm ở khu vực này", chuyển chế độ có `contentDescription`, cao tối thiểu 48dp. Thẻ tóm tắt đọc theo thứ tự tên → quận → khoảng cách → giá → điểm, như thẻ 020.
- Người dùng TalkBack luôn có chế độ danh sách với cùng bộ lọc (FR-001) làm lối thay thế.
- `MarkerIcons` cũng tạo icon cụm đủ tương phản ở chế độ tối (chữ trắng trên `colorPrimary`, viền `colorSurface`).

**Phương án đã loại:** chỉ dựa vào chế độ danh sách: không đạt FR-023 ("marker, cụm và các nút PHẢI có mô tả"); `contentDescription` trên `MapView`: TalkBack chỉ đọc được một câu cho cả bản đồ.

## R13. Dữ liệu seed và dữ liệu thử cho bản đồ

**Đã kiểm tra `scripts/seed/venues.json` (10 cơ sở, tọa độ gần đúng):**

| Quận | Cơ sở |
|---|---|
| Quận 12 (5) | PAM Badminton, Quang Vinh, Minh Tâm CN1, Tân Thới Nhất, Gia Hân. Cách nhau tối đa **1,8 km** |
| Gò Vấp (2) | Sơn Tạ, Minh Tâm CN2 |
| Tân Bình (2) | K3, Nụ Cười |
| Tân Phú (1) | Trường Chinh 909 |

Mọi cơ sở có `location`, `geohash` (độ dài 10, cùng thuật toán với `GeoFireUtils`, R2), `minPricePerHour`, `status = ACTIVE`; `ratingAvg = 0`; `amenityIds = []`. Cả 10 nằm trong vòng **5,5 km** của nhau.

**So với "Independent Test" của spec:**

| Kịch bản spec | Seed có đủ không | Cách làm |
|---|---|---|
| US1: 10 cơ sở ở 3 quận, đủ 10 marker | Có 10 cơ sở nhưng ở **4** quận | Dùng seed, sửa câu chữ trong quickstart thành 4 quận (không cần sửa spec) |
| US2: 8 cơ sở sát nhau ở Quận 10 thành cụm "8", kéo sang Quận 3 thấy sân khác | **Không**: không có sân ở Quận 10, Quận 3; cụm lớn nhất là 5 ở Quận 12 | Thêm `map-demo.json` (dưới) |
| Edge: nhiều cơ sở cùng tọa độ; cơ sở thiếu vị trí; cơ sở `HIDDEN` | **Không** | `map-demo.json` |
| US3: máy thử ở Quận 1 thấy khoảng cách | Có, nhưng sân gần nhất cách **6,8 km** | Dùng được |
| US4: ba cơ sở cách 0,8 / 2,5 / 12 km, chỉ hai cơ sở đầu hiện | Gần đủ với vị trí giả **(10.7990, 106.6480)** ở Tân Bình: Nụ Cười 0,55 km, K3 1,05 km, Trường Chinh 909 2,64 km … Sơn Tạ 4,91 km (8 cơ sở trong 5 km); Quang Vinh 5,47 km và Minh Tâm CN2 5,80 km nằm ngoài | Dùng vị trí này để kiểm tra cắt bán kính 5 km |
| US4-5: không có sân trong 5 km | Có: vị trí giả trung tâm Quận 1 **(10.7769, 106.7009)**, sân gần nhất 6,8 km | Dùng |

**Quyết định:** thêm `scripts/seed/map-demo.json` **chỉ nạp vào Emulator** (cờ `--emulator --with-map-demo` của `seed.mjs`, script từ chối khi chạy với project thật). Đây là dữ liệu giả hợp lý (constitution VI), ID `mapdemo-*`, `ownerId = "demo-owner-1"`, có `courts` và `priceRules` tối thiểu:
- 8 cơ sở trong bán kính 300 m quanh **(10.7735, 106.6670)** (Quận 10) để có cụm "8" ở mức phóng 13–14 và tách ở mức 17.
- 2 cơ sở **trùng tọa độ** **(10.7790, 106.6880)** (Quận 3) để thử cụm không tách ở mức phóng tối đa.
- 3 cơ sở ở Quận 3 quanh **(10.7840, 106.6860)** cho kịch bản "kéo sang Quận 3, Tìm ở khu vực này".
- 1 cơ sở `location = (0, 0)` và `geohash` rỗng (thiếu vị trí hợp lệ): có trong danh sách 020, không có trên bản đồ.
- 1 cơ sở `HIDDEN` ở Quận 10: không có ở cả hai chế độ.
- 1 cơ sở ở Củ Chi **(10.9730, 106.4930)**, cách Quận 1 khoảng 31 km, để thử vùng quá rộng và khoảng cách lớn.
- Ba cơ sở có `ratingAvg`/`ratingCount` khác 0 để kiểm tra mô tả TalkBack và thẻ.

Tổng 16 cơ sở thêm; cùng seed là 26, vẫn dưới 100 nên chưa thử được FR-006 bằng dữ liệu thật. FR-006 kiểm tra bằng unit test với 150 cơ sở giả.

**Vector geohash:** `scripts/seed/geohash.vectors.json` (khoảng 10 cặp tọa độ → geohash độ dài 10, lấy từ 10 cơ sở seed). Test Kotlin so `GeoFireUtils.getGeoHashForLocation` với file này. `seed.mjs` cũng kiểm tra với file này trong `npm run check`, để chắc dữ liệu cũ khớp thuật toán của app.

## R14. Lượt đọc và thời gian (SC-001, hạn mức Spark)

**Ước lượng lượt đọc mỗi lần tải** (query trả 0 kết quả vẫn tính 1 lượt; cache `priceRules`/`courts` 10 phút của 020):

| Kịch bản | Lượt đọc |
|---|---|
| `VIEWPORT` mức 13, không khung giờ, 26 cơ sở demo, ~10 trong vùng | ~4 query + ~14 cơ sở trong vòng tròn ngoại tiếp ≈ **18** |
| Như trên, có khung giờ, ~8 qua lọc rẻ, lần đầu | 18 + 8 × (3 + 4 + 2) ≈ **90** |
| Như trên, lần sau (đã cache bảng giá, sân con) | 18 + 8 × 2 ≈ **34** |
| `DISTRICT` Quận 12, không khung giờ | 5 |
| Vùng rộng 15 km (giới hạn), 100 cơ sở, có khung giờ, lần đầu (tệ nhất) | ~6 query + ~150 + 100 × 9 ≈ **1.050** |
| "Sân gần bạn" | ~4 + 8 ≈ **12** (seed), không tải lại khi vị trí lệch dưới 500 m |

Giả sử cả nhóm thử và demo 150 lần tải bản đồ/ngày (trung bình 40 lượt) và 100 lần mở màn hình chính có "Sân gần bạn" (12 lượt): **~7.200 lượt/ngày**. Cộng 24.000 của 020 là ~31.000, dưới hạn 50.000 nhưng không còn nhiều chỗ cho các tính năng khác. Biện pháp: không tự tải khi kéo (R8), cache bảng giá của 020, "Sân gần bạn" không tải lại khi vị trí chưa đổi, log `MapReads` khi debug ([quickstart §5](quickstart.md#5-đo-thời-gian-và-lượt-đọc-sc-001)).

**Thời gian (SC-001, 4G ~100 ms RTT):** 4 query geohash song song 300–600 ms; có khung giờ thêm 2 đợt kiểm tra ≈ 600–800 ms → **0,4–1,4 giây** cho vùng thường. Tệ nhất (100 cơ sở cần kiểm tra giờ trống) ≈ 17 đợt × 300 ms ≈ 5 giây, vượt 3 giây. Giảm thiểu: (1) **hiện marker theo từng đợt** khi đã xác nhận còn trống, không đợi đủ 100; thanh tiến trình chạy tới khi xong; (2) ngưỡng vùng quá rộng 15 km; (3) đợt kiểm tra lười theo khoảng cách tới tâm, nên marker gần giữa màn hình hiện trước. Đo lại theo quickstart §5.

## R15. Quyền đọc của khách (FR-022) và Rules hiện tại

**Quyết định:** 021 **không** thêm Rules mới. Query luôn có `status == "ACTIVE"`, đúng yêu cầu Rules `venues` mà 020 R13 và 030 R10 đã gửi D. `districts` công khai (020).

**Phát hiện khi đọc `firestore.rules` trên `main` (sau PR #43 của B):** match `venues/{venueId}` hiện là `allow read: if true` (mọi trạng thái). Rules này **rộng hơn** đề xuất của 020 R13/030 R10 (khách chỉ đọc `ACTIVE`). Với 021 thì query vẫn chạy được. Nhưng cơ sở `HIDDEN`/`LOCKED` đang đọc công khai được bằng `get()`, nên cần D chốt lại khi viết Rules của 111. Ghi vào bảng phối hợp, không sửa Rules của D trong 021.

**Test Emulator thêm (`firestore-tests/test/map.test.ts`):**
- M-1: khách chạy query `status == ACTIVE` + khoảng `geohash` → thành công.
- M-2: khách chạy query khoảng `geohash` **không** có `status == ACTIVE` → **phải fail** khi D đã áp Rules theo 020 R13 (để `it.todo` cho tới khi D sửa; hiện tại sẽ pass vì `read: if true`).
- M-3: khách `get` cơ sở `HIDDEN` → fail (cùng điều kiện như M-2).

Emulator không kiểm tra index, nên quickstart có bước chạy với project thật để bắt lỗi thiếu index `status, geohash` (giống 020 T098).

## R16. Những chỗ cần sửa spec hoặc tài liệu khác

| # | Ở đâu | Sửa gì | Lý do |
|---|---|---|---|
| 1 | spec 021, US4-1 và FR-012 | "mở Sân gần bạn lần đầu" → "bấm 'Bật vị trí' ở mục Sân gần bạn lần đầu" | R9, constitution VI |
| 2 | spec 021, Assumptions | Thêm: "Vào bản đồ khi bộ lọc có quận thì bản đồ hiện toàn bộ sân của quận đó và đưa camera bao các sân; tìm theo vùng chỉ bắt đầu khi người chơi bấm 'Tìm ở khu vực này'" | R3 |
| 3 | spec 021, US1 Independent Test | "10 cơ sở ở 3 quận" → "10 cơ sở ở 4 quận" (seed thật) | R13 |
| 4 | spec 021, Edge case máy tính bảng | Ghi rõ trên `w600dp` chạm thẻ danh sách là chọn, chạm thẻ tóm tắt mới mở chi tiết | R11 |
| 5 | spec 021, Assumptions | Thêm ngưỡng vùng quá rộng 15 km (nửa đường chéo) và mức phóng mặc định 12 | R2, R7 |
| 6 | Đặc tả CSDL §4.3 | `geohash`: "độ dài 10, sinh bằng `GeoFireUtils.getGeoHashForLocation` (app) hoặc `geofire-common` (Edge Function); D ghi lại mỗi khi `location` đổi"; `location` bắt buộc hợp lệ khi `ACTIVE` | R2 |
| 7 | Đặc tả CSDL §6 | Thay dòng `venues: geohash` bằng `venues: status ASC, geohash ASC` | R2 |
| 8 | Wireframe `02_court-search_buoc1_ban-do-dang-chon-san.png` | Chữ "2026-10-05" thừa đè lên hàng chip (lỗi khi xuất ảnh) | Ghi vào `ui/README.md` |
| 9 | plan/tasks 020 | 5 điểm ở R1 | R1 |
| 10 | plan/tasks 030 | `VenueMapController` dùng `core/map` | R1, R4 |
