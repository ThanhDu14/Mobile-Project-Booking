# Contract: Màn hình, điều hướng và trạng thái

Wireframe: [ui/](../ui/README.md) (thiết kế 2026-11-06: Trang chủ + Tìm kiếm một màn hình; ảnh 4 bước cũ đã lỗi thời). Quy ước giao diện theo constitution V. Chuỗi trong `values/strings_search.xml`, tiền tố `search_`.

> **Cập nhật 2026-11-06 (thay đổi thiết kế điều hướng, [spec Clarifications 2026-11-06](../spec.md#session-2026-11-06-thay-đổi-thiết-kế-điều-hướng)):** Trang chủ và Tìm kiếm là 2 tab riêng; luồng 4 bước cũ (`SearchFragment` bước 1/2, `SearchInputFragment`) được thay bằng `HomeFragment` + `SearchFragment` một màn hình. Bảng lọc đầy đủ giữ nguyên.

## 1. Nav graph

Thanh dưới của người chơi (`menu_player_bottom_nav.xml`, FR-028): `tab_home` · `tab_search` · `tab_bookings` · `tab_drop_in` · `tab_community`. Không còn `tab_account` (spec 011: avatar góc phải trên mở `accountFragment`). Mỗi tab là một graph lồng trong `nav_player` và có back stack riêng (Navigation multiple back stacks), nên chuyển tab không làm mất trạng thái (FR-033).

```text
nav_home (tab "Trang chủ", start = homeFragment)
├── homeFragment              HomeFragment              danh sách "Tất cả sân · N" + sắp xếp
└── action → nav_court_detail (spec 030) args: venueId: String   (không có khung giờ)

nav_search (tab "Tìm kiếm", start = searchFragment)
├── searchFragment            SearchFragment            ô tìm + chip lọc nhanh + "Tìm gần đây" / kết quả
├── searchFilterSheet         SearchFilterBottomSheet   bảng lọc đầy đủ (mở từ chip "+" hoặc cuộn tới mục của chip "Quận"/"Giờ trống"/"Giá")
└── action → nav_court_detail (spec 030) args: venueId: String,
                                         date: String? (yyyyMMdd), startTime: String? (HHmm), endTime: String? (HHmm)
```

- `HomeViewModel` scope theo `nav_home` (`hiltNavGraphViewModels(R.id.nav_home)`): chỉ giữ cách sắp xếp, trang đã tải và vị trí cuộn; tìm với `criteria` rỗng qua cùng `VenueSearchEngine` với tab Tìm kiếm.
- `SearchViewModel` scope theo `nav_search` (`hiltNavGraphViewModels(R.id.nav_search)`): `searchFragment` và `searchFilterSheet` dùng chung một `criteria` và danh sách (FR-017, FR-033). Không chia sẻ với `HomeViewModel`.
- Khách chưa đăng nhập vào được cả `nav_home` và `nav_search` (FR-026, FR-028) nhờ chế độ khách của spec 010 ([research R15 của 010](../../010-account-auth/research.md#r15-chế-độ-khách-và-quay-lại-đúng-chỗ-sau-khi-đăng-nhập-fr-025-fr-026)). Lời mời đăng nhập (lịch sử, ô AI của 022) gọi `AuthNavigator.openLogin(navController, returnTo)`: `nav_auth` mở chồng lên, đăng nhập xong `popBackStack` nên `SearchViewModel` còn nguyên từ khóa, bộ lọc và vị trí cuộn.
- Bấm lại tab đang mở: cuộn danh sách về đầu, không xóa điều kiện (Edge case "Bấm lại tab đang mở").

## 2. Màn hình

| Màn hình | Nội dung | Ghi chú |
|---|---|---|
| `HomeFragment` (tab "Trang chủ", US5, FR-029) | Header dùng chung (logo + avatar góc phải, spec 011); tiêu đề "Tất cả sân · N"; nút sắp xếp; danh sách thẻ `item_search_venue.xml`, tải thêm khi cuộn | **Không** có ô tìm, chip lọc, ô AI, "Sân gần bạn". Rỗng: "Chưa có sân nào đang hoạt động" (không có "Xóa bộ lọc"); lỗi kèm "Thử lại"; offline có dữ liệu cũ thì banner `search_offline_stale` |
| `SearchFragment` khi chưa có điều kiện (FR-030, FR-031) | Ô tìm theo tên (focus + bàn phím khi vào tab lần đầu hoặc khi chưa có điều kiện); hàng chip lọc nhanh "Quận", "Giờ trống", "Giá", "+"; vùng chừa ô AI (022, FR-032); "Tìm gần đây" + "Xóa tất cả" + danh sách 10 mục + ghi chú `search_history_note` | Khách: thay lịch sử bằng "Đăng nhập để lưu lịch sử tìm kiếm" (US4-8). Vùng AI là `FragmentContainerView` trống cho tới khi 022 làm |
| `SearchFragment` khi đang gõ | Như trên, phía trên kết quả có hàng gợi ý quận/huyện khớp từ khóa (lọc trên máy từ danh mục) | Chọn gợi ý → áp dụng chip quận, lưu lịch sử (FR-021) |
| `SearchFragment` khi có từ khóa hoặc bộ lọc (FR-030, FR-031) | Ô tìm hiện từ khóa + nút xóa; chip lọc nhanh; chip bộ lọc đang áp dụng có "x" (FR-009); "N sân phù hợp" (hoặc "Kết quả phù hợp"); nút sắp xếp; vùng chừa nút Danh sách/Bản đồ (021, FR-032); danh sách kết quả | Kết quả cập nhật tại chỗ, không chuyển màn hình (FR-018). Xóa hết từ khóa và bộ lọc → quay về trạng thái "Tìm gần đây". Quay lại từ chi tiết sân không tự mở bàn phím |
| `SearchFilterBottomSheet` (bảng lọc đầy đủ) | Danh mục quận/tiện ích đang tải: hai mục này hiện khung chờ, các mục khác vẫn dùng được; tải lỗi: dòng "Không tải được danh mục" + "Thử lại" ngay tại mục đó, không chặn giá/khung giờ. Quận (dropdown danh mục); Giá mỗi giờ (`RangeSlider` 0–200.000, bước 10.000, đầu phải = "200.000 đ+"); Ngày chơi (31 ô từ hôm nay); Từ/Đến (bước 30 phút, FR-007); Tiện ích (chip chọn nhiều); "Xóa bộ lọc"; nút "Xem N sân" hoặc "Xem kết quả" | Mở từ chip "+" (đầu bảng) hoặc chip "Quận"/"Giờ trống"/"Giá" (cuộn tới mục tương ứng). Sửa trong bản nháp, chỉ áp dụng khi bấm nút chính. Số N lấy từ `VenueSearchEngine.count` khi có (research R3) |
| Hộp chọn sắp xếp | 3 lựa chọn, mặc định "Đánh giá cao nhất" | Dùng ở cả Trang chủ và tab Tìm kiếm, mỗi tab giữ lựa chọn riêng. Chọn "Gần nhất" chạy luồng quyền vị trí (research R9) |
| Hộp giải thích quyền vị trí | Một câu lý do + "Tiếp tục" / "Để sau" | Hiện trước hộp xin quyền của hệ thống (FR-012) |

**Thẻ kết quả `item_search_venue.xml`:** ảnh 16:9 (Coil, ảnh mặc định khi không có), tên, quận, khoảng cách (khi có), "N sân trống khung này" (khi có khung giờ), giá theo `DisplayPrice`, điểm "★ 4,6 (128)" hoặc "Chưa có đánh giá". Nút trái tim trên wireframe thuộc spec 070 (C); thẻ chừa chỗ, C gắn vào sau.

Thẻ dùng chung cho Trang chủ và kết quả tìm kiếm. Thứ tự đọc của trình đọc màn hình: tên → quận → khoảng cách → giá → điểm. Mỗi chip có `contentDescription` dạng "Bỏ bộ lọc Quận 10". Vùng chạm ≥ 48dp. `w600dp`: danh sách dùng `GridLayoutManager` 2 cột.

## 3. ViewModel

| ViewModel | Trạng thái | Hàm |
|---|---|---|
| `HomeViewModel` (`nav_home`) | `StateFlow<HomeUiState>` (Loading, Content(items, total, sort, isStale), Empty, Error) | `onSortSelected`, `onLocationPermissionResult`, `onLoadMore`, `onRetry`, `onVenueClicked`, `onTabReselected` |
| `SearchViewModel` (`nav_search`) | `StateFlow<SearchUiState>` ([data-model §3.3](../data-model.md#33-trạng-thái-màn-hình)) | `onKeywordChanged`, `onSubmitKeyword`, `onDistrictPicked`, `onApplyFilters(draft)`, `onRemoveChip`, `onClearFilters`, `onSortSelected`, `onLocationPermissionResult`, `onLoadMore`, `onRetry`, `onVenueClicked`, `applyExternal` |
| `SearchHistoryViewModel` | `StateFlow<HistoryUiState>` (Loading, Guest, Empty, Content(items), Error) | `onHistoryClicked` (chạy `SearchCriteriaSanitizer`, phát sự kiện một lần `RestoreCriteria(sanitizeResult)`; `SearchFragment` nhận và gọi `SearchViewModel.applyExternal(criteria, source = HISTORY)` vì hai ViewModel khác scope, không gọi trực tiếp nhau), `onDelete(id)`, `onDeleteAll()` (sau xác nhận) |
| `SearchFilterViewModel` (sheet) | `StateFlow<FilterDraftUiState>` | Bản nháp; `previewCount()` debounce 400 ms |

Sự kiện một lần đi qua `Channel` (constitution I), Fragment thu bằng `repeatOnLifecycle(STARTED)`.

## 4. Chuỗi chính (`strings_search.xml`)

| Khóa | Nội dung |
|---|---|
| `search_hint` | Tìm sân theo tên hoặc quận |
| `home_all_venues_title` | Tất cả sân · %d |
| `home_empty` | Chưa có sân nào đang hoạt động |
| `search_quick_district` / `search_quick_time` / `search_quick_price` | Quận / Giờ trống / Giá |
| `search_quick_more` | Thêm bộ lọc (contentDescription của chip "+") |
| `search_result_count` | %d sân phù hợp |
| `search_result_unknown` | Kết quả phù hợp |
| `nav_tab_search` | Tìm kiếm |
| `search_empty_title` | Không tìm thấy sân phù hợp |
| `search_empty_hint` | Thử bỏ bớt bộ lọc hoặc đổi khung giờ |
| `search_clear_filters` | Xóa bộ lọc |
| `search_error_retry` | Thử lại |
| `search_offline_stale` | Không có kết nối, kết quả có thể chưa cập nhật |
| `search_price_from` | từ %1$s đ/giờ |
| `search_price_window` | %1$s đ/giờ · %2$s |
| `search_price_contact` | Liên hệ |
| `search_no_rating` | Chưa có đánh giá |
| `search_free_courts` | %d sân trống khung này |
| `search_location_rationale` | Cho phép vị trí để xếp sân theo khoảng cách. Vị trí chỉ dùng trên máy, không lưu lại. |
| `search_location_denied` | Cần quyền vị trí để xếp theo khoảng cách |
| `search_location_unavailable` | Không lấy được vị trí |
| `search_history_title` | Tìm gần đây |
| `search_history_guest` | Đăng nhập để lưu lịch sử tìm kiếm |
| `search_history_note` | Lịch sử giữ 10 lần tìm gần nhất của bạn. Mục có ngày đã qua sẽ bỏ bộ lọc khung giờ khi mở lại. |
| `search_history_past_date` | Ngày đã qua, vui lòng chọn lại |
| `search_capped` | Có nhiều sân, hãy chọn quận để kết quả đầy đủ hơn |
| `search_catalog_error` | Không tải được danh mục |
