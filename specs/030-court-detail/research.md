# Research: Chi tiết sân (spec 030)

**Ngày**: 2026-10-25 · **Spec**: [spec.md](spec.md) · **Plan**: [plan.md](plan.md)

Mỗi mục ghi quyết định, lý do, phương án đã loại và việc cần phối hợp. Spec này chỉ đọc dữ liệu; không có Edge Function hay transaction mới.

## R1. Tham số vào màn hình và lối vào từ 020, 021, 070, thông báo (FR-001, FR-002)

**Quyết định:**
- Một nav graph lồng `nav_court_detail` (start = `courtDetailFragment`) được `include` vào `nav_player`. Tham số Safe Args giữ đúng contract 020 đã công bố: `venueId: String`, `date: String?` (`yyyyMMdd`), `startTime: String?` (`HHmm`), `endTime: String?` (`HHmm`).
- Deep link `smashnow://venue/{venueId}?date={date}&start={startTime}&end={endTime}` khai báo trên `courtDetailFragment`. Spec 070 (yêu thích), 100 (thông báo) và 021 (thẻ bản đồ) mở bằng deep link này hoặc action có cùng tham số; không ai import class trong `feature/courtdetail/ui` (constitution I).
- Ba tham số giờ chỉ hợp lệ khi đủ cả ba và đúng định dạng; thiếu hoặc sai một cái thì coi như không có ngữ cảnh (không làm nổi bật, không truyền sang 040). Hàm thuần `SearchWindowArgs.parse(date, start, end, now)` có unit test, trả `null` khi khung đã qua giờ (Edge case "khung giờ đã qua").
- Quay lại (FR-002) dùng back stack bình thường; ViewModel của 020 scope theo `nav_search` nên còn nguyên bộ lọc và vị trí cuộn.

**Lý do:** 020 đã chốt tham số này trong [contracts/ui-screens.md](../020-court-search/contracts/ui-screens.md) §1. Deep link cho phép thông báo và màn hình của nhóm khác mở chi tiết sân mà không phụ thuộc code.

**Phương án đã loại:** truyền cả object `SearchCriteria` của 020 → buộc 030 phụ thuộc `feature/search/domain`, và thông báo hay yêu thích không có `SearchCriteria` để truyền.

**Phối hợp B (040):** action `action_courtDetail_to_bookingSlotGrid` trong [040 ui-contracts §1.1](../040-booking-slot-grid/contracts/ui-contracts.md) hiện chỉ có `venueId`. FR-016 cần mở lưới giờ đúng ngày và làm nổi bật khung đã chọn, nên đề nghị B thêm 3 tham số tùy chọn `date: String?`, `startTime: String?`, `endTime: String?` (cùng định dạng) vào `bookingSlotGridFragment`. Nếu B chưa thêm, 030 vẫn truyền `venueId` và ghi `TODO(B)`.

## R2. Đọc dữ liệu, thứ tự tải và số lượt đọc (FR-024, FR-025, SC-001)

**Quyết định:**

| # | Đọc gì | Cách | Lượt đọc | Khi nào |
|---|---|---|---|---|
| 1 | `venues/{venueId}` | **Snapshot listener** (`callbackFlow`, gỡ khi Fragment `STOPPED`) | 1 + 1 mỗi lần đổi | Ngay khi mở, song song với #2, #3 |
| 2 | `venues/{venueId}/priceRules` | `get()` một lần | số dòng giá (3–9 với dữ liệu seed) | Ngay khi mở |
| 3 | `venues/{venueId}/courts` | `get()` một lần | số sân con (3–12) | Ngay khi mở |
| 4 | `reviews` where `venueId == X`, `status == VISIBLE` order by `createdAt desc` limit 3 | `get()` một lần | 1–3 (truy vấn rỗng tính 1) | Sau khi phần đầu đã hiện |
| 5 | `users/{uid}/favorites/{venueId}` | Qua `FavoriteRepository.observeIsFavorite` (spec 070) | 1 | Chỉ khi đã đăng nhập |
| 6 | `districts`, `amenities` | Qua `CatalogRepository` của 020 (`core/catalog`, có cache) | 0 khi đã có cache (lần đầu 22 + 4) | Ngay khi mở |
| 7 | Ô bản đồ (osmdroid) | HTTP tới máy chủ tile | 0 lượt Firestore | Khi mục "Vị trí" sắp hiện trên màn hình (R7) |

- **Tổng mỗi lần mở** (dữ liệu seed, đã có cache danh mục): khoảng 1 + 6 + 8 + 3 + 1 ≈ **19 lượt**, nhiều nhất khoảng 26 lượt (12 sân, 9 dòng giá). Hạn mức 50.000 lượt/ngày cho phép khoảng **1.900–2.600 lần mở/ngày**, đủ cho quy mô đồ án. Firestore offline cache trả lại kết quả khi mở lại cơ sở vừa xem, nhưng `get()` có mạng vẫn tính lượt đọc.
- **Listener chỉ cho document venue:** cần cho FR-023 (cơ sở bị ẩn/khóa khi đang xem) và để điểm đánh giá luôn mới. `courts`, `priceRules`, `reviews` ít đổi trong một phiên xem nên `get()` một lần là đủ; nút "Thử lại" của từng mục đọc lại mục đó.
- **Phần đầu** (ảnh đầu tiên, tên, địa chỉ, điểm, trạng thái mở cửa, giá ở thanh dưới, nút "Đặt sân") hiện khi có #1. Giá ở thanh dưới hiện khi có #2. Đánh giá (#4) và bản đồ (#7) tải sau, mỗi mục có trạng thái đang tải riêng.
- **Ước lượng thời gian trên 4G** (~100 ms RTT, cùng cách tính với 020 R4): #1, #2, #3 chạy song song qua cùng kết nối gRPC, khoảng 300–600 ms; ảnh bìa qua Coil 300–900 ms (ảnh seed 100–300 KB). Phần đầu hiện trong khoảng **0,7–1,5 giây**, dưới mục tiêu 3 giây. Đo lại theo [quickstart](quickstart.md) §4.
- Mọi lời gọi dùng `awaitWithTimeout` 15 giây và ánh xạ `AppError` của 010.

**Phương án đã loại:**
- **Listener cho cả 4 truy vấn:** mỗi thay đổi nhỏ (một đánh giá mới) đọc lại cả truy vấn và giữ kết nối lâu hơn, trái với yêu cầu "gỡ listener" của constitution. Không cần cho FR nào.
- **`get()` cho venue rồi đọc lại khi bấm "Đặt sân":** vẫn cần đọc lại ở R10, nhưng màn hình không tự cập nhật khi cơ sở bị khóa, nên bỏ lỡ một phần FR-023.
- **Dùng `VenueStaticCache` của 020:** nằm trong `feature/search/data`, import vào đây là vi phạm constitution I. Lợi ích nhỏ (cache 10 phút) không đáng để chuyển lên `core/`. Ghi lại làm hướng tối ưu.

## R3. Điểm đánh giá và 3 đánh giá mới nhất (FR-003, FR-014, FR-015, SC-006)

**Quyết định:**
- Điểm lấy từ document venue đang lắng nghe (R2 #1): `ratingAvg`, `ratingCount` và `ratingSums` (Map `court`, `cleanliness`, `service`, `overall10`; trường 🔒 **mới** do spec 070 đề xuất, [070 data-model](../070-review-favorite/data-model.md)). Trung bình từng tiêu chí = `ratingSums[key] / ratingCount`, làm tròn 1 chữ số thập phân. **Không** tự tính từ `reviews`.
- Hàm chuyển đổi đặt ở `core/model/VenueRatingSummary.kt` (`fromVenue(ratingAvg, ratingCount, ratingSums)`). Spec 070 cũng dùng hàm này cho `ReviewRepository.observeSummary`, để màn hình chi tiết và danh sách đánh giá ra cùng một số (SC-006). `ratingCount == 0` hoặc thiếu `ratingSums` thì hiện "Chưa có đánh giá" / "Sân chưa có đánh giá".
- 3 đánh giá mới nhất: truy vấn ở R2 #4 (index `venueId`, `status`, `createdAt desc` đã có trong đặc tả CSDL §6). Đề nghị C thêm vào interface `ReviewRepository` (đặt ở `feature/review/domain`) hàm `suspend fun latestVisible(venueId: String, limit: Int): List<Review>`. 030 gọi qua interface này nên không cần DTO đánh giá riêng.
- "Người dùng đã xóa": theo [070 research R9](../070-review-favorite/research.md#r9-đánh-giá-của-người-đã-xóa-tài-khoản), `delete-account` (A, spec 011) ghi `authorDeleted = true` và xóa `userSnapshot`. **Đã kiểm tra:** [011 research R12](../011-account-profile/research.md#r12-ẩn-danh-và-các-bước-do-nhóm-khác-cung-cấp) có bước `anonymizeReviews(uid)` (C viết, `delete-account` gọi; tasks 011 T074 đã có bản giả). 030 không cần thêm gì ở server. App hiện "Người dùng đã xóa" và ảnh mặc định khi `authorDeleted == true` hoặc `userSnapshot.displayName == null`.
- Nút "Xem tất cả (N)" dùng `N = ratingCount`, mở `VenueReviewsFragment(venueId, venueName)` của 070 bằng deep link `smashnow://review/venue/{venueId}` (070 đã công bố).

**Lý do:** 070 cập nhật tổng điểm trong transaction (R1, R2 của 070), nên điểm trên venue luôn khớp tập đánh giá `VISIBLE`. Tự tính từ `reviews` cần đọc hết đánh giá, không đạt hạn mức.

**Phương án đã loại:** 030 tự truy vấn `reviews` bằng DTO riêng → hai bản ánh xạ (ẩn danh, ảnh, phản hồi chủ sân) lệch nhau. Nếu C chưa có `latestVisible`, 030 viết tạm trong `feature/courtdetail/data` kèm `TODO(C)`.

## R4. Bảng giá: nhóm theo ngày và giá "Khung bạn chọn" (FR-008, FR-009, SC-002)

**Quyết định:**
- **Nhóm ngày:** với mỗi thứ trong tuần, dựng danh sách dòng giá của ngày đó (lọc theo `daysOfWeek`, sắp theo `startTime`). Các thứ **liền nhau có danh sách giống hệt** thì gộp thành một nhóm. Nhãn nhóm: một ngày → "Thứ 2"; hai ngày → "Thứ 7, Chủ nhật"; từ ba ngày liền nhau → "Thứ 2 – Thứ 6". Hàm thuần `PriceTableBuilder.build(rules): List<PriceDayGroup>` có unit test.
  - Ví dụ `demo-quang-vinh` có các dòng "T2-T6", "T2", "T3-T6", "T7-CN". Nhóm theo tập ngày của từng dòng sẽ ra bảng lặp và khó đọc. Nhóm theo lịch của từng ngày ra 3 nhóm rõ ràng: "Thứ 2", "Thứ 3 – Thứ 6", "Thứ 7, Chủ nhật".
- Mỗi dòng: khung giờ (`HH:mm – HH:mm`, `2400` hiện "24:00"), nhãn theo `label` (`NORMAL` → "Giờ thường", `PEAK` → "Giờ cao điểm", `WEEKEND` → "Cuối tuần"), giá `pricePerHour` định dạng "120.000 đ/giờ". Không có dòng nào → "Liên hệ" kèm số điện thoại; không có số điện thoại → "Liên hệ chủ sân" kèm nút chat (Edge case).
- **Giá "Khung bạn chọn"** gọi đúng hàm của `core/pricing` mà 020 và 040 dùng ([020 research R5](../020-court-search/research.md#r5-logic-giá-và-lưới-giờ-dùng-chung-với-spec-040-b)): `SlotPricing.priceForWindow(rules, date, start, end)` rồi `SlotPricing.perHour(total, start, end)`. Trả `null` (có khung 30 phút không có giá) → "Liên hệ". Không viết lại phép tính.
- **Làm nổi bật:** những dòng giá áp dụng cho ngày đã chọn và có giao với `[start, end)`. Khung nằm giữa hai mức thì cả hai dòng được làm nổi bật (US2-3).
- **Test vector dùng chung (SC-002):** file `app/src/test/resources/pricing/window-price-vectors.json` gồm các ca (`rules`, `date`, `start`, `end`, `expectedPerHour`). Test của `core/pricing` (020) chạy trên file này, và test của 030, 040 nạp **cùng file** để bảo đảm ba màn hình ra cùng một giá. Các ca lấy từ dữ liệu seed, xem [quickstart](quickstart.md) §3.

**Phương án đã loại:** nhóm theo tập `daysOfWeek` của từng dòng (đơn giản hơn nhưng sai với dữ liệu thật, như ví dụ trên).

**Phối hợp:** `core/pricing` do A viết trong PR `chore(core)` phần 2 của 020. Nếu 030 làm trước 020 thì 030 viết `core/pricing`, `core/schedule` đúng như contract của 020.

## R5. Giờ `"2400"` và quy ước `daysOfWeek` (dữ liệu seed)

**Quyết định:**
- `core/schedule/TimeOfDay` chấp nhận `"2400"` (= 1440 phút) **chỉ ở vị trí giờ kết thúc** (`closeTime`, `endTime`). `"2400"` ở `openTime` hay `startTime` là dữ liệu sai. Phạm vi ngày là nửa mở `[start, end)`, nên `2400` là mốc cuối ngày, không phải giờ của ngày hôm sau. `SlotPricing` và `OpeningHours` (R6) dùng cùng hàm phân tích. Thêm ca `"2400"` vào test của `core/schedule`.
- **`daysOfWeek`:** seed dùng ISO (1 = Thứ hai … 7 = Chủ nhật), 020 R5 đề xuất ISO, còn [040 data-model](../040-booking-slot-grid/data-model.md) ghi 1 = Chủ nhật. 030 **không** tự đổi số ngày. Mọi phép đổi giữa số trong `daysOfWeek` và `java.time.DayOfWeek` nằm trong một hàm `core/pricing/DayConvention.toDayOfWeek(Int)`, nên khi nhóm chốt quy ước chỉ cần sửa một chỗ. Ghi rõ đây là **điểm chặn** cho B và D: phải chốt trước khi 040 và 030 cùng chạy trên dữ liệu thật, vì quy ước sai làm nhóm "Thứ 2 – Thứ 6" lệch sang "Chủ nhật – Thứ 5".

**Lý do:** dữ liệu seed đã có `2400` ở 9/10 cơ sở. Nếu hàm phân tích từ chối giá trị này, bảng giá và giờ mở cửa sẽ lỗi trên hầu hết dữ liệu demo.

## R6. Giờ mở cửa và trạng thái đang mở (FR-007)

**Quyết định:** hàm thuần `OpeningHours.status(openTime, closeTime, now: ZonedDateTime)` trong `feature/courtdetail/domain`. `now` lấy từ `AppClock` của 010 và đổi sang `Asia/Ho_Chi_Minh` (`core/schedule/BookingWindow.ZONE`). Kết quả:

| Trường hợp | Điều kiện | Hiển thị |
|---|---|---|
| Mở cả ngày | `open == 0000` và `close == 2400`, hoặc `open == close` | "Mở cả ngày", luôn "Đang mở cửa" |
| Bình thường | `open < close` | Mở khi `open ≤ now < close` |
| Qua nửa đêm | `close < open` (ví dụ 05:00–01:00) | Mở khi `now ≥ open` hoặc `now < close` |
| Đã đóng | ngoài khoảng trên | "Đã đóng cửa · Mở lại HH:mm" (giờ mở của lần kế tiếp) |

Trạng thái được tính lại mỗi phút khi màn hình đang hiện (`tickerFlow` từ `AppClock`), để người để màn hình mở qua giờ đóng cửa vẫn thấy đúng. Unit test với `AppClock` giả: 20:00 và 23:30 cho 05:00–23:00 (US1-4), 00:30 cho 05:00–01:00, 00:30 cho 05:00–24:00 (đóng, mở lại 05:00), 0000–2400.

**Sân con (FR-006):** `CourtSummary.from(courts)` chỉ đếm `isActive == true` và gộp theo `surfaceType` (`WOOD` → "Gỗ", `PU` → "PU", `MAT` → "Thảm"). Kết quả: "3 sân · Thảm" (một loại), "3 sân · 2 Gỗ, 1 Thảm" (nhiều loại, sắp theo số lượng giảm dần), 0 sân → "Tạm thời chưa có sân hoạt động". Dữ liệu seed đều là `PU`.

## R7. Bản đồ nhúng và "Chỉ đường" (FR-010, FR-011, FR-013)

**Quyết định:**
- **osmdroid (OpenStreetMap)**, theo dòng "Bản đồ" của constitution: nhóm dùng Firebase Spark và không gắn thẻ, nên không có tài khoản thanh toán Google Maps Platform. Dùng osmdroid ngay từ đầu, không làm hai bản. Marker quả cầu lông là `res/drawable/ic_marker_shuttlecock.xml` để spec 021 dùng lại.
- Bản đồ là `MapView` nhỏ (cao 180dp) **không tương tác** (tắt cuộn, phóng to, xoay) để không tranh thao tác cuộn với `NestedScrollView`. Mức phóng 16 và marker ở giữa. Bản đồ nằm trong `ViewStub` và chỉ khởi tạo khi mục "Vị trí" cách màn hình hiện tại dưới một chiều cao màn hình (FR-024). `onResume`/`onPause`/`onDetach` của `MapView` theo vòng đời của `viewLifecycleOwner`.
- Cấu hình osmdroid: `userAgentValue = BuildConfig.APPLICATION_ID` (bắt buộc theo chính sách tile của OSM), cache tile trong `context.cacheDir/osmdroid` nên **không cần quyền bộ nhớ**. Chế độ tối dùng `TilesOverlay.INVERT_COLORS`. Bắt buộc ghi nguồn "© OpenStreetMap contributors" trên bản đồ (`CopyrightOverlay` của osmdroid hoặc `TextView` chồng góc) theo giấy phép ODbL và chính sách tile của OSM; chuỗi nằm trong `strings_court_detail.xml`. Tile tải lỗi (mất mạng, máy chủ lỗi) thì ẩn `MapView` và chỉ hiện địa chỉ, nút "Chỉ đường" vẫn dùng được, các mục khác không bị ảnh hưởng (FR-013).
- **Không xin quyền vị trí** (FR-010). Thẻ "1,2 km Từ vị trí của bạn" trên wireframe chỉ hiện khi `LocationProvider` của 020 (`core/location`) đã có vị trí **và** quyền đã được cấp từ trước. Không thì thẻ đó hiện "Chỉ đường" (cùng hành vi với nút ở mục Vị trí).
- **"Chỉ đường":** thử `Intent(ACTION_VIEW, "geo:{lat},{lng}?q={lat},{lng}({tên cơ sở})")`. Gặp `ActivityNotFoundException` thì mở `https://www.google.com/maps/dir/?api=1&destination={lat},{lng}` trong trình duyệt. Dùng `try/catch` thay cho `resolveActivity` nên không phải khai báo `<queries>` (package visibility từ Android 11).
- Không có `location` hợp lệ (thiếu, hoặc `0,0`) thì ẩn bản đồ và nút "Chỉ đường", vẫn hiện địa chỉ (Edge case).

**Lưu ý dữ liệu:** tọa độ seed là gần đúng, lấy theo tên đường (README seed), có thể lệch vài trăm mét. Ghi vào quickstart để người kiểm thử không báo nhầm là lỗi. Trường `approx` chỉ có trong `venues.json`, không lên Firestore, nên app không hiển thị gì thêm.

**Phương án đã loại:**
- **Google Maps SDK:** cần khóa API từ project có tài khoản thanh toán, trái ràng buộc "không gắn thẻ".
- **Ảnh bản đồ tĩnh (Static Maps API):** cũng cần thanh toán.
- **Chỉ hiện địa chỉ, không có bản đồ:** không đạt FR-010.

## R8. Thư viện ảnh và xem toàn màn hình có phóng to (FR-004)

**Quyết định:**
- Tải ảnh bằng **Coil** (đã có trong constitution và plan 070 R12): cache bộ nhớ và đĩa, `placeholder` và `error` là `img_venue_placeholder`. Thư viện ở đầu màn hình là `ViewPager2` + `ListAdapter`, với chỉ báo "2/5" (`contentDescription` "Ảnh 2 trên 5"). Không có ảnh thì hiện một ô ảnh mặc định.
- **Đường dẫn ảnh:** hàm `core/media/PhotoUrlResolver.venuePhoto(path)`: path bắt đầu bằng `http://` hoặc `https://` thì dùng nguyên (dữ liệu seed trỏ tới `m-files.alobo.vn`), ngược lại ghép `"${SUPABASE_URL}/storage/v1/object/public/venue-photos/$path"`. Thẻ kết quả của 020 và mục yêu thích của 070 dùng cùng hàm này.
- **Xem toàn màn hình:** `core/ui/photo/PhotoViewerFragment` (tham số `photoUrls: Array<String>`, `startIndex: Int`), là destination toàn cục trong `nav_player`, dùng `ViewPager2` và `ZoomableImageView` (`core/ui/photo`, khoảng 150 dòng: `ScaleGestureDetector` + `GestureDetector`, ma trận giới hạn trong 1×–4×, chạm hai lần để phóng/thu). Khi đang phóng to, `ZoomableImageView` giữ thao tác vuốt (`requestDisallowInterceptTouchEvent`) để `ViewPager2` không chuyển ảnh. Phần tính ma trận (giới hạn tỉ lệ, kẹp biên) là hàm thuần có unit test.
- Đề nghị C dùng `PhotoViewerFragment` này cho ảnh đánh giá (070 T046) thay vì viết bản riêng. Tham số giống contract 070 đã công bố.

**Lý do không thêm thư viện phóng to:** `PhotoView` (chrisbanes) và `subsampling-scale-image-view` đều đã ngừng bảo trì và phát hành qua JitPack. Các thư viện còn bảo trì như `telephoto` chỉ có bản cho Compose, còn app dùng XML (constitution V). Một view tự viết nhỏ, có test, ít rủi ro hơn một phụ thuộc không còn bảo trì (constitution VII).

**Lưu ý dữ liệu:** ảnh seed lấy từ máy chủ của Alobo. Máy chủ đó có thể chặn hotlink hoặc đổi đường dẫn, khi đó ảnh rơi về ảnh mặc định mà không lỗi màn hình. Vấn đề bản quyền ghi ở R13.

## R9. Số điện thoại (FR-012)

**Quyết định:**
- Hiển thị `phone` cho mọi người, kể cả khách (đã chốt ở Clarifications của spec).
- Hàm thuần `PhoneDisplay.of(raw)`: bỏ khoảng trắng và dấu chấm. Khớp `^(0|\+84)\d{9,10}$` (9 chữ số cho di động, 10 cho số bàn có mã vùng 3 chữ số như `028 3812 3456`) → `Dialable(e164, display)`; không khớp → `DisplayOnly(raw)`. Không dùng `PhoneRule` của 010 vì nó nằm trong `feature/auth/domain` (constitution I) và chặt hơn (chỉ đầu số di động), trong khi số của cơ sở có thể là số bàn.
- `Dialable`: bấm → `Intent(ACTION_DIAL, "tel:…")`, **không cần quyền** `CALL_PHONE`. Máy không có `PackageManager.FEATURE_TELEPHONY` (máy tính bảng chỉ có wifi) hoặc gặp `ActivityNotFoundException` → sao chép số vào clipboard kèm snackbar "Đã sao chép số điện thoại".
- `DisplayOnly` (dữ liệu seed bị che, ví dụ `0944xxx486`): vẫn hiện số nhưng không bấm được, chỉ nhấn giữ để sao chép. Mục "Liên hệ" của bảng giá cũng theo quy tắc này.
- Không có `phone`: ẩn thẻ số điện thoại (Edge case).

## R10. Trạng thái cơ sở và Rules đọc `venues` (FR-019, FR-021, FR-022, FR-023, SC-005)

**Quyết định:**
- **Ánh xạ trạng thái:**

| Đọc `venues/{id}` | Người dùng | Hiển thị |
|---|---|---|
| Có, `status == ACTIVE` | mọi người | Bình thường |
| Có, `HIDDEN` hoặc `LOCKED` | đã đăng nhập | Thông tin đầy đủ kèm ghi chú "Sân tạm ngừng hoạt động"; ẩn "Đặt sân" và "Chat"; trái tim chỉ cho bỏ yêu thích |
| Có, `DELETED`, hoặc không tồn tại | mọi người (cả khách, nhờ Rules cho đọc `DELETED`) | Màn hình "Sân không còn tồn tại" kèm "Quay lại"; thêm "Bỏ khỏi yêu thích" nếu đang yêu thích |
| `PERMISSION_DENIED` (chỉ còn `HIDDEN`/`LOCKED`) | khách | Màn hình "Sân tạm ngừng hoạt động" rút gọn (không có thông tin), kèm "Quay lại" và "Đăng nhập" |

- **Rules đề xuất cho D** (gộp yêu cầu của 020 R13 và 070 R6):

```text
match /venues/{venueId} {
  allow get:  if resource == null                       // cho biết "không tồn tại"
              || resource.data.status in ['ACTIVE', 'DELETED']  // khách biết cơ sở đã xóa (FR-022)
              || isSignedIn()                           // 070 R6: người chơi mở được cơ sở tạm ngừng
              || isAdmin();
  allow list: if resource.data.status == 'ACTIVE'
              || (isInitialized() && resource.data.ownerId == request.auth.uid)
              || isAdmin();
  match /courts/{id}     { allow read: if true; }       // như 020 §2
  match /priceRules/{id} { allow read: if true; }
}
```

  Khớp test V-3 của 020 (khách đọc trực tiếp cơ sở `HIDDEN` bị chặn). Cho khách `get` cơ sở `DELETED` (sửa sau `/speckit-analyze` I1) để khách mở liên kết cũ thấy "Sân không còn tồn tại" đúng FR-022 thay vì "tạm ngừng"; cơ sở đã xóa không còn gì cần giấu. `list` vẫn chỉ trả `ACTIVE`. Khách mở liên kết tới cơ sở tạm ngừng thì nhận `PERMISSION_DENIED` và thấy màn hình rút gọn. Đây là giới hạn đúng: thông tin của cơ sở tạm ngừng chỉ người đã đăng nhập xem được.
- **Trước khi mở lưới giờ (FR-023):** `CheckVenueBookableUseCase` đọc lại `venues/{id}` bằng `get(Source.SERVER)` (1 lượt). Không phải `ACTIVE` → báo "Sân tạm ngừng hoạt động" và cập nhật màn hình. Mất mạng → dùng trạng thái mới nhất từ listener và mở lưới giờ; luồng đặt sân của 040 tự xử lý lỗi mạng (Edge case). Kiểm tra này chỉ để trải nghiệm tốt hơn: **`create-booking` của B phải từ chối cơ sở không `ACTIVE` ở server** (constitution II).
- **Đánh giá cho khách (FR-019):** Rules của 070 hiện yêu cầu `isSignedIn()` để đọc `reviews`, nên khách không xem được đánh giá. Đề nghị C đổi thành: `allow get, list: if resource.data.status == 'VISIBLE' || (isSignedIn() && (resource.data.userId == request.auth.uid || isAdmin()))`. Đánh giá `VISIBLE` là nội dung công khai, đã có `userSnapshot` (tên, ảnh) do người viết chọn.
- **Phân biệt khách ở đâu:** repository chỉ trả `VenueResult.PermissionDenied`; `ObserveVenueDetailUseCase` mới đổi thành `SUSPENDED_GUEST` khi `SessionManager.isGuest`, còn đã đăng nhập thì là lỗi chung. Tầng data không phụ thuộc phiên đăng nhập.
- **Test Emulator** (`firestore-tests/test/court-detail.test.ts`, A viết như 020 đã làm): VD-1 đến VD-10 ở [contracts/security-rules.md](contracts/security-rules.md). Khi D và C sửa Rules thì các test này phải xanh.

**Phương án đã loại:** cho khách đọc mọi cơ sở ở mọi trạng thái → trái test V-3 và SC-004 của 020 (khách không được thấy cơ sở bị khóa).

**Khác với 070 R6:** 070 coi `DELETED` là "tạm ngừng", còn spec 030 FR-022 yêu cầu cơ sở đã xóa hiện "Sân không còn tồn tại". Đề nghị C dùng cùng bảng ánh xạ ở trên cho danh sách yêu thích để hai màn hình thống nhất.

## R11. Khách chưa đăng nhập và "thao tác đang chờ" (FR-018, FR-020, SC-004)

**Quyết định:**
- Khi khách bấm "Đặt sân", trái tim hoặc "Chat", hiện `MaterialAlertDialog` mời đăng nhập ("Đăng nhập để đặt sân" / "…lưu sân yêu thích" / "…nhắn tin cho chủ sân", nút "Đăng nhập", "Để sau"). Chọn "Đăng nhập" thì `CourtDetailViewModel` ghi `pendingAction ∈ {BOOK, FAVORITE, CHAT}` vào `SavedStateHandle`, rồi Fragment gọi `AuthNavigator.openLogin(navController, returnTo = null)`.
- **Không cần mở rộng `returnTo`:** theo [010 R15](../010-account-auth/research.md#r15-chế-độ-khách-và-quay-lại-đúng-chỗ-sau-khi-đăng-nhập-fr-025-fr-026), `nav_auth` mở chồng lên và khi người chơi đăng nhập xong thì `popBackStack(nav_auth)`. Màn hình chi tiết hiện lại với **cùng ViewModel**, nên ngày, khung giờ và `pendingAction` còn nguyên. `SavedStateHandle` giữ được qua xoay màn hình và process death.
- **Tiếp tục hoặc hủy:** ViewModel kết hợp `SessionManager.sessionState` với một tín hiệu Fragment báo `onScreenVisible()` (gọi ở `onResume`):
  - `SignedIn` và có `pendingAction` → kiểm tra chủ sở hữu (`venue.ownerId == currentUid()` → báo "Đây là cơ sở của bạn", FR-018), rồi chạy thao tác đúng một lần và xóa `pendingAction`. `BOOK` chạy qua R10 và mở lưới giờ với ngày và khung giờ còn hợp lệ. `FAVORITE` gọi `FavoriteRepository.setFavorite(venue, true)` (ID = `venueId`, đã có thì không đổi gì). `CHAT` mở `smashnow://chat/direct?venueId=` (091).
  - Vẫn là khách, màn hình hiện lại và còn `pendingAction` → người dùng đã thoát giữa chừng → xóa `pendingAction`, không làm gì (US5-6). Khi `nav_auth` đang nằm trên thì màn hình chi tiết không ở `RESUMED`, nên không xóa nhầm.
- **Hàm thuần** `PendingActionResolver.resolve(state, pending, ownerId, uid)` trả `Run(action)`, `RejectOwnVenue`, `Cancel` hoặc `Wait`, có unit test cho mọi nhánh.

**Giới hạn và đề xuất sửa spec:** theo 010 R15, tài khoản `OWNER`/`ADMIN` đăng nhập từ chế độ khách được đưa sang `nav_owner`/`nav_admin` (`popUpTo(nav_main)`), nên màn hình chi tiết bị đóng và thao tác chờ không chạy. US5-7 ("tài khoản vừa đăng nhập là chủ sở hữu cơ sở đang xem") vì vậy chỉ xảy ra khi chủ sân đang ở chế độ người chơi (spec 011) và bấm vào cơ sở của mình. Đề xuất sửa US5-7 thành "chủ sân ở chế độ người chơi xem cơ sở của mình" khi `/speckit-analyze`.

**Phương án đã loại:** thêm tham số `resume=BOOK` vào `returnTo` rồi `navigate(returnTo)` sau khi đăng nhập → đẩy thêm một màn hình chi tiết thứ hai lên back stack, và phải sửa `SessionRouter` của 010.

## R12. Lỗi, offline và giữ trạng thái (FR-025, FR-026, SC-007, FR-027)

**Quyết định:**
- `CourtDetailUiState` gồm trạng thái riêng cho từng mục: `header`, `prices`, `courts`, `reviews`, `map`. Mỗi mục là `Loading | Content | Empty | Error(AppError)`. Mục lỗi hiện dòng thông báo kèm "Thử lại" ngay tại chỗ (`onRetry(section)`), không chặn các mục khác.
- **Mất mạng:** `get()` mặc định (`Source.DEFAULT`) trả dữ liệu từ cache offline của Firestore khi không có mạng. Snapshot có `metadata.isFromCache == true` thì hiện banner "Không có kết nối, thông tin có thể chưa cập nhật" (FR-026). Chưa từng tải cơ sở này (cache rỗng) thì hiện lỗi toàn màn hình kèm "Thử lại". Có mạng lại thì listener tự cập nhật và banner biến mất.
- Ảnh và ô bản đồ chưa tải được thì hiện ô trống có biểu tượng, không ảnh hưởng phần còn lại.
- **Giữ trạng thái khi xoay màn hình hoặc đổi sáng/tối:** dữ liệu nằm trong ViewModel. Vị trí ảnh đang xem (`photoIndex`) và `pendingAction` lưu trong `SavedStateHandle`. Vị trí cuộn của `NestedScrollView` được hệ thống tự lưu (view có `id`).
- **Máy tính bảng (`w600dp`):** `layout-w600dp/fragment_court_detail.xml` chia hai cột: bên trái thư viện ảnh và bản đồ, bên phải thông tin, bảng giá và đánh giá. Thanh "Đặt sân" vẫn cố định ở cuối. Chế độ tối dùng màu từ theme `values-night`. TalkBack: thứ tự đọc là tên → địa chỉ → điểm → trạng thái mở cửa → giá; mọi nút và biểu tượng tiện ích có `contentDescription`; vùng chạm ≥ 48dp.

## R13. Dữ liệu seed của nhóm (`scripts/seed/`) và constitution VI

**Quyết định:**
- Dùng seed của Duy (`scripts/seed/seed.mjs --emulator`: 10 cơ sở `venues/demo-*` kèm `courts`, `priceRules`; 22 quận, 4 tiện ích) làm dữ liệu cho quickstart và Espresso. Cách 030 xử lý từng điểm trong bảng "Những chỗ khác" của README seed:

| Điểm của seed | Cách 030 xử lý |
|---|---|
| `photoPaths` là URL đầy đủ `https://m-files.alobo.vn/...` | `PhotoUrlResolver` dùng nguyên URL (R8) |
| `closeTime`/`endTime` = `"2400"` | `TimeOfDay` chấp nhận ở vị trí giờ kết thúc (R5) |
| Số điện thoại bị che `0944xxx486` | `PhoneDisplay.DisplayOnly`: hiện nhưng không mở trình gọi (R9) |
| Tọa độ gần đúng | Vẫn hiện marker; ghi chú trong quickstart (R7) |
| `amenityIds = []` | Mục "Tiện ích" có trạng thái rỗng "Chưa có thông tin tiện ích" (bổ sung cho FR-005, constitution V yêu cầu trạng thái rỗng) |
| Mọi sân `surfaceType = "PU"` | "N sân · PU" (R6) |
| `ownerId = "demo-owner-1"` | Ca kiểm thử FR-018 dùng Auth Emulator tạo tài khoản có `uid = demo-owner-1` (quickstart) |
| `daysOfWeek` theo ISO, chưa chốt | Đổi qua `DayConvention` (R5); điểm chặn cần B, D chốt |
| `recurringPricePerHour` chưa lên Firestore | Không hiển thị, không đọc |
| Không có `ratingSums`, `ratingAvg = 0` | Hiện "Chưa có đánh giá" |
| Chưa có `reviews` | Xem dưới |

- **Đánh giá cho US4:** seed chưa có `reviews`. Nếu seed đánh giá của 070 (T025) đã có thì dùng. Chưa có thì 030 thêm `scripts/seed/seed-reviews-030.mjs` (**chỉ chạy với `--emulator`**, từ chối project thật). Script ghi 12 đánh giá cho `demo-k3` (1 `HIDDEN`, 1 `authorDeleted`, 1 có phản hồi của chủ sân, 2 có ảnh) và tính `ratingAvg`, `ratingCount`, `ratingSums` khớp với 11 đánh giá `VISIBLE`. Schema theo 070 data-model. Script không sửa `seed.mjs` của Duy.
- **Quyết định C1 (Thành Dự, 2026-10-25, sau `/speckit-analyze`):** seed Alobo **chỉ dùng để demo đồ án** (buổi demo, ảnh chụp báo cáo). Cơ sở thật vẫn do chủ sân tạo qua tài khoản chủ sân ở spec 111, với ảnh tự chụp hoặc miễn phí bản quyền. Hệ quả:
  - ảnh chụp và báo cáo ghi chú "Dữ liệu demo lấy từ datlich.alobo.vn, chỉ dùng cho mục đích học tập" (T073);
  - khi có 111, tạo ít nhất 1 cơ sở qua giao diện chủ sân và chạy lại Q1, Q6, Q9 (T077) để chắc 030 đọc đúng dữ liệu do app ghi (path Supabase, có tiện ích, nhiều loại mặt sân);
  - đây vẫn là **ngoại lệ** với constitution VI, ghi ở Complexity Tracking của plan. Nếu báo cáo cuối kỳ vẫn dùng seed này thì nhóm nên sửa constitution (3/4 đồng ý) thay vì để ngoại lệ ngầm.

  Bối cảnh ban đầu: constitution ghi "Dữ liệu demo là dữ liệu giả hợp lý. Ảnh dùng ảnh tự chụp, ảnh miễn phí bản quyền hoặc ảnh chủ sân cung cấp. Không sao chép giao diện, logo hay nội dung của app tham khảo". Seed hiện lấy tên, địa chỉ, số điện thoại (đã che) và **ảnh** của 10 sân thật từ Alobo, mà Alobo là app tham khảo của nhóm. Các phương án đã cân nhắc (nhóm chọn giữ seed cho demo như trên):
  1. trước buổi demo và báo cáo, thay ảnh bằng ảnh miễn phí bản quyền (hoặc ảnh tự chụp) và tải lên bucket `venue-photos`;
  2. hoặc giữ tên và địa chỉ thật nhưng đổi số điện thoại thành số giả hợp lệ, để kiểm thử được luồng gọi điện;
  3. hoặc sửa constitution (cần 3/4 thành viên đồng ý).

  Code 030 không phụ thuộc lựa chọn nào: `PhotoUrlResolver` xử lý cả URL đầy đủ lẫn path Supabase.

## R14. Thư viện và cấu trúc code

**Quyết định:**

| Thư viện | Trạng thái | Lý do |
|---|---|---|
| Coil (`io.coil-kt.coil3:coil` + `coil-network-okhttp`) | Đã chốt ở plan 070 R12 và 020; thêm vào catalog nếu chưa có | Tải và cache ảnh |
| osmdroid (`org.osmdroid:osmdroid-android`) | **Mới** | Bản đồ nhúng (R7), có tên trong constitution, không cần khóa hay thanh toán |
| ViewPager2 | AndroidX, thường đã có qua Material | Thư viện ảnh |

- Package mới `feature/courtdetail/` (`ui`, `domain`, `data`), tên khớp nhóm `03-court-detail`.
- Phần mới đặt ở `core/` vì nhóm khác dùng:
  - `core/media/PhotoUrlResolver`: 020, 070, 111;
  - `core/ui/photo/PhotoViewerFragment`, `ZoomableImageView`: 070;
  - `core/model/VenueRatingSummary`: 070;
  - `res/drawable/ic_marker_shuttlecock.xml`: 021.
- Dùng lại, không viết lại: `core/session`, `core/network`, `core/util/AppClock`, `core/ui/LoginPromptView`, `AuthNavigator` (010); `core/pricing`, `core/schedule`, `core/catalog`, `core/location` (020); `FavoriteRepository`, `ReviewRepository` (interface trong `feature/review/domain` của 070).

## Tóm tắt việc cần phối hợp

| Ai | Việc | Mục | Nếu chưa có |
|---|---|---|---|
| **B** (040) | Thêm `date`, `startTime`, `endTime` (nullable) vào `bookingSlotGridFragment` | R1 | Chỉ truyền `venueId`, ghi `TODO(B)` |
| **B** (040) | `create-booking` từ chối cơ sở không `ACTIVE` | R10 | Báo trong PR; 030 vẫn kiểm tra ở client |
| **B + D** | Chốt quy ước `daysOfWeek` (đề xuất ISO), ghi vào đặc tả CSDL §4.3 | R5 | Điểm chặn trước khi chạy chung dữ liệu thật |
| **B** (040), **A** (020) | Test 040 nạp chung `window-price-vectors.json` | R4 | — |
| **C** (070) | Thêm `ReviewRepository.latestVisible(venueId, limit)`; dùng `core/model/VenueRatingSummary` | R3 | 030 truy vấn tạm, `TODO(C)` |
| **C** (070) | Rules `reviews`: khách đọc được đánh giá `VISIBLE` | R10 | Khách thấy lỗi ở mục đánh giá (chỉ mục đó) |
| **C** (070) | Dùng `core/ui/photo/PhotoViewerFragment` cho ảnh đánh giá; ánh xạ `DELETED` → "không còn tồn tại" | R8, R10 | — |
| **C** (091) | Deep link `smashnow://chat/direct?venueId=` | R11 | Nút chat ghi `TODO(C)`, báo "Tính năng sắp có" |
| **D** (111) | Rules `venues` theo R10; `ratingSums` trong §4.3; ảnh có thứ tự trong `photoPaths`; `phone` công khai (ghi cho chủ sân biết) | R3, R10 | Test VD-* của A đỏ cho tới khi D sửa |
| **C** (070) | Viết `anonymizeReviews(uid)` cho `delete-account` (011 R12) | R3 | Đánh giá của người đã xóa vẫn hiện tên cũ |
| **D** (111) | Chủ sân tạo cơ sở qua giao diện để chạy lại Q1, Q6, Q9 (đã quyết C1: seed chỉ dùng demo) | R13, T077 | Demo bằng seed, ghi nguồn |
