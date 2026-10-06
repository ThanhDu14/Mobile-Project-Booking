# Contract: Màn hình và điểm nối với tính năng khác

Spec 070 cung cấp các màn hình và một interface domain để các nhóm khác gọi vào, không import `ui/` hay `data/` của nhau (constitution nguyên tắc I).

## 1. Màn hình (Fragment) của spec 070

| Fragment | Layout | Story | Tham số Safe Args | Vào từ |
|---|---|---|---|---|
| `WriteReviewFragment` | `fragment_review_write.xml` | US1, US2, US5 | `bookingId: String`, `mode: CREATE \| EDIT` | Chi tiết đơn (spec 060), "Đánh giá của tôi" |
| `VenueReviewsFragment` | `fragment_review_list.xml` (+ `item_review.xml`) | US6 | `venueId: String`, `venueName: String` | "Xem tất cả" ở chi tiết sân (spec 030) |
| `OwnerReviewsFragment` | `fragment_review_owner_list.xml` | US4 | — (lấy cơ sở của chủ sân hiện tại) | Menu chế độ quản lý sân (spec 111/112) |
| `ReplyReviewBottomSheet` | `bottom_sheet_review_reply.xml` | US4 | `reviewId: String` | `OwnerReviewsFragment` |
| `FavoriteVenuesFragment` | `fragment_favorite_list.xml` (+ `item_favorite_venue.xml`) | US3 | — | Trang "Tài khoản" (spec 011) |
| `PhotoViewerFragment` | `fragment_review_photo_viewer.xml` | US2 | `photoUrls: Array<String>`, `startIndex: Int` | `item_review` |

Các màn hình nằm trong nav graph lồng `nav_review.xml`, được `include` vào graph chính. Mọi màn hình có đủ 4 trạng thái: loading, content, empty, error + "Thử lại" (FR-017).

## 2. Điểm nối cho nhóm khác

| Nhóm | Họ làm | Spec 070 cung cấp |
|---|---|---|
| A – spec 030 (chi tiết sân) | Đặt nút trái tim trên app bar; đặt phần tổng quan + nút "Xem tất cả" | `FavoriteRepository.observeIsFavorite(venueId): Flow<Boolean>`, `setFavorite(venue, favorite)`; action → `VenueReviewsFragment(venueId, venueName)`; `ReviewRepository.observeSummary(venueId): Flow<VenueRatingSummary>` cho phần tổng quan |
| A – spec 011 (tài khoản) | Thêm mục "Sân yêu thích" vào trang Tài khoản | action → `FavoriteVenuesFragment` |
| B – spec 060 (lịch đặt của tôi) | Hiện nút "Đánh giá" / "Xem đánh giá của bạn" trên chi tiết đơn `COMPLETED` | `ReviewRepository.observeMyReview(bookingId): Flow<Review?>` và `ReviewEligibility.of(booking, now)` → `CAN_WRITE \| CAN_VIEW \| EXPIRED \| NOT_ELIGIBLE`; action → `WriteReviewFragment(bookingId, mode)` |
| D – spec 111/112 (chủ sân) | Thêm mục "Đánh giá của khách" vào menu quản lý sân | action → `OwnerReviewsFragment` |
| D – spec 120 (admin) | Ẩn/hiện đánh giá | `applyReviewVisibility()` trong `_shared/reviews.ts` ([edge-function-review.md](edge-function-review.md)) |
| D – spec 100 (thông báo) | Gửi `REVIEW_NEW`, `REVIEW_REPLY`, `REVIEW_REMINDER`; deep link mở màn hình | Deep link: `smashnow://review/write/{bookingId}`, `smashnow://review/venue/{venueId}` |

Interface đặt trong `feature/review/domain/` để nhóm khác inject qua Hilt:

```kotlin
interface FavoriteRepository {
    fun observeIsFavorite(venueId: String): Flow<Boolean>
    fun observeFavorites(): Flow<List<FavoriteVenue>>        // phân trang trong impl
    suspend fun setFavorite(venue: VenueSummary, favorite: Boolean): Result<Unit>
}

interface ReviewRepository {
    fun observeSummary(venueId: String): Flow<VenueRatingSummary>
    fun observeMyReview(bookingId: String): Flow<Review?>
    // các hàm còn lại chỉ dùng trong feature/review
}
```

`VenueSummary` (`venueId`, `name`, `photoPath`, `address`) đặt ở `core/model/` vì nhiều nhóm dùng.

## 3. Chuỗi hiển thị chính (strings.xml, tiền tố `review_` / `favorite_`)

| Key | Nội dung |
|---|---|
| `review_criterion_court` / `_cleanliness` / `_service` | Chất lượng sân / Vệ sinh / Thái độ phục vụ |
| `review_error_missing_criteria` | Vui lòng chấm đủ 3 tiêu chí |
| `review_photo_limit` | Tối đa 3 ảnh |
| `review_edited_label` | Đã chỉnh sửa |
| `review_hidden_label` | Đánh giá đã bị ẩn |
| `review_deleted_user` | Người dùng đã xóa |
| `review_owner_reply_label` | Phản hồi của chủ sân |
| `review_window_closed` | Đã hết hạn đánh giá |
| `review_discard_title` | Bỏ đánh giá đang viết? |
| `review_empty_venue` | Sân chưa có đánh giá |
| `favorite_added` | Đã thêm vào yêu thích |
| `favorite_venue_suspended` | Sân tạm ngừng hoạt động |
| `favorite_venue_gone` | Sân không còn tồn tại |
| `favorite_empty` / `favorite_empty_action` | Bạn chưa lưu sân nào / Tìm sân |
