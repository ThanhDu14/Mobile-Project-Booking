# feature/review — Đánh giá sân và sân yêu thích (spec 070, C)

Tài liệu thiết kế: [specs/070-review-favorite](../../../../../../../../../specs/070-review-favorite/). Đã có đủ 6 user story: viết đánh giá (US1), ảnh (US2), sân yêu thích (US3), chủ sân phản hồi (US4), sửa/xóa (US5), xem tất cả đánh giá của sân (US6).

## Gắn nút trái tim vào màn hình của nhóm khác

Dùng cho chi tiết sân (spec 030, A). Không import `data/` của spec 070, chỉ dùng view và ViewModel trong `ui/favorite/`.

1. Layout: thêm nút vào app bar.

   ```xml
   <com.example.booking_app.feature.review.ui.favorite.FavoriteToggleButton
       android:id="@+id/btnFavorite"
       android:layout_width="wrap_content"
       android:layout_height="wrap_content" />
   ```

   Nút tự lo icon, màu, `contentDescription` ("Thêm/Bỏ yêu thích") và vùng chạm 48dp.

2. Fragment: lấy `FavoriteToggleViewModel`, gọi `bind()` khi đã có thông tin sân, rồi nối nút và sự kiện.

   ```kotlin
   private val favoriteViewModel: FavoriteToggleViewModel by viewModels()

   // khi đã tải xong cơ sở
   favoriteViewModel.bind(VenueSummary(venueId, name, photoPath, address))

   binding.btnFavorite.setOnClickListener { favoriteViewModel.onToggle() }
   launch { favoriteViewModel.isFavorite.collect { binding.btnFavorite.isFavorite = it } }
   launch {
       favoriteViewModel.events.collect { event ->
           when (event) {
               FavoriteToggleEvent.Added -> R.string.favorite_added
               FavoriteToggleEvent.Removed -> R.string.favorite_removed
               FavoriteToggleEvent.SyncFailed -> R.string.favorite_sync_failed
               FavoriteToggleEvent.LoginRequired -> R.string.favorite_login_required
           }.let { Snackbar.make(binding.root, it, Snackbar.LENGTH_SHORT).show() }
       }
   }
   ```

   Trái tim đổi ngay khi bấm, kể cả offline (Firestore tự gửi khi có mạng). Nếu ghi bị từ chối thì trái tim trở lại trạng thái cũ và có sự kiện `SyncFailed` (FR-021).

Ví dụ đang chạy: header của `BookingSlotGridFragment` (spec 040), gắn tạm để demo cho đến khi có chi tiết sân.

Cần dữ liệu thô thay vì view: inject `FavoriteRepository` (`observeIsFavorite`, `observeFavorites`, `setFavorite`).

## Mở danh sách "Sân yêu thích"

Dùng cho trang Tài khoản (spec 011, A). Màn hình nằm trong nav graph lồng `nav_review` (đã `include` trong `nav_player`), start destination là `FavoriteVenuesFragment`, không có tham số:

```xml
<action
    android:id="@+id/action_account_to_favoriteVenues"
    app:destination="@id/nav_review" />
```

Nút tạm "Sân yêu thích (Feature 070)" ở trang chủ placeholder dùng cách này; bỏ nút khi trang Tài khoản có mục chính thức.

## Nút "Đánh giá" trên chi tiết đơn

Dùng cho chi tiết đơn trong "Lịch đặt của tôi" (spec 060, B). Inject `ReviewRepository` (interface trong `domain/`), không import `data/`.

1. Quyết định nút theo `ReviewEligibility`:

   ```kotlin
   @Inject lateinit var reviewRepository: ReviewRepository   // hoặc inject vào ViewModel

   reviewRepository.observeMyReview(bookingId)               // Flow<Review?>, null = chưa đánh giá
       .map { myReview ->
           ReviewEligibility.of(
               bookingStatus = booking.status,                // chuỗi "COMPLETED", "CONFIRMED", ...
               completedAt = booking.completedAt,             // Instant?, trường bookings.completedAt
               existingReview = myReview,
               now = Instant.now(),
           )
       }
   ```

   | Giá trị | Hiển thị |
   |---|---|
   | `CAN_WRITE` | Nút "Đánh giá" |
   | `CAN_VIEW` | Nút "Xem đánh giá của bạn" |
   | `EXPIRED` | Chữ `R.string.review_window_closed` ("Đã hết hạn đánh giá"), không có nút |
   | `NOT_ELIGIBLE` | Không hiện gì (đơn chưa hoàn thành, đã hủy, `NO_SHOW`, ...) |

   Gửi xong, listener `observeMyReview` tự phát đánh giá mới nên nút đổi thành "Xem đánh giá của bạn".

2. Mở màn hình viết/xem đánh giá (cả hai nút dùng chung, màn hình tự chuyển sang chỉ xem khi đã có đánh giá):

   ```xml
   <!-- trong graph của chi tiết đơn -->
   <action
       android:id="@+id/action_bookingDetail_to_writeReview"
       app:destination="@id/writeReviewFragment" />
   ```

   ```kotlin
   findNavController().navigate(
       R.id.action_bookingDetail_to_writeReview,
       bundleOf("bookingId" to bookingId),   // mode mặc định CREATE
   )
   ```

   Đã có đánh giá thì màn hình hiện nội dung đã gửi, có nút "Sửa" (còn trong 7 ngày) và "Xóa đánh giá". Muốn mở thẳng chế độ sửa thì truyền thêm `"mode" to WriteReviewMode.EDIT`:

   ```kotlin
   bundleOf("bookingId" to bookingId, "mode" to WriteReviewMode.EDIT)
   ```

   Hoặc deep link `smashnow://review/write/{bookingId}` (thông báo `REVIEW_REMINDER` của spec 100 cũng dùng link này).

## Tổng quan điểm và nút "Xem tất cả" ở chi tiết sân

Dùng cho chi tiết sân (spec 030, A).

1. Khối tổng quan: inject `ReviewRepository`, thu thập `observeSummary(venueId)` → `VenueRatingSummary` (`average`, `count`, `courtAvg`, `cleanlinessAvg`, `serviceAvg`). `count == 0` thì hiện `R.string.review_empty_venue` ("Sân chưa có đánh giá").
2. Nút "Xem tất cả": màn `VenueReviewsFragment` nằm trong `nav_review` (đã include trong `nav_player`).

   ```xml
   <action
       android:id="@+id/action_venueDetail_to_venueReviews"
       app:destination="@id/venueReviewsFragment" />
   ```

   ```kotlin
   findNavController().navigate(
       R.id.action_venueDetail_to_venueReviews,
       bundleOf("venueId" to venueId, "venueName" to venueName),
   )
   ```

   Hoặc deep link `smashnow://review/venue/{venueId}` (thông báo `REVIEW_REPLY` của spec 100 dùng link này).

Màn hình có chip lọc 1–5 sao (`starBucket`) và "Có ảnh", mỗi trang 20 đánh giá, chạm ảnh để xem toàn màn hình.

## Mục "Đánh giá của khách" cho chủ sân

Dùng cho menu chế độ quản lý sân (spec 111/112, D). Màn `OwnerReviewsFragment` nằm trong graph lồng `nav_review_owner`, đã `include` trong `nav_owner`, không có tham số:

```xml
<action
    android:id="@+id/action_ownerMenu_to_ownerReviews"
    app:destination="@id/nav_review_owner" />
```

Hoặc deep link `smashnow://review/owner` (giống cách trang Tài khoản mở `smashnow://owner/venues`). Danh sách cơ sở lấy qua `OwnerVenueRepository.getOwnedVenues()` của spec 111 (bỏ cơ sở `DELETED`, tối đa 30 cơ sở vì giới hạn `whereIn`). Chủ sân lọc theo cơ sở và "Chưa phản hồi", bấm "Phản hồi"/"Sửa phản hồi" để mở bottom sheet (1–500 ký tự, không xóa được phản hồi).

## Việc còn chờ nhóm khác

| Chỗ | Đang làm tạm | Khi nhóm khác xong |
|---|---|---|
| Bấm một sân trong danh sách | Mở lưới đặt sân (spec 040) | Đổi `action_favoriteVenues_to_bookingSlotGrid` sang chi tiết sân (spec 030) |
| Nút "Tìm sân" ở trạng thái rỗng | Quay về trang trước | Mở tìm kiếm (spec 020) |
| Sân bị ẩn/khóa | Đọc `venues/{id}` bằng `get`. Rules chỉ cho người chơi đọc cơ sở `ACTIVE`, nên `PERMISSION_DENIED` được coi là "Sân tạm ngừng hoạt động" (sân đã xóa hẳn cũng hiện như vậy) | Nếu D tách Rules `venues` thành `get` mọi trạng thái / `list` chỉ `ACTIVE` (research R6) thì lại phân biệt được sân đã xóa |
