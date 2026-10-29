# Contract: Màn hình, điều hướng và trạng thái (spec 021)

Wireframe: [ui/](../ui/README.md) (`Map`, `MapNoLocation`; bản tối trên canvas). Quy ước giao diện theo constitution V. Chuỗi trong `values/strings_map.xml`, tiền tố `map_`; dùng lại chuỗi `search_` của 020 khi trùng nghĩa (giá, điểm, offline). Lý do: [research R6–R12](../research.md).

## 1. Vị trí trong `nav_search` (020)

021 **không** thêm destination. Hai phần của 021 là Fragment con trong `SearchFragment` của 020:

```text
SearchFragment (020, scope nav_search)
├── nearby_container   → NearbyVenuesFragment   (021) dưới thanh tìm kiếm, chỉ khi criteria trống
├── results_list       → RecyclerView           (020)
└── map_container      → SearchMapFragment      (021) hiện khi displayMode == MAP; luôn hiện trên w600dp
```

| Từ | Đến | Cách | Tham số |
|---|---|---|---|
| Thẻ tóm tắt bản đồ, thẻ "Sân gần bạn" | `nav_court_detail` (030) | Action của `nav_search` mà 020 khai báo (`action_search_to_courtDetail`) | `venueId`; `date`, `startTime`, `endTime` khi `criteria.timeWindow != null` (chỉ từ bản đồ) |
| "Tìm theo quận" | `searchFilterSheet` (020) | Như chip "Quận" của 020 | Cuộn tới mục Quận |
| "Xem tất cả trên bản đồ", "Tìm rộng hơn" | Chế độ bản đồ | `SearchViewModel.onMapIntent(AroundMe(zoom))` | zoom 14 / 12 |
| "Cài đặt" (đã từ chối quyền) | Cài đặt app | `ACTION_APPLICATION_DETAILS_SETTINGS` | package |

`SearchMapViewModel` và `NearbyViewModel` lấy bằng `hiltNavGraphViewModels(R.id.nav_search)` để còn nguyên khi mở chi tiết sân rồi quay lại (FR-009). `SearchMapFragment` lấy thêm `SearchViewModel` (cùng scope) và làm cầu nối: thu `criteria` → `mapVm.onCriteriaChanged`; nhận sự kiện `DropDistrict` → `searchVm.dropDistrictForArea()`; nhận `MapFailed` → `searchVm.onMapUnavailable()`. Hai ViewModel không gọi trực tiếp nhau.

## 2. `SearchMapFragment` (`fragment_search_map.xml`)

Hàng chip, thanh tìm kiếm và nút chuyển chế độ thuộc `SearchFragment` (020) và nằm **chồng lên** bản đồ ở chế độ bản đồ (wireframe). `SearchMapFragment` gồm:

| Thành phần | Mô tả | Ghi chú |
|---|---|---|
| `MapView` | Toàn màn hình phía dưới thanh tìm kiếm; `MapThemer` (tối = đảo màu tile); `CopyrightOverlay` góc dưới trái, trên thẻ tóm tắt | Tắt xoay; bật multi-touch |
| Thanh tiến trình | `LinearProgressIndicator` mỏng ngay dưới hàng chip khi `loading` | Không che bản đồ (FR-019) |
| Nút "Tìm ở khu vực này" | `MaterialButton` dạng viên thuốc, icon làm mới, giữa trên | Hiện khi `showSearchArea` |
| Banner vị trí | Thẻ trắng có icon vị trí gạch chéo: `map_default_area_banner` | Khi `locationBanner == DefaultArea` (wireframe 2) |
| Banner offline | `search_offline_stale` | Khi `staleFromCache` |
| Thẻ nổi trạng thái | `Empty`: tiêu đề + "Thu nhỏ bản đồ" + "Xóa bộ lọc"; `Error`: thông báo + "Thử lại"; `TooWide`: "Phóng gần hơn để tìm sân"; `capped`: dòng nhỏ "Phóng gần hơn để xem thêm sân" | Giữa màn hình, không chặn kéo bản đồ ngoài thẻ |
| Nút "Vị trí của tôi" | FAB nhỏ góc dưới phải, trên thẻ tóm tắt | Icon đầy khi có vị trí, viền khi chưa |
| Dãy thẻ tóm tắt | `ViewPager2`, `item_map_venue_card.xml`, mép trang kế lộ 24dp | Ẩn khi `selectedVenueId == null` |
| Marker | `ic_marker_shuttlecock` (thường) / bản lớn hơn (nổi bật); cụm là vòng tròn `colorPrimary` có số, viền `colorSurface` | `MarkerIcons` (`core/map`) |
| Chấm vị trí | `MyLocationNewOverlay` với `FlowLocationProvider`, không follow | Chỉ khi có quyền và có vị trí |

**Thẻ `item_map_venue_card.xml`:** ảnh trái 96dp (Coil, ảnh mặc định), tên (1 dòng, cắt cuối), "Quận · 850 m" (bỏ khoảng cách khi không có), "★ 4,6 (128)" hoặc "Chưa có đánh giá", giá theo `VenueCardFormatter` (020). Cao tối thiểu 112dp, toàn thẻ là vùng chạm. Trên `w600dp` rộng tối đa 420dp.

**Thao tác:**

| Thao tác | Kết quả | Yêu cầu |
|---|---|---|
| Chạm marker | Nổi bật, `CardOrder.build`, pager trang 0 | FR-007, US1-3 |
| Chạm cụm | Phóng vào khung bao; ở mức tối đa hoặc cùng tọa độ thì hiện dãy thẻ của thành viên | FR-003, US2-2 |
| Vuốt thẻ | Chọn cơ sở của trang, marker nổi bật, camera `animateTo` (không làm hiện nút vùng) | FR-008 |
| Chạm thẻ | Mở chi tiết sân | FR-009 |
| Chạm vùng trống | Ẩn thẻ, bỏ nổi bật | FR-010 |
| Kéo/phóng | Sau 300 ms: hiện nút "Tìm ở khu vực này" nếu vùng khác đáng kể | FR-004 |
| Bấm "Tìm ở khu vực này" | Vùng > 15 km → `TooWide`; có quận → bỏ quận + snackbar; tải `VIEWPORT` | FR-005 |
| Bấm "Vị trí của tôi" | Luồng ở research R7 | FR-011 đến FR-013 |
| "Thu nhỏ bản đồ" | `zoom - 2` (không thấp hơn 11), hiện nút vùng | Edge case |
| "Xóa bộ lọc" | `SearchViewModel.onClearFilters()` (020) | Edge case |

## 3. `NearbyVenuesFragment` (`fragment_nearby_venues.xml`)

| Trạng thái | Hiển thị |
|---|---|
| `NeedPermission` | Tiêu đề "Sân gần bạn"; dòng `map_nearby_need_permission`; nút "Bật vị trí" |
| `Denied` | Dòng `map_nearby_denied`; nút "Bật vị trí" (mở cài đặt nếu `canAskAgain == false`) và "Tìm theo quận" |
| `Locating`, `Loading` | 3 thẻ khung chờ |
| `Unavailable` | Dòng `map_location_unavailable`; "Thử lại", "Tìm theo quận" |
| `Content` | Tiêu đề + "Xem tất cả trên bản đồ"; dải thẻ ngang `item_nearby_venue.xml` (ảnh 16:9 rộng 200dp, tên, khoảng cách, "từ X đ/giờ", điểm) |
| `Empty` | `map_nearby_empty`; "Tìm rộng hơn", "Tìm theo quận" |
| `Error` | `search_error_retry` của 020 |

Dải thẻ là `RecyclerView` ngang với `ListAdapter`/`DiffUtil`. Trình đọc màn hình đọc mỗi thẻ "Tên, 850 m, từ 80.000 đồng mỗi giờ, 4,6 sao".

## 4. Máy tính bảng (`w600dp`)

- `layout-w600dp/fragment_search.xml` (020): danh sách bên trái (40%, tối thiểu 320dp), bản đồ bên phải. Ẩn nút chuyển chế độ.
- Chạm thẻ danh sách → `mapVm.onListItemSelected(venueId)`: chọn, camera di tới, hiện thẻ tóm tắt (research R11). Thẻ danh sách đang chọn có nền `colorSecondaryContainer`.
- Chạm marker → danh sách tô và cuộn tới thẻ nếu có trong các trang đã tải.

## 5. Trợ năng

- `MapAccessibilityHelper` (`ExploreByTouchHelper`) trên `MapView`: mỗi marker/cụm trong màn hình là một virtual view, vùng 48dp quanh điểm neo, `ACTION_CLICK`. Mô tả theo bảng chuỗi dưới.
- Nút "Vị trí của tôi", "Tìm ở khu vực này", nút chuyển chế độ: `contentDescription`, cao ≥ 48dp.
- Khi bản đồ tải xong: `announceForAccessibility("Đã tìm thấy N sân trong khu vực")`.

## 6. Chuỗi (`strings_map.xml`)

| Khóa | Nội dung |
|---|---|
| `map_toggle_map` / `map_toggle_list` | Bản đồ / Danh sách |
| `map_search_this_area` | Tìm ở khu vực này |
| `map_searched_by_area` | Đã tìm theo khu vực trên bản đồ |
| `map_too_wide` | Phóng gần hơn để tìm sân |
| `map_capped` | Phóng gần hơn để xem thêm sân |
| `map_empty_title` | Không có sân phù hợp trong khu vực này |
| `map_zoom_out` | Thu nhỏ bản đồ |
| `map_unavailable` | Không tải được bản đồ, đã chuyển về danh sách |
| `map_my_location` | Vị trí của tôi |
| `map_location_rationale` | Cho phép vị trí để đưa bản đồ về chỗ bạn và hiện khoảng cách tới sân. Vị trí chỉ dùng trên máy, không lưu lại. |
| `map_default_area_banner` | Đang hiện khu vực trung tâm TP.HCM. Bật vị trí để xem khoảng cách. |
| `map_location_denied` | Chưa có quyền vị trí. Bạn có thể bật trong Cài đặt. |
| `map_location_unavailable` | Không lấy được vị trí. Hãy bật định vị rồi thử lại. |
| `map_settings` | Cài đặt |
| `map_marker_cd` | %1$s, %2$s, %3$s (tên, khoảng cách, điểm; bỏ phần trống) |
| `map_marker_selected_cd` | %1$s, đang chọn |
| `map_cluster_cd` | Cụm %d sân, chạm hai lần để phóng to |
| `map_rating_cd` | %1$s sao |
| `map_results_announce` | Đã tìm thấy %d sân trong khu vực |
| `map_nearby_title` | Sân gần bạn |
| `map_nearby_see_all` | Xem tất cả trên bản đồ |
| `map_nearby_need_permission` | Bật vị trí để xem các sân gần bạn. Vị trí chỉ dùng trên máy. |
| `map_nearby_denied` | Chưa có quyền vị trí nên chưa xem được sân gần bạn. |
| `map_nearby_enable` | Bật vị trí |
| `map_nearby_by_district` | Tìm theo quận |
| `map_nearby_empty` | Chưa có sân trong bán kính 5 km quanh bạn |
| `map_nearby_wider` | Tìm rộng hơn |
