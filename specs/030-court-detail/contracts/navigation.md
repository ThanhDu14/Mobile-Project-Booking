# Contract: Điều hướng vào và ra khỏi chi tiết sân

Nhóm khác mở chi tiết sân và nhận điều hướng từ chi tiết sân **chỉ** qua các điểm dưới đây. Không import class trong `feature/courtdetail/ui` (constitution I). Lý do và phương án đã loại: [research R1, R3, R11](../research.md).

## 1. Vào chi tiết sân

### 1.1. Nav graph và tham số

`res/navigation/nav_court_detail.xml`, được `include` vào `nav_player.xml`.

```xml
<navigation android:id="@+id/nav_court_detail"
    app:startDestination="@id/courtDetailFragment">
  <fragment android:id="@+id/courtDetailFragment"
      android:name="com.example.booking_app.feature.courtdetail.ui.CourtDetailFragment">
    <argument android:name="venueId"   app:argType="string" />
    <argument android:name="date"      app:argType="string" app:nullable="true" android:defaultValue="@null" />  <!-- yyyyMMdd -->
    <argument android:name="startTime" app:argType="string" app:nullable="true" android:defaultValue="@null" />  <!-- HHmm -->
    <argument android:name="endTime"   app:argType="string" app:nullable="true" android:defaultValue="@null" />  <!-- HHmm -->
    <deepLink app:uri="smashnow://venue/{venueId}?date={date}&amp;start={startTime}&amp;end={endTime}" />
  </fragment>
</navigation>
```

- Tham số giống hệt [contract 020](../../020-court-search/contracts/ui-screens.md) §1.
- `date`, `startTime`, `endTime` chỉ có hiệu lực khi đủ cả ba, đúng định dạng và chưa qua giờ. Ngược lại màn hình coi như không có khung giờ (không báo lỗi).
- `venueId` không tồn tại → màn hình "Sân không còn tồn tại", không crash.

### 1.2. Ai mở và mở bằng gì

| Nhóm | Từ đâu | Cách mở | Tham số giờ |
|---|---|---|---|
| A – 020 | Thẻ kết quả tìm kiếm | Action trong `nav_search` → `nav_court_detail` | Có, nếu bộ lọc có ngày và khung giờ |
| A – 021 | Thẻ tóm tắt trên bản đồ, "Sân gần bạn" | Action hoặc deep link | Có, nếu bộ lọc có |
| A – 022 | Kết quả AI (qua danh sách 020) | Như 020 | Có |
| C – 070 | Danh sách yêu thích | Deep link `smashnow://venue/{venueId}` | Không |
| D – 100 | Thông báo có `venueId` (khuyến mãi, sân yêu thích…) | Deep link trong `PendingIntent` | Không |
| B – 060 | Chi tiết đơn → "Xem sân" (nếu có) | Deep link | Không |

## 2. Từ chi tiết sân sang màn hình khác

| Thao tác | Đích | Cách | Tham số | Phụ trách đích |
|---|---|---|---|---|
| "Đặt sân" | Lưới giờ `bookingSlotGridFragment` | `action_courtDetail_to_bookingSlotGrid` ([040 §1.1](../../040-booking-slot-grid/contracts/ui-contracts.md)) | `venueId`; **đề nghị thêm** `date: String?`, `startTime: String?`, `endTime: String?` cùng định dạng (R1) | B |
| "Xem tất cả (N)" | `VenueReviewsFragment` | Deep link `smashnow://review/venue/{venueId}` (070 đã công bố) | `venueId` (`venueName` do 070 tự đọc) | C |
| Nút chat | Hội thoại với chủ sân | Deep link `smashnow://chat/direct?venueId={venueId}` ([091 tasks T030](../../091-chat/tasks.md)) | `venueId` | C |
| Chạm ảnh | `PhotoViewerFragment` (`core/ui/photo`) | Action toàn cục `action_global_photoViewer` trong `nav_player` | `photoUrls: Array<String>`, `startIndex: Int` | A (dùng chung với 070) |
| Đăng nhập (khách) | `nav_auth` | `AuthNavigator.openLogin(navController, returnTo = null)` (010) | — | A |
| "Chỉ đường" | Ứng dụng bản đồ / trình duyệt | `Intent` `geo:` rồi URL Google Maps (R7) | `lat`, `lng`, tên | — |
| Số điện thoại | Trình gọi điện | `Intent(ACTION_DIAL, tel:)` (R9) | số E.164 | — |

**Mở lưới giờ khi khung giờ đã qua:** không truyền `date`/`startTime`/`endTime`; 040 mở ở ngày hôm nay (Edge case "khung giờ từ tìm kiếm đã qua").

## 3. Interface domain của nhóm khác mà 030 gọi

| Interface | Gói | Hàm 030 dùng | Ghi chú |
|---|---|---|---|
| `FavoriteRepository` | `feature/review/domain` (070) | `observeIsFavorite(venueId)`, `setFavorite(VenueSummary, Boolean)` | Gọi qua adapter `FavoriteGateway` của 030 (T062); `VenueSummary` ở `core/model` |
| `ReviewRepository` | `feature/review/domain` (070) | `latestVisible(venueId, limit)` **(đề nghị thêm)** | Gọi qua adapter `LatestReviewsSource` của 030 (T055). Chưa có → 030 truy vấn tạm, `TODO(C)` |
| `CatalogRepository` | `core/catalog` (020) | tên quận, danh mục tiện ích | |
| `LocationProvider` | `core/location` (020) | `hasPermission()`, rồi `current()` chỉ khi đã có quyền | Không xin quyền (R7) |
| `SessionManager` | `core/session` (010) | `sessionState`, `isGuest`, `currentUid()` | |
| `SlotPricing`, `PriceRule`, `DayConvention` | `core/pricing` (020) | Bảng giá, giá khung | |
| `TimeOfDay`, `BookingWindow.ZONE` | `core/schedule` (020) | Giờ mở cửa, khung giờ | |
| `AppClock` | `core/util` (010) | Thời điểm hiện tại | |

## 4. Phần 030 cung cấp cho nhóm khác

| Thành phần | Gói | Dùng bởi |
|---|---|---|
| `PhotoUrlResolver.venuePhoto(path)` | `core/media` | 020 (thẻ kết quả), 021, 070 (yêu thích), 111 |
| `PhotoViewerFragment`, `ZoomableImageView` | `core/ui/photo` | 070 (ảnh đánh giá) |
| `VenueRatingSummary.fromVenue(...)` | `core/model` | 070 (`observeSummary`), 020 |
| `ic_marker_shuttlecock.xml` | `res/drawable` | 021 |
