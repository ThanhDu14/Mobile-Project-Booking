# Contract: Màn hình, trạng thái và ViewModel

Wireframe: [ui/](../ui/README.md) (`03_court-detail_buoc1_chi-tiet-san.png` và bản chế độ tối). Quy ước giao diện theo constitution V. Chuỗi đặt trong `values/strings_court_detail.xml`, tiền tố `court_detail_`.

## 1. Màn hình

| Màn hình | Layout | Nội dung |
|---|---|---|
| `CourtDetailFragment` | `fragment_court_detail.xml`; `layout-w600dp/fragment_court_detail.xml` (hai cột) | Toàn bộ mục ở §2; thanh dưới cố định |
| `CourtDetailGoneView` (trạng thái của cùng Fragment) | `view_court_detail_gone.xml` | "Sân không còn tồn tại", "Quay lại", "Bỏ khỏi yêu thích" (khi đang yêu thích) |
| `CourtDetailSuspendedGuestView` | `view_court_detail_suspended_guest.xml` | "Sân tạm ngừng hoạt động", "Quay lại", "Đăng nhập" |
| `PhotoViewerFragment` (`core/ui/photo`) | `fragment_photo_viewer.xml` | `ViewPager2` + `ZoomableImageView`, chỉ báo "2/5", nút đóng |

## 2. Các mục theo wireframe (từ trên xuống)

| # | Mục | Hiển thị | Trạng thái riêng | FR |
|---|---|---|---|---|
| 1 | Thư viện ảnh | `ViewPager2` 16:9, chỉ báo "1/5", nút quay lại và trái tim chồng lên ảnh | Không ảnh → ảnh mặc định; ảnh lỗi → ảnh mặc định ở ô đó | FR-004, FR-017 |
| 2 | Thông tin chính | Tên, địa chỉ · quận, "★ 4,6 (128 đánh giá)" hoặc "Chưa có đánh giá", chip "Đang mở cửa · 05:00–23:00" / "Đã đóng cửa · Mở lại 05:00" / "Mở cả ngày" | Ghi chú nổi bật "Sân tạm ngừng hoạt động" khi `SUSPENDED` | FR-003, FR-007, FR-021 |
| 3 | Ba thẻ nhanh | (a) "4 sân / 3 Thảm, 1 Gỗ"; (b) "1,2 km / Từ vị trí của bạn" hoặc "Chỉ đường"; (c) số điện thoại "Bấm để gọi" | (a) 0 sân → "Tạm thời chưa có sân hoạt động"; (c) ẩn khi không có số; `DisplayOnly` → bỏ "Bấm để gọi" | FR-006, FR-011, FR-012 |
| 4 | Tiện ích | Chip có biểu tượng `ic_amenity_{iconKey}` và tên | Rỗng → "Chưa có thông tin tiện ích"; lỗi danh mục → "Thử lại" | FR-005 |
| 5 | Bảng giá | Thẻ "Khung bạn chọn · T3, 06/10 · 19:00 – 21:00 · 120.000 đ/giờ" (chỉ khi có `SearchWindow`); các nhóm ngày; dòng có khung giờ, nhãn, giá; dòng áp dụng được tô nền | Rỗng → "Liên hệ" + số điện thoại (hoặc "Liên hệ chủ sân" + chat); khung không phủ hết → "Khung bạn chọn: Liên hệ" | FR-008, FR-009 |
| 6 | Vị trí | Bản đồ osmdroid 180dp không tương tác, marker `ic_marker_shuttlecock`; địa chỉ đầy đủ; nút "Chỉ đường" | Đang tải → khung chờ; lỗi tile → ẩn bản đồ, giữ địa chỉ và nút; không có tọa độ → ẩn cả bản đồ và nút | FR-010, FR-011, FR-013 |
| 7 | Đánh giá | "4,6", sao, "128 đánh giá"; ba thanh tiêu chí "Chất lượng sân", "Vệ sinh", "Phục vụ"; tối đa 3 thẻ (`item_court_detail_review.xml`: avatar hoặc chữ cái đầu, tên hoặc "Người dùng đã xóa", ngày, số sao, bình luận rút gọn 3 dòng, ảnh thu nhỏ, khối "Phản hồi của chủ sân"); "Xem tất cả (N)" | Rỗng → "Sân chưa có đánh giá", ẩn "Xem tất cả"; lỗi → "Thử lại" | FR-014, FR-015 |
| 8 | Thanh dưới (cố định) | Giá "120.000 đ/giờ" + khung đã chọn (hoặc "từ X đ/giờ" khi không có khung, "Liên hệ" khi không có giá); nút chat (biểu tượng); nút "Đặt sân" | `SUSPENDED` → ẩn chat và "Đặt sân", hiện "Sân tạm ngừng hoạt động"; chủ cơ sở → vẫn hiện, bấm thì báo "Đây là cơ sở của bạn" | FR-016, FR-017, FR-018, FR-021 |

Banner "Không có kết nối, thông tin có thể chưa cập nhật" nằm dưới ảnh khi `isOffline` (FR-026).

**Thứ tự tải:** mục 1, 2, 3 và giá ở thanh dưới hiện trước; mục 7 tải sau khi phần đầu đã có; mục 6 khởi tạo khi sắp cuộn tới (FR-024, research R2, R7).

**Accessibility:** thứ tự đọc: tên → địa chỉ → điểm → trạng thái mở cửa → giá. `contentDescription` cho:
- ảnh ("Ảnh 2 trên 5 của {tên}");
- trái tim ("Thêm vào yêu thích" / "Bỏ khỏi yêu thích");
- nút chat ("Nhắn tin cho chủ sân");
- biểu tượng tiện ích;
- bản đồ ("Bản đồ vị trí {tên}").

Vùng chạm ≥ 48dp. Ở `w600dp`: cột trái 40% (ảnh, bản đồ), cột phải 60% (thông tin, bảng giá, đánh giá), form tối đa theo `dimens` của từng cột.

## 3. Hộp thoại

| Hộp thoại | Khi nào | Nút |
|---|---|---|
| Mời đăng nhập | Khách bấm "Đặt sân" / trái tim / chat | "Đăng nhập" (ghi `pendingAction`, mở `nav_auth`), "Để sau" |
| Xác nhận gọi | Không dùng (ACTION_DIAL không tự gọi) | — |

## 4. ViewModel

`CourtDetailViewModel` (`@HiltViewModel`, scope theo destination), `StateFlow<CourtDetailUiState>` ([data-model §2.5](../data-model.md#25-trạng-thái-màn-hình)), sự kiện qua `Channel<CourtDetailEffect>`.

| Hàm | Việc |
|---|---|
| `onScreenVisible()` | Gọi từ `onResume`; chạy `PendingActionResolver` (R11) |
| `onRetry(section)` | Đọc lại một mục |
| `onBookClicked()` | Khách → `ShowLoginPrompt(BOOK)`; chủ cơ sở → snackbar; còn lại → `CheckVenueBookableUseCase` rồi `OpenBookingGrid(venueId, window?)` |
| `onFavoriteClicked()` | Khách → `ShowLoginPrompt(FAVORITE)`; `SUSPENDED` và chưa yêu thích → không làm gì (nút đã tắt); còn lại → `FavoriteGateway.setFavorite` (gọi `FavoriteRepository` của 070; lạc quan, lỗi thì trả lại) |
| `onChatClicked()` | Khách → `ShowLoginPrompt(CHAT)`; chủ cơ sở → snackbar; còn lại → `OpenChat(venueId)` |
| `onLoginChosen(action)` | Ghi `pendingAction`, phát `OpenLogin` |
| `onPhotoClicked(index)`, `onPhotoPageChanged(index)` | `OpenPhotoViewer`; lưu `photoIndex` vào `SavedStateHandle` |
| `onPhoneClicked()`, `onPhoneLongPressed()` | `Dial` hoặc `CopyPhone` (R9) |
| `onDirectionsClicked()` | `OpenDirections` |
| `onSeeAllReviewsClicked()` | `OpenReviews(venueId, name)` |
| `onRemoveFavoriteFromGone()` | `setFavorite(venue, false)` ở màn hình "Sân không còn tồn tại" |

Use case (`feature/courtdetail/domain/usecase`):
- `ObserveVenueDetailUseCase`: listener venue + danh mục + `PhotoUrlResolver`;
- `LoadPriceTableUseCase`: `priceRules` + `PriceTableBuilder` + `SlotPricing`;
- `LoadCourtSummaryUseCase`;
- `LoadLatestReviewsUseCase`;
- `CheckVenueBookableUseCase`.

Repository: `VenueDetailRepository` (interface trong `domain`, bản cài đặt `FirestoreVenueDetailRepository` trong `data`, dùng `awaitWithTimeout`).
