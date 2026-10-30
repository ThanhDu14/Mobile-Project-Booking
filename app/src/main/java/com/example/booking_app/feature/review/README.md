# feature/review — Đánh giá sân và sân yêu thích (spec 070, C)

Tài liệu thiết kế: [specs/070-review-favorite](../../../../../../../../../specs/070-review-favorite/). Hiện đã có phần **Sân yêu thích (US3)**; phần đánh giá (US1, US2, US4–US6) làm ở tuần 5.

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

## Việc còn chờ nhóm khác

| Chỗ | Đang làm tạm | Khi nhóm khác xong |
|---|---|---|
| Bấm một sân trong danh sách | Mở lưới đặt sân (spec 040) | Đổi `action_favoriteVenues_to_bookingSlotGrid` sang chi tiết sân (spec 030) |
| Nút "Tìm sân" ở trạng thái rỗng | Quay về trang trước | Mở tìm kiếm (spec 020) |
| Sân bị ẩn/khóa | Đọc `venues/{id}` bằng `get` (Rules hiện cho đọc mọi cơ sở) | Giữ nguyên nếu D tách Rules `venues` thành `get` mọi trạng thái / `list` chỉ `ACTIVE` (research R6) |
