# Contract: Màn hình, điều hướng và trạng thái

Wireframe: [ui/](../ui/README.md) (4 bước, có bản tối trên canvas). Quy ước giao diện theo constitution V. Chuỗi trong `values/strings_search.xml`, tiền tố `search_`.

## 1. Nav graph `nav_search` (lồng trong `nav_player`, tab "Trang chủ")

```text
nav_search (start = searchFragment)
├── searchFragment            SearchFragment            bước 1 + bước 2
├── searchInputFragment       SearchInputFragment       bước 4 (ô gõ, lịch sử, gợi ý quận)
├── searchFilterSheet         SearchFilterBottomSheet   bước 3
└── action → nav_court_detail (spec 030) args: venueId: String,
                                         date: String? (yyyyMMdd), startTime: String? (HHmm), endTime: String? (HHmm)
```

`SearchViewModel` scope theo `nav_search` (`hiltNavGraphViewModels(R.id.nav_search)`) nên ba màn hình dùng chung một `criteria` và danh sách (FR-017). Khách chưa đăng nhập vào được `nav_search` (FR-026) nhờ chế độ khách của spec 010 ([research R15 của 010](../../010-account-auth/research.md#r15-chế-độ-khách-và-quay-lại-đúng-chỗ-sau-khi-đăng-nhập-fr-025-fr-026)). Lời mời đăng nhập (lịch sử, ô AI của 022) gọi `AuthNavigator.openLogin(navController, returnTo)`: `nav_auth` mở chồng lên, đăng nhập xong `popBackStack` nên `SearchViewModel` của `nav_search` còn nguyên, giữ từ khóa, bộ lọc và vị trí cuộn (Edge case "Khách… bấm đặt sân" của spec 020).

## 2. Màn hình

| Màn hình | Nội dung | Ghi chú |
|---|---|---|
| `SearchFragment` khi `criteria` trống (bước 1, "Trang chủ") | Ô tìm kiếm (chỉ đọc, chạm → `searchInputFragment`); vùng chừa cho **ô AI** (022) và **"Sân gần bạn"** (021); tiêu đề "Tất cả sân · N"; nút chuyển Danh sách/Bản đồ (021); hàng chip nhanh "Bộ lọc", "Quận", "Giờ trống", "Giá" (mỗi chip mở `searchFilterSheet` cuộn tới mục tương ứng); nút sắp xếp; danh sách | Hai vùng 021/022 là `FragmentContainerView` trống cho tới khi spec đó làm |
| `SearchFragment` khi có điều kiện (bước 2) | Nút quay lại (xóa toàn bộ điều kiện, về bước 1); ô tìm kiếm hiện từ khóa; chip bộ lọc đang áp dụng có "x" (FR-009); chip "Sửa" mở bảng lọc; "N sân phù hợp"; sắp xếp; danh sách | Ẩn vùng AI và "Sân gần bạn" |
| `SearchInputFragment` (bước 4) | Ô nhập có focus và bàn phím; khi ô trống: "Tìm gần đây" + "Xóa tất cả" + danh sách 10 mục + ghi chú thông tin; khi đang gõ: gợi ý quận khớp tên (từ danh mục, lọc trên máy) | Khách: thay lịch sử bằng "Đăng nhập để lưu lịch sử tìm kiếm" (US4-8). Bấm tìm trên bàn phím → cập nhật `criteria`, lưu lịch sử, quay về `searchFragment` |
| `SearchFilterBottomSheet` (bước 3) | Danh mục quận/tiện ích đang tải: hai mục này hiện khung chờ, các mục khác vẫn dùng được; tải lỗi: dòng "Không tải được danh mục" + "Thử lại" ngay tại mục đó, không chặn giá/khung giờ. Quận (dropdown danh mục); Giá mỗi giờ (`RangeSlider` 0–200.000, bước 10.000, đầu phải = "200.000 đ+"); Ngày chơi (31 ô từ hôm nay); Từ/Đến (bước 30 phút, FR-007); Tiện ích (chip chọn nhiều); "Xóa bộ lọc"; nút "Xem N sân" hoặc "Xem kết quả" | Sửa trong bản nháp, chỉ áp dụng khi bấm nút chính. Số N lấy từ `VenueSearchEngine.count` khi có (research R3) |
| Hộp chọn sắp xếp | 3 lựa chọn, mặc định "Đánh giá cao nhất" | Chọn "Gần nhất" chạy luồng quyền vị trí (research R9) |
| Hộp giải thích quyền vị trí | Một câu lý do + "Tiếp tục" / "Để sau" | Hiện trước hộp xin quyền của hệ thống (FR-012) |

**Thẻ kết quả `item_search_venue.xml`:** ảnh 16:9 (Coil, ảnh mặc định khi không có), tên, quận, khoảng cách (khi có), "N sân trống khung này" (khi có khung giờ), giá theo `DisplayPrice`, điểm "★ 4,6 (128)" hoặc "Chưa có đánh giá". Nút trái tim trên wireframe thuộc spec 070 (C); thẻ chừa chỗ, C gắn vào sau.

Thứ tự đọc của trình đọc màn hình: tên → quận → khoảng cách → giá → điểm. Mỗi chip có `contentDescription` dạng "Bỏ bộ lọc Quận 10". Vùng chạm ≥ 48dp. `w600dp`: danh sách dùng `GridLayoutManager` 2 cột.

## 3. ViewModel

| ViewModel | Trạng thái | Hàm |
|---|---|---|
| `SearchViewModel` (nav graph) | `StateFlow<SearchUiState>` ([data-model §3.3](../data-model.md#33-trạng-thái-màn-hình)) | `onKeywordChanged`, `onSubmitKeyword`, `onDistrictPicked`, `onApplyFilters(draft)`, `onRemoveChip`, `onClearFilters`, `onSortSelected`, `onLocationPermissionResult`, `onLoadMore`, `onRetry`, `onVenueClicked`, `applyExternal` |
| `SearchHistoryViewModel` | `StateFlow<HistoryUiState>` (Loading, Guest, Empty, Content(items), Error) | `onHistoryClicked` (chạy `SearchCriteriaSanitizer`, phát sự kiện một lần `RestoreCriteria(sanitizeResult)`; `SearchInputFragment` nhận và gọi `SearchViewModel.applyExternal(criteria, source = HISTORY)` vì hai ViewModel khác scope, không gọi trực tiếp nhau), `onDelete(id)`, `onDeleteAll()` (sau xác nhận) |
| `SearchFilterViewModel` (sheet) | `StateFlow<FilterDraftUiState>` | Bản nháp; `previewCount()` debounce 400 ms |

Sự kiện một lần đi qua `Channel` (constitution I), Fragment thu bằng `repeatOnLifecycle(STARTED)`.

## 4. Chuỗi chính (`strings_search.xml`)

| Khóa | Nội dung |
|---|---|
| `search_hint` | Tìm sân theo tên hoặc quận |
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
