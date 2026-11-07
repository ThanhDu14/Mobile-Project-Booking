# Data Model: Tìm kiếm sân (spec 020)

Spec 020 **chỉ đọc** `venues`, `courts`, `priceRules`, `slots` (của D, B). Spec **sở hữu** `users/{uid}/searchHistory`, `districts`, `amenities` (A, đặc tả CSDL mục 2). Quyết định và lý do ở [research.md](research.md).

Quy ước chung theo đặc tả CSDL mục 1: thời gian `Timestamp` UTC, ngày `yyyyMMdd` và giờ `HHmm` theo `Asia/Ho_Chi_Minh`, tiền `Long` VND, 🔒 = chỉ server ghi.

## 1. Firestore: dữ liệu spec 020 đọc (của người khác)

### 1.1. `venues/{venueId}` (D)

Các trường spec 020 dùng và **yêu cầu thêm** với D:

| Trường | Kiểu | Spec 020 dùng để | Yêu cầu với D |
|---|---|---|---|
| `name` | String | Thẻ kết quả, khớp từ khóa trên máy | |
| `nameKeywords` | Array\<String> | `array-contains` token dài nhất | Sinh bằng `KeywordGenerator` (Kotlin) hoặc `_shared/search-keywords.ts` mỗi khi tạo/đổi `name`. ≤ 60 phần tử ([contract](contracts/keyword-normalization.md)) |
| `districtId` | String | Lọc quận, nhãn quận trên thẻ | ID có trong `districts` |
| `location` | GeoPoint | Khoảng cách | |
| `openTime`, `closeTime` | String `HHmm` | Khung giờ phải nằm trong giờ mở cửa | |
| `amenityIds` | Array\<String> | Lọc "đủ tất cả" trên máy | ID có trong `amenities` |
| `photoPaths` | Array\<String> | Ảnh đầu tiên trên thẻ (bucket `venue-photos`) | |
| `minPricePerHour` 🔒 | Long? | Lọc, sắp xếp, "từ X đ/giờ" khi không có khung giờ | **Luôn có trường**: số khi có bảng giá, `null` khi chưa có. Cập nhật mỗi khi bảng giá đổi (R7) |
| `ratingAvg` 🔒, `ratingCount` 🔒 | Double, Int | Sắp xếp và hiển thị điểm | **Luôn có**: `0.0`, `0` khi tạo cơ sở (R7). C cập nhật qua Edge Function `review` (plan 070 R1) |
| `status` | String | Chỉ `ACTIVE` | |

### 1.2. `venues/{venueId}/priceRules/{id}` (D)

`daysOfWeek` (Array\<Int>), `startTime`, `endTime` (`HHmm`, bội số 30 phút), `pricePerHour` (Long, chia hết 2.000). Quy ước `daysOfWeek` **cần chốt** (đề xuất ISO 1 = Thứ hai … 7 = Chủ nhật, research R5).

### 1.3. `venues/{venueId}/courts/{courtId}` (D)

Spec 020 đọc `isActive` để đếm sân con hoạt động.

### 1.4. `slots/{courtId}_{yyyyMMdd}_{HHmm}` (B)

Spec 020 đọc `venueId`, `courtId`, `date`, `startAt`, `status`, `holdExpiresAt`. Bận khi `BOOKED`, `BLOCKED`, hoặc `HOLD` có `holdExpiresAt > now`. Không đọc `holdBy`, `bookingId` (vẫn nằm trong document, research R13).

## 2. Firestore: dữ liệu spec 020 sở hữu (A)

### 2.1. `users/{uid}/searchHistory/{historyId}`

`historyId` = 20 ký tự hex đầu của SHA-256 trên `SearchCriteria.canonicalKey()` (research R11).

| Trường | Kiểu | Bắt buộc | Ràng buộc |
|---|---|---|---|
| `keyword` | String | có (có thể rỗng) | ≤ 100 ký tự, giữ nguyên chữ người dùng gõ để hiển thị |
| `districtId` | String? | | ID danh mục |
| `priceMin`, `priceMax` | Long? | | ≥ 0, bội số 10.000; `priceMin ≤ priceMax` khi có cả hai |
| `date` | String? `yyyyMMdd` | Cùng có hoặc cùng không với `startTime`, `endTime` | |
| `startTime`, `endTime` | String? `HHmm` | | Bội số 30 phút, `endTime > startTime` |
| `amenityIds` | Array\<String> | có | ≤ 20 phần tử, đã sắp xếp |
| `sort` | String | có | `RATING`, `PRICE_ASC`, `NEAREST` |
| `searchedAt` | Timestamp | có | `serverTimestamp()` mỗi lần ghi |
| `createdAt`, `updatedAt` | Timestamp | có | `serverTimestamp()` (đặc tả CSDL mục 1). Khi ghi đè, `createdAt` giữ giá trị cũ |

Không có vị trí (FR-014, FR-021). Tối đa 10 mục mỗi người (app dọn, research R11). Xóa cùng tài khoản qua `delete-account` bước 7 của spec 011.

**Thao tác:**

| Thao tác | Cách làm |
|---|---|
| Ghi một lần tìm | Transaction: `get(doc(historyId))`; chưa có thì `set` đủ trường, đã có thì `update` mọi trường trừ `createdAt` (1 lượt đọc + 1 lượt ghi) |
| Đọc | `orderBy("searchedAt", DESC).limit(10)` |
| Dọn | Sau khi ghi: `orderBy("searchedAt", DESC).startAfter(mục thứ 10).limit(5)` rồi xóa |
| Xóa một / xóa tất cả | `delete()` / `WriteBatch` |

### 2.2. `districts/{districtId}` và `amenities/{amenityId}`

ID là slug ổn định, ví dụ `q10`, `binh-thanh`, `parking`, `drinks`, `shower`, `racket-rental`.

| Collection | Trường |
|---|---|
| `districts` | `name` (String, "Quận 10"), `cityName` (String, "TP. Hồ Chí Minh"), `sortOrder` (Int), `isActive` (Boolean), `createdAt`, `updatedAt` |
| `amenities` | `name` (String, "Gửi xe"), `iconKey` (String, tên drawable `ic_amenity_<iconKey>`), `sortOrder` (Int), `isActive` (Boolean), `createdAt`, `updatedAt` |

Mọi người đọc; chỉ admin ghi (spec 120). `isActive == false` = không còn trong danh mục (FR-010, FR-023). Dữ liệu ban đầu ở `scripts/seed/catalog.json`.

## 3. Mô hình phía app

### 3.1. `feature/search/domain/model`

| Kiểu | Trường chính | Ghi chú |
|---|---|---|
| `SearchCriteria` | `keyword`, `districtId?`, `priceMin?`, `priceMax?`, `timeWindow?`, `amenityIds: Set`, `sort` | Bất biến, `@Serializable`. Contract đầy đủ ở [search-criteria.md](contracts/search-criteria.md) |
| `TimeWindow` | `date: LocalDate`, `start: TimeOfDay`, `end: TimeOfDay` | Bước 30 phút |
| `SortOption` | `RATING` (mặc định), `PRICE_ASC`, `NEAREST` | FR-011 |
| `VenueSummary` | `id`, `name`, `districtId`, `photoPath?`, `location`, `ratingAvg`, `ratingCount`, `minPricePerHour?`, `openTime`, `closeTime`, `amenityIds`, `nameWords` (tên đã chuẩn hóa, tách từ) | Từ document `venues`, chỉ trường công khai |
| `VenueResult` | `venue: VenueSummary`, `price: DisplayPrice`, `freeCourtCount: Int?`, `distanceMeters: Int?` | Một thẻ kết quả |
| `DisplayPrice` | `From(perHour)`, `ForWindow(perHour, window)`, `Contact` | FR-015: "từ X đ/giờ" / "X đ/giờ · HH:mm–HH:mm" / "Liên hệ" |
| `SearchPage` | `items: List<VenueResult>`, `hasMore`, `totalCount: Int?`, `isFromCache`, `capped: Boolean` | `totalCount == null` khi chưa biết (research R3). `capped` khi vượt 150 ứng viên |
| `SearchSession` | `id`, `criteria`, `strategy: SERVER_ORDERED \| CLIENT_ORDERED`, `cursor: SearchCursor` | `SERVER_ORDERED` là S1, `CLIENT_ORDERED` là S2 trong research R3. `SearchCursor` là kiểu **mờ** (opaque) với domain: chỉ `FirestoreVenueSearchEngine` tạo và đọc. Bên trong: S1 giữ `lastSnapshot` và pha sắp xếp giá (`PRICED`/`UNPRICED`); S2 giữ danh sách ứng viên đã sắp theo cận dưới, vị trí đã kiểm tra và kết quả đã xác nhận chưa trả. Domain/UI không phụ thuộc kiểu Firestore |
| `SearchHistoryEntry` | `id`, `criteria`, `searchedAt` | |
| `SanitizeResult` | `criteria`, `dropped: List<DroppedField>` | `DroppedField`: `PAST_DATE`, `DATE_OUT_OF_RANGE`, `INVALID_TIME`, `UNKNOWN_DISTRICT`, `UNKNOWN_AMENITY`, `INVALID_PRICE`, `KEYWORD_TRUNCATED` |

### 3.2. `core/` mới (dùng chung, research R5, R6, R9, R14)

| Package | Thành phần |
|---|---|
| `core/text` | `VietnameseNormalizer`, `KeywordGenerator`, `KeywordQuery` |
| `core/pricing` | `PriceRule`, `SlotPricing` |
| `core/schedule` | `TimeOfDay`, `SlotMath`, `BookingWindow`, `SlotId` |
| `core/location` | `LocationProvider` (interface) + `AndroidLocationProvider`, `GeoDistance`, `DistanceFormatter` |
| `core/catalog` | `CatalogRepository` (interface) + `FirestoreCatalogRepository`, `District`, `Amenity` |

### 3.3. Trạng thái màn hình

```text
SearchUiState(
  criteria: SearchCriteria,
  chips: List<FilterChipUi>,          // FR-009, thứ tự: quận, giá, khung giờ, từng tiện ích
  results: ResultsState,              // Loading | Content(items, hasMore, loadingMore) | Empty | Error(AppError)
  header: HeaderUi,                   // "N sân phù hợp" hoặc "Kết quả phù hợp"
  staleBanner: Boolean,               // FR-020: kết quả từ cache khi mất mạng
  locationAvailable: Boolean,         // FR-013: hiện khoảng cách
  isGuest: Boolean                    // FR-027
)
```

Sự kiện một lần (`Channel`): mở chi tiết sân, xin quyền vị trí, hiện giải thích quyền, snackbar (giá trị bị bỏ khi khôi phục, từ chối quyền, không lấy được vị trí).

### 3.4. Chuyển trạng thái của danh sách kết quả

```text
Loading ──trang đầu có kết quả──▶ Content
Loading ──0 kết quả──▶ Empty                 (FR-019: gợi ý bỏ bớt bộ lọc, nút "Xóa bộ lọc")
Loading ──lỗi, không có cache──▶ Error       (nút "Thử lại" → Loading)
Content ──cuộn cuối, hasMore──▶ Content(loadingMore=true) ──▶ Content
Content ──lỗi khi tải thêm──▶ Content + snackbar "Thử lại"
* ──criteria đổi──▶ Loading                  (hủy phiên cũ, FR-018)
```

## 4. Index

Bảng V1–V12 và S1 ở [research R8](research.md#r8-index-cần-tạo), cú pháp `firestore.indexes.json` ở [contracts/security-rules-and-indexes.md](contracts/security-rules-and-indexes.md#3-firestoreindexesjson).

## Danh sách thay đổi cần đưa vào `docs/design/database/README.md`

1. **Mục 4.3 `venues`:**
   - `nameKeywords`: thay "(A định nghĩa cách sinh)" bằng link tới `specs/020-court-search/contracts/keyword-normalization.md`; ghi giới hạn 60 phần tử.
   - `minPricePerHour` 🔒: kiểu `Long?`, **luôn có trường**, `null` khi chưa có bảng giá; ghi rõ bên cập nhật sau khi D chốt.
   - `ratingAvg`, `ratingCount` 🔒: khởi tạo `0.0`, `0` khi tạo cơ sở.
   - `amenityIds`: đổi ghi chú "Lọc bằng `array-contains-any`" thành "Lọc 'đủ tất cả' trên máy (spec 020 R2)".
   - `priceRules.daysOfWeek`: ghi quy ước đã chốt (đề xuất ISO 1 = Thứ hai).
2. **Mục 4 (thêm mục mới):** `users/{uid}/searchHistory/{id}` (A) theo §2.1; `districts`, `amenities` (A) theo §2.2.
3. **Mục 5 (ID tất định):** thêm dòng `searchHistory`: `sha256(canonicalKey)[0..20)`, lý do "tìm lại cùng tổ hợp không tạo mục trùng".
4. **Mục 6 (index):** thay hai dòng `venues` cũ bằng V1–V12; đổi dòng `slots` thành `venueId`, `date`, `startAt`. Dòng `venues: geohash` ghi "spec 021 quyết định".
5. **Mục 7 (Rules):**
   - `venues`: "Mọi người (kể cả khách) R khi `ACTIVE`".
   - `courts`, `priceRules`: "Mọi người R".
   - `slots`: "Mọi người R (kể cả khách, để lọc giờ trống)".
   - Thêm dòng `districts`, `amenities`: "Mọi người R; admin CUD".
   - Thêm dòng `searchHistory`: "Chủ R/C/U/D; người khác và khách bị chặn".
6. **Mục 9:** câu 6 ghi "Đã chốt 19/10: dùng `nameKeywords` (spec 020 R6)".
