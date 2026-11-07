# Data Model: Chi tiết sân (spec 030)

**Ngày**: 2026-10-25 · **Plan**: [plan.md](plan.md) · **Research**: [research.md](research.md)

Spec này **chỉ đọc**. Không có collection, trường hay Edge Function mới do 030 sở hữu. Các bảng dưới đây liệt kê những trường 030 đọc và mô hình phía app.

## 1. Dữ liệu Firestore được đọc

### 1.1. `venues/{venueId}` (D, đặc tả CSDL §4.3)

| Trường | Kiểu | 030 dùng cho | Ghi chú |
|---|---|---|---|
| `name` | String | Tiêu đề, `contentDescription`, tham số "Xem tất cả" | |
| `address`, `districtId` | String | Địa chỉ, tên quận qua `CatalogRepository` | `districtId` không có trong danh mục → chỉ hiện địa chỉ |
| `location` | GeoPoint | Bản đồ, "Chỉ đường", khoảng cách | Thiếu hoặc `0,0` → ẩn bản đồ (R7). Seed: tọa độ gần đúng |
| `openTime`, `closeTime` | String `HHmm` | `OpeningHours` (R6) | `closeTime` có thể là `"2400"` (R5) |
| `phone` | String? | `PhoneDisplay` (R9) | Công khai cho mọi người. Seed: số bị che |
| `amenityIds` | Array\<String> | Mục tiện ích | Id không có trong danh mục thì bỏ qua (FR-005). Rỗng → trạng thái rỗng |
| `photoPaths` | Array\<String> | Thư viện ảnh, theo đúng thứ tự | Path Supabase hoặc URL đầy đủ (R8). Tối đa 5 ảnh (đặc tả CSDL §8) |
| `ownerId` | String | FR-018 | Seed: `demo-owner-1` |
| `ratingAvg` 🔒, `ratingCount` 🔒 | Double, Int | Điểm, số lượt, "Xem tất cả (N)" | Seed: `0`, `0` |
| `ratingSums` 🔒 | Map\<String, Long> | Điểm từng tiêu chí | **Trường mới của 070**, D thêm vào §4.3. Thiếu → không hiện thanh tiêu chí |
| `status` | String | Bảng trạng thái ở R10 | `ACTIVE`, `HIDDEN`, `LOCKED` 🔒, `DELETED` |

Không đọc: `nameKeywords`, `geohash`, `minPricePerHour`, `acceptsRecurring`, `courtCount` (đếm lại từ `courts` để loại sân tạm ngưng, FR-006).

### 1.2. `venues/{venueId}/courts/{courtId}` (D)

| Trường | Kiểu | Dùng cho |
|---|---|---|
| `surfaceType` | String `WOOD` \| `PU` \| `MAT` | Gộp theo loại mặt sân. Giá trị lạ → nhóm "Khác" |
| `isActive` | Boolean | Chỉ đếm sân đang hoạt động |

### 1.3. `venues/{venueId}/priceRules/{id}` (D)

| Trường | Kiểu | Dùng cho |
|---|---|---|
| `daysOfWeek` | Array\<Int> 1–7 | Nhóm ngày (R4), đổi qua `DayConvention` (R5) |
| `startTime`, `endTime` | String `HHmm` | Dòng giá, `SlotPricing`. `endTime` có thể là `"2400"` |
| `pricePerHour` | Long (VND) | Giá hiển thị và tính giá khung |
| `label` | String `NORMAL` \| `PEAK` \| `WEEKEND` | Nhãn dòng. Giá trị lạ → "Giờ thường" |

Ánh xạ DTO sang `core/pricing/PriceRule` dùng đúng mapper của 020 (không viết lại).

### 1.4. `reviews` (C, đặc tả CSDL §4.7)

Truy vấn: `venueId == X`, `status == VISIBLE`, order by `createdAt desc`, limit 3 (index đã có ở §6). 030 đọc qua interface riêng `LatestReviewsSource` (`feature/courtdetail/domain`); bản cài đặt gọi `ReviewRepository.latestVisible` của 070 khi đã có, chưa có thì truy vấn tạm (R3, T055). Các trường hiển thị: `userSnapshot` (`displayName`, `avatarPath`), `authorDeleted`, `overall`, `comment`, `photoPaths`, `ownerReply` (`text`, `repliedAt`), `createdAt`.

### 1.5. Dữ liệu khác

- `districts`, `amenities`: qua `CatalogRepository` (020, `core/catalog`).
- `users/{uid}/favorites/{venueId}`: qua interface riêng `FavoriteGateway` (`feature/courtdetail/domain`), bản cài đặt gọi `FavoriteRepository` của 070 (T062). 030 không đọc hay ghi trực tiếp.

## 2. Mô hình phía app

### 2.1. Tham số vào (`feature/courtdetail/domain/model`)

| Model | Trường | Quy tắc |
|---|---|---|
| `CourtDetailArgs` | `venueId: String`, `window: SearchWindow?` | Lấy từ Safe Args hoặc deep link |
| `SearchWindow` | `date: LocalDate`, `start: TimeOfDay`, `end: TimeOfDay` | `SearchWindowArgs.parse(date, start, end, now)`: đủ cả 3, đúng định dạng, `start < end`, bội số 30 phút, chưa qua giờ (`date + start > now` theo giờ VN). Không thỏa → `null` (R1) |

### 2.2. Thông tin cơ sở

| Model | Trường | Ghi chú |
|---|---|---|
| `VenueDetail` | `id`, `name`, `address`, `districtName: String?`, `location: LatLng?`, `openTime`, `closeTime`, `phone: PhoneDisplay?`, `amenities: List<Amenity>`, `photoUrls: List<String>`, `ownerId`, `rating: VenueRatingSummary`, `availability: VenueAvailability`, `isFromCache: Boolean` | Đã gộp danh mục và giải URL ảnh |
| `VenueAvailability` | `ACTIVE`, `SUSPENDED` (`HIDDEN`/`LOCKED`), `GONE` (`DELETED`/không tồn tại), `SUSPENDED_GUEST` (khách bị `PERMISSION_DENIED`, chỉ `ObserveVenueDetailUseCase` quyết định; repository trả `VenueResult.PermissionDenied`) | Bảng R10 |
| `OpeningStatus` | `AllDay`, `Open(closesAt)`, `Closed(reopensAt)` | `OpeningHours.status` (R6) |
| `CourtSummary` | `activeCount: Int`, `bySurface: List<Pair<SurfaceType, Int>>` | Sắp theo số lượng giảm dần (R6) |
| `PhoneDisplay` | `Dialable(e164, display)`, `DisplayOnly(raw)` | R9 |
| `VenueRatingSummary` (`core/model`) | `average: Double`, `count: Int`, `courtAvg`, `cleanlinessAvg`, `serviceAvg: Double?` | `fromVenue(ratingAvg, ratingCount, ratingSums)`; tiêu chí `null` khi thiếu `ratingSums` (R3) |

### 2.3. Bảng giá

| Model | Trường | Ghi chú |
|---|---|---|
| `PriceDayGroup` | `days: List<DayOfWeek>`, `title: DayGroupTitle`, `rows: List<PriceRow>` | `PriceTableBuilder.build(rules)` gộp các ngày liền nhau có cùng danh sách dòng (R4) |
| `DayGroupTitle` | `Single(day)`, `Pair(a, b)`, `Range(from, to)` | → "Thứ 2" / "Thứ 7, Chủ nhật" / "Thứ 2 – Thứ 6" |
| `PriceRow` | `start`, `end: TimeOfDay`, `label: PriceLabel`, `pricePerHour: Long`, `highlighted: Boolean` | `highlighted` khi có `SearchWindow`, đúng ngày và giao với `[start, end)` |
| `SelectedWindowPrice` | `window: SearchWindow`, `perHour: Long?` | `null` → "Liên hệ". Tính bằng `SlotPricing` (R4) |

### 2.4. Thao tác đang chờ (R11)

| Model | Giá trị | Lưu ở |
|---|---|---|
| `PendingAction` | `BOOK`, `FAVORITE`, `CHAT` | `SavedStateHandle["pendingAction"]` |
| `PendingDecision` | `Run(action)`, `RejectOwnVenue`, `Cancel`, `Wait` | Kết quả của `PendingActionResolver.resolve(sessionState, pending, ownerId, currentUid, screenVisible)` |

Bảng quyết định của `PendingActionResolver`:

| `sessionState` | `pending` | Màn hình đang hiện | `ownerId == uid` | Kết quả |
|---|---|---|---|---|
| bất kỳ | `null` | bất kỳ | — | `Wait` |
| `SignedIn` | có | có | có, và `pending ∈ {BOOK, CHAT}` | `RejectOwnVenue` |
| `SignedIn` | có | có | còn lại | `Run(pending)` |
| `SignedOut` | có | có | — | `Cancel` |
| bất kỳ | có | không | — | `Wait` |

### 2.5. Trạng thái màn hình

```text
CourtDetailUiState(
  availability: VenueAvailability?,          // null = đang tải lần đầu
  header:  Section<HeaderContent>,           // tên, địa chỉ, quận, điểm, OpeningStatus, ảnh
  prices:  Section<PriceContent>,            // List<PriceDayGroup> + SelectedWindowPrice?; Empty = "Liên hệ"
  courts:  Section<CourtSummary>,
  amenities: Section<List<Amenity>>,         // Empty = "Chưa có thông tin tiện ích"
  reviews: Section<ReviewsContent>,          // VenueRatingSummary + ≤3 Review; Empty = "Sân chưa có đánh giá"
  map:     Section<LatLng>,                  // Empty = ẩn bản đồ và "Chỉ đường"
  isFavorite: Boolean?,                      // null = khách hoặc đang tải
  isOffline: Boolean,                        // banner "thông tin có thể chưa cập nhật"
  bottomBar: BottomBarState                  // Book(priceText) | Suspended | Hidden
)
Section<T> = Loading | Content(T) | Empty | Error(AppError)
```

Sự kiện một lần (`Channel<CourtDetailEffect>`):
- `OpenBookingGrid(venueId, window?)`;
- `OpenReviews(venueId, venueName)`;
- `OpenChat(venueId)`;
- `OpenPhotoViewer(urls, index)`;
- `Dial(e164)`, `CopyPhone(text)`;
- `OpenDirections(lat, lng, name)`;
- `ShowLoginPrompt(action)`, `OpenLogin`;
- `Snackbar(message)`: "Đây là cơ sở của bạn", "Sân tạm ngừng hoạt động", "Đã thêm vào yêu thích", lỗi mạng.

## 3. Quy tắc kiểm tra và bất biến

| Quy tắc | Nguồn | Test |
|---|---|---|
| `TimeOfDay.parse("2400")` chỉ hợp lệ ở vị trí giờ kết thúc | R5 | `core/schedule` |
| Giá "Khung bạn chọn" = `SlotPricing.perHour(SlotPricing.priceForWindow(...))`; có khung 30 phút không có giá → `null` | FR-009, SC-002 | Test vector dùng chung (R4) |
| Một ngày nằm trong đúng một `PriceDayGroup` | R4 | `PriceTableBuilderTest` |
| Chỉ đếm `isActive == true` | FR-006 | `CourtSummaryTest` |
| `SUSPENDED` / `GONE` → không có "Đặt sân", "Chat"; trái tim chỉ bỏ được | FR-021, FR-022 | `CourtDetailViewModelTest` |
| Bấm "Đặt sân" luôn kiểm tra lại `status` từ server trước khi mở lưới giờ (khi có mạng) | FR-023 | `CheckVenueBookableUseCaseTest` |
| Thao tác chờ chạy đúng một lần | FR-020, SC-004 | `PendingActionResolverTest`, ViewModel test |
| Không lưu số điện thoại, tọa độ hay dữ liệu cá nhân vào DataStore | Constitution VI | Review |

## 4. Thay đổi cần đưa vào `docs/design/database/README.md`

030 không sở hữu collection nào. Các thay đổi dưới đây do người phụ trách ghi, 030 chỉ đề xuất:

1. **§4.3 `venues` (D):**
   - thêm `ratingSums` 🔒 (Map\<String, Long>: `court`, `cleanliness`, `service`, `overall10`), cùng đề xuất với 070;
   - ghi `closeTime`/`endTime` có thể là `"2400"` (mở tới nửa đêm), `openTime == closeTime` hoặc `0000`–`2400` nghĩa là mở cả ngày;
   - `photoPaths` theo thứ tự hiển thị (chủ sân sắp xếp), phần tử đầu là ảnh bìa; chấp nhận URL đầy đủ cho dữ liệu demo;
   - `phone` là thông tin công khai;
   - thêm giá trị `DELETED` vào `status` (đã có trong quy ước xóa mềm §1).
2. **§4.3 `priceRules` (B + D):** ghi quy ước `daysOfWeek` đã chốt (đề xuất ISO 1 = Thứ hai).
3. **§7 dòng `venues`, `courts`, `priceRules` (D):** người chơi "R `get` mọi trạng thái khi đã đăng nhập, `list` khi `ACTIVE`"; khách "R khi `ACTIVE`, `get` biết được không tồn tại"; `courts`, `priceRules` đọc công khai (R10).
4. **§7 dòng `reviews` (C):** khách đọc được đánh giá `VISIBLE` (R10).
5. **§6 index:** không thêm (`reviews` `venueId`, `status`, `createdAt desc` đã có).
